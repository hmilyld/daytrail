# DayTrail 工具链探测：verify.ps1 与 setup-my-app.ps1 共用。
#
# 只使用 PowerShell 5.1 也支持的语法；不写死任何本机路径。
# 定位顺序：已有环境变量 → Android Studio 自带 → 常见安装位置 → local.properties。
# 两个脚本共用本文件后，换机器或换装机方式只需要在这里补一条候选路径。

function Test-JdkHome {
    param([string]$Path)
    if (-not $Path) { return $false }
    return (Test-Path (Join-Path $Path 'bin/java.exe')) -or (Test-Path (Join-Path $Path 'bin/java'))
}

function Resolve-JdkHome {
    if (Test-JdkHome $env:JAVA_HOME) { return $env:JAVA_HOME }

    $candidates = @()
    if ($env:LOCALAPPDATA) {
        # Windows 默认安装位置；macOS/Linux 上该变量为空，跳过而不是让 Join-Path 报错。
        $candidates += (Join-Path $env:LOCALAPPDATA 'Programs\Android Studio\jbr')
    }
    $candidates += @(
        'C:\Program Files\Android\Android Studio\jbr',
        'C:\Program Files\Java\jdk-21',
        'C:\Program Files\Java\jdk-17',
        '/Applications/Android Studio.app/Contents/jbr/Contents/Home',
        '/opt/homebrew/opt/openjdk@21',
        '/usr/local/opt/openjdk@21',
        '/usr/lib/jvm/default-java'
    )
    foreach ($candidate in $candidates) {
        if (Test-JdkHome $candidate) { return $candidate }
    }

    # macOS：系统的 java_home 工具知道所有已安装的 JDK
    if (Test-Path '/usr/libexec/java_home') {
        foreach ($version in @('21', '17')) {
            try {
                $resolved = & /usr/libexec/java_home -v $version 2>$null
                if (Test-JdkHome $resolved) { return $resolved }
            } catch {
                # 该版本不存在时继续尝试下一个，不打断整体探测
            }
        }
    }

    throw '未找到 JDK：请安装 Android Studio（自带 JDK），或设置 JAVA_HOME（需要 JDK 17 或更高）'
}

function Read-SdkDirFromLocalProperties {
    param([string]$Root)
    $file = Join-Path $Root 'local.properties'
    if (-not (Test-Path $file)) { return $null }
    $line = Select-String -Path $file -Pattern '^\s*sdk\.dir\s*=' | Select-Object -First 1
    if (-not $line) { return $null }
    # Properties 格式里反斜杠与盘符冒号都带转义
    $value = ($line.Line -split '=', 2)[1].Trim()
    return ($value -replace '\\\\', '\' -replace '\\:', ':')
}

function Resolve-AndroidSdk {
    param([string]$Root)

    if ($env:ANDROID_HOME -and (Test-Path $env:ANDROID_HOME)) { return $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT -and (Test-Path $env:ANDROID_SDK_ROOT)) { return $env:ANDROID_SDK_ROOT }

    $fromProperties = Read-SdkDirFromLocalProperties -Root $Root
    if ($fromProperties -and (Test-Path $fromProperties)) { return $fromProperties }

    $candidates = @()
    if ($env:LOCALAPPDATA) {
        $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk')
    }
    $candidates += 'C:\Android\Sdk'
    if ($env:HOME) {
        $candidates += (Join-Path $env:HOME 'Library/Android/sdk')
        $candidates += (Join-Path $env:HOME 'Android/Sdk')
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path $candidate)) { return $candidate }
    }

    throw '未找到 Android SDK：请先打开一次 Android Studio 完成首次设置（它会下载 SDK），或设置 ANDROID_HOME'
}

function Set-ToolchainEnvironment {
    param([Parameter(Mandatory = $true)][string]$Root)

    $javaHome = Resolve-JdkHome
    $androidSdk = Resolve-AndroidSdk -Root $Root
    $env:JAVA_HOME = $javaHome
    $env:ANDROID_HOME = $androidSdk
    $env:ANDROID_SDK_ROOT = $androidSdk
    return [pscustomobject]@{
        JavaHome   = $javaHome
        AndroidSdk = $androidSdk
    }
}

function Read-GradleProperty {
    param(
        [Parameter(Mandatory = $true)][string]$Root,
        [Parameter(Mandatory = $true)][string]$Key
    )
    $file = Join-Path $Root 'gradle.properties'
    if (-not (Test-Path $file)) { return $null }
    $pattern = '^\s*' + [regex]::Escape($Key) + '\s*=\s*(.*)$'
    $line = Select-String -Path $file -Pattern $pattern | Select-Object -First 1
    if (-not $line) { return $null }
    return $line.Matches[0].Groups[1].Value.Trim()
}

function Get-AppNamespace {
    param([Parameter(Mandatory = $true)][string]$Root)
    $file = Join-Path $Root 'app/build.gradle.kts'
    if (-not (Test-Path $file)) { throw "找不到 app/build.gradle.kts：请在项目根目录运行" }
    $line = Select-String -Path $file -Pattern '^\s*namespace\s*=\s*"([^"]+)"' | Select-Object -First 1
    if (-not $line) { throw '无法从 app/build.gradle.kts 读出 namespace' }
    return $line.Matches[0].Groups[1].Value
}

function Get-AppApplicationId {
    param([Parameter(Mandatory = $true)][string]$Root)

    # 优先读 gradle.properties（用户可改），缺失时回退到 build.gradle.kts 的默认值。
    $property = Read-GradleProperty -Root $Root -Key 'app.applicationId'
    if ($property) { return $property }

    $file = Join-Path $Root 'app/build.gradle.kts'
    $line = Select-String -Path $file -Pattern 'applicationId\s*=\s*"([^"]+)"' | Select-Object -First 1
    if ($line) { return $line.Matches[0].Groups[1].Value }
    throw '无法确定 applicationId：gradle.properties 里没有 app.applicationId'
}
