# 用 AI 帮你编译（推荐）

> 提示词版本 1.0 · 对应脚本参数 `--name` / `--id` / `--type` / `--json`
>
> 这一页有两种用法，先看下面那张表选一个。选错了也能重来，不会弄坏任何东西。

## 一、先选你属于哪种情况

| 你的 AI | 长什么样 | 用哪段 |
|---|---|---|
| **能操作电脑的 AI** | Claude Code、Codex、Cursor、Cline、OpenCode 这类，能读写文件、能运行命令 | 下面的「口令 A」，4 行 |
| **只会聊天的 AI** | 网页版的 ChatGPT、Kimi、豆包、通义等，只能说话不能动手 | 下面的「对话脚本 B」，它出主意，你动手 |

不确定的话：如果你的 AI 能"帮我改文件"、"跑命令"，就是第一种。

## 二、口令 A（粘给能操作电脑的 AI）

在**项目文件夹**里打开 AI，把下面这段整个粘进去：

```text
当前文件夹是 DayTrail，一个 Kotlin + Jetpack Compose 的开源轨迹记录应用。

请先完整阅读 docs/ai-prompt.md，然后严格按里面的「执行契约」，把项目改造成我自己的应用并编译出 APK。
先做环境自检；缺什么就告诉我要装什么，然后停下来等我。
需要我亲手做的事，一次只让我做一件，并告诉我做完回复什么。
```

如果 AI 说"找不到 docs/ai-prompt.md"，说明你手上的项目版本太旧：回它一句「请先让我把这个仓库升级到最新版本」，或者重新下载一次 ZIP。

## 三、对话脚本 B（粘给网页聊天 AI）

```text
我在把开源 Android 应用 DayTrail 编译成我自己的版本。我完全不懂技术，请你一步一步带我，
一次只让我做一件事，等我说「好了」再继续。

背景：
- 项目地址我会发给你；我只需要下载 ZIP，不需要 GitHub 账号。
- 我的电脑是（Windows 11 / macOS，请先问我）。
- 这个项目自带一个向导脚本：Windows 是 setup-my-app.cmd，macOS 是 setup-my-app.command，
  改包名、改应用名、生成签名、编译都由它完成。
- 地图用高德，我要自己申请两个 Key（正式版 Key + Web 服务 Key），
  并把「包名」和「SHA1」填到高德网站。这两个值脚本会打印出来，我会复制给你核对。
- 全程不要让我手敲命令；必须用命令时，给我一条完整可复制的命令，并说明在哪里粘贴。

请按顺序带我：
1. 确认系统版本和 10 GB 以上空闲磁盘。
2. 带我从 GitHub 下载 ZIP 并解压（提醒我必须在解压后的文件夹里操作，不能在压缩包里双击）。
3. 带我到官网装 Android Studio，并让我第一次打开它、等 SDK 下载完再关。
4. 让我双击向导脚本，先只做「改名 + 生成签名」，把打印的卡片内容给你检查。
5. 带我去高德开放平台注册、实名认证、创建两个 Key，逐个格子告诉我填什么。
6. 让我把 Key 填回脚本再运行一次，直到出现 APK。
7. 带我把 APK 装到手机（提醒允许「未知来源」，不要用微信直接打开）。

规则：
- 不要让我关掉正在下载或编译的窗口；教我分辨「在下载」和「真的卡住」。
- 有报错就让我把窗口文字整段复制给你，不要猜。
- 我的 Key 不要在聊天里完整显示。
```

## 四、执行契约（口令 A 的 AI 读这一节）

### 目标

把当前仓库改造成用户自己的应用，并产出一个可安装的 release APK。全程用中文，一次只问一个问题。

### 允许修改的范围（只允许这 6 处）

1. `gradle.properties` → `app.applicationId`、`app.updateRepo`
2. `app/src/main/res/values/strings.xml` → 应用显示名（`app_name` 与两处正文品牌词）
3. `app/src/main/res/values/ic_launcher_background.xml` → 图标底色
4. `app/keystore/release.jks` 与 `keystore.properties`（仅当两者都不存在时生成）
5. `local.properties` → 只增改 `AMAP_KEY_RELEASE` / `AMAP_KEY_WEB` / `AMAP_KEY_DEBUG`
6. `dist/` 下的产物（`amap-fill-card.txt`、`setup-result.json`、`setup-log.txt`）

### 禁止

- 改 `namespace`、移动或重命名 Kotlin 包与源码目录；
- 改 `.github/`、`scripts/verify.ps1`、任何 CI 配置；
- `git push`；把 `keystore.properties`、`*.jks`、`local.properties` 加进版本控制；
- 在对话里回显完整 Key、keystore 口令；
- 凭记忆手改 Gradle 结构。脚本不存在 = 仓库版本过旧，停下来让用户升级；
- 编造包名、Key，或使用 `com.example.*`。

### 步骤

0. **环境自检**：操作系统；JDK 与 Android SDK（优先 Android Studio 自带）。
   缺 SDK 时让用户先打开一次 Android Studio 完成 SDK 下载，然后停。
1. **收集信息**，一次只问一个：
   - 应用显示名（中文即可，≤12 字）
   - 英文昵称（小写字母/数字，用于拼包名 `com.<昵称>.daytrail`）
   - 安装包类型：`release`（默认，只要两个 Key）或 `debug`
2. **只做准备，不编译**（禁止交互式运行，会挂住）：

   Windows：

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\setup-my-app.ps1 -Name <名字> -Id <昵称> -Type release -NoBuild -Json -Yes
   ```

   macOS / Linux：

   ```bash
   bash scripts/setup-my-app.sh --name <名字> --id <昵称> --type release --no-build --json --yes
   ```

   然后读取 `dist/setup-result.json`（比解析控制台输出可靠），取 `packageName`、`sha1Release`、`sha1Debug`、`cardPath`。
   如果返回 `built=false` 且 `needsKeys=true`，说明还缺 Key，继续第 3 步，不要重试编译。

3. **让用户去申请 Key**：`https://console.amap.com/dev/key`，需要两个（正式版 + Web 服务）。
   把 `packageName` 与 `sha1Release` 原样告诉用户——这两个值不是密钥，可以显示。
4. **等用户把两个 Key 贴回来**。不要自己编造，不要从网上找。
5. **填 Key 并编译**（去掉 `-NoBuild`，带上 Key）：

   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\setup-my-app.ps1 -Name <名字> -Id <昵称> -Type release -KeyRelease <Key> -KeyWeb <Key> -Json -Yes
   ```

6. **校验并报告**：
   - 包名与高德卡片一致；
   - APK 存在且非空（`apkPath`）；
   - 应用名与用户输入一致；
   - 给出 APK 的完整路径和安装方法。
7. **可选安装**：检测 adb 与已连接设备，询问后再 `-Install`。

### 失败处理

- 构建失败：先看 `dist/setup-log.txt`，用中文给出 3 条最可能原因，不要粘贴原始 Gradle 堆栈。
- 用户中途放弃：不要继续写文件，如实汇报已完成到哪一步。
- 用户拒绝安装 Android Studio：说明没有它无法编译，给出官网链接后停止。

## 五、AI 做完后，你验收这三件事

| 检查 | 怎么做 | 正常表现 |
|---|---|---|
| 名字 | 看手机桌面图标下面 | 是你起的名字 |
| 包名 | 打开 `dist/setup-result.json` 看 `packageName` | 与高德 Key 里填的一致 |
| 地图 | 打开应用首页 | 有底图；Key 没填时是空白，属正常 |

## 六、给 AI 的提醒（可整段贴给它）

```text
这个项目已经带了经过测试的脚本，不要自己重写文件操作逻辑。
你的工作是：问清楚信息 → 调用脚本 → 读 dist/setup-result.json → 把结果用中文讲给我。
每一步只说结论，不要贴代码，不要贴 Gradle 日志。
```
