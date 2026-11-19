#!/usr/bin/env pwsh
# DayTrail 验收脚本：Debug/Release 构建 + 单测 + lint + 格式化检查。
#
# 用法：
#   .\scripts\verify.ps1            # 完整校验（Debug + Release）
#   .\scripts\verify.ps1 -Quick     # 只跑 Debug 那组
#   .\scripts\verify.ps1 -Install   # 校验后安装到已连接设备并启动
#
# 注意：AGP 9 起本地单元测试统一走 hostTests 模型，任务名不再带 debug/release 前缀。
param(
    [switch]$Quick,
    [switch]$Install
)

$ErrorActionPreference = 'Stop'

# 工具链固定在 E 盘，命令行与 Android Studio 用同一套 JDK
$env:JAVA_HOME = '<ANDROID_TOOLCHAIN>\jdk21'
$env:ANDROID_HOME = '<ANDROID_TOOLCHAIN>\sdk'
$env:ANDROID_SDK_ROOT = '<ANDROID_TOOLCHAIN>\sdk'
$env:GRADLE_USER_HOME = '<ANDROID_TOOLCHAIN>\gradle-home'

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$gradlew = Join-Path $root 'gradlew.bat'

function Invoke-Gradle {
    param([string[]]$Tasks)
    # Windows PowerShell 5.1 在 $ErrorActionPreference='Stop' 下会把原生命令的
    # stderr 当成终止错误——哪怕 Gradle 构建成功（例如 ktlint 的「无法自动修正
    # 全部违规」只是一条提示），脚本也会直接中断。必须在调用期间放宽。
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & $gradlew @Tasks --console=plain 2>&1 | ForEach-Object { Write-Host $_ }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previous
    }
    if ($code -ne 0) {
        throw "Gradle 失败：$($Tasks -join ' ')"
    }
}

Write-Host '=== 1/4 格式化检查 (spotlessCheck) ===' -ForegroundColor Cyan
Invoke-Gradle @(':app:spotlessCheck')

Write-Host '=== 2/4 Debug 构建 + 单测 + lint ===' -ForegroundColor Cyan
Invoke-Gradle @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')

if (-not $Quick) {
    Write-Host '=== 3/4 Release 构建 + 单测 + lint ===' -ForegroundColor Cyan
    Invoke-Gradle @(':app:assembleRelease', ':app:testReleaseUnitTest', ':app:lintRelease')
}

Write-Host '=== 4/4 产物 ===' -ForegroundColor Cyan
$apks = Get-ChildItem -Path 'app\build\outputs\apk' -Filter '*.apk' -Recurse -ErrorAction SilentlyContinue
foreach ($apk in $apks) {
    $sizeMb = [math]::Round($apk.Length / 1MB, 2)
    Write-Host ("  {0}  ({1} MB)" -f $apk.FullName, $sizeMb)
}

if ($Install) {
    Write-Host '=== 安装到已连接的设备 ===' -ForegroundColor Cyan
    $adb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
    & $adb install -r 'app\build\outputs\apk\debug\app-debug.apk'
    if ($LASTEXITCODE -ne 0) { throw 'adb install 失败' }
    & $adb shell am start -n 'com.hmilyld.daytrail.debug/com.hmilyld.daytrail.MainActivity'
}

Write-Host ''
Write-Host '全部通过。' -ForegroundColor Green
