#!/usr/bin/env pwsh
# DayTrail 一键改造向导（Windows）。
#
# 两种用法：
#   1) 普通人：双击仓库根目录的 setup-my-app.cmd，按提示输入即可。
#   2) AI / 自动化：带参数运行，全程不提问，结束时用 -Json 输出一行机器可读结果。
#      完整契约见 docs/ai-prompt.md。
#
# 这个脚本只改下面这几处，改完可以随时用 git checkout 还原：
#   gradle.properties           app.applicationId / app.updateRepo
#   res/values/strings.xml      应用名（含两处正文里的品牌词）
#   res/values/ic_launcher_background.xml   图标底色
#   app/keystore/release.jks + keystore.properties   签名（只在缺失时生成）
#   local.properties            只增改 AMAP_* 三行
#   dist/amap-fill-card.txt     给高德控制台照着填的卡片
#
# 注意：本文件必须保存为 UTF-8 with BOM 且使用 CRLF。
# Windows PowerShell 5.1 对无 BOM 的文件按 ANSI 解码，中文注释会让整个脚本解析失败。

param(
    [string]$Name,
    [string]$Id,
    [string]$Package,
    [ValidateSet('release', 'debug')][string]$Type = 'release',
    [string]$KeyRelease,
    [string]$KeyWeb,
    [string]$KeyDebug,
    [string]$IconColor,
    [switch]$NoBuild,
    [switch]$Json,
    [switch]$Yes,
    [switch]$DryRun,
    [switch]$Install,
    [switch]$Advanced,
    [switch]$UseChinaMirrors,
    [switch]$Help
)

$ErrorActionPreference = 'Stop'

# 输出编码固定为 UTF-8：AI 通过管道读取结果时不会乱码，Windows 10/11 的控制台也能正常显示中文。
# 只设 .NET 的 OutputEncoding 在某些环境下仍会沿用本地代码页（936），先切控制台代码页更稳。
try { & chcp.com 65001 > $null } catch { }
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch { }
$OutputEncoding = [System.Text.Encoding]::UTF8

# 用一句人话收尾，而不是把 PowerShell 的堆栈直接甩给用户。
trap {
    Write-Host ''
    Write-Host ("  出错了：" + $_.Exception.Message) -ForegroundColor Red
    Write-Host '  如果看不懂这句报错，请把窗口里的文字整段复制给帮你的人。' -ForegroundColor Yellow
    exit 2
}

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

. (Join-Path $PSScriptRoot 'toolchain.ps1')

$script:DefaultPackage = 'com.hmilyld.daytrail'
$script:DefaultName = '行迹'
$script:DefaultIconColor = '#2F6BFF'
$script:StepTotal = 7
$script:StringsPath = Join-Path $root 'app/src/main/res/values/strings.xml'
$script:IconColorPath = Join-Path $root 'app/src/main/res/values/ic_launcher_background.xml'
$script:LocalPropertiesPath = Join-Path $root 'local.properties'
$script:GradlePropertiesPath = Join-Path $root 'gradle.properties'
$script:KeystorePropertiesPath = Join-Path $root 'keystore.properties'
$script:ReleaseJksPath = Join-Path $root 'app/keystore/release.jks'
$script:DistPath = Join-Path $root 'dist'

# 只要给了任意参数就进入无人值守模式：AI 调用时不能卡在 Read-Host 上。
$script:NonInteractive = [bool](
    $Json -or $Yes -or $NoBuild -or $DryRun -or $UseChinaMirrors -or $Name -or $Id -or $Package -or
        $KeyRelease -or $KeyWeb -or $KeyDebug -or $IconColor
)

function Show-Help {
    Write-Host @'
DayTrail 一键改造向导

普通人（推荐）：
  双击仓库根目录的 setup-my-app.cmd，按窗口里的提示输入即可。

AI 或自动化（全程不提问）：
  powershell -ExecutionPolicy Bypass -File scripts\setup-my-app.ps1 `
    -Name 山行 -Id zhangsan -Type release -NoBuild -Json

参数：
  -Name <显示名>        手机桌面上显示的名字，中文即可（建议 12 个字以内，最多 24）
  -Id <英文昵称>        小写字母或数字，用来拼包名 com.<昵称>.daytrail
  -Package <完整包名>   直接指定包名，优先于 -Id
  -Type release|debug   安装包类型，默认 release（只要两个高德 Key）
  -KeyRelease <Key>     高德 Android 平台 SDK Key（正式版）
  -KeyWeb <Key>         高德 Web 服务 Key（补全路线、地点名反查）
  -KeyDebug <Key>       高德 Android 平台 SDK Key（调试版，-Type debug 时用）
  -IconColor <#RRGGBB>  图标底色，例如 #E4572E
  -NoBuild              只做准备（改名、生成签名、出卡片），不编译
  -Install              编译完成后安装到已连接的手机
  -UseChinaMirrors      下载很慢或打不开时使用国内镜像（阿里云 Maven + 腾讯云 Gradle）
  -Json                 结束时在最后输出一行 JSON
  -DryRun               只打印将要修改的内容，不写任何文件
  -Yes                  跳过确认
  -Advanced             交互模式下允许直接输入完整包名
  -Help                 显示本说明

改完想还原：重新解压一次 ZIP，或 git checkout -- gradle.properties app/src/main/res
'@
}

if ($Help) {
    Show-Help
    exit 0
}

function Test-CommandExists {
    param([string]$Command)
    $null -ne (Get-Command $Command -ErrorAction SilentlyContinue)
}

function Write-Step {
    param([int]$Number, [string]$Title)
    Write-Host ''
    Write-Host ('=' * 62) -ForegroundColor DarkGray
    Write-Host ("  DayTrail · 编译你自己的版本        第 {0} 步 / 共 {1} 步" -f $Number, $script:StepTotal) -ForegroundColor Cyan
    Write-Host ("  $Title") -ForegroundColor Cyan
    Write-Host ('=' * 62) -ForegroundColor DarkGray
}

function Write-Ok {
    param([string]$Message)
    Write-Host "  [完成] $Message" -ForegroundColor Green
}

function Write-Warn {
    param([string]$Message)
    Write-Host "  [注意] $Message" -ForegroundColor Yellow
}

function Read-EnterToContinue {
    param([string]$Message = '按回车继续')
    if ($script:NonInteractive) { return }
    Read-Host $Message | Out-Null
}

function Read-TextInput {
    param([string]$Prompt, [string]$Default = '')

    if ($script:NonInteractive) {
        if ([string]::IsNullOrWhiteSpace($Default)) {
            throw "非交互模式缺少参数：$Prompt"
        }
        return $Default
    }

    while ($true) {
        $hint = if ($Default) { "（直接回车 = $Default）" } else { '' }
        $answer = Read-Host "$Prompt$hint"
        if ([string]::IsNullOrWhiteSpace($answer)) { $answer = $Default }
        if (-not [string]::IsNullOrWhiteSpace($answer)) { return $answer.Trim() }
        Write-Host '  这里不能空着，请重新输入。' -ForegroundColor Yellow
    }
}

function Read-ChoiceInput {
    param([string]$Prompt, [string[]]$Options, [int]$Default = 1)

    if ($script:NonInteractive) { return $Default }

    while ($true) {
        for ($i = 0; $i -lt $Options.Count; $i++) {
            Write-Host ("    {0}. {1}" -f ($i + 1), $Options[$i])
        }
        $answer = Read-Host "$Prompt（直接回车 = $Default）"
        if ([string]::IsNullOrWhiteSpace($answer)) { return $Default }
        $parsed = 0
        if ([int]::TryParse($answer, [ref]$parsed) -and $parsed -ge 1 -and $parsed -le $Options.Count) {
            return $parsed
        }
        Write-Host '  请输入列表里的序号。' -ForegroundColor Yellow
    }
}

function Get-TextFile {
    param([string]$Path)
    return [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
}

function Set-TextFile {
    param([string]$Path, [string]$Text, [switch]$Bom)
    # 默认写不带 BOM 的 UTF-8；原文件本来带 BOM 时由调用方传 -Bom 保持原样，
    # 避免为改一个字符串把整个文件的第一行都算成改动。
    $encoding = New-Object System.Text.UTF8Encoding($Bom.IsPresent)
    [System.IO.File]::WriteAllText($Path, $Text, $encoding)
}

function Test-FileHasBom {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return $false }
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $first = $stream.ReadByte()
        $second = $stream.ReadByte()
        $third = $stream.ReadByte()
        return ($first -eq 0xEF -and $second -eq 0xBB -and $third -eq 0xBF)
    } finally {
        $stream.Dispose()
    }
}

function Get-NewLineOf {
    param([string]$Text)
    if ($Text -match "`r`n") { return "`r`n" }
    return "`n"
}

function Get-StringResourceValue {
    param([string]$Key)
    if (-not (Test-Path $script:StringsPath)) { return $null }
    $text = Get-TextFile $script:StringsPath
    $pattern = '<string name="' + [regex]::Escape($Key) + '">(.*?)</string>'
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) { return $null }
    return [System.Net.WebUtility]::HtmlDecode($match.Groups[1].Value)
}

function Set-StringResourceValue {
    param([string]$Key, [string]$Value, [string]$From = '')

    $text = Get-TextFile $script:StringsPath
    $pattern = '(<string name="' + [regex]::Escape($Key) + '"[^>]*>)(.*?)(</string>)'
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) { throw "strings.xml 里找不到名为 $Key 的字符串" }

    $old = $match.Groups[2].Value
    if ($From) {
        # 正文里嵌着旧名字，只替换旧名字本身，保留句子其余部分。
        $newContent = $old.Replace($From, $Value)
        if ($newContent -eq $old) {
            # 旧名字没有出现在这句正文里（例如用户之前改过名），退回替换项目原名。
            $newContent = $old.Replace($script:DefaultName, $Value)
        }
    } else {
        $newContent = $Value
    }

    $escaped = [System.Security.SecurityElement]::Escape($newContent)
    $replacement = $match.Groups[1].Value + $escaped + $match.Groups[3].Value
    $text = $text.Substring(0, $match.Index) + $replacement + $text.Substring($match.Index + $match.Length)
    Set-TextFile $script:StringsPath $text -Bom:(Test-FileHasBom $script:StringsPath)
}

function Set-PropertyLine {
    param([string]$Path, [string]$Key, [string]$Value)

    $text = if (Test-Path $Path) { Get-TextFile $Path } else { '' }
    $nl = Get-NewLineOf $text
    $lines = @()
    if ($text.Length -gt 0) { $lines = $text -split "`r?`n" }

    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*='
    $found = $false
    for ($i = 0; $i -lt $lines.Count; $i++) {
        if ($lines[$i] -match $pattern) {
            $lines[$i] = "$Key=$Value"
            $found = $true
        }
    }
    if (-not $found) { $lines += "$Key=$Value" }

    $result = $lines -join $nl
    if ($result -notmatch "(`r`n|`n)$") { $result += $nl }
    Set-TextFile $Path $result
}

function Get-PropertyLine {
    param([string]$Path, [string]$Key)
    if (-not (Test-Path $Path)) { return $null }
    $text = Get-TextFile $Path
    $pattern = '(?m)^\s*' + [regex]::Escape($Key) + '\s*=\s*(.*)$'
    $match = [regex]::Match($text, $pattern)
    if (-not $match.Success) { return $null }
    return $match.Groups[1].Value.Trim()
}

function Get-AmapKeyFromLocal {
    param([string]$Key)
    return Get-PropertyLine -Path $script:LocalPropertiesPath -Key $Key
}

function Format-MaskedKey {
    param([string]$Value)
    if ([string]::IsNullOrWhiteSpace($Value)) { return '（未填写）' }
    if ($Value.Length -le 8) { return '****' }
    return $Value.Substring(0, 4) + '****' + $Value.Substring($Value.Length - 4)
}

function Assert-AppName {
    param([string]$Value)
    $Value = $Value.Trim()
    if ([string]::IsNullOrWhiteSpace($Value)) { throw '应用名不能为空' }
    if ($Value.Length -gt 24) { throw '应用名太长（最多 24 个字），建议控制在 12 个字以内' }
    if ($Value.Length -gt 12) { Write-Warn '名字超过 12 个字，手机桌面可能显示不全' }
    if ($Value -match '[&<>"'']') { throw '应用名里不能出现 & < > " 或单引号' }
    if ($Value -match '[\\/:*?|]') { throw '应用名里不能出现 \ / : * ? | 这些符号' }
    if ($Value.EndsWith('.')) { throw '应用名不要以句点结尾' }
    return $Value
}

function Assert-Nickname {
    param([string]$Value)
    $Value = $Value.Trim().ToLowerInvariant()
    if ($Value -notmatch '^[a-z][a-z0-9]{0,15}$') {
        throw '昵称只能用小写字母和数字，并以字母开头，例如 zhangsan、xiaoli2024'
    }
    return $Value
}

function Assert-PackageName {
    param([string]$Value)
    $Value = $Value.Trim()
    if ($Value -notmatch '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$') {
        throw '包名格式不对：只能用小写字母、数字、下划线，至少两段，例如 com.zhangsan.daytrail'
    }
    if ($Value -eq $script:DefaultPackage) {
        throw '这是本项目的包名。请换成自己的，否则会和本项目发布的安装包冲突，高德也会把调用量记到同一把 Key 上'
    }
    if ($Value -match '^com\.example\.') {
        throw 'com.example 是示例包名，请换成自己的昵称，例如 com.zhangsan.daytrail'
    }
    return $Value
}

function Assert-IconColor {
    param([string]$Value)
    $Value = $Value.Trim()
    if ($Value -notmatch '^#[0-9A-Fa-f]{6}$') {
        throw '颜色要写成 #RRGGBB 的形式，例如 #E4572E'
    }
    return $Value.ToUpperInvariant()
}

function New-RandomPassword {
    $alphabet = (48..57) + (65..90) + (97..122)
    return -join ($alphabet | Get-Random -Count 24 | ForEach-Object { [char]$_ })
}

function Get-KeytoolPath {
    $candidate = Join-Path $env:JAVA_HOME 'bin/keytool.exe'
    if (Test-Path $candidate) { return $candidate }
    $candidate = Join-Path $env:JAVA_HOME 'bin/keytool'
    if (Test-Path $candidate) { return $candidate }
    if (Test-CommandExists 'keytool') { return 'keytool' }
    throw '找不到 keytool：请确认 JAVA_HOME 指向一个完整的 JDK'
}

function Get-KeystoreSha1 {
    param([string]$Path, [string]$Alias, [string]$Password)

    if (-not (Test-Path $Path)) { return $null }
    $keytool = Get-KeytoolPath
    $previous = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $output = & $keytool -list -v -keystore $Path -alias $Alias -storepass $Password 2>&1
    } finally {
        $ErrorActionPreference = $previous
    }
    foreach ($line in $output) {
        if ("$line" -match '^\s*SHA1:\s*([0-9A-Fa-f:]{59})\s*$') {
            return $Matches[1].ToUpperInvariant()
        }
    }
    return $null
}

function Get-AdbPath {
    $candidate = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
    if (Test-Path $candidate) { return $candidate }
    $candidate = Join-Path $env:ANDROID_HOME 'platform-tools/adb'
    if (Test-Path $candidate) { return $candidate }
    if (Test-CommandExists 'adb') { return 'adb' }
    return $null
}

# 结果同时写进 dist/setup-result.json：管道编码在 Windows 上不可靠，
# AI 读文件比解析控制台输出稳。
function Write-Result {
    param([System.Collections.Specialized.OrderedDictionary]$Data)

    $text = ([pscustomobject]$Data) | ConvertTo-Json -Compress
    if (-not $DryRun) {
        New-Item -ItemType Directory -Force -Path $script:DistPath | Out-Null
        Set-TextFile (Join-Path $script:DistPath 'setup-result.json') ($text + "`n")
    }
    if ($Json) { [Console]::Out.WriteLine($text) }
}

# 构建失败时用日志里的实际证据给提示，不靠猜用户网络环境。
function Show-BuildFailureHint {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return }
    $text = Get-TextFile $Path
    if ($text -match '(?i)(Failed to find target with hash string|android-37|SDK platform|license|not accepted)') {
        Write-Host '  日志里看起来是「Android SDK 平台没装全」：按第 1 步的提示装 API 37 后再试。' -ForegroundColor Yellow
    } elseif ($text -match '(?i)(services\.gradle\.org|dl\.google\.com|maven\.google\.com|aliyun|tencent|Could not resolve|ConnectException|Timeout|timed out|UnknownHost)') {
        Write-Host '  日志里看起来是「下载被挡住或太慢」：换网络/手机热点，或按文档第 14 节换镜像。' -ForegroundColor Yellow
    }
}

# 国内网络加速：把 Gradle 发行包指到腾讯云镜像，并在本次构建里挂上阿里云 Maven 镜像。
# 只改 gradle-wrapper.properties 一行；想还原执行
#   git checkout -- gradle/wrapper/gradle-wrapper.properties
function Enable-ChinaMirrors {
    $wrapper = Join-Path $root 'gradle/wrapper/gradle-wrapper.properties'
    if (-not (Test-Path $wrapper)) {
        Write-Warn '找不到 gradle-wrapper.properties，跳过 Gradle 镜像'
        return
    }
    $text = Get-TextFile $wrapper
    if ($text.Contains('services.gradle.org/distributions/')) {
        $text = $text.Replace(
            'services.gradle.org/distributions/',
            'mirrors.cloud.tencent.com/gradle/'
        )
        Set-TextFile $wrapper $text
        Write-Ok '已把 Gradle 下载地址换成腾讯云镜像（还原：重新解压一次 ZIP，或执行 git checkout -- gradle/wrapper/gradle-wrapper.properties）'
    } else {
        Write-Ok 'Gradle 下载地址已不是官方源，保持原样'
    }
    Write-Ok '本次构建会通过阿里云镜像下载依赖（不改项目文件）'
}

# ---------------------------------------------------------------- 准备阶段

if (-not (Test-Path (Join-Path $root 'gradlew.bat'))) {
    throw '请在项目根目录运行：这里找不到 gradlew.bat。如果你是双击运行，请先解压 ZIP，再到解压后的文件夹里双击 setup-my-app.cmd'
}
if (-not (Test-Path (Join-Path $root 'app/build.gradle.kts'))) {
    throw '项目文件不完整：找不到 app/build.gradle.kts'
}

Write-Step 1 '检查电脑环境'
Write-Host '  正在找 Java 和 Android SDK，第一次可能要几秒……'

$sdkDir = $null
$probeError = $null
try {
    $toolchain = Set-ToolchainEnvironment -Root $root
    $sdkDir = $toolchain.AndroidSdk
    Write-Ok '找到 Java'
    Write-Ok '找到 Android SDK'
} catch {
    $probeError = $_.Exception.Message
    $sdkDir = $null
}

if (-not $sdkDir) {
    if ($script:NonInteractive) {
        Write-Host ''
        Write-Host '  这台电脑还没找到编译需要的工具。' -ForegroundColor Yellow
        Write-Host '  请先安装 Android Studio（自带 Java 与 Android SDK，官网免费）：' -ForegroundColor Yellow
        Write-Host '    https://developer.android.google.cn/studio' -ForegroundColor Yellow
        Write-Host '  装好后打开一次，等 SDK 下载完再关，然后重新运行。' -ForegroundColor Yellow
        Write-Host "  探测结果：$probeError" -ForegroundColor DarkGray
        exit 1
    }

    # 自动探测失败不代表没装：很多人把 Android Studio 装在 D 盘，探测不到。
    Write-Host ''
    Write-Host '  没有自动找到，但你可能已经装过了。可以手动指一下位置。' -ForegroundColor Yellow
    Write-Host '  小技巧：在文件资源管理器里找到那个文件夹，用鼠标拖进这个窗口，路径会自动填好。'
    Write-Host ''
    $sdkManual = Read-Host '  Android SDK 文件夹路径（直接回车 = 跳过，先看下面的说明）'
    $javaManual = Read-Host '  Java 文件夹路径（Android Studio 里的 jbr；直接回车 = 跳过）'
    $sdkManual = ($sdkManual -replace '^"|"$', '').Trim()
    $javaManual = ($javaManual -replace '^"|"$', '').Trim()
    $javaOk = (Test-Path (Join-Path $javaManual 'bin/java.exe')) -or (Test-Path (Join-Path $javaManual 'bin/java'))
    if ([string]::IsNullOrWhiteSpace($sdkManual) -or [string]::IsNullOrWhiteSpace($javaManual) -or
        -not (Test-Path (Join-Path $sdkManual 'platforms')) -or -not $javaOk) {
        Write-Host ''
        Write-Host '  这个位置找不到需要的文件，或者还没装 Android Studio。' -ForegroundColor Yellow
        Write-Host '  请到 https://developer.android.google.cn/studio 下载安装（免费），装完打开一次。' -ForegroundColor Yellow
        Write-Host "  探测结果：$probeError" -ForegroundColor DarkGray
        exit 1
    }
    $env:JAVA_HOME = $javaManual
    $env:ANDROID_HOME = $sdkManual
    $env:ANDROID_SDK_ROOT = $sdkManual
    $sdkDir = $sdkManual
    $escapedSdk = $sdkDir -replace '\\', '\\' -replace ':', '\:'
    Set-PropertyLine -Path $script:LocalPropertiesPath -Key 'sdk.dir' -Value $escapedSdk
    Write-Ok '已记住你指定的位置'
}

# 只检查目录存在还不够：API 37 的编译平台没装的话，要等构建十几分钟后才报看不懂的错。
$hasApi37 = (Test-Path (Join-Path $sdkDir 'platforms/android-37.0')) -or
    (Test-Path (Join-Path $sdkDir 'platforms/android-37'))
if (-not $hasApi37) {
    Write-Host ''
    Write-Host '  还差一个东西：Android 17（API 37）的编译平台。' -ForegroundColor Yellow
    Write-Host '  请打开 Android Studio → Tools → SDK Manager → SDK Platforms，' -ForegroundColor Yellow
    Write-Host '  勾选「Android SDK Platform 37.0」（在 Android 17 / Cinnamon Bun 下面），点 Apply 下载。' -ForegroundColor Yellow
    Write-Host '  再到 SDK Tools → SDK Build-Tools 里勾选 37.x，点 Apply。' -ForegroundColor Yellow
    Write-Host '  列表里找不到 37 的话，说明 Android Studio 太旧，先 Help → Check for Updates 更新。' -ForegroundColor Yellow
    Write-Host '  装好后重新双击本脚本即可。' -ForegroundColor Yellow
    exit 1
}
Write-Ok '找到 API 37 编译平台'

Write-Ok '找到项目文件'
if (-not (Test-Path $script:LocalPropertiesPath)) {
    Write-Warn '还没有 local.properties，稍后会按找到的 SDK 路径自动生成'
}

$currentName = Get-StringResourceValue -Key 'app_name'
if (-not $currentName) { $currentName = $script:DefaultName }
$currentPackage = Get-AppApplicationId -Root $root
$currentIconColor = '#2F6BFF'
if (Test-Path $script:IconColorPath) {
    $iconText = Get-TextFile $script:IconColorPath
    $iconMatch = [regex]::Match($iconText, '<color name="ic_launcher_background">(#[0-9A-Fa-f]{6})</color>')
    if ($iconMatch.Success) { $currentIconColor = $iconMatch.Groups[1].Value.ToUpperInvariant() }
}

$hasKeystore = (Test-Path $script:ReleaseJksPath) -and (Test-Path $script:KeystorePropertiesPath)
$currentKeyRelease = Get-AmapKeyFromLocal 'AMAP_KEY_RELEASE'
$currentKeyWeb = Get-AmapKeyFromLocal 'AMAP_KEY_WEB'
$currentKeyDebug = Get-AmapKeyFromLocal 'AMAP_KEY_DEBUG'

Write-Host ''
Write-Host '  现在的状态：'
$nameMark = if ($currentName -eq $script:DefaultName) { '[ ]' } else { '[x]' }
$pkgMark = if ($currentPackage -eq $script:DefaultPackage) { '[ ]' } else { '[x]' }
$signMark = if ($hasKeystore) { '[x]' } else { '[ ]' }
$keyMark = if ($currentKeyRelease -and $currentKeyWeb) { '[x]' } else { '[ ]' }
Write-Host ("    {0} 应用名：{1}" -f $nameMark, $currentName)
Write-Host ("    {0} 包名：{1}" -f $pkgMark, $currentPackage)
Write-Host ("    {0} 签名文件" -f $signMark)
Write-Host ("    {0} 高德 Key：正式版 {1} / Web 服务 {2}" -f $keyMark, (Format-MaskedKey $currentKeyRelease), (Format-MaskedKey $currentKeyWeb))
Write-Host ''
if (-not $script:NonInteractive) {
    Write-Host '  已经完成的项目会自动跳过，从没做的那一步接着做。'
    Read-EnterToContinue '按回车继续'
}

# ---------------------------------------------------------------- 第 2 步：名字与包名

Write-Step 2 '给应用起名字'

if ($Name) {
    $finalName = Assert-AppName $Name
} elseif ($script:NonInteractive) {
    $finalName = $currentName
    Write-Warn "没有指定 -Name，沿用当前名字：$finalName"
} else {
    Write-Host '  这个名字会显示在手机桌面的图标下面，用中文就行。'
    if ($currentName -eq $script:DefaultName) {
        Write-Host '  （还是默认的「行迹」，建议换成自己的名字）' -ForegroundColor DarkGray
    }
    $finalName = Assert-AppName (Read-TextInput '请输入名字' $currentName)
}
Write-Ok "应用名：$finalName"

if ($Package) {
    $finalPackage = Assert-PackageName $Package
} elseif ($Id) {
    $finalPackage = Assert-PackageName ("com.{0}.daytrail" -f (Assert-Nickname $Id))
} elseif ($script:NonInteractive) {
    $finalPackage = $currentPackage
    Write-Warn "没有指定 -Package / -Id，沿用当前包名：$finalPackage"
} else {
    Write-Host ''
    Write-Host '  接下来要生成「包名」。它是应用在手机里的身份证，不能和别人重复。'
    Write-Host '  你只需要给一个英文昵称，剩下的交给我。'
    Write-Host '  昵称只能用小写字母和数字，例如 zhangsan、xiaoli2024。'
    Write-Host ''

    while ($true) {
        if ($Advanced) {
            $finalPackage = Assert-PackageName (Read-TextInput '请输入完整包名' $currentPackage)
        } else {
            $suggestId = if ($currentPackage -match '^com\.([a-z0-9_]+)\.daytrail$') { $Matches[1] } else { '' }
            while ($true) {
                $nickname = Read-TextInput '请输入昵称' $suggestId
                try {
                    $finalPackage = Assert-PackageName ("com.{0}.daytrail" -f (Assert-Nickname $nickname))
                    break
                } catch {
                    Write-Host ("  " + $_.Exception.Message) -ForegroundColor Yellow
                }
            }
        }

        Write-Host ''
        Write-Host "  你的包名将是：$finalPackage" -ForegroundColor White
        $confirm = Read-Host '  用它吗？（直接回车 = 是，输入 n 重来）'
        if ($confirm -notmatch '^(n|N|no|NO)$') { break }
        Write-Host '  好的，重新来一次。'
    }
}
if ($finalPackage -ne $currentPackage) {
    Write-Ok "包名：$finalPackage"
}

# ---------------------------------------------------------------- 第 3 步：安装包类型

Write-Step 3 '选择安装包类型'

if ($PSBoundParameters.ContainsKey('Type')) {
    $finalType = $Type
} else {
    Write-Host '  正式版只需要两个高德 Key，签名长期有效，推荐。'
    Write-Host '  调试版给开发者用，需要三个 Key，而且名字带 debug 后缀。'
    $choice = Read-ChoiceInput '请选择' @('正式版（推荐）', '调试版') 1
    $finalType = if ($choice -eq 2) { 'debug' } else { 'release' }
}
Write-Ok ("安装包类型：" + $(if ($finalType -eq 'release') { '正式版' } else { '调试版' }))

# ---------------------------------------------------------------- 第 4 步：签名

Write-Step 4 '生成签名文件'

if ($hasKeystore) {
    Write-Ok '已经有签名文件，直接沿用（这样以后升级才能覆盖安装）'
    $storePassword = Get-PropertyLine -Path $script:KeystorePropertiesPath -Key 'storePassword'
    $keyAlias = Get-PropertyLine -Path $script:KeystorePropertiesPath -Key 'keyAlias'
    if (-not $keyAlias) { $keyAlias = 'daytrail' }
} else {
    if (Test-Path $script:ReleaseJksPath) {
        throw '发现 app/keystore/release.jks 还在，但 keystore.properties 不见了（口令丢失）。请先找回那个文件；如果你确定不要这份旧签名，删掉 app/keystore/release.jks 再运行'
    }
    Write-Host '  签名文件相当于应用的身份证。没有它，以后就无法覆盖升级。'
    Write-Host '  我会自动生成一个，口令随机，保存在 keystore.properties 里。'
    Write-Host ''
    if (-not $script:NonInteractive) {
        $answer = Read-Host '  现在生成吗？（直接回车 = 是）'
        if ($answer -match '^(n|N|no|NO)$') {
            Write-Warn '已跳过签名生成，只能编译调试版'
            $finalType = 'debug'
        }
    }
    $storePassword = New-RandomPassword
    $keyAlias = 'daytrail'
    if ($finalType -eq 'release' -and -not $DryRun) {
        $keytool = Get-KeytoolPath
        New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:ReleaseJksPath) | Out-Null
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try {
            & $keytool -genkeypair -v `
                -keystore $script:ReleaseJksPath -storetype PKCS12 -alias $keyAlias `
                -keyalg RSA -keysize 4096 -validity 10950 `
                -storepass $storePassword -keypass $storePassword `
                -dname 'CN=DayTrail, OU=Personal, O=Personal, L=Unknown, ST=Unknown, C=CN' 2>&1 |
                ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }
            $code = $LASTEXITCODE
        } finally {
            $ErrorActionPreference = $previous
        }
        if ($code -ne 0) { throw '生成签名文件失败（keytool 报错）。请把上面的红字发给帮你的人' }

        Set-TextFile $script:KeystorePropertiesPath @"
storeFile=app/keystore/release.jks
storePassword=$storePassword
keyAlias=$keyAlias
keyPassword=$storePassword
"@
        Write-Ok '签名文件已生成：app/keystore/release.jks'
        Write-Warn '记得备份 app/keystore/release.jks 和 keystore.properties，丢了就无法覆盖升级'
    } elseif ($DryRun) {
        Write-Warn '试运行：不会真的生成签名文件'
    } else {
        Write-Ok '调试版使用仓库内置的调试签名，不需要生成正式签名'
    }
}

# ---------------------------------------------------------------- 第 5 步：高德填写卡

Write-Step 5 '给你一张高德 Key 填写卡'

$sha1Release = $null
$sha1Debug = $null
if ($hasKeystore -or (-not $DryRun -and $finalType -eq 'release' -and (Test-Path $script:ReleaseJksPath))) {
    if ($storePassword) {
        $sha1Release = Get-KeystoreSha1 -Path $script:ReleaseJksPath -Alias $keyAlias -Password $storePassword
    } else {
        Write-Warn '读不到签名口令，跳过 SHA1 读取（重新运行本脚本可补齐）'
    }
}
$debugJks = Join-Path $root 'app/keystore/debug.jks'
$sha1Debug = Get-KeystoreSha1 -Path $debugJks -Alias 'androiddebugkey' -Password 'android'

$debugPackage = "$finalPackage.debug"
$sha1ReleaseText = if ($sha1Release) { $sha1Release } else { '（还没生成，稍后重新运行本脚本）' }
$sha1DebugText = if ($sha1Debug) { $sha1Debug } else { '（读不到 app/keystore/debug.jks）' }
$card = @"
DayTrail · 高德 Key 填写卡
生成时间：$(Get-Date -Format 'yyyy-MM-dd HH:mm')
应用名：$finalName

包名（PackageName）：
  正式版：$finalPackage
  调试版：$debugPackage

SHA1：
  正式版：$sha1ReleaseText
  调试版：$sha1DebugText

到高德开放平台 https://console.amap.com/dev/key 新建 Key 时照着填：
  1) Android 平台 SDK（正式版）
     PackageName = $finalPackage
     发布版安全码 SHA1 = $sha1ReleaseText
  2) Android 平台 SDK（调试版，只有用 Android Studio 调试才需要）
     PackageName = $debugPackage
     调试版安全码 SHA1 = $sha1DebugText
  3) Web 服务（不需要包名与 SHA1，用于「补全路线」和地点名反查）

这个文件里没有任何 Key 本身，可以放心保存。
"@

Write-Host ''
Write-Host $card
if (-not $DryRun) {
    New-Item -ItemType Directory -Force -Path $script:DistPath | Out-Null
    Set-TextFile (Join-Path $script:DistPath 'amap-fill-card.txt') $card
    Write-Ok "已保存到 dist/amap-fill-card.txt"
}
if (-not $script:NonInteractive) {
    $openConsole = Read-Host '  要我帮你打开高德控制台吗？（直接回车 = 是，输入 n 跳过）'
    if ($openConsole -notmatch '^(n|N|no|NO)$') {
        try { Start-Process 'https://console.amap.com/dev/key' } catch { Write-Warn '打不开浏览器，请手动访问 https://console.amap.com/dev/key' }
    }
    Read-EnterToContinue '填好 Key 后按回车继续（也可以现在先跳过）'
}

# ---------------------------------------------------------------- 第 6 步：填 Key

Write-Step 6 '把高德 Key 填进来'

$finalKeyRelease = if ($KeyRelease) { $KeyRelease.Trim() } else { $currentKeyRelease }
$finalKeyWeb = if ($KeyWeb) { $KeyWeb.Trim() } else { $currentKeyWeb }
$finalKeyDebug = if ($KeyDebug) { $KeyDebug.Trim() } else { $currentKeyDebug }

if (-not $script:NonInteractive -and -not $KeyRelease) {
    Write-Host '  下面两项可以直接粘进来。还没有 Key 就一路回车跳过，之后重新双击本脚本接着做。'
    Write-Host ''
    Write-Host ("  现在：正式版 {0}／Web 服务 {1}" -f (Format-MaskedKey $finalKeyRelease), (Format-MaskedKey $finalKeyWeb))
    $entry = Read-Host '  正式版 Key（直接回车 = 保持不变／跳过）'
    if (-not [string]::IsNullOrWhiteSpace($entry) -and $entry -notmatch '^(skip|SKIP|跳过)$') { $finalKeyRelease = $entry.Trim() }
    $entry = Read-Host '  Web 服务 Key（直接回车 = 保持不变／跳过）'
    if (-not [string]::IsNullOrWhiteSpace($entry) -and $entry -notmatch '^(skip|SKIP|跳过)$') { $finalKeyWeb = $entry.Trim() }
    if ($finalType -eq 'debug') {
        $entry = Read-Host '  调试版 Key（直接回车 = 保持不变／跳过）'
        if (-not [string]::IsNullOrWhiteSpace($entry) -and $entry -notmatch '^(skip|SKIP|跳过)$') { $finalKeyDebug = $entry.Trim() }
    }
}

foreach ($pair in @(
        @{ Label = '正式版 Key'; Value = $finalKeyRelease },
        @{ Label = 'Web 服务 Key'; Value = $finalKeyWeb },
        @{ Label = '调试版 Key'; Value = $finalKeyDebug }
    )) {
    if ($pair.Value -and $pair.Value -notmatch '^[0-9A-Fa-f]{32}$') {
        Write-Warn ("{0} 不像 32 位十六进制（高德通常给 32 位），仍会保存，请自己核对" -f $pair.Label)
    }
}

if (-not $DryRun) {
    if (-not (Test-Path $script:LocalPropertiesPath)) {
        $escapedSdk = $sdkDir -replace '\\', '\\' -replace ':', '\:'
        Set-TextFile $script:LocalPropertiesPath "sdk.dir=$escapedSdk`n"
        Write-Ok '已创建 local.properties 并写入 SDK 路径'
    }
    if ($finalKeyRelease) { Set-PropertyLine -Path $script:LocalPropertiesPath -Key 'AMAP_KEY_RELEASE' -Value $finalKeyRelease }
    if ($finalKeyWeb) { Set-PropertyLine -Path $script:LocalPropertiesPath -Key 'AMAP_KEY_WEB' -Value $finalKeyWeb }
    if ($finalKeyDebug) { Set-PropertyLine -Path $script:LocalPropertiesPath -Key 'AMAP_KEY_DEBUG' -Value $finalKeyDebug }

    # 更新源必须指向自己的仓库或留空，否则会去拉本项目的 Release，签名校验必然失败。
    $repo = Get-PropertyLine -Path $script:GradlePropertiesPath -Key 'app.updateRepo'
    if ($repo -eq 'hmilyld/daytrail') {
        Set-PropertyLine -Path $script:GradlePropertiesPath -Key 'app.updateRepo' -Value ''
        Write-Ok '已清空在线更新源（避免指向本项目的仓库）'
    }
}

# 编译时真正生效的 SDK Key 取决于安装包类型：正式版用 AMAP_KEY_RELEASE，调试版用 AMAP_KEY_DEBUG。
$sdkKeyLabel = if ($finalType -eq 'debug') { '调试版 Key' } else { '正式版 Key' }
$sdkKeyForType = if ($finalType -eq 'debug') { $finalKeyDebug } else { $finalKeyRelease }
$keysMissing = [string]::IsNullOrWhiteSpace($sdkKeyForType) -or [string]::IsNullOrWhiteSpace($finalKeyWeb)

Write-Ok ("{0}：{1}" -f $sdkKeyLabel, (Format-MaskedKey $sdkKeyForType))
Write-Ok ("Web 服务 Key：{0}" -f (Format-MaskedKey $finalKeyWeb))
if ($keysMissing) {
    Write-Warn '还有 Key 没填。不影响编译，但地图、路线补全、地点名反查会用不了'
}

# ---------------------------------------------------------------- 第 7 步：写入、图标、编译

Write-Step 7 '写入配置并编译'

if ($finalName -ne $currentName -and -not $DryRun) {
    Set-StringResourceValue -Key 'app_name' -Value $finalName
    Set-StringResourceValue -Key 'onboarding_subtitle' -Value $finalName -From $currentName
    Set-StringResourceValue -Key 'today_map_empty_body' -Value $finalName -From $currentName
    Write-Ok "已改应用名：$finalName"
} else {
    Write-Ok '应用名没有变化'
}

if ($finalPackage -ne $currentPackage -and -not $DryRun) {
    Set-PropertyLine -Path $script:GradlePropertiesPath -Key 'app.applicationId' -Value $finalPackage
    Write-Ok "已改包名：$finalPackage"
}

if ($IconColor) {
    $finalIconColor = Assert-IconColor $IconColor
} elseif ($script:NonInteractive) {
    $finalIconColor = $currentIconColor
} else {
    Write-Host ''
    Write-Host '  图标底色可以换一个，只改一行，图形（白色路线 + 绿色起点）保持不变。'
    Write-Host '  常用配色：#2F6BFF 蓝、#16A34A 绿、#E4572E 橙、#7C3AED 紫、#111827 深灰。'
    while ($true) {
        try {
            $finalIconColor = Assert-IconColor (Read-TextInput '图标底色（直接回车 = 保持不变）' $currentIconColor)
            break
        } catch {
            Write-Host ("  " + $_.Exception.Message) -ForegroundColor Yellow
        }
    }
}
if ($finalIconColor -ne $currentIconColor -and -not $DryRun) {
    $iconText = Get-TextFile $script:IconColorPath
    $iconText = [regex]::Replace(
        $iconText,
        '(?<=<color name="ic_launcher_background">)#[0-9A-Fa-f]{6}(?=</color>)',
        $finalIconColor
    )
    Set-TextFile $script:IconColorPath $iconText
    Write-Ok "已改图标底色：$finalIconColor"
}

$apkPath = $null
$buildOk = $false
$logPath = Join-Path $script:DistPath 'setup-log.txt'

if ($DryRun) {
    Write-Host ''
    Write-Warn '试运行结束：没有写入任何文件，也没有编译'
    Write-Host ("  应用名：{0}" -f $finalName)
    Write-Host ("  包名：{0}" -f $finalPackage)
    Write-Host ("  图标底色：{0}" -f $finalIconColor)
    Write-Host ("  签名：{0}" -f $(if ($hasKeystore) { '沿用已有' } else { '将生成新签名' }))
    Write-Host ("  安装包：{0}" -f $(if ($finalType -eq 'release') { '正式版' } else { '调试版' }))
    Write-Result -Data ([ordered]@{
            ok          = $true
            dryRun      = $true
            appName     = $finalName
            packageName = $finalPackage
            type        = $finalType
            iconColor   = $finalIconColor
        })
    exit 0
}

if ($NoBuild) {
    Write-Host ''
    Write-Ok '准备工作已完成，按 -NoBuild 要求跳过编译'
    Write-Result -Data ([ordered]@{
            ok           = $true
            built        = $false
            appName      = $finalName
            packageName  = $finalPackage
            debugPackage = $debugPackage
            type         = $finalType
            sha1Release  = $sha1Release
            sha1Debug    = $sha1Debug
            cardPath     = (Join-Path $script:DistPath 'amap-fill-card.txt')
            logPath      = $logPath
        })
    exit 0
}

# 没有 Key 时默认不编译：先让用户去申请，回来再运行一次，
# 避免让完全不懂的人白等一次 10–30 分钟的构建。
if ($keysMissing) {
    $skipBuild = $true
    if (-not $script:NonInteractive) {
        $answer = Read-Host '  现在还没有高德 Key。先跳过编译，等申请回来再运行？（直接回车 = 是；输入 n = 现在就编一个没有地图的版本）'
        $skipBuild = $answer -notmatch '^(n|N|no|NO)$'
    }
    if ($skipBuild) {
        Write-Host ''
        Write-Warn '已跳过编译。等 Key 申请好，再双击本脚本一次，它会自动接着往下做'
        Write-Host '  接下来你要做的：'
        Write-Host '    1. 打开 https://console.amap.com/dev/key，申请「Android 平台 SDK」与「Web 服务」两个 Key'
        Write-Host "    2. 按 dist/amap-fill-card.txt 里的包名与 SHA1 填进控制台"
        Write-Host '    3. 再双击本脚本，把两个 Key 粘进去，它会自动编译出 APK'
        Write-Result -Data ([ordered]@{
                ok           = $true
                built        = $false
                needsKeys    = $true
                appName      = $finalName
                packageName  = $finalPackage
                debugPackage = $debugPackage
                type         = $finalType
                sha1Release  = $sha1Release
                sha1Debug    = $sha1Debug
                iconColor    = $finalIconColor
                cardPath     = (Join-Path $script:DistPath 'amap-fill-card.txt')
            })
        exit 0
    }
}

$mirrorEnabled = [bool]$UseChinaMirrors
if (-not $script:NonInteractive -and -not $mirrorEnabled) {
    Write-Host ''
    Write-Host '  如果下载 Gradle 或依赖特别慢、或一直失败，可以改用国内镜像（阿里云 + 腾讯云）。'
    $answer = Read-Host '  这次使用国内镜像吗？（输入 y 使用，直接回车 = 不用）'
    $mirrorEnabled = $answer -match '^(y|Y|yes|YES)$'
}
if ($mirrorEnabled) { Enable-ChinaMirrors }

if (-not $Yes) {
    Write-Host ''
    Write-Host '  准备开始编译。第一次会下载很多文件，通常要 10 到 30 分钟。'
    Write-Host '  中途看到百分比在动就是正常，请不要关窗口。'
    Read-EnterToContinue '按回车开始编译'
}

$gradlew = Join-Path $root 'gradlew.bat'
$task = if ($finalType -eq 'debug') { ':app:assembleDebug' } else { ':app:assembleRelease' }
New-Item -ItemType Directory -Force -Path $script:DistPath | Out-Null

$gradleArgs = @()
if ($mirrorEnabled) {
    $gradleArgs += @('-I', (Join-Path $root 'scripts/china-mirrors.gradle'))
}
$gradleArgs += @($task, '--console=plain')

Write-Host ''
Write-Host "  正在编译（$task）……" -ForegroundColor Cyan
$previous = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
try {
    & $gradlew @gradleArgs 2>&1 | Tee-Object -FilePath $logPath
    $buildCode = $LASTEXITCODE
} finally {
    $ErrorActionPreference = $previous
}

if ($buildCode -ne 0) {
    Write-Host ''
    Write-Host '  编译失败了。最常见的原因有三种：' -ForegroundColor Red
    Write-Host '    1. 还没有同意 Android SDK 的许可：打开 Android Studio 一次，让它自动装完' -ForegroundColor Red
    Write-Host '    2. 网络太慢或中断：换个网络再来一次' -ForegroundColor Red
    Write-Host '    3. 电脑上的杀毒软件拦截了 Gradle：临时关掉再试' -ForegroundColor Red
    Write-Host ''
    Write-Host "  详细日志已保存在：$logPath" -ForegroundColor Yellow
    Write-Host '  把上面这个文件发给帮你的人，里面不会暴露你的高德 Key。' -ForegroundColor Yellow
    Show-BuildFailureHint -Path $logPath
    Write-Result -Data ([ordered]@{
            ok           = $false
            built        = $false
            appName      = $finalName
            packageName  = $finalPackage
            type         = $finalType
            chinaMirrors = $mirrorEnabled
            logPath      = $logPath
        })
    exit 3
}

$apkDir = Join-Path $root "app/build/outputs/apk/$finalType"
$apk = Get-ChildItem -Path $apkDir -Filter '*.apk' -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
if (-not $apk) {
    Write-Host '  编译命令成功了，但没找到 APK。请把下面的目录内容发给帮你的人：' -ForegroundColor Red
    Write-Host "  $apkDir" -ForegroundColor Yellow
    Write-Result -Data ([ordered]@{
            ok          = $false
            built       = $false
            reason      = 'apk-not-found'
            appName     = $finalName
            packageName = $finalPackage
            type        = $finalType
            logPath     = $logPath
        })
    exit 3
}
$apkPath = $apk.FullName
$buildOk = $true

Write-Host ''
Write-Ok ("编译完成：{0}（{1} MB）" -f $apkPath, [math]::Round($apk.Length / 1MB, 1))

if ($Install -or (-not $script:NonInteractive)) {
    $adb = Get-AdbPath
    if ($adb) {
        $previous = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { $devices = & $adb devices 2>&1 } finally { $ErrorActionPreference = $previous }
        $connected = @($devices | Where-Object { $_ -match '^\S+\s+device$' })
        if ($connected.Count -gt 0) {
            $shouldInstall = $Install
            if (-not $shouldInstall) {
                $answer = Read-Host "  检测到手机已连接，要现在装进去吗？（直接回车 = 是，输入 n 跳过）"
                $shouldInstall = $answer -notmatch '^(n|N|no|NO)$'
            }
            if ($shouldInstall) {
                Write-Host '  正在安装……'
                $previous = $ErrorActionPreference
                $ErrorActionPreference = 'Continue'
                try {
                    & $adb install -r $apkPath 2>&1 | ForEach-Object { Write-Host "  $_" }
                    $installCode = $LASTEXITCODE
                } finally {
                    $ErrorActionPreference = $previous
                }
                if ($installCode -eq 0) {
                    $namespace = Get-AppNamespace -Root $root
                    $launchPackage = if ($finalType -eq 'debug') { "$finalPackage.debug" } else { $finalPackage }
                    & $adb shell am start -n "$launchPackage/$namespace.MainActivity" 2>&1 | Out-Null
                    Write-Ok '已安装并打开'
                } else {
                    Write-Warn '安装没成功。手机上需要允许「安装未知应用」，装好后再点一次打开'
                }
            }
        } elseif ($Install) {
            Write-Warn '没有检测到手机。请用数据线连上并打开 USB 调试后再试'
        }
    } elseif ($Install) {
        Write-Warn '找不到 adb，无法自动安装。请手动把 APK 传到手机上安装'
    }
}

# ---------------------------------------------------------------- 收尾

Write-Host ''
Write-Host ('=' * 62) -ForegroundColor DarkGray
Write-Host '  全部完成' -ForegroundColor Green
Write-Host ('=' * 62) -ForegroundColor DarkGray
Write-Host "  APK：$apkPath"
Write-Host '  装到手机：连数据线复制这个文件，或用微信/邮件发给自己，再用文件管理器点开安装。'
Write-Host '  手机上如果提示「禁止安装未知应用」，在提示里点「设置」允许即可。'
Write-Host ''
Write-Host '  还需要你做的事：'
if ($keysMissing) {
    Write-Host '    · 去高德申请 Key，然后重新双击本脚本填进来（现在地图是空白的）'
}
Write-Host '    · 备份 app/keystore/release.jks 与 keystore.properties（丢了就不能覆盖升级）'
Write-Host "    · 想换成自己的图片图标：看 docs/build-your-own.md 的「换图标」一节"
Write-Host "    · 这个应用的包名：$finalPackage"
Write-Host ''

Write-Result -Data ([ordered]@{
        ok           = $buildOk
        built        = $true
        appName      = $finalName
        packageName  = $finalPackage
        debugPackage = $debugPackage
        type         = $finalType
        sha1Release  = $sha1Release
        sha1Debug    = $sha1Debug
        iconColor    = $finalIconColor
        chinaMirrors = $mirrorEnabled
        apkPath      = $apkPath
        cardPath     = (Join-Path $script:DistPath 'amap-fill-card.txt')
        logPath      = $logPath
    })
