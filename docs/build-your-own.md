# 编译属于你自己的行迹

这篇文档写给**完全没有编程经验**的人。照着做，你会得到一个属于你自己的 APK：

- 名字是你起的，图标底色可以换；
- 包名是你自己的，可以和你手机上已有的版本共存；
- 高德 Key 是你自己申请的，调用量只算在你名下，不会和别人的混在一起、把额度用爆。

全程**不需要看懂代码，也不需要手敲命令**。Windows 和 macOS 都可以。

---

## 0. 30 秒速查

| 问题 | 答案 |
|---|---|
| 要装什么 | 只装一个 **Android Studio**（自带 Java 和 Android SDK，官网免费） |
| 要多久 | 第一次约 30–60 分钟，其中下载占大半 |
| 要花钱吗 | 不用。Android Studio 与高德个人开发者额度都免费 |
| 会上传我的位置吗 | 不会。轨迹只存在你手机里，详见 `privacy-policy.md` |
| 要 GitHub 账号吗 | 不需要。下载 ZIP 就行 |
| 看不懂"包名""Key" | 看下面第 2 节，每个词一句话解释 |

**想让 AI 带你做？** 复制这段，粘给能操作电脑的 AI（Claude Code、Codex、Cursor、Cline、OpenCode 等），然后在项目文件夹里让它干活：

```text
当前文件夹是 DayTrail，一个 Kotlin + Jetpack Compose 的开源轨迹记录应用。

请先完整阅读 docs/ai-prompt.md，然后严格按里面的「执行契约」，把项目改造成我自己的应用并编译出 APK。
先做环境自检；缺什么就告诉我要装什么，然后停下来等我。
需要我亲手做的事，一次只让我做一件，并告诉我做完回复什么。
```

如果你只有网页版聊天 AI（ChatGPT、Kimi、豆包等），用 [`ai-prompt.md`](ai-prompt.md) 里的「对话脚本 B」，它会一步一步指挥你。

---

## 1. 为什么不直接用别人打好的安装包

不是小气，是 Key 的规则决定的：

- 高德的 Android Key **绑定「包名 + 签名指纹（SHA1）」**，一个 Key 只认一个包名。
- 别人编译好的 APK 里，Key 已经写死在安装包里，你**换不掉**。
- 所有装同一个 APK 的人共用同一把 Key。人一多，调用量叠加，高德侧就会限流，甚至开始要求付费开通。

所以正确的用法是：**每个人自己申请 Key、自己编译**。做一次，之后一直用。

---

## 2. 先认几个词（每个一句话）

| 词 | 一句话解释 |
|---|---|
| ZIP | 压缩包。要**先解压**再操作，不能在压缩包里双击 |
| Android Studio | 谷歌官方的安卓开发软件。装上它，编译要用的东西就全了 |
| JDK / Java | 编译用的运行环境。Android Studio 自带，不用单独装 |
| Android SDK | 编译用的安卓工具包。Android Studio 第一次打开时会自动下载 |
| 编译 | 把源代码变成手机上能装的 APK 的过程 |
| APK | 安卓的安装包文件，和 `.exe` 类似 |
| 包名 | 应用在手机里的身份证，全世界最好不重复，例如 `com.zhangsan.daytrail` |
| 签名 / keystore | 证明"这个包是我做的"的凭证。以后升级必须用同一个，**千万别丢** |
| 高德 Key | 用地图服务的通行证。绑定包名与签名，需要你实名申请 |
| SHA1 | 签名文件的一串指纹，申请 Key 时要填 |
| 终端 / PowerShell | 一个能打命令的黑窗口。本流程**不需要你用它**，双击脚本即可 |

---

## 3. 你需要准备

| 项目 | 要求 |
|---|---|
| 电脑 | Windows 10/11 或 macOS（Linux 也能用，方法相同） |
| 磁盘空间 | 至少 10 GB 空闲 |
| 手机 | Android 12 及以上 |
| 网络 | 能访问 `developer.android.com` 与 `console.amap.com` |
| 时间 | 第一次 30–60 分钟，其中实名认证可能要等半天到 1 天 |

---

## 4. 第 1 步：把项目下载到电脑

**不需要 GitHub 账号。**

1. 用浏览器打开项目主页（就是给你这个链接的人发的那个地址）。
2. 找到绿色的 **`Code`** 按钮 → 点 **`Download ZIP`**。
3. 把下载到的 `xxx-main.zip` **解压**到 `D:\daytrail`（Windows）或「下载」文件夹（macOS）。
4. 记住这个文件夹：后面每一步都在**解压后的文件夹里面**操作。

> **最常见的错误**：在压缩包预览窗口里直接双击脚本。那样什么都没发生，或者报错。请务必先解压。
>
> 目录名建议只用英文和数字、不要有空格；放在 `D:\daytrail` 这种短路径最省事。

如果是朋友发给你的压缩包，同样适用，直接从下一步开始。

---

## 5. 第 2 步：安装 Android Studio（只装这一个）

1. 打开 <https://developer.android.com/studio>，点 **Download Android Studio**（约 1 GB）。
2. 双击安装包，一路 **Next / 下一步**，保持默认即可。
3. 安装完成后**先打开一次 Android Studio**。首次启动向导会让你选主题、然后开始下载 Android SDK——**让它下完（有进度条）**，完成后关掉即可。

   > 这一步不能跳。没下完 SDK，后面编译会失败。第一次可能要 10–30 分钟，取决于网络。
   >
   > 国内下载慢是常见情况，可以换个时间段重试；不建议从来路不明的"加速器"下载安装包。

4. 如何确认装好了：能正常打开 Android Studio 且首次向导已经走完，就可以了。**不需要在 Android Studio 里打开这个项目**。

---

## 6. 第 3 步：双击向导脚本

向导会替你改包名、改应用名、生成签名，并打印一张"高德填写卡"。

| 系统 | 操作 |
|---|---|
| Windows | 进入解压后的文件夹，双击 **`setup-my-app.cmd`** |
| macOS | 双击 **`setup-my-app.command`**（会弹出一个终端窗口，属正常） |

**可能遇到的拦路提示：**

| 提示 | 处理 |
|---|---|
| Windows「Windows 已保护你的电脑」 | 点「更多信息」→「仍要运行」 |
| macOS「无法打开，因为无法验证开发者」 | 右键点该文件 →「打开」→ 再点「打开」 |
| macOS 提示 `permission denied` / 权限不足 | 在终端里执行一次 `chmod +x setup-my-app.command`，再双击 |
| 杀毒软件报警 | 加入信任后重试（脚本只改文本文件、调用官方的 keytool 与 Gradle） |

**窗口里会问你 3 件事：**

1. **应用名字**：显示在手机桌面图标下面。中文即可，直接回车则保留「行迹」。
2. **英文昵称**：只给小写字母和数字，例如 `zhangsan`、`xiaoli2024`。脚本会拼出包名 `com.zhangsan.daytrail`。

   > 为什么不让你直接填包名？因为包名有格式要求，填错会编译失败。昵称够用，而且不会和本项目冲突。

3. **图标底色**：输入 `#E4572E` 这种颜色就换底色，直接回车则保持不变。

然后它会：

- 生成签名文件 `app/keystore/release.jks` 与 `keystore.properties`（口令随机）；
- 把包名写进 `gradle.properties`；
- 把应用名写进 `res/values/strings.xml`；
- 在屏幕上打印一张「高德 Key 填写卡」，并保存为 `dist/amap-fill-card.txt`；
- 问你要不要现在填高德 Key。**没有 Key 就直接回车跳过**，稍后补上再运行一次即可。

> 第一次运行**默认不编译**，因为还缺高德 Key——先申请，回来再运行一次即可。
> 如果你就想先看看效果，可以在提示里输入 `n`，它会编一个没有地图的版本（其余功能正常）。

---

## 7. 第 4 步：申请高德 Key（两个就够）

### 7.1 注册与实名

1. 打开 <https://console.amap.com/dev/key>，注册并登录。
2. 控制台 →「用户权限」→ 完成**实名认证**（个人即可，需要身份证与人脸核验）。
   **未认证的 Key 调不通**；认证通常几分钟到 1 个工作日。
3. 控制台 →「应用管理」→「创建新应用」，名称随便填，类型选「出行」或「生活服务」。

### 7.2 创建正式版 Key（Android 平台）

| 字段 | 填什么 |
|---|---|
| Key 名称 | 随便，例如 `daytrail-release` |
| 服务平台 | **Android 平台 SDK** |
| 发布版安全码 SHA1 | 打开 `dist/amap-fill-card.txt`，复制「正式版」那行 SHA1 |
| 调试版安全码 SHA1 | 留空 |
| PackageName | 复制卡片里的「正式版包名」，例如 `com.zhangsan.daytrail` |

### 7.3 创建 Web 服务 Key

| 字段 | 填什么 |
|---|---|
| Key 名称 | 随便，例如 `daytrail-web` |
| 服务平台 | **Web 服务** |
| 其他 | 不需要包名与 SHA1；建议在控制台设置**日配额上限**，防止意外超量 |

### 7.4 两个 Key 分别管什么

| Key | 用途 | 不填会怎样 |
|---|---|---|
| 正式版（Android SDK） | 地图底图 | 地图区域是空白，但记录、统计、导出都正常 |
| Web 服务 | 「补全路线」「补全名称」 | 这两个按钮不可用；其余功能正常 |

> 用 Android Studio 连手机调试的人，还需要第三个「调试版」Key，PackageName 用卡片里的「调试版包名」。普通使用用不到。

---

## 8. 第 5 步：把 Key 填回去，再运行一次

1. 再次双击 `setup-my-app.cmd`（macOS 是 `setup-my-app.command`）。
2. 它已经记得你改过的包名与名字，会从「填 Key」这一步继续。
3. 把两个 Key 分别粘进去（粘贴用 Ctrl+V / Command+V）。
4. 它会自动开始编译。

**第一次编译会下载很多东西（几百 MB 到 1 GB），通常 10–30 分钟。**
看到进度条或百分比在动就是正常，**请不要关窗口**。

编译成功后，窗口最后会打印：

```
APK：D:\daytrail\app\build\outputs\apk\release\app-release.apk
```

这就是你的安装包。

---

## 9. 第 6 步：装到手机

三种方式，任选：

| 方式 | 做法 |
|---|---|
| 自动装（推荐） | 手机用数据线连电脑并打开「USB 调试」，脚本编译完会问你要不要直接装 |
| 传文件 | 把 `app-release.apk` 用微信/邮件发给自己，在手机上保存到「文件」再用文件管理器点开 |
| 云盘 | 上传到网盘，手机下载后安装 |

**手机上装不上怎么办：**

- 提示「禁止安装未知应用」→ 点提示里的「设置」，允许你的文件管理器/浏览器安装。
  Android 12+ 的路径：设置 → 应用 → 特殊应用权限 → 安装未知应用。
- **不要用微信直接点开 APK**：微信会拦截或用内置浏览器打开，装不上。先「保存到手机」，再用系统文件管理器安装。
- 桌面出现两个图标（原来是别人的、现在是你自己的）是正常的：两个包名不同，是两个独立应用。

---

## 10. 换名字、换包名、换图标

### 10.1 应用名字

- 简单办法：重新运行向导，输入新名字即可。
- 手动办法：改 `app/src/main/res/values/strings.xml` 里的 `<string name="app_name">`。
  另外两句正文里也出现过项目原名（引导页副标题、今日页空状态），向导会一起替换。

### 10.2 包名

- 简单办法：重新运行向导，重新输入昵称。
- 手动办法：改 `gradle.properties` 里的 `app.applicationId`。
- **注意**：包名一换，手机就把它当成另一个应用——原来的记录不会自动过去。先在旧应用里导出 GeoJSON/GPX，装好新包后再导入。
- 换了包名，**高德 Key 必须重新绑定**：去控制台把 Key 的 PackageName 改成新包名（SHA1 不变）。

### 10.3 图标：只换整体色调（推荐，最简单）

这个项目的图标是「蓝色底 + 白色路线 + 绿色起点」。**底色就是一个颜色值**，改一行就能换整体色调：

- 文件：`app/src/main/res/values/ic_launcher_background.xml`
- 内容：`<color name="ic_launcher_background">#2F6BFF</color>`
- 把 `#2F6BFF` 换成你想要的颜色即可（向导里也会问这一项）。

常用配色（复制即可）：

| 颜色 | 值 | 适合 |
|---|---|---|
| 蓝（默认） | `#2F6BFF` | 项目原色 |
| 绿 | `#16A34A` | 户外、运动 |
| 橙 | `#E4572E` | 醒目 |
| 紫 | `#7C3AED` | 柔和 |
| 深灰 | `#111827` | 低调 |

**怎么自己定一个颜色**：`#RRGGBB` 是十六进制写法，每两位依次代表红、绿、蓝，`00` 最暗、`FF` 最亮。
不想记这个也没关系——用任意「取色器 / 调色板」工具挑好颜色，复制它给出的 `#` 开头那串值即可；
手机自带的图片编辑、系统截图工具里通常也能取色。

**注意**：图形是白色的，所以底色要选中等偏深的颜色；选接近白色（如 `#F5F7FA`）会让白色路线看不清。

改完重新运行一次脚本（或 `./gradlew :app:assembleRelease`）就生效。

### 10.4 图标：换成自己的图片

1. 打开 Android Studio → 打开本项目 → 左侧找到 `app/src/main/res`。
2. 右键 `res` → **New → Image Asset**。
3. `Asset Type` 选 **Launcher Icons (Adaptive and Legacy)**，`Path` 选你准备好的方形图片（建议 1024×1024，透明背景）。
4. 一路 **Next → Finish**。它会自动生成各尺寸图标，并替换 `ic_launcher` 与 `ic_launcher_round`。
5. 重新编译。

两个必踩的坑：

- **`ic_launcher_round` 要一起换**，否则部分手机的圆形图标还是旧的。
- 不要删除 `app/src/main/res/values/ic_launcher_background.xml` 里的 `<color>` 定义，否则 lint 会报 `MissingDefaultResource` 导致构建失败。

### 10.5 通知栏小图标

`app/src/main/res/drawable/ic_stat_track.xml` 是记录通知里的小图标。它必须是**单色**的（系统会染色），换成彩色图会显示成一团黑块。

---

## 11. 以后作者更新了怎么办

| 方式 | 做法 | 适合 |
|---|---|---|
| 重新下载（推荐） | 下载新版 ZIP 解压到新文件夹；把旧文件夹里的 `local.properties`、`keystore.properties`、`app/keystore/release.jks` 复制过去，再运行一次向导，名字和昵称填一样的 | 大多数人 |
| Fork 同步 | 注册 GitHub 账号，点 Fork，以后用 GitHub 的同步按钮拉更新 | 想长期跟随的人 |

**不要**把自己改好的项目再发成公开安装包——那会让所有下载的人共用你的 Key，又回到文档开头那个问题。

---

## 12. 卡住了怎么办

| 现象 | 原因与处理 |
|---|---|
| 双击没反应 | 可能没解压，或被杀毒软件拦了；换到 `D:\daytrail` 再试 |
| 提示"找不到 Java / 没找到 Android SDK" | Android Studio 没装完，或装完没打开过；打开一次等 SDK 下完再运行 |
| 窗口停住很久 | 第一次编译在下载依赖，看百分比是否在动；真的半小时没动就换网络重试 |
| 编译失败 | 看 `dist/setup-log.txt`；把最后 30 行发给帮你的人，里面不含 Key |
| 地图一片空白 | 高德 Key 没填、或 Key 里的包名与 SHA1 与实际不符；重新核对 `dist/amap-fill-card.txt` 与控制台 |
| 高德报 `INVALID_USER_SCODE` | Key 里填的包名还是旧的（或填成了 `com.hmilyld.daytrail`） |
| 高德报 `USERKEY_PLAT_NOMATCH` | 把 Web 服务的 Key 填到了 SDK 位置，或相反 |
| 提示"签名不一致"无法覆盖安装 | 手机上有另一个签名的同包名应用；卸载旧的再装，或换包名 |
| 装上后打开就闪退 | 极少数机型缺少高德原生库；先确认是 ARM 真机（x86 模拟器地图必然不可用） |
| 想反悔 | 在项目文件夹里执行 `git checkout -- gradle.properties app/src/main/res`（会撤掉改名换色） |

---

## 13. 关于费用与配额

- **地图 SDK（Android 平台 Key）**：个人开发者不按日调用次数计费，只受并发（QPS）限制。
- **Web 服务 Key**：有免费日配额。「补全路线」和「补全名称」才会消耗它；你不点，它就不消耗。
- 超出免费额度后，当天这些功能会不可用；**本项目不做任何自动扣费**，是否开通付费以高德控制台为准。
- 三个止损习惯：
  1. 在高德控制台给 Web 服务 Key 设置**日配额上限**；
  2. 不常用的日子里，把 `local.properties` 里的 `AMAP_KEY_WEB` 留空，相关按钮会自动禁用；
  3. 别把带 Key 的 APK 或 `local.properties` 发给别人。

---

## 附录 A：不用脚本，手动改（含 macOS 命令）

Windows 命令在 PowerShell 里执行，macOS 命令在「终端」里执行，**二选一**。

1. 改包名：编辑 `gradle.properties`

   ```properties
   app.applicationId=com.zhangsan.daytrail
   app.updateRepo=
   ```

   `app.updateRepo` 留空 = 不用本项目作者的更新源（否则更新检查会去拉别人的 Release，签名校验必然失败）。

2. 改应用名：编辑 `app/src/main/res/values/strings.xml` 的 `app_name`（以及两处正文里的原名）。

3. 生成签名（仓库外备份好这两个文件）：

   ```powershell
   # Windows
   & "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore app\keystore\release.jks `
     -storetype PKCS12 -alias daytrail -keyalg RSA -keysize 4096 -validity 10950 `
     -dname "CN=DayTrail, OU=Personal, O=Personal, L=Unknown, ST=Unknown, C=CN"
   ```

   ```bash
   # macOS / Linux
   "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore app/keystore/release.jks \
     -storetype PKCS12 -alias daytrail -keyalg RSA -keysize 4096 -validity 10950 \
     -dname "CN=DayTrail, OU=Personal, O=Personal, L=Unknown, ST=Unknown, C=CN"
   ```

   然后写 `keystore.properties`：

   ```properties
   storeFile=app/keystore/release.jks
   storePassword=<你刚输入的口令>
   keyAlias=daytrail
   keyPassword=<同上>
   ```

4. 填高德 Key：编辑 `local.properties`

   ```properties
   sdk.dir=<你的 SDK 路径>
   AMAP_KEY_RELEASE=<高德 Android Key>
   AMAP_KEY_WEB=<高德 Web 服务 Key>
   ```

   Windows 的 `sdk.dir` 要写成 `E\:\\Androiddev\\sdk`（反斜杠与盘符冒号都转义），否则 lint 会报 `PropertyEscape`。

5. 编译：

   ```powershell
   .\gradlew.bat :app:assembleRelease
   ```

   ```bash
   ./gradlew :app:assembleRelease
   ```

   产物：`app/build/outputs/apk/release/app-release.apk`。

> **macOS 说明**：向导脚本按 macOS 自带的 bash 3.2 编写，但尚未在 Mac 真机实测。遇到问题请按上面的手动路径操作，或提 Issue 贴上 `dist/setup-log.txt`。

---

## 附录 B：仓库的尺度

向导只改这些文件，其余代码保持原样，随时可回退：

| 文件 | 改什么 |
|---|---|
| `gradle.properties` | `app.applicationId`、`app.updateRepo` |
| `res/values/strings.xml` | 应用名 |
| `res/values/ic_launcher_background.xml` | 图标底色 |
| `app/keystore/release.jks`、`keystore.properties` | 新建签名 |
| `local.properties` | 三个高德 Key |

它**不会**改：Kotlin 代码、数据库结构、`namespace`、CI 配置。

---

## 附录 C：合规边界

- 本项目以 MIT 许可发布，保留 `LICENSE` 与 `THIRD_PARTY_NOTICES.md` 即可自由修改。
- 高德地图 SDK 为专有软件，**不得移除或遮挡地图上的高德标识与版权信息**。
- 若你要公开发布自己的版本，请换掉名字与图标避免与官方混淆，并自备隐私政策地址：
  - `res/values/strings.xml` 里隐私弹窗提到"项目仓库"的那段（`privacy_dialog_body`）；
  - `docs/privacy-policy.md` 里的仓库与联系方式。
- 不要用别人的 Key，也不要把自己的 Key 提交进 Git（`local.properties`、`*.jks` 已在 `.gitignore` 中）。
