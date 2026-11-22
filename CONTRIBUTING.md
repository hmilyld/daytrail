# 贡献指南

## 提交前检查

```powershell
.\scripts\verify.ps1
```

macOS / Linux 没有对应的 `.ps1` 运行环境，直接跑同一组 Gradle 任务：

```bash
./gradlew :app:spotlessCheck :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

该脚本依次执行 `spotlessCheck`、Debug 与 Release 构建、两套单元测试与两套 lint。验收标准为 lint 无 error；warning 允许存在，多为依赖版本提示。

仅需快速自检时可使用 `.\scripts\verify.ps1 -Quick`。

## 代码规范

| 项目 | 要求 |
|---|---|
| 语言与框架 | Kotlin、Jetpack Compose、Material 3 |
| 依赖注入 | 使用手写的 `AppContainer`，不引入 Hilt 或 Koin |
| 分层 | 遵循 `data`、`domain`、`location`、`ui` 四层划分，界面层不直接访问 DAO 或网络 |
| 状态 | ViewModel 暴露 `StateFlow`，界面使用 `collectAsStateWithLifecycle` 订阅 |
| 文案 | 界面文案统一放入 `res/values/strings.xml`；ViewModel 使用 `UiText.Res(...)` 表达 |
| 颜色与圆角 | 仅使用 `MaterialTheme.colorScheme` 与 `MaterialTheme.shapes`，页面内不写十六进制色值与 `RoundedCornerShape(...)` |
| 间距 | 使用 `TrailSpacing`（4、8、12、16、24、32），不写裸数值 |
| 触达目标 | 不小于 48dp |
| 格式化 | `.\gradlew.bat :app:spotlessApply`（ktlint，行宽 100） |
| 注释 | 说明设计取舍与已知约束，不复述代码逻辑 |

## 提交信息

遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范：

```
feat(places): 支持手动合并重复地点
fix(location): 采样失败时不再停止采集
docs(readme): 补充高德 Key 配置说明
```

`type` 取值为 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`perf`；`scope` 使用模块名。

## 测试要求

- `core`、`domain`、`location`、`data/import`、`data/update` 中的纯逻辑须附带 JUnit 测试；
- 单元测试不得依赖 Android 运行时；
- 解析器、坐标转换、版本比较等难以通过肉眼发现错误的逻辑，需覆盖边界值。

## 提交 Pull Request

1. 单个 PR 只处理一件事；
2. 描述中说明改动原因与验证方式（执行了哪些命令、在真机还是模拟器上验证）；
3. 涉及界面的改动需附截图或录屏；
4. 涉及记录链路（采样、休眠、写入）的改动需说明真机验证情况。

## 禁止事项

- 提交 `local.properties`、`keystore.properties`、`*.jks`（`app/keystore/debug.jks` 除外）、`.env` 或任何真实密钥；
- 提交 `build/`、`dist/` 或 APK 文件；
- 使用 `--no-verify` 跳过 git hooks；
- 在未说明验证范围的情况下升级依赖矩阵中的组件，包括 AGP、Gradle、Kotlin、Compose BOM、Room 与高德 SDK；
- 在页面中硬编码颜色、圆角与间距。

## 提交 Issue

- 缺陷请使用 Issue 模板，并提供系统版本、机型、应用版本（设置页可见）与复现步骤；
- 针对「未采到位置」的问题，请先查看「我的 → 诊断日志」，必要时导出 JSON 一并附上。该日志不含坐标；
- 安全漏洞请勿通过公开 Issue 报告，处理方式见 [`SECURITY.md`](SECURITY.md)。

## 许可

贡献的代码视为以 [MIT](LICENSE) 许可授权。
