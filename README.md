# 行迹 DayTrail

纯本地、离线优先的个人轨迹记录器。后台按省电策略采点，按天落库，在手机上回看每天的路线、停留点和里程；自动把连续几天的出差归成「行程」；数据可导出带走。

- 包名：`com.hmilyld.daytrail`（debug 变体 `com.hmilyld.daytrail.debug`）
- 最低系统：Android 12（API 31）
- 设计文档：`<USER_HOME>\.opencode\plan\daytrail-design.md`

## 环境

```powershell
$env:JAVA_HOME = '<ANDROID_TOOLCHAIN>\jdk21'
$env:ANDROID_HOME = '<ANDROID_TOOLCHAIN>\sdk'
$env:ANDROID_SDK_ROOT = '<ANDROID_TOOLCHAIN>\sdk'
$env:GRADLE_USER_HOME = '<ANDROID_TOOLCHAIN>\gradle-home'
```

## 构建与验证

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
.\gradlew.bat :app:assembleRelease :app:testReleaseUnitTest :app:lintRelease
.\gradlew.bat :app:spotlessCheck
```

## 签名

Release keystore：`app/keystore/release.jks`（别名 `daytrail`，4096 位 RSA，有效期 30 年）。
密码在 `keystore.properties`，该文件与 `*.jks` 都在 `.gitignore` 里，**不进 Git**。

**必须自行备份** `app/keystore/release.jks` + `keystore.properties` 到仓库之外的安全位置。
一旦丢失，已安装的旧版本无法覆盖升级，只能卸载重装。

### SHA1（申请高德 Key 用）

| 用途 | SHA1 |
|---|---|
| Release | `<YOUR_RELEASE_SHA1>` |
| Debug | `<YOUR_DEBUG_SHA1>` |

Release SHA256：`<YOUR_RELEASE_SHA256>`

Debug keystore 固定为 `app/keystore/debug.jks`（密码 `android`），**随仓库提交**，保证任何机器上 debug SHA1 一致，不用重复申请 Key。Release keystore 不进 Git。

## 高德 Key

申请步骤见 `docs/amap-key-application.md`。申请完填进 `local.properties`：

```properties
AMAP_KEY_DEBUG=...
AMAP_KEY_RELEASE=...
```

`local.properties` 不进 Git。
