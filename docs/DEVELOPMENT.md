# 开发说明

本文档记录系统架构、实现约束与已验证的设计取舍。面向使用者的说明见 `README.md`；两者不一致时，以本文档为准。

## 1. 数据流

```
RecordingService（前台服务）
        │
        ▼
SystemLocationSource（系统 LocationManager，唯一位置来源）
        │  updates(intervalMs)：单条常驻订阅
        ▼
        ├── SamplingTempo     采样节奏：依据平均速度与连续静止时长决定间隔
        ├── PointFilter       精度、跳变与重复过滤
        └── TrackRecorder     内存缓冲，批量写入 Room
                │
                ▼
        TrackRepository（接口，预留云端接入点）
                │
        LocalTrackRepository
                │
                ▼
        Room（track_day / track_point / stay_segment / day_gap / place / route_override）
                │
                ▼
        ViewModel → TrackDisplay（精度筛选 → 毛刺修复 → 停留吸附 → 断点切段）
                │
                ▼
        Compose 界面（Navigation 3 与手写导航栏）
```

位置采集仅使用系统 `LocationManager`，不引入高德定位 SDK。后者需要额外声明 `APSService`，缺失时始终返回错误码 7；同时会与地图上的位置标记形成两条独立链路，造成「界面有位置标记但提示定位失败」的现象。

## 2. 采样策略

`SamplingTempo` 仅依据两项输入：上一个被接受的点到当前点的平均速度，以及已经持续的静止时长。

| 条件 | 档位 |
|---|---|
| 平均速度不低于 4 m/s | 驾车 |
| 平均速度不低于 0.9 m/s | 移动 |
| 其他情况 | 静止，并累计静止时长 |
| 连续静止超过 60 分钟（位于已知地点内为 15 分钟） | 休眠，间隔 1 小时 |

以下约束均有实测依据：

- 两次投递的间隔小于 15 秒时，不参与速度计算。这属于同一轮采样的重复投递，用于计算速度会产生 12 m/s 的异常值，使静止档位直接跳至驾车档位。
- 不以位移判断移动状态。精度为 5 公里的定位点出现 200 米位移属于常见现象，据此判断会导致室内场景采样频率持续升高。
- 降档需持续 5 分钟方才生效，升档立即生效。进入休眠不设额外延迟，静止时长本身即为延迟。
- 静止与休眠状态均需可被唤醒。`TYPE_SIGNIFICANT_MOTION` 为一次性触发器，触发后必须重新注册；该传感器属于 wake-up 类型，在 Doze 模式下可唤醒应用。部分 ROM 不提供该传感器，因此每小时心跳机制必须保留。唤醒后应立即补充一次定位。
- 网络切换不等同于离开地点。电梯口、楼下与地下车库均会触发切换。因此切换仅启动一次定位探针：若结果距最近地点锚点不超过 200 米则不唤醒，并开启 15 分钟守候窗口。监听使用 `registerDefaultNetworkCallback`，不得使用 `startScan()`。

## 3. 坐标系统一

| 来源 | 坐标系 |
|---|---|
| 高德地图 SDK、高德定位 | GCJ-02 |
| Android `LocationManager` | WGS-84 |
| 导出的 GPX 与 GeoJSON | WGS-84（国际标准） |

境内两者相差 50 至 550 米（上海实测 548 米）。据此确立三条规则：

- 写入数据库前必须转换为 GCJ-02，由 `core/CoordinateTransform` 完成，该实现为纯数学计算；
- 导出时必须转换回 WGS-84，否则在 Google Earth 等工具中整体偏移约 500 米；
- 导入时必须转换为 GCJ-02。

坐标转换不使用高德的 `CoordinateConverter`，其依赖 ARM 原生库，在 x86_64 环境不可用。

上海地区 GCJ-02 相对 WGS-84 的偏移方向为南偏东，即纬度减小、经度增大。编写测试时不应依据直觉假设方向。

## 4. 精度阈值

系统维护两个独立阈值，不得合并。

| 阈值 | 取值 | 作用 |
|---|---|---|
| 入库门槛 `PointFilter.maxAccuracyMeters` | 5000 米 | 仅授予大致位置权限的用户也需要产生数据，否则整日无记录 |
| 绘制门槛 `TrackDisplay.MAX_LINE_ACCURACY_METERS` | 120 米 | 误差 2 公里的点参与绘制会形成横穿城区的错误连线 |

当某一日没有满足绘制门槛的点时，回退使用粗精度点绘制。

里程统计需具备误差感知能力：仅当路段距离大于 `max(15m, 2 × 精度)` 时才计入里程，否则静止状态也会产生数公里的虚假里程。

## 5. 停留与地点

- 停留退出采用迟滞判定，需连续 2 个点位于半径之外方判定离开。人在住所内会出现缓慢漂移（实测 0.02 至 0.6 m/s，42 至 78 米），单个漂移点不应将 5 小时的停留切分为三段。停留结束时间取最后一个位于半径内的点。
- 候选窗口需以质心重新定位。首个点常位于停留区域边缘，以其为圆心会使有效半径呈单向性。实现上先用首点生成候选段，再以该段质心重新生成，最多两轮，仅接受时长更长的结果。
- 停留段在图上渲染为单一锚点。既然已判定为同一地点，将数十个点连成折线在放大后会形成无意义的往复线。吸附后需合并相邻重复点。
- 地点为派生数据。停留落在已有地点 `place.radius_m` 范围内则归属该地点，否则新建。锚点一经确定不随新观察漂移，否则地点在地图上的位置会持续变化。
- 删除地点仅置 `suppressed` 标志。直接删除记录会导致下一次 `recomputeDay` 重新创建同一地点。
- 归并半径取地点自身 `radius_m`，不使用全局常量。同一小区内两次停车相距两三百米属常见情况（实测 260 米）。
- 手动合并需同时完成三项操作：停留归属改判至目标地点、目标半径扩展至「两锚点距离 + 源半径 + 50 米」（上限 500 米）、删除源记录。仅完成第一项时，60 秒后的重算会重新创建源地点。
- 候选合并距离不得超过半径上限 500 米，否则合并结果会在下一次重算时被还原。

## 6. 地图

- 必须使用 `TextureMapView`。`MapView` 内部为 `SurfaceView`，绘制于独立 Surface 层，不参与 View 层级的动画，页面切换时会出现残留。`TextureView` 遵循正常绘制层级。
- 地图实例跨页面复用。`MapViewCache` 以强引用持有单例并维护引用计数，`onDispose` 不销毁实例。重建实例需要重新建立 GL 上下文并重新加载瓦片，会造成明显白屏。代价为常驻 30 至 60MB 内存。
- 页面切换转场期间存在两个页面同时持有地图，引用计数归零后方可暂停，否则先离开的页面会暂停正在使用的地图。
- `acquire()` 发生在 View 挂载窗口之前，此时 `onResume()` 为空操作。恢复渲染应通过 `doOnAttach { MapViewCache.sync(...) }` 完成。
- 全屏时外层布局不消费 inset，由地图浮层通过 `Modifier.immersiveSafePadding(true)` 自行避让。不得同时叠加 `safeDrawingPadding()` 与 `imePadding()`。
- 不得遮挡高德标识与版权信息（位于地图左下角）。本项目关闭缩放按钮与比例尺：按屏幕密度绘制的缩放按钮在高 DPI 设备上尺寸偏大且显示模糊，功能上亦与双指缩放重复。
- 地图实例为全局单例，页面只能调用 `marker.remove()`，不得调用 `map.clear()`，否则会清除其他图层的覆盖物。

## 7. 导出与导入

- 导出通过 SAF（`ACTION_CREATE_DOCUMENT`）完成，不申请存储权限。
- GeoJSON 当前版本为 schema 2，新增 `schema`、`generator` 与逐点 `accuracies` 字段。导入同时接受 schema 1（缺少精度字段，按 50 米处理），遇到更高版本直接拒绝，不做推测。
- 解析实现：GPX 采用流式扫描，GeoJSON 按 Feature 切分后逐个解析，不构建完整语法树。文件上限 50MB、20 万点。
- 写入在单个事务内完成。解析与校验均在写库前结束，任一日写入失败则整体回滚。
- 停留与地点不从文件导入，由 `recomputeDay` 重新识别。
- 详细字段定义见 [`import-format.md`](import-format.md)。

## 8. 在线更新

- 主源为 `releases/latest/download/update.json`，属静态文件，不消耗 API 配额。仅在清单缺失时回退至 GitHub API。
- 自动检查间隔为 24 小时。网络不可达不记录检查时间，避免一次网络波动导致用户长时间收不到版本提示；服务端返回错误则记录，避免重复触发限流。
- 安装前执行三项校验：SHA-256、包名一致性、签名证书一致性。
- 发布流程校验 tag 与 `app.versionName` 一致，并从 `CHANGELOG.md` 抽取对应小节作为更新说明。
- 详细设计见 [`update.md`](update.md)。

## 9. 测试

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\scripts\verify.ps1
```

`core`、`domain`、`location`、`data/import`、`data/update` 中的纯逻辑均有 JUnit 测试覆盖。新增此类逻辑时应同步补充测试。单元测试不得使用依赖 Android 运行时的类。

## 10. 模拟器验收

```powershell
$env:ANDROID_AVD_HOME='<PATH_TO_AVD>'
$env:ANDROID_USER_HOME='<PATH_TO_ANDROID_USER>'
<ANDROID_SDK>\emulator\emulator.exe -avd <AVD_NAME> -no-window -no-audio -gpu swiftshader_indirect
```

- 首次启动会停留在锁屏状态，此时 `am start` 报 `Error type 3`。可先执行 `adb shell locksettings set-disabled true` 后重启，或冷启动时附加 `-wipe-data`。
- 位置注入：`adb emu geo fix <经度> <纬度>`。
- 平板布局验证：`adb shell wm size 1200x1920 && adb shell wm density 240`，验证完成后执行 `wm size reset && wm density reset`。
- 截图坐标应通过 `uiautomator dump` 读取实际 bounds，不得按比例估算。

## 11. 已知限制

| 限制 | 原因 |
|---|---|
| x86_64 模拟器上地图必然显示不可用 | 高德仅提供 `arm64-v8a` 与 `armeabi-v7a` 原生库。界面会显示地图不可用说明，轨迹记录与统计功能不受影响 |
| 高德 Web 服务存在日配额 | 轨迹纠偏与逆地理编码依赖该配额。失败时静默处理，不影响离线功能 |
| 未实施证书固定 | 高德证书轮换会导致地图与纠偏整体失效，风险高于收益 |
| 数据库未加密 | 依赖 Android 沙箱与全盘加密（FBE）。引入 SQLCipher 对已 root 设备无实质防护 |

## 12. 依赖矩阵

以下组件相互约束，升级需单独验证。

| 组件 | 版本 | 说明 |
|---|---|---|
| AGP | 9.5.2 | Compose 1.12.x 要求 `compileSdk 37` 与 AGP 9.1 及以上 |
| Gradle | 9.6.0 | 与 AGP 配套 |
| Kotlin | 2.4.21 | AGP 9 内置 Kotlin 支持，不再需要 `org.jetbrains.kotlin.android` 插件 |
| Compose BOM | 2026.09.00 | material3 1.4.0 |
| Navigation 3 | 1.2.2 | |
| Room | 2.8.5 | KSP 2.3.14 |
| 高德地图 | `com.amap.api:3dmap:10.0.600` | 已内置定位 SDK，不得再引入 `com.amap.api:location`，否则产生重复类 |
