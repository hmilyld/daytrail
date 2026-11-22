#!/usr/bin/env bash
# DayTrail 一键改造向导（macOS / Linux）。
#
# 两种用法：
#   1) 普通人：双击仓库根目录的 setup-my-app.command，或运行 bash scripts/setup-my-app.sh
#   2) AI / 自动化：带参数运行，全程不提问；结束时输出 JSON，并写到 dist/setup-result.json
#      完整契约见 docs/ai-prompt.md。
#
# 与 scripts/setup-my-app.ps1 是同一套行为，改一处时请同步另一处：
#   gradle.properties / strings.xml / ic_launcher_background.xml
#   app/keystore/release.jks + keystore.properties / local.properties / dist/amap-fill-card.txt
#
# 兼容 macOS 自带的 bash 3.2：不使用关联数组、${var,,}、mapfile。

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
ROOT=$(cd "$SCRIPT_DIR/.." && pwd)
cd "$ROOT"

# shellcheck source=toolchain.sh
. "$SCRIPT_DIR/toolchain.sh"

DEFAULT_PACKAGE="com.hmilyld.daytrail"
DEFAULT_NAME="行迹"
DEFAULT_ICON_COLOR="#2F6BFF"
STEP_TOTAL=7

STRINGS_XML="$ROOT/app/src/main/res/values/strings.xml"
ICON_COLOR_XML="$ROOT/app/src/main/res/values/ic_launcher_background.xml"
LOCAL_PROPERTIES="$ROOT/local.properties"
GRADLE_PROPERTIES="$ROOT/gradle.properties"
KEYSTORE_PROPERTIES="$ROOT/keystore.properties"
RELEASE_JKS="$ROOT/app/keystore/release.jks"
DEBUG_JKS="$ROOT/app/keystore/debug.jks"
DIST="$ROOT/dist"

NAME=""
ID=""
PACKAGE=""
APP_TYPE="release"
TYPE_SET=0
KEY_RELEASE=""
KEY_WEB=""
KEY_DEBUG=""
ICON_COLOR=""
NO_BUILD=0
JSON=0
YES=0
DRY_RUN=0
INSTALL=0
ADVANCED=0

show_help() {
    cat <<'EOF'
DayTrail 一键改造向导（macOS / Linux）

普通人（推荐）：
  双击仓库根目录的 setup-my-app.command，按窗口里的提示输入即可。

AI 或自动化（全程不提问）：
  bash scripts/setup-my-app.sh --name 山行 --id zhangsan --type release --no-build --json

参数：
  --name <显示名>        手机桌面上显示的名字，中文即可（最多 12 个字）
  --id <英文昵称>        小写字母或数字，用来拼包名 com.<昵称>.daytrail
  --package <完整包名>   直接指定包名，优先于 --id
  --type release|debug   安装包类型，默认 release（只要两个高德 Key）
  --key-release <Key>    高德 Android 平台 SDK Key（正式版）
  --key-web <Key>        高德 Web 服务 Key（补全路线、地点名反查）
  --key-debug <Key>      高德 Android 平台 SDK Key（调试版，--type debug 时用）
  --icon-color <#RRGGBB> 图标底色，例如 #E4572E
  --no-build             只做准备（改名、生成签名、出卡片），不编译
  --install              编译完成后安装到已连接的手机
  --json                 结束时输出一行 JSON，并写入 dist/setup-result.json
  --dry-run              只打印将要修改的内容，不写任何文件
  --yes                  跳过确认
  --advanced             允许直接输入完整包名
  --help                 显示本说明

改完想还原：git checkout -- gradle.properties app/src/main/res
EOF
}

die() {
    printf '\n  %s\n' "$1" >&2
    exit "${2:-1}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --name) NAME=${2:-}; [ $# -ge 2 ] || die '--name 后面要跟名字'; shift 2 ;;
        --id) ID=${2:-}; [ $# -ge 2 ] || die '--id 后面要跟昵称'; shift 2 ;;
        --package) PACKAGE=${2:-}; [ $# -ge 2 ] || die '--package 后面要跟包名'; shift 2 ;;
        --type) APP_TYPE=${2:-}; TYPE_SET=1; [ $# -ge 2 ] || die '--type 后面要跟 release 或 debug'; shift 2 ;;
        --key-release) KEY_RELEASE=${2:-}; [ $# -ge 2 ] || die '--key-release 后面要跟 Key'; shift 2 ;;
        --key-web) KEY_WEB=${2:-}; [ $# -ge 2 ] || die '--key-web 后面要跟 Key'; shift 2 ;;
        --key-debug) KEY_DEBUG=${2:-}; [ $# -ge 2 ] || die '--key-debug 后面要跟 Key'; shift 2 ;;
        --icon-color) ICON_COLOR=${2:-}; [ $# -ge 2 ] || die '--icon-color 后面要跟颜色'; shift 2 ;;
        --no-build) NO_BUILD=1; shift ;;
        --json) JSON=1; shift ;;
        --yes) YES=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --install) INSTALL=1; shift ;;
        --advanced) ADVANCED=1; shift ;;
        --help|-h) show_help; exit 0 ;;
        *) die "不认识的参数：$1（用 --help 看用法）" 2 ;;
    esac
done

case "$APP_TYPE" in
    release|debug) ;;
    *) die '--type 只能是 release 或 debug' 2 ;;
esac

# 只要给了任意参数就进入无人值守模式：AI 调用时不能卡在 read 上。
NON_INTERACTIVE=0
if [ "$JSON" -eq 1 ] || [ "$YES" -eq 1 ] || [ "$NO_BUILD" -eq 1 ] || [ "$DRY_RUN" -eq 1 ] ||
    [ -n "$NAME" ] || [ -n "$ID" ] || [ -n "$PACKAGE" ] || [ -n "$KEY_RELEASE" ] ||
    [ -n "$KEY_WEB" ] || [ -n "$KEY_DEBUG" ] || [ -n "$ICON_COLOR" ]; then
    NON_INTERACTIVE=1
fi

step() {
    printf '\n%s\n' '=============================================================='
    printf '  DayTrail · 编译你自己的版本        第 %s 步 / 共 %s 步\n' "$1" "$STEP_TOTAL"
    printf '  %s\n' "$2"
    printf '%s\n' '=============================================================='
}

ok() { printf '  [完成] %s\n' "$1"; }
warn() { printf '  [注意] %s\n' "$1"; }
info() { printf '  %s\n' "$1"; }

pause_enter() {
    [ "$NON_INTERACTIVE" -eq 1 ] && return 0
    read -r -p "$1" _ || true
}

ask_text() {
    # ask_text 提示 默认值 -> 结果写入变量 ASK_RESULT
    local prompt="$1" default="${2:-}" answer hint
    if [ -n "$default" ]; then hint="（直接回车 = $default）"; else hint=""; fi
    while true; do
        read -r -p "$prompt$hint" answer || answer=""
        if [ -z "$answer" ]; then answer="$default"; fi
        answer=$(printf '%s' "$answer" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
        if [ -n "$answer" ]; then
            ASK_RESULT="$answer"
            return 0
        fi
        warn '这里不能空着，请重新输入。'
    done
}

assert_app_name() {
    local value chars
    value=$(printf '%s' "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')
    [ -n "$value" ] || die '应用名不能为空' 2
    # bash 3.2 的 ${#value} 按字节算，中文名会被高估，这里用 wc -m 按字符数判断。
    chars=$(printf '%s' "$value" | wc -m | tr -d ' ')
    if [ "$chars" -gt 24 ]; then die '应用名太长了，请控制在 12 个字以内' 2; fi
    if [ "$chars" -gt 12 ]; then printf '  [注意] 名字超过 12 个字，手机桌面可能显示不全\n' >&2; fi
    case "$value" in
        *'&'*|*'<'*|*'>'*|*'"'*|*"'"*) die '应用名里不能出现 & < > " 或单引号' 2 ;;
        *\\*|*/*|*:*|*\**|*\?*|*\|*) die '应用名里不能出现 \ / : * ? | 这些符号' 2 ;;
    esac
    case "$value" in *.) die '应用名不要以句点结尾' 2 ;; esac
    printf '%s' "$value"
}

assert_nickname() {
    local value
    value=$(printf '%s' "$1" | tr '[:upper:]' '[:lower:]')
    if ! printf '%s' "$value" | grep -Eq '^[a-z][a-z0-9]{0,15}$'; then
        die '昵称只能用小写字母和数字，并以字母开头，例如 zhangsan、xiaoli2024' 2
    fi
    printf '%s' "$value"
}

assert_package_name() {
    local value="$1"
    if ! printf '%s' "$value" | grep -Eq '^[a-z][a-z0-9_]*(\.[a-z][a-z0-9_]*)+$'; then
        die '包名格式不对：只能用小写字母、数字、下划线，至少两段，例如 com.zhangsan.daytrail' 2
    fi
    if [ "$value" = "$DEFAULT_PACKAGE" ]; then
        die '这是本项目的包名。请换成自己的，否则会和本项目发布的安装包冲突，高德也会把调用量记到同一把 Key 上' 2
    fi
    case "$value" in com.example.*) die 'com.example 是示例包名，请换成自己的昵称，例如 com.zhangsan.daytrail' 2 ;; esac
    printf '%s' "$value"
}

assert_icon_color() {
    local value
    value=$(printf '%s' "$1" | tr '[:lower:]' '[:upper:]')
    if ! printf '%s' "$value" | grep -Eq '^#[0-9A-F]{6}$'; then
        die '颜色要写成 #RRGGBB 的形式，例如 #E4572E' 2
    fi
    printf '%s' "$value"
}

get_property_line() {
    local file="$1" key="$2"
    [ -f "$file" ] || return 1
    awk -v key="$key" '
        {
            line = $0
            sub(/^[ \t]*/, "", line)
            if (substr(line, 1, length(key)) == key) {
                rest = substr(line, length(key) + 1)
                sub(/^[ \t]*=/, "", rest)
                sub(/^[ \t]*/, "", rest)
                print rest
                exit
            }
        }
    ' "$file"
}

set_property_line() {
    local file="$1" key="$2" value="$3" tmp
    tmp=$(mktemp "${TMPDIR:-/tmp}/daytrail.XXXXXX")
    if [ -f "$file" ]; then
        PL_KEY="$key" PL_VALUE="$value" awk '
            BEGIN {
                key = ENVIRON["PL_KEY"]
                val = ENVIRON["PL_VALUE"]
                pattern = "^[ \t]*" key "[ \t]*="
                found = 0
            }
            {
                if ($0 ~ pattern) { print key "=" val; found = 1; next }
                print
            }
            END { if (!found) print key "=" val }
        ' "$file" >"$tmp"
    else
        printf '%s=%s\n' "$key" "$value" >"$tmp"
    fi
    mv "$tmp" "$file"
}

get_string_resource() {
    local key="$1"
    [ -f "$STRINGS_XML" ] || return 1
    SR_KEY="$key" perl -0777 -ne '
        my $key = $ENV{"SR_KEY"};
        if (m{<string name="\Q$key\E"[^>]*>(.*?)</string>}s) { print $1 }
    ' "$STRINGS_XML"
}

set_string_resource() {
    # set_string_resource <key> <新值> [被替换的旧品牌词]
    local key="$1" value="$2" from="${3:-}"
    SR_KEY="$key" SR_NEW="$value" SR_OLD="$from" perl -0777 -pi -e '
        my $key = $ENV{"SR_KEY"};
        my $new = $ENV{"SR_NEW"};
        my $old = $ENV{"SR_OLD"};
        my $re  = qr{(<string name="\Q$key\E"[^>]*>)(.*?)(</string>)}s;
        s{$re}{
            my ($open, $body, $close) = ($1, $2, $3);
            if ($old ne "") {
                my $before = $body;
                $body =~ s/\Q$old\E/$new/g;
                $body =~ s/行迹/$new/g if $body eq $before;
            } else {
                $body = $new;
            }
            $open . $body . $close;
        }e;
    ' "$STRINGS_XML"
}

mask_key() {
    local value="$1"
    if [ -z "$value" ]; then
        printf '（未填写）'
        return 0
    fi
    if [ ${#value} -le 8 ]; then
        printf '****'
        return 0
    fi
    printf '%s****%s' "$(printf '%s' "$value" | cut -c1-4)" "$(printf '%s' "$value" | rev | cut -c1-4 | rev)"
}

random_password() {
    local chars='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'
    local out='' i index
    i=0
    while [ "$i" -lt 24 ]; do
        index=$((RANDOM % 62))
        out="$out${chars:$index:1}"
        i=$((i + 1))
    done
    printf '%s' "$out"
}

sha1_of_keystore() {
    local keystore="$1" alias="$2" password="$3"
    [ -f "$keystore" ] || return 1
    LC_ALL=C "$KEYTOOL" -list -v -keystore "$keystore" -alias "$alias" -storepass "$password" 2>/dev/null |
        sed -n '/^[[:space:]]*SHA1:/{s/^[[:space:]]*SHA1:[[:space:]]*//;p;q;}'
}

json_escape() {
    printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'
}

write_result() {
    # 参数：json 文本
    local text="$1"
    if [ "$DRY_RUN" -eq 0 ]; then
        mkdir -p "$DIST"
        printf '%s\n' "$text" >"$DIST/setup-result.json"
    fi
    if [ "$JSON" -eq 1 ]; then
        printf '%s\n' "$text"
    fi
}

# ---------------------------------------------------------------- 环境检查

[ -f "$ROOT/gradlew" ] || die '请在项目根目录运行：这里找不到 gradlew。如果你是双击运行，请先解压 ZIP，再到解压后的文件夹里操作'
[ -f "$ROOT/app/build.gradle.kts" ] || die '项目文件不完整：找不到 app/build.gradle.kts'

step 1 '检查电脑环境'
info '正在找 Java 和 Android SDK，第一次可能要几秒……'

JDK_HOME=$(resolve_jdk_home || true)
[ -n "$JDK_HOME" ] || {
    printf '\n'
    warn '这台电脑还缺少编译需要的工具。'
    warn '请先安装 Android Studio（它会自带 Java 和 Android SDK）：'
    warn '  https://developer.android.com/studio'
    printf '\n'
    warn '装好后请打开 Android Studio 一次，等它把 SDK 下载完（有进度条），再关掉，'
    warn '然后重新运行本脚本。'
    exit 1
}
ANDROID_SDK=$(resolve_android_sdk "$ROOT" || true)
[ -n "$ANDROID_SDK" ] || {
    printf '\n'
    warn '没找到 Android SDK。'
    warn '请先打开一次 Android Studio，让它把 SDK 下载完（有进度条），再关掉重新运行。'
    exit 1
}

export JAVA_HOME="$JDK_HOME"
export ANDROID_HOME="$ANDROID_SDK"
export ANDROID_SDK_ROOT="$ANDROID_SDK"
KEYTOOL="$JAVA_HOME/bin/keytool"
[ -x "$KEYTOOL" ] || die '找不到 keytool：请确认 JAVA_HOME 指向一个完整的 JDK'

ok '找到 Java'
ok '找到 Android SDK'
ok '找到项目文件'
if [ ! -x "$ROOT/gradlew" ]; then
    chmod +x "$ROOT/gradlew" 2>/dev/null || true
fi

CURRENT_NAME=$(get_string_resource 'app_name' || true)
[ -n "$CURRENT_NAME" ] || CURRENT_NAME="$DEFAULT_NAME"
CURRENT_PACKAGE=$(get_property_line "$GRADLE_PROPERTIES" 'app.applicationId' || true)
[ -n "$CURRENT_PACKAGE" ] || CURRENT_PACKAGE="$DEFAULT_PACKAGE"
CURRENT_ICON_COLOR="$DEFAULT_ICON_COLOR"
if [ -f "$ICON_COLOR_XML" ]; then
    parsed_color=$(sed -n 's/.*<color name="ic_launcher_background">\(#[0-9A-Fa-f]\{6\}\)<\/color>.*/\1/p' "$ICON_COLOR_XML" | sed -n '1p')
    [ -n "$parsed_color" ] && CURRENT_ICON_COLOR=$(printf '%s' "$parsed_color" | tr '[:lower:]' '[:upper:]')
fi

HAS_KEYSTORE=0
[ -f "$RELEASE_JKS" ] && [ -f "$KEYSTORE_PROPERTIES" ] && HAS_KEYSTORE=1
CURRENT_KEY_RELEASE=$(get_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_RELEASE' || true)
CURRENT_KEY_WEB=$(get_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_WEB' || true)
CURRENT_KEY_DEBUG=$(get_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_DEBUG' || true)

printf '\n  现在的状态：\n'
mark_name='[ ]'; [ "$CURRENT_NAME" != "$DEFAULT_NAME" ] && mark_name='[x]'
mark_pkg='[ ]'; [ "$CURRENT_PACKAGE" != "$DEFAULT_PACKAGE" ] && mark_pkg='[x]'
mark_sign='[ ]'; [ "$HAS_KEYSTORE" -eq 1 ] && mark_sign='[x]'
mark_key='[ ]'; { [ -n "$CURRENT_KEY_RELEASE" ] && [ -n "$CURRENT_KEY_WEB" ]; } && mark_key='[x]'
printf '    %s 应用名：%s\n' "$mark_name" "$CURRENT_NAME"
printf '    %s 包名：%s\n' "$mark_pkg" "$CURRENT_PACKAGE"
printf '    %s 签名文件\n' "$mark_sign"
printf '    %s 高德 Key：正式版 %s / Web 服务 %s\n' "$mark_key" "$(mask_key "$CURRENT_KEY_RELEASE")" "$(mask_key "$CURRENT_KEY_WEB")"
printf '\n'
if [ "$NON_INTERACTIVE" -eq 0 ]; then
    info '已经完成的项目会自动跳过，从没做的那一步接着做。'
    pause_enter '按回车继续'
fi

# ---------------------------------------------------------------- 第 2 步：名字与包名

step 2 '给应用起名字'

if [ -n "$NAME" ]; then
    FINAL_NAME=$(assert_app_name "$NAME")
elif [ "$NON_INTERACTIVE" -eq 1 ]; then
    FINAL_NAME="$CURRENT_NAME"
    warn "没有指定 --name，沿用当前名字：$FINAL_NAME"
else
    info '这个名字会显示在手机桌面的图标下面，用中文就行。'
    if [ "$CURRENT_NAME" = "$DEFAULT_NAME" ]; then
        info '（还是默认的「行迹」，建议换成自己的名字）'
    fi
    ask_text '请输入名字' "$CURRENT_NAME"
    FINAL_NAME=$(assert_app_name "$ASK_RESULT")
fi
ok "应用名：$FINAL_NAME"

if [ -n "$PACKAGE" ]; then
    FINAL_PACKAGE=$(assert_package_name "$PACKAGE")
elif [ -n "$ID" ]; then
    FINAL_PACKAGE=$(assert_package_name "com.$(assert_nickname "$ID").daytrail")
elif [ "$NON_INTERACTIVE" -eq 1 ]; then
    FINAL_PACKAGE="$CURRENT_PACKAGE"
    warn "没有指定 --package / --id，沿用当前包名：$FINAL_PACKAGE"
else
    printf '\n'
    info '接下来要生成「包名」。它是应用在手机里的身份证，不能和别人重复。'
    info '你只需要给一个英文昵称，剩下的交给我。'
    info '昵称只能用小写字母和数字，例如 zhangsan、xiaoli2024。'
    printf '\n'
    while true; do
        if [ "$ADVANCED" -eq 1 ]; then
            ask_text '请输入完整包名' "$CURRENT_PACKAGE"
            FINAL_PACKAGE=$(assert_package_name "$ASK_RESULT")
        else
            suggest_id=''
            case "$CURRENT_PACKAGE" in
                com.*.daytrail)
                    middle=${CURRENT_PACKAGE#com.}
                    middle=${middle%.daytrail}
                    case "$middle" in *.*) ;; *) suggest_id="$middle" ;; esac
                    ;;
            esac
            while true; do
                ask_text '请输入昵称' "$suggest_id"
                if FINAL_PACKAGE=$(assert_package_name "com.$(assert_nickname "$ASK_RESULT").daytrail" 2>/dev/null); then
                    break
                fi
                warn '昵称只能用小写字母和数字，并以字母开头，例如 zhangsan'
            done
        fi

        printf '\n  你的包名将是：%s\n' "$FINAL_PACKAGE"
        read -r -p '  用它吗？（直接回车 = 是，输入 n 重来）' confirm || confirm=''
        case "$confirm" in
            n|N|no|NO) info '好的，重新来一次。' ;;
            *) break ;;
        esac
    done
fi
if [ "$FINAL_PACKAGE" != "$CURRENT_PACKAGE" ]; then
    ok "包名：$FINAL_PACKAGE"
fi

# ---------------------------------------------------------------- 第 3 步：安装包类型

step 3 '选择安装包类型'

if [ "$TYPE_SET" -eq 1 ]; then
    FINAL_TYPE="$APP_TYPE"
else
    info '正式版只需要两个高德 Key，签名长期有效，推荐。'
    info '调试版给开发者用，需要三个 Key，而且名字带 debug 后缀。'
    printf '    1. 正式版（推荐）\n    2. 调试版\n'
    read -r -p '请选择（直接回车 = 1）' type_choice || type_choice=''
    case "$type_choice" in
        2) FINAL_TYPE='debug' ;;
        *) FINAL_TYPE='release' ;;
    esac
fi
if [ "$FINAL_TYPE" = 'release' ]; then ok '安装包类型：正式版'; else ok '安装包类型：调试版'; fi

# ---------------------------------------------------------------- 第 4 步：签名

step 4 '生成签名文件'

STORE_PASSWORD=""
KEY_ALIAS="daytrail"

if [ "$HAS_KEYSTORE" -eq 1 ]; then
    ok '已经有签名文件，直接沿用（这样以后升级才能覆盖安装）'
    STORE_PASSWORD=$(get_property_line "$KEYSTORE_PROPERTIES" 'storePassword' || true)
    saved_alias=$(get_property_line "$KEYSTORE_PROPERTIES" 'keyAlias' || true)
    [ -n "$saved_alias" ] && KEY_ALIAS="$saved_alias"
else
    if [ -f "$RELEASE_JKS" ]; then
        die '发现 app/keystore/release.jks 还在，但 keystore.properties 不见了（口令丢失）。请先找回那个文件；如果你确定不要这份旧签名，删掉 app/keystore/release.jks 再运行'
    fi
    info '签名文件相当于应用的身份证。没有它，以后就无法覆盖升级。'
    info '我会自动生成一个，口令随机，保存在 keystore.properties 里。'
    printf '\n'
    if [ "$NON_INTERACTIVE" -eq 0 ]; then
        read -r -p '  现在生成吗？（直接回车 = 是）' answer || answer=''
        case "$answer" in
            n|N|no|NO)
                warn '已跳过签名生成，只能编译调试版'
                FINAL_TYPE='debug'
                ;;
        esac
    fi
    STORE_PASSWORD=$(random_password)
    if [ "$FINAL_TYPE" = 'release' ] && [ "$DRY_RUN" -eq 0 ]; then
        mkdir -p "$(dirname "$RELEASE_JKS")"
        if ! "$KEYTOOL" -genkeypair -v \
            -keystore "$RELEASE_JKS" -storetype PKCS12 -alias "$KEY_ALIAS" \
            -keyalg RSA -keysize 4096 -validity 10950 \
            -storepass "$STORE_PASSWORD" -keypass "$STORE_PASSWORD" \
            -dname 'CN=DayTrail, OU=Personal, O=Personal, L=Unknown, ST=Unknown, C=CN' >/dev/null 2>&1; then
            die '生成签名文件失败（keytool 报错）。请把终端里的红字发给帮你的人'
        fi
        cat >"$KEYSTORE_PROPERTIES" <<EOF
storeFile=app/keystore/release.jks
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$STORE_PASSWORD
EOF
        ok '签名文件已生成：app/keystore/release.jks'
        warn '记得备份 app/keystore/release.jks 和 keystore.properties，丢了就无法覆盖升级'
    elif [ "$DRY_RUN" -eq 1 ]; then
        warn '试运行：不会真的生成签名文件'
    fi
fi

# ---------------------------------------------------------------- 第 5 步：高德填写卡

step 5 '给你一张高德 Key 填写卡'

SHA1_RELEASE=''
SHA1_DEBUG=''
if [ -f "$RELEASE_JKS" ] && [ -n "$STORE_PASSWORD" ]; then
    SHA1_RELEASE=$(sha1_of_keystore "$RELEASE_JKS" "$KEY_ALIAS" "$STORE_PASSWORD" || true)
fi
SHA1_DEBUG=$(sha1_of_keystore "$DEBUG_JKS" 'androiddebugkey' 'android' || true)

DEBUG_PACKAGE="$FINAL_PACKAGE.debug"
CARD="$DIST/amap-fill-card.txt"
if [ -z "$SHA1_RELEASE" ]; then SHA1_RELEASE_TEXT='（还没生成，稍后重新运行本脚本）'; else SHA1_RELEASE_TEXT="$SHA1_RELEASE"; fi
if [ -z "$SHA1_DEBUG" ]; then SHA1_DEBUG_TEXT='（读不到 app/keystore/debug.jks）'; else SHA1_DEBUG_TEXT="$SHA1_DEBUG"; fi

cat <<EOF

DayTrail · 高德 Key 填写卡
生成时间：$(date '+%Y-%m-%d %H:%M')
应用名：$FINAL_NAME

包名（PackageName）：
  正式版：$FINAL_PACKAGE
  调试版：$DEBUG_PACKAGE

SHA1：
  正式版：$SHA1_RELEASE_TEXT
  调试版：$SHA1_DEBUG_TEXT

到高德开放平台 https://console.amap.com/dev/key 新建 Key 时照着填：
  1) Android 平台 SDK（正式版）
     PackageName = $FINAL_PACKAGE
     发布版安全码 SHA1 = $SHA1_RELEASE_TEXT
  2) Android 平台 SDK（调试版，只有用 Android Studio 调试才需要）
     PackageName = $DEBUG_PACKAGE
     调试版安全码 SHA1 = $SHA1_DEBUG_TEXT
  3) Web 服务（不需要包名与 SHA1，用于「补全路线」和地点名反查）

这个文件里没有任何 Key 本身，可以放心保存。
EOF

if [ "$DRY_RUN" -eq 0 ]; then
    mkdir -p "$DIST"
    {
        printf 'DayTrail · 高德 Key 填写卡\n'
        printf '生成时间：%s\n' "$(date '+%Y-%m-%d %H:%M')"
        printf '应用名：%s\n\n' "$FINAL_NAME"
        printf '包名（PackageName）：\n  正式版：%s\n  调试版：%s\n\n' "$FINAL_PACKAGE" "$DEBUG_PACKAGE"
        printf 'SHA1：\n  正式版：%s\n  调试版：%s\n\n' "$SHA1_RELEASE_TEXT" "$SHA1_DEBUG_TEXT"
        printf '到高德开放平台 https://console.amap.com/dev/key 新建 Key 时照着填：\n'
        printf '  1) Android 平台 SDK（正式版）\n     PackageName = %s\n     发布版安全码 SHA1 = %s\n' "$FINAL_PACKAGE" "$SHA1_RELEASE_TEXT"
        printf '  2) Android 平台 SDK（调试版，只有用 Android Studio 调试才需要）\n     PackageName = %s\n     调试版安全码 SHA1 = %s\n' "$DEBUG_PACKAGE" "$SHA1_DEBUG_TEXT"
        printf '  3) Web 服务（不需要包名与 SHA1，用于「补全路线」和地点名反查）\n\n'
        printf '这个文件里没有任何 Key 本身，可以放心保存。\n'
    } >"$CARD"
    ok '已保存到 dist/amap-fill-card.txt'
fi

if [ "$NON_INTERACTIVE" -eq 0 ]; then
    read -r -p '  要我帮你打开高德控制台吗？（直接回车 = 是，输入 n 跳过）' open_console || open_console=''
    case "$open_console" in
        n|N|no|NO) ;;
        *)
            if command -v open >/dev/null 2>&1; then
                open 'https://console.amap.com/dev/key' >/dev/null 2>&1 || warn '打不开浏览器，请手动访问 https://console.amap.com/dev/key'
            elif command -v xdg-open >/dev/null 2>&1; then
                xdg-open 'https://console.amap.com/dev/key' >/dev/null 2>&1 || warn '打不开浏览器，请手动访问 https://console.amap.com/dev/key'
            else
                warn '请手动访问 https://console.amap.com/dev/key'
            fi
            ;;
    esac
    pause_enter '填好 Key 后按回车继续（也可以现在先跳过）'
fi

# ---------------------------------------------------------------- 第 6 步：填 Key

step 6 '把高德 Key 填进来'

FINAL_KEY_RELEASE="$CURRENT_KEY_RELEASE"
FINAL_KEY_WEB="$CURRENT_KEY_WEB"
FINAL_KEY_DEBUG="$CURRENT_KEY_DEBUG"
[ -n "$KEY_RELEASE" ] && FINAL_KEY_RELEASE="$KEY_RELEASE"
[ -n "$KEY_WEB" ] && FINAL_KEY_WEB="$KEY_WEB"
[ -n "$KEY_DEBUG" ] && FINAL_KEY_DEBUG="$KEY_DEBUG"

if [ "$NON_INTERACTIVE" -eq 0 ] && [ -z "$KEY_RELEASE" ]; then
    info '下面两项可以直接粘进来。还没有 Key 就一路回车跳过，之后重新运行本脚本接着做。'
    printf '\n'
    info "现在：正式版 $(mask_key "$FINAL_KEY_RELEASE") / Web 服务 $(mask_key "$FINAL_KEY_WEB")"
    read -r -p '  正式版 Key（直接回车 = 保持不变／跳过）' entry || entry=''
    case "$entry" in ''|skip|SKIP|跳过) ;; *) FINAL_KEY_RELEASE="$entry" ;; esac
    read -r -p '  Web 服务 Key（直接回车 = 保持不变／跳过）' entry || entry=''
    case "$entry" in ''|skip|SKIP|跳过) ;; *) FINAL_KEY_WEB="$entry" ;; esac
    if [ "$FINAL_TYPE" = 'debug' ]; then
        read -r -p '  调试版 Key（直接回车 = 保持不变／跳过）' entry || entry=''
        case "$entry" in ''|skip|SKIP|跳过) ;; *) FINAL_KEY_DEBUG="$entry" ;; esac
    fi
fi

for pair in "正式版 Key:$FINAL_KEY_RELEASE" "Web 服务 Key:$FINAL_KEY_WEB" "调试版 Key:$FINAL_KEY_DEBUG"; do
    label=${pair%%:*}
    value=${pair#*:}
    if [ -n "$value" ] && ! printf '%s' "$value" | grep -Eq '^[0-9A-Fa-f]{32}$'; then
        warn "$label 不像 32 位十六进制（高德通常给 32 位），仍会保存，请自己核对"
    fi
done

if [ "$DRY_RUN" -eq 0 ]; then
    if [ ! -f "$LOCAL_PROPERTIES" ]; then
        printf 'sdk.dir=%s\n' "$ANDROID_SDK" >"$LOCAL_PROPERTIES"
        ok '已创建 local.properties 并写入 SDK 路径'
    fi
    [ -n "$FINAL_KEY_RELEASE" ] && set_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_RELEASE' "$FINAL_KEY_RELEASE"
    [ -n "$FINAL_KEY_WEB" ] && set_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_WEB' "$FINAL_KEY_WEB"
    [ -n "$FINAL_KEY_DEBUG" ] && set_property_line "$LOCAL_PROPERTIES" 'AMAP_KEY_DEBUG' "$FINAL_KEY_DEBUG"

    current_repo=$(get_property_line "$GRADLE_PROPERTIES" 'app.updateRepo' || true)
    if [ "$current_repo" = 'hmilyld/daytrail' ]; then
        set_property_line "$GRADLE_PROPERTIES" 'app.updateRepo' ''
        ok '已清空在线更新源（避免指向本项目的仓库）'
    fi
fi

ok "正式版 Key：$(mask_key "$FINAL_KEY_RELEASE")"
ok "Web 服务 Key：$(mask_key "$FINAL_KEY_WEB")"
if [ -z "$FINAL_KEY_RELEASE" ] || [ -z "$FINAL_KEY_WEB" ]; then
    warn '还有 Key 没填。不影响编译，但地图、路线补全、地点名反查会用不了'
fi

# ---------------------------------------------------------------- 第 7 步：写入、图标、编译

step 7 '写入配置并编译'

if [ "$FINAL_NAME" != "$CURRENT_NAME" ] && [ "$DRY_RUN" -eq 0 ]; then
    set_string_resource 'app_name' "$FINAL_NAME"
    set_string_resource 'onboarding_subtitle' "$FINAL_NAME" "$CURRENT_NAME"
    set_string_resource 'today_map_empty_body' "$FINAL_NAME" "$CURRENT_NAME"
    ok "已改应用名：$FINAL_NAME"
else
    ok '应用名没有变化'
fi

if [ "$FINAL_PACKAGE" != "$CURRENT_PACKAGE" ] && [ "$DRY_RUN" -eq 0 ]; then
    set_property_line "$GRADLE_PROPERTIES" 'app.applicationId' "$FINAL_PACKAGE"
    ok "已改包名：$FINAL_PACKAGE"
fi

if [ -n "$ICON_COLOR" ]; then
    FINAL_ICON_COLOR=$(assert_icon_color "$ICON_COLOR")
elif [ "$NON_INTERACTIVE" -eq 1 ]; then
    FINAL_ICON_COLOR="$CURRENT_ICON_COLOR"
else
    printf '\n'
    info '图标底色可以换一个，只改一行，图形（白色路线 + 绿色起点）保持不变。'
    info '常用配色：#2F6BFF 蓝、#16A34A 绿、#E4572E 橙、#7C3AED 紫、#111827 深灰。'
    while true; do
        ask_text '图标底色（直接回车 = 保持不变）' "$CURRENT_ICON_COLOR"
        if FINAL_ICON_COLOR=$(assert_icon_color "$ASK_RESULT" 2>/dev/null); then
            break
        fi
        warn '颜色要写成 #RRGGBB 的形式，例如 #E4572E'
    done
fi

if [ "$FINAL_ICON_COLOR" != "$CURRENT_ICON_COLOR" ] && [ "$DRY_RUN" -eq 0 ]; then
    ICON_NEW_COLOR="$FINAL_ICON_COLOR" perl -0777 -pi -e '
        my $color = $ENV{"ICON_NEW_COLOR"};
        s{(<color name="ic_launcher_background">)#[0-9A-Fa-f]{6}(</color>)}{$1 . $color . $2}e;
    ' "$ICON_COLOR_XML"
    ok "已改图标底色：$FINAL_ICON_COLOR"
fi

LOG_PATH="$DIST/setup-log.txt"

if [ "$DRY_RUN" -eq 1 ]; then
    printf '\n'
    warn '试运行结束：没有写入任何文件，也没有编译'
    info "应用名：$FINAL_NAME"
    info "包名：$FINAL_PACKAGE"
    info "图标底色：$FINAL_ICON_COLOR"
    if [ "$HAS_KEYSTORE" -eq 1 ]; then info '签名：沿用已有'; else info '签名：将生成新签名'; fi
    if [ "$FINAL_TYPE" = 'release' ]; then info '安装包：正式版'; else info '安装包：调试版'; fi
    write_result "$(printf '{"ok":true,"dryRun":true,"appName":"%s","packageName":"%s","type":"%s","iconColor":"%s"}' \
        "$(json_escape "$FINAL_NAME")" "$(json_escape "$FINAL_PACKAGE")" "$FINAL_TYPE" "$FINAL_ICON_COLOR")"
    exit 0
fi

if [ "$NO_BUILD" -eq 1 ]; then
    printf '\n'
    ok '准备工作已完成，按 --no-build 要求跳过编译'
    write_result "$(printf '{"ok":true,"built":false,"appName":"%s","packageName":"%s","debugPackage":"%s","type":"%s","sha1Release":"%s","sha1Debug":"%s","cardPath":"%s","logPath":"%s"}' \
        "$(json_escape "$FINAL_NAME")" "$(json_escape "$FINAL_PACKAGE")" "$(json_escape "$DEBUG_PACKAGE")" \
        "$FINAL_TYPE" "$(json_escape "$SHA1_RELEASE")" "$(json_escape "$SHA1_DEBUG")" \
        "$(json_escape "$CARD")" "$(json_escape "$LOG_PATH")")"
    exit 0
fi

# 没有 Key 时默认不编译：先让用户去申请，回来再运行一次，
# 避免让完全不懂的人白等一次 10–30 分钟的构建。
keys_missing=0
if [ -z "$FINAL_KEY_RELEASE" ] || [ -z "$FINAL_KEY_WEB" ]; then
    keys_missing=1
fi
if [ "$keys_missing" -eq 1 ]; then
    skip_build=1
    if [ "$NON_INTERACTIVE" -eq 0 ]; then
        read -r -p '  现在还没有高德 Key。先跳过编译，等申请回来再运行？（直接回车 = 是；输入 n = 现在就编一个没有地图的版本）' answer || answer=''
        case "$answer" in
            n|N|no|NO) skip_build=0 ;;
            *) skip_build=1 ;;
        esac
    fi
    if [ "$skip_build" -eq 1 ]; then
        printf '\n'
        warn '已跳过编译。等 Key 申请好，再运行本脚本一次，它会自动接着往下做'
        info '  接下来你要做的：'
        info '    1. 打开 https://console.amap.com/dev/key，申请「Android 平台 SDK」与「Web 服务」两个 Key'
        info '    2. 按 dist/amap-fill-card.txt 里的包名与 SHA1 填进控制台'
        info '    3. 再运行本脚本，把两个 Key 粘进去，它会自动编译出 APK'
        write_result "$(printf '{"ok":true,"built":false,"needsKeys":true,"appName":"%s","packageName":"%s","debugPackage":"%s","type":"%s","sha1Release":"%s","sha1Debug":"%s","iconColor":"%s","cardPath":"%s"}' \
            "$(json_escape "$FINAL_NAME")" "$(json_escape "$FINAL_PACKAGE")" "$(json_escape "$DEBUG_PACKAGE")" \
            "$FINAL_TYPE" "$(json_escape "$SHA1_RELEASE")" "$(json_escape "$SHA1_DEBUG")" \
            "$FINAL_ICON_COLOR" "$(json_escape "$CARD")")"
        exit 0
    fi
fi

if [ "$YES" -eq 0 ]; then
    printf '\n'
    info '准备开始编译。第一次会下载很多文件，通常要 10 到 30 分钟。'
    info '中途看到百分比在动就是正常，请不要关窗口。'
    pause_enter '按回车开始编译'
fi

TASK=':app:assembleRelease'
[ "$FINAL_TYPE" = 'debug' ] && TASK=':app:assembleDebug'
mkdir -p "$DIST"

printf '\n  正在编译（%s）……\n' "$TASK"
set +e
./gradlew "$TASK" --console=plain 2>&1 | tee "$LOG_PATH"
build_code=${PIPESTATUS[0]}
set -e

if [ "$build_code" -ne 0 ]; then
    printf '\n'
    warn '编译失败了。最常见的原因有三种：'
    warn '  1. 还没有同意 Android SDK 的许可：打开 Android Studio 一次，让它自动装完'
    warn '  2. 网络太慢或中断：换个网络再来一次'
    warn '  3. 电脑上的杀毒软件拦截了 Gradle：临时关掉再试'
    printf '\n'
    warn "详细日志已保存在：$LOG_PATH"
    warn '把这个文件发给帮你的人，里面不会暴露你的高德 Key。'
    write_result "$(printf '{"ok":false,"built":false,"appName":"%s","packageName":"%s","type":"%s","logPath":"%s"}' \
        "$(json_escape "$FINAL_NAME")" "$(json_escape "$FINAL_PACKAGE")" "$FINAL_TYPE" "$(json_escape "$LOG_PATH")")"
    exit 3
fi

APK_PATH=''
for candidate in "$ROOT/app/build/outputs/apk/$FINAL_TYPE"/*.apk; do
    [ -f "$candidate" ] || continue
    APK_PATH="$candidate"
done
if [ -z "$APK_PATH" ]; then
    warn '编译命令成功了，但没找到 APK。请把下面的目录内容发给帮你的人：'
    warn "  $ROOT/app/build/outputs/apk/$FINAL_TYPE"
    exit 3
fi

ok "编译完成：$APK_PATH"

ADB="$ANDROID_SDK/platform-tools/adb"
[ -x "$ADB" ] || ADB=''
if [ "$INSTALL" -eq 1 ] || [ "$NON_INTERACTIVE" -eq 0 ]; then
    if [ -n "$ADB" ]; then
        connected=$("$ADB" devices 2>/dev/null | awk 'NR > 1 && $2 == "device" { print $1; exit }' || true)
        if [ -n "$connected" ]; then
            should_install=$INSTALL
            if [ "$should_install" -eq 0 ]; then
                read -r -p '  检测到手机已连接，要现在装进去吗？（直接回车 = 是，输入 n 跳过）' answer || answer=''
                case "$answer" in n|N|no|NO) should_install=0 ;; *) should_install=1 ;; esac
            fi
            if [ "$should_install" -eq 1 ]; then
                info '正在安装……'
                if "$ADB" install -r "$APK_PATH"; then
                    launch_package="$FINAL_PACKAGE"
                    if [ "$FINAL_TYPE" = 'debug' ]; then launch_package="$FINAL_PACKAGE.debug"; fi
                    "$ADB" shell am start -n "$launch_package/com.hmilyld.daytrail.MainActivity" >/dev/null 2>&1 || true
                    ok '已安装并打开'
                else
                    warn '安装没成功。手机上需要允许「安装未知应用」，装好后再点一次打开'
                fi
            fi
        elif [ "$INSTALL" -eq 1 ]; then
            warn '没有检测到手机。请用数据线连上并打开 USB 调试后再试'
        fi
    elif [ "$INSTALL" -eq 1 ]; then
        warn '找不到 adb，无法自动安装。请手动把 APK 传到手机上安装'
    fi
fi

printf '\n%s\n' '=============================================================='
printf '  全部完成\n'
printf '%s\n' '=============================================================='
printf '  APK：%s\n' "$APK_PATH"
info '装到手机：连数据线复制这个文件，或用微信/邮件发给自己，再用文件管理器点开安装。'
info '手机上如果提示「禁止安装未知应用」，在提示里点「设置」允许即可。'
printf '\n  还需要你做的事：\n'
if [ -z "$FINAL_KEY_RELEASE" ] || [ -z "$FINAL_KEY_WEB" ]; then
    info '  · 去高德申请 Key，然后重新运行本脚本填进来（现在地图是空白的）'
fi
info '  · 备份 app/keystore/release.jks 与 keystore.properties（丢了就不能覆盖升级）'
info '  · 想换成自己的图片图标：看 docs/build-your-own.md 的「换图标」一节'
info "  · 这个应用的包名：$FINAL_PACKAGE"
printf '\n'

write_result "$(printf '{"ok":true,"built":true,"appName":"%s","packageName":"%s","debugPackage":"%s","type":"%s","sha1Release":"%s","sha1Debug":"%s","iconColor":"%s","apkPath":"%s","cardPath":"%s","logPath":"%s"}' \
    "$(json_escape "$FINAL_NAME")" "$(json_escape "$FINAL_PACKAGE")" "$(json_escape "$DEBUG_PACKAGE")" \
    "$FINAL_TYPE" "$(json_escape "$SHA1_RELEASE")" "$(json_escape "$SHA1_DEBUG")" \
    "$FINAL_ICON_COLOR" "$(json_escape "$APK_PATH")" "$(json_escape "$CARD")" "$(json_escape "$LOG_PATH")")"
