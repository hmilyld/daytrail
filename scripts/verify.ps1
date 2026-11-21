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

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

# 工具链不写死本机路径：优先用已有环境变量，其次探测常见安装位置，
# 最后读 Android Studio 生成的 local.properties 里的 sdk.dir。
function Resolve-JdkHome {
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
        return $env:JAVA_HOME
    }
    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr'),
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Java\jdk-17',
        '/Applications/Android Studio.app/Contents/jbr/Contents/Home'
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path (Join-Path $candidate 'bin\java.exe'))) {
            return $candidate
        }
    }
    throw '未找到 JDK：请设置 JAVA_HOME（需要 JDK 17 或更高）'
}

function Resolve-AndroidSdk {
    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }

    # local.properties 是 Properties 格式：反斜杠与盘符冒号都带转义
    $localProperties = Join-Path $root 'local.properties'
    if (Test-Path $localProperties) {
        $line = Select-String -Path $localProperties -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
        if ($line) {
            $value = ($line.Line -split '=', 2)[1].Trim()
            $value = $value -replace '\\\\', '\' -replace '\\:', ':'
            if (Test-Path $value) { return $value }
        }
    }

    $candidates = @(
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'),
        'C:\Android\Sdk',
        (Join-Path $env:HOME 'Library/Android/sdk')
    )
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }
    throw '未找到 Android SDK：请设置 ANDROID_HOME，或让 Android Studio 生成 local.properties'
}

$env:JAVA_HOME = Resolve-JdkHome
$env:ANDROID_HOME = Resolve-AndroidSdk
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME

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
