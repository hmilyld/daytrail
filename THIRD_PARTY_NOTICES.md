# 第三方组件许可声明（THIRD PARTY NOTICES）

本项目源代码以 [MIT](LICENSE) 发布。下表列出构建产物中包含的第三方组件及其许可。

**MIT 只覆盖本项目自有代码。** 下列组件各自保留其原始许可，不因本项目的 MIT
授权而改变。

## 运行时组件（随 APK 分发）

| 组件 | 版本 | 许可 | 用途 |
|---|---|---|---|
| 高德地图 Android SDK（`com.amap.api:3dmap`） | 10.0.600 | **专有软件**，依《高德开放平台服务条款》使用 | 地图渲染。**不是开源软件，不适用 MIT** |
| AndroidX：`core-ktx`、`activity-compose`、`lifecycle-*`、`compose-*`、`material3`、`material-icons-extended`、`room`、`datastore`、`navigation3`、`adaptive-*`、`startup`、`profileinstaller`、`emoji2` 等 | 见 `gradle/libs.versions.toml` | Apache License 2.0 | UI、状态管理、本地数据库、导航 |
| Kotlin 标准库 | 2.4.21 | Apache License 2.0 | 语言运行时 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | 1.10.2 | Apache License 2.0 | 并发 |
| `org.jetbrains.kotlinx:kotlinx-serialization-json` | 1.9.0 | Apache License 2.0 | JSON 解析（纠偏与地名反查） |

### 关于高德地图 SDK

- 该 SDK 由高德（AutoNavi）提供，**不是**开源组件，其使用受
  [高德开放平台服务条款](https://lbs.amap.com/home/terms) 约束。
- 本项目**不重新分发**该 SDK 的源码或二进制包；构建时由 Gradle 从 Maven 仓库拉取。
- 使用本项目构建应用时，你需要自行申请高德 Key，并遵守其配额与合规要求。
- 分发构建产物时**不得移除或遮挡**地图上的高德 logo 与版权信息。
- 本项目不使用高德定位 SDK（定位走 Android 系统 `LocationManager`），
  也不包含高德 Web 服务的服务端代码。

### 关于 Apache License 2.0

上述 Apache-2.0 组件的许可全文见
<https://www.apache.org/licenses/LICENSE-2.0>。

按 Apache-2.0 第 4(d) 条，分发时应保留组件自带的 `NOTICE` 文件。本项目的
Android 打包配置**未排除** `META-INF/NOTICE`，因此构建产物中保留了这些声明。

## 仅构建期依赖（不随 APK 分发）

| 组件 | 许可 | 用途 |
|---|---|---|
| Android Gradle Plugin | Apache License 2.0 | 构建 |
| Kotlin Gradle Plugin / KSP | Apache License 2.0 | 编译与注解处理 |
| Spotless + ktlint | Apache License 2.0 | 代码格式化 |
| Gradle Wrapper | Apache License 2.0 | 构建工具 |

## 仅测试期依赖（不随 APK 分发）

| 组件 | 许可 | 说明 |
|---|---|---|
| `junit:junit` 4.13.2 | **Eclipse Public License 1.0** | 仅 `testImplementation`。EPL-1.0 与 MIT 不兼容，因此**绝不可**改为 `implementation` |
| `androidx.test.ext:junit` | Apache License 2.0 | 仪器测试 |
| `androidx.test.espresso:espresso-core` | Apache License 2.0 | 仪器测试 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-test` | Apache License 2.0 | 单元测试 |

## 许可兼容性结论

本项目依赖中没有 GPL / AGPL / LGPL 等传染性许可，MIT 发布可行。
唯一需要注意的是 JUnit 的 EPL-1.0：它只在测试类路径上出现，不进入分发的 APK，
因此不构成对 MIT 授权的冲突。
