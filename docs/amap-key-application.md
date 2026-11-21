# 高德地图 Key 申请指南

> 申请所需材料与入口：https://console.amap.com/dev/key
>
> 本项目**不内置任何 Key**。每个使用者（包括 fork 者）必须自己申请，
> 否则地图显示占位说明、纠偏与地名反查静默失败。

## 一、账号前置

| 步骤 | 说明 |
|---|---|
| 1. 注册高德开放平台账号 | https://lbs.amap.com/dev/id/ ，手机号 + 邮箱验证 |
| 2. 实名认证 | 控制台 → 用户权限 → 实名认证。**个人开发者**即可，需要身份证 + 人脸核验。未认证的 Key 调用会失败 |
| 3. 创建应用 | 控制台 → 应用管理 → 创建新应用。名称自填，类型选「出行」或「生活服务」 |

> 认证通常需要几分钟至一个工作日。企业认证配额更高，个人使用无需升级。

## 二、要申请三个 Key

| Key | 平台 | 绑定 |
|---|---|---|
| Release 版 | Android 平台 SDK | 包名 `com.hmilyld.daytrail` + release SHA1 |
| Debug 版 | Android 平台 SDK | 包名 `com.hmilyld.daytrail.debug` + debug SHA1 |
| Web 服务版 | Web 服务 | 用于轨迹纠偏与地点名反查 |

高德**一个 Key 只绑定一个包名**，而本工程的 debug 变体带 `.debug` 后缀，所以 SDK Key 必须两个。
Web 服务 Key 与 Android SDK Key 不可混用，用途不同。

### Key A — Release 版

| 字段 | 值 |
|---|---|
| Key 名称 | 自填，例如 `daytrail-release` |
| 发布版安全码 SHA1 | `<YOUR_RELEASE_SHA1>` |
| 调试版安全码 SHA1 | 留空 |
| PackageName | `com.hmilyld.daytrail` |

### Key B — Debug 版

| 字段 | 值 |
|---|---|
| Key 名称 | 自填，例如 `daytrail-debug` |
| 发布版安全码 SHA1 | 留空 |
| 调试版安全码 SHA1 | `<YOUR_DEBUG_SHA1>` |
| PackageName | `com.hmilyld.daytrail.debug` |

### Key C — Web 服务版

| 字段 | 值 |
|---|---|
| Key 名称 | 自填，例如 `daytrail-web` |
| 服务平台 | **Web 服务**（不是 Android 平台） |
| 白名单 | 客户端无法配置 IP 白名单，建议在控制台设置**日配额上限**止损 |

## 三、SHA1 怎么来

```powershell
# Release（你自己的 keystore）
keytool -list -v -keystore <PATH_TO_YOUR_KEYSTORE> -alias <YOUR_KEY_ALIAS>

# Debug（仓库内已固定的公开调试密钥）
keytool -list -v -keystore app/keystore/debug.jks -alias androiddebugkey -storepass android
```

取输出中的 `SHA1:` 一行（部分 JDK 显示为「证书指纹 → SHA1」）。

**为什么 debug 用项目内的 keystore**：Android Studio 自动生成的 `~/.android/debug.keystore`
在每台机器上都不一样，换机器或重装就要重新申请 Key。把调试密钥固定在仓库里
（`app/keystore/debug.jks`，公开密钥，不含隐私）后，debug SHA1 就永久稳定。
它**只能签 debug 包**，与发布签名无关。

Release keystore 由你自己生成并保存在仓库之外，`keystore.properties` 指向它。
**一旦丢失，无法给已安装版本升级**，只能卸载重装。

## 四、Key 填进哪里

```properties
sdk.dir=<PATH_TO_ANDROID_SDK>
AMAP_KEY_DEBUG=<YOUR_AMAP_ANDROID_KEY_DEBUG>
AMAP_KEY_RELEASE=<YOUR_AMAP_ANDROID_KEY_RELEASE>
AMAP_WEB_KEY=<YOUR_AMAP_WEB_SERVICE_KEY>
```

模板见仓库根目录 `local.properties.example`。`local.properties` 已在 `.gitignore` 里，不会进 Git。

`app/build.gradle.kts` 的处理方式：

- Android Key → `manifestPlaceholders["amapApiKey"]`，按 buildType 注入 `AndroidManifest.xml` 的 `com.amap.api.v2.apikey`
- Web 服务 Key → `buildConfigField("String", "AMAP_WEB_KEY", …)`

## 五、配额说明

| 项 | 个人开发者 |
|---|---|
| 地图 SDK 显示 | 无日调用量上限（按 QPS 限制） |
| 定位 SDK | 无日调用量上限（按 QPS 限制） |
| 逆地理编码 / 轨迹纠偏（Web 服务） | **有日限额** |

本项目只用 Web 服务做两件**用户可感知的**联网操作：按需轨迹纠偏、新建地点时反查地名。
两者失败都静默，不影响记录与离线功能。若日限额耗尽，建议在设置里关闭自动反查。

## 六、常见报错对照

| 报错 | 原因 |
|---|---|
| `INVALID_USER_SCODE` | PackageName 与 `applicationId` 不一致，或 SHA1 填错（最常见：debug 包填了 release 的 SHA1） |
| `INVALID_USER_KEY` | Key 填错，或绑定的服务类型不是「Android 平台 SDK」 |
| `USERKEY_PLAT_NOMATCH` | 用了 Web 服务的 Key 调 SDK |
| 地图空白但无报错 | 隐私合规接口 `updatePrivacyShow` / `updatePrivacyAgree` 没调，或调用晚于 `MapView` 构造 |
| `INVALID_USER_DOMAIN` | Key 绑定了域名（Web 服务类型），不是 SDK 类型 |
| `USERKEY_VERIFY_FAIL` | 应用签名被篡改，或用了未在 Key 里登记的 keystore |
| 定位总是失败、地图黑屏 | x86_64 模拟器缺少 ARM 原生库，属 SDK 限制；请用 ARM 真机或 arm64 模拟器 |
