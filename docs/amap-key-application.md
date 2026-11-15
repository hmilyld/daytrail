# 高德地图 Key 申请信息

> 一次性填完所需材料。申请入口：https://console.amap.com/dev/key

## 一、账号前置

| 步骤 | 说明 |
|---|---|
| 1. 注册高德开放平台账号 | https://lbs.amap.com/dev/id/ ，手机号 + 邮箱验证 |
| 2. 实名认证 | 控制台 → 用户权限 → 实名认证。**个人开发者**即可，需要身份证 + 人脸核验。未认证的 Key 调用会失败 |
| 3. 创建应用 | 控制台 → 应用管理 → 创建新应用。名称填 `行迹`，类型选「出行」或「生活服务」 |

> 认证通常几分钟到 1 个工作日。企业认证配额更高，个人自用没必要。

## 二、要申请两个 Key（因为 debug 包名不同）

高德**一个 Key 只绑定一个包名**，而本工程的 debug 变体带 `.debug` 后缀，所以必须两个 Key。

在同一个应用下点两次「添加新 Key」：

### Key A — Release 版

| 字段 | 值 |
|---|---|
| Key 名称 | `daytrail-release` |
| 绑定服务 | **Android 平台 SDK** |
| 发布版安全码 SHA1 | `<YOUR_RELEASE_SHA1>` |
| 调试版安全码 SHA1 | 留空 |
| PackageName | `com.hmilyld.daytrail` |

### Key B — Debug 版

| 字段 | 值 |
|---|---|
| Key 名称 | `daytrail-debug` |
| 绑定服务 | **Android 平台 SDK** |
| 发布版安全码 SHA1 | 留空 |
| 调试版安全码 SHA1 | `<REDACTED>:A8:2C:97:3F:AA:40:C9:DA:EE:F4:DB:EA:DD:C8:60:9C` |
| PackageName | `com.hmilyld.daytrail.debug` |

> 也可以只申请 Release 一个 Key，然后去掉 debug 的 `.debug` 后缀让两者共用包名。但那样 debug 包会和正式包互相覆盖安装，且两个 SHA1 填在同一个 Key 里也能用。**建议还是申请两个**，互不干扰。

## 三、SHA1 的来源（已实测）

| 用途 | keystore 路径 | SHA1 |
|---|---|---|
| Release | `app/keystore/release.jks`（别名 `daytrail`） | `<YOUR_RELEASE_SHA1>` |
| Debug | `<USER_HOME>\.android\debug.keystore`（别名 `androiddebugkey`） | `<REDACTED>:A8:2C:97:3F:AA:40:C9:DA:EE:F4:DB:EA:DD:C8:60:9C` |

Release keystore 已生成：4096 位 RSA，有效期 30 年（10950 天），密码存在 `keystore.properties`（已加入 `.gitignore`，不进 Git）。

**备份提醒**：`app/keystore/release.jks` 和 `keystore.properties` 一旦丢失，就无法给已安装的旧版本升级，只能卸载重装。请自行备份到安全位置。

## 四、Key 填进哪里

申请完把两个 Key 填进 `local.properties`：

```properties
sdk.dir=E:\\Androiddev\\sdk
AMAP_KEY_DEBUG=Key B 的值
AMAP_KEY_RELEASE=Key A 的值
```

`local.properties` 已在 `.gitignore` 里，不会进 Git。

`app/build.gradle.kts` 会按 buildType 注入到 manifest 的 `com.amap.api.v2.apikey`：

- `debug` → `AMAP_KEY_DEBUG`
- `release` → `AMAP_KEY_RELEASE`

## 五、配额说明

| 项 | 个人开发者 |
|---|---|
| 地图 SDK 显示 | 无日调用量上限（按 QPS 限制） |
| 定位 SDK | 无日调用量上限（按 QPS 限制） |
| 逆地理编码（本项目不用） | 有日限额 |

本项目的设计**不调用任何 Web 服务 API**：地图瓦片和定位都走 SDK，停留点地名靠手动命名 + 同位置复用。所以配额基本不是问题。

## 六、常见报错对照

| 报错 | 原因 |
|---|---|
| `INVALID_USER_SCODE` | PackageName 与 `applicationId` 不一致，或 SHA1 填错（常见：debug 包填了 release 的 SHA1） |
| `INVALID_USER_KEY` | Key 填错，或绑定的服务类型不是「Android 平台 SDK」 |
| `USERKEY_PLAT_NOMATCH` | 用了 Web 服务的 Key 调 SDK |
| 地图空白但无报错 | 隐私合规接口 `updatePrivacyShow` / `updatePrivacyAgree` 没调，或调用晚于 `MapView` 构造 |
| `INVALID_USER_DOMAIN` | Key 绑定了域名（Web 服务类型），不是 SDK 类型 |
