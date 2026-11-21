# 在线更新

应用不通过应用商店分发，Release 渠道即为更新渠道。更新检查读取 Release 附带的静态清单，不直接调用 GitHub API。

## 1. 不直接使用 GitHub API 的原因

匿名调用 `api.github.com` 的限制为每小时每 IP 60 次。按每日一次检查的频率，单个用户不会触及上限，但同一出口 IP 下的用户共享配额（企业 NAT、校园网、运营商 NAT），数十名用户即可耗尽，之后所有请求都会失败。

因此主源为每个 Release 附带的静态文件：

```
https://github.com/OWNER/REPO/releases/latest/download/update.json
```

该地址由 GitHub CDN 直接返回，不消耗 API 配额。仅在清单缺失时回退至 API，用于获取 Release 页面链接。

## 2. 清单格式

清单由发布工作流在每个 Release 上生成，详见 [`RELEASE.md`](RELEASE.md)。

```json
{
  "versionName": "0.4.0",
  "versionCode": 6,
  "tag": "v0.4.0",
  "title": "行迹 0.4.0",
  "notes": "## 修复\n- 导入重复点",
  "pageUrl": "https://github.com/OWNER/REPO/releases/tag/v0.4.0",
  "apkUrl": "https://github.com/OWNER/REPO/releases/download/v0.4.0/daytrail-0.4.0.apk",
  "apkSize": 39234567,
  "apkSha256": "3f8a…"
}
```

| 字段 | 必需 | 说明 |
|---|---|---|
| `versionName` | 是 | 必须可解析为语义化版本。无法解析时整份清单视为不可用，回退至 API |
| `versionCode` | 否 | 用于识别仅递增构建号的热修复。缺省值为 0 |
| `tag` | 否 | 缺省为 `v` 加 `versionName` |
| `notes` | 否 | 更新说明，在对话框中滚动显示 |
| `apkUrl` | 否 | 缺省时客户端提供「打开 Release 页面」入口 |
| `apkSha256` | 否 | 提供时强制执行校验；未提供时仅依赖签名一致性校验 |

`apkUrl` 必须固定指向当前 tag，不得使用 `releases/latest/download/` 形式，否则用户下载 0.4.0 时可能获取到 0.5.0 的安装包。

## 3. 检查频率与失败处理

| 规则 | 取值 |
|---|---|
| 自动检查间隔 | 24 小时，常量 `UpdateGate.CHECK_INTERVAL_MS` |
| 手动检查 | 忽略间隔限制与「稍后」记录 |
| 网络不可达 | 不记录检查时间，下次启动可重试 |
| 服务端错误或限流 | 记录检查时间，避免重复触发 |
| 返回 403 且响应体包含 `rate limit` | 判定为限流，提示稍后重试 |
| 主源缺失或解析失败 | 回退至 API |
| 主源连接失败 | 直接返回失败，不再请求 API |
| API 回退时的草稿与预发布版本 | 跳过 |

## 4. 下载与校验

安装前依次执行三项校验：

1. **SHA-256**：与清单中的 `apkSha256` 比对，不一致时删除文件并终止；
2. **包名一致性**：通过 `getPackageArchiveInfo` 读取 APK 包名，须与当前应用一致；
3. **签名一致性**：通过 `GET_SIGNING_CERTIFICATES` 比较签名证书的 SHA-256。

系统安装器本身也会拒绝签名不一致的安装包，但自行校验可提供明确的错误说明，而非系统级报错。

下载文件存放于 `cacheDir/updates/`，通过 `FileProvider`（authority 为 `${applicationId}.updates`）交予系统安装器。安装包在移交后不得立即删除，用户确认安装时安装器才会读取该文件。清理工作在下次下载前执行。

## 5. 安装前置条件

- 清单中声明 `REQUEST_INSTALL_PACKAGES`；
- 用户需在系统设置中允许本应用安装应用。应用通过 `canRequestPackageInstalls()` 检测，未授权时跳转 `ACTION_MANAGE_UNKNOWN_APP_SOURCES`；
- 未授权期间保留已下载的安装包，用户授权返回后可直接安装，无需重新下载。

## 6. 发布配置

更新源在 `gradle.properties` 中配置：

```properties
app.updateRepo=OWNER/REPO
# 可选：覆盖清单地址，便于本地联调
app.updateFeedUrl=
```

`app.updateFeedUrl` 为空时使用 `https://github.com/<app.updateRepo>/releases/latest/download/update.json`。

## 7. 边界场景

| 场景 | 行为 |
|---|---|
| 未配置 `app.updateRepo` | 设置页入口可见，点击后说明未配置，不发起请求 |
| 仓库中没有任何 Release | 手动检查提示已是最新，自动检查静默 |
| Release 未附带 APK | 提供「打开 Release 页面」入口 |
| 下载中断 | 保留 `.part` 文件，下次重新下载。不实现断点续传，39MB 规模下收益有限 |
| 用户选择「稍后」 | 记录该版本，不再自动提示；手动检查仍然可见 |
| 存储空间不足 | 捕获 `IOException`，提示无法保存 |
