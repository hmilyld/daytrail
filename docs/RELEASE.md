# 发布流程

从修改版本号到用户收到更新共六个步骤。步骤顺序不可调整，跳过任一步骤均会在 CI 阶段失败。

## 0. 前置条件

自己编译（不是维护本仓库）的人不需要读这一页，看 [`build-your-own.md`](build-your-own.md) 即可。
如果你 fork 后要发自己的版本，必须先完成那里的改名与 Key 配置：`update.json` 的包名与签名校验
都按你自己的仓库与签名算，直接沿用本项目的发布参数会在安装阶段被拒绝。

需在仓库中配置以下四个 secret，否则只能发布未签名包：

| Secret | 内容 |
|---|---|
| `KEYSTORE_BASE64` | `base64 -w0 release.jks` 的输出 |
| `KEYSTORE_PASSWORD` | keystore 口令 |
| `KEY_ALIAS` | 密钥别名 |
| `KEY_PASSWORD` | 密钥口令 |

签名密钥必须与已发布版本保持一致。更换 keystore 会导致无法覆盖安装，用户需卸载重装并丢失数据。`release.jks` 与 `keystore.properties` 必须在仓库之外另行备份。

## 1. 修改版本号

版本号唯一来源为 `gradle.properties`：

```properties
app.versionName=0.4.0
app.versionCode=6
```

- `versionName` 必须可解析为「主版本.次版本.修订号」；
- `versionCode` 每次发布必须严格递增，否则系统将拒绝安装。

## 2. 编写更新日志

在 `CHANGELOG.md` 中将 `[Unreleased]` 的内容移至新版本小节，标题格式必须为：

```markdown
## [0.4.0] - 2026-11-21
```

CI 使用 `awk` 从 `^## [0.4.0]` 抽取至下一个 `## [` 为止，作为 Release 说明并写入 `update.json` 的 `notes` 字段。标题格式不符时发布失败，错误信息为「CHANGELOG.md 里没有 x.y.z 的小节」。

## 3. 本地全量验证

```powershell
.\scripts\verify.ps1
```

该步骤与 CI 执行的检查项一致，本地通过可减少 CI 往返。
脚本开始时会清空 `dist/`：那里只放临时测试产物，绝不能作为发布包上传。

## 4. 提交并创建 tag

```powershell
git add -A
git commit -m "chore(release): 0.4.0"
git tag -a v0.4.0 -m "行迹 0.4.0"
git push origin main
git push origin v0.4.0
```

tag 名称必须等于 `v` 加 `app.versionName`。CI 首先校验该一致性。

## 5. CI 执行内容

`.github/workflows/release.yml` 依次执行：

1. 校验 tag 与 `app.versionName` 一致；
2. 从 `CHANGELOG.md` 抽取对应小节，生成 `release-notes.md`；
3. 使用 secret 中的 keystore 完成签名；
4. 执行 `spotlessCheck`、Debug 构建与单测、lint，以及 Release 构建、单测与 lint；
5. 运行 `node tools/write-update-manifest.mjs` 生成 `update.json`，其中 `apkSha256` 由实际字节计算，`apkUrl` 固定指向当前 tag；
6. 将 APK 与 `update.json` 上传至该 tag 的 Release。

未配置 keystore 时，仅发布未签名 APK，不生成 `update.json`，并在说明中注明。此时客户端回退至 GitHub API，仅提供 Release 页面链接。

## 6. 发布后验证

| 检查项 | 方法 |
|---|---|
| 清单可访问 | 打开 `https://github.com/OWNER/REPO/releases/latest/download/update.json`，核对 `versionName` 与 `apkSha256` |
| 应用内可见 | 使用旧版本进入「我的 → 关于 → 检查更新」，应提示新版本 |
| 下载与安装 | 执行下载，确认进度显示与系统安装界面正常 |
| 覆盖安装成功 | 安装后版本号更新且原有数据保留，说明为覆盖安装而非重装 |
| 仓库为私有状态 | 匿名请求返回 404，应用显示「已是最新」，属预期行为 |

## 失败排查

| 现象 | 原因 | 处理 |
|---|---|---|
| `tag vX 与 app.versionName=Y 不一致` | 创建 tag 前未修改 `gradle.properties` | 修改版本号后重新创建 tag，本地执行 `git tag -d`，远端执行 `git push --delete` |
| `CHANGELOG.md 里没有 x.y.z 的小节` | 小节标题格式不符或缺失 | 补充 `## [x.y.z] - <日期>` |
| `期望恰好一个 release APK` | 输出目录存在多个 APK | 清理 `app/build`，确认仅有一个变体 |
| CI 在 `sdkmanager "platforms;android-37"` 阶段失败 | 公开 SDK 仓库尚未提供该平台 | 将 workflow 改为实际可用版本，并同步确认 `compileSdk` |
| 应用始终提示「已是最新」 | 仓库为私有、无 Release，或 `app.updateRepo` 为空 | 逐项确认；私有仓库的匿名请求即为 404 |
| 下载后提示签名不一致 | 本次发布使用的 keystore 与已安装版本不同 | 找回原 keystore；无法找回时只能更换包名重新发布 |
| 提示「这个版本没有提供安装包」 | Release 仅含源码，未上传 APK | 客户端会退化为打开 Release 页面，属预期行为 |

## 回滚

Release 内容可以编辑并重新上传，但已安装至用户设备的版本无法回滚。若新版本存在严重问题：

1. 立即发布修订版本，`versionCode` 需大于当前版本；
2. 不得将旧版 `update.json` 重新上传，版本比较仅识别更高版本，用户不会被降级；
3. 本项目不设服务端，无法远程停用功能，只能通过新版本修复。

## 发布检查清单

- [ ] `gradle.properties` 中 `versionName` 与 `versionCode` 已更新
- [ ] `CHANGELOG.md` 存在对应小节且标题格式正确
- [ ] `.\scripts\verify.ps1` 全部通过
- [ ] 本次改动的关键路径已在真机验证
- [ ] tag 名称为 `v` 加 `versionName`
- [ ] Release 中同时包含 APK 与 `update.json`
- [ ] 使用旧版本实机完整验证「检查更新 → 下载 → 安装」流程
- [ ] `dist/` 下没有遗留的测试包。该目录不纳入版本控制，只放临时测试产物，
      发布前跑 `verify.ps1` 会自动清空
