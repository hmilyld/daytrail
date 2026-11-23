# DayTrail 工具链探测（macOS / Linux）：被 setup-my-app.sh 引用。
#
# 与 scripts/toolchain.ps1 保持同一套定位顺序：
# 已有环境变量 → Android Studio 自带 → 常见安装位置 → local.properties。
# 只用 POSIX shell + 常见命令，兼容 macOS 自带的 bash 3.2。

resolve_jdk_home() {
    if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
        printf '%s\n' "$JAVA_HOME"
        return 0
    fi

    for candidate in \
        "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
        "/opt/homebrew/opt/openjdk@21" \
        "/usr/local/opt/openjdk@21" \
        "/opt/homebrew/opt/openjdk" \
        "/usr/local/opt/openjdk" \
        "/usr/lib/jvm/default-java"; do
        if [ -x "$candidate/bin/java" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    if [ -x "/usr/libexec/java_home" ]; then
        for version in 21 17; do
            resolved=$("/usr/libexec/java_home" -v "$version" 2>/dev/null || true)
            if [ -n "$resolved" ] && [ -x "$resolved/bin/java" ]; then
                printf '%s\n' "$resolved"
                return 0
            fi
        done
    fi

    return 1
}

resolve_android_sdk() {
    root=${1:-.}

    if [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME" ]; then
        printf '%s\n' "$ANDROID_HOME"
        return 0
    fi
    if [ -n "${ANDROID_SDK_ROOT:-}" ] && [ -d "$ANDROID_SDK_ROOT" ]; then
        printf '%s\n' "$ANDROID_SDK_ROOT"
        return 0
    fi

    # local.properties 是 Properties 格式，Windows 上写入时带转义，这里还原。
    if [ -f "$root/local.properties" ]; then
        value=$(sed -n 's/^[[:space:]]*sdk\.dir[[:space:]]*=[[:space:]]*//p' "$root/local.properties" | sed -n '1p')
        value=${value//\\\\/\\}
        value=${value//\\:/:}
        if [ -n "$value" ] && [ -d "$value" ]; then
            printf '%s\n' "$value"
            return 0
        fi
    fi

    for candidate in "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
        if [ -d "$candidate" ]; then
            printf '%s\n' "$candidate"
            return 0
        fi
    done

    return 1
}
