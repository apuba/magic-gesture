# MagicGesture IDE 开发交接文档（2026-10-01 下午版）

> 本文档取代同日上午的 `IDE_DEVELOPMENT_HANDOFF_2026-10-01.md`（其 Git 状态与任务清单已全面过时）。
> 需求基线见 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`（V2.2）；功能选型依据见 `docs/手势功能目录与推荐星级_2026-10-01_v2.docx`。
> 接手前请通读本文档第 3、6、7、9 节。

## 1. 项目概况

- **工程**：`e:/2026/MagicGesture-v0.9`，单模块 Android 应用（Kotlin，无 Compose，自绘 View）
- **身份**：`applicationId` / `namespace` = `com.magicgesture.app`；`versionName 0.9.0` / `versionCode 9`
- **SDK**：minSdk 26，target/compileSdk 36；依赖 MediaPipe Tasks Vision 0.10.21（手部关键点）
- **源码**：`app/src/main/java/com/magicgesture/app/` 下 12 个类，全部单层包结构
- **测试**：`app/src/test/` 下 3 个测试类，35 个用例（GestureArchitectureTest 19 / GestureEngineReplayTest 14 / GlobalCooldownManagerTest 2）

## 2. 核心架构（今日重构后）

```text
HandPipeline（MediaPipe 20fps 关键点）
  → GestureEngine.kt        姿势/状态机检测，产出 GestureEvent（sealed，含 Feedback）
  → GestureMappingManager   手势码→动作：默认映射 + 用户覆盖表（持久化于 SharedPreferences 键 mapping_Gxx）
  → GestureFeatureGate      手势开关门控（跟手势走，不跟动作走）
  → GestureActionExecutor   以动作为中心的分发（不依赖原始事件类型，换绑后任何手势可执行任何动作）
       ├─ ControlAccessibilityService  全局动作 / dispatchGesture 注入 / 语音助手意图
       ├─ CameraProbeService 内部动作（自拍、缩略图、悬浮反馈）
       └─ 媒体/音量           KeyEvent 派发 + AudioManager
GlobalCooldownManager：2000ms，仅动作成功回调后启动；G01 光标与 Feedback 在白名单
```

关键设计决策（勿破坏）：
1. **执行器只看映射结果动作**，换绑后才不炸（如其他手势换绑 CLICK 会降级为点击当前光标位置）。
2. **功能开关管"手势是否识别"，映射管"触发后做什么"，二者独立**（UI 已有此文案）。
3. **G01 光标固定不可换绑**；G16-G19/G23 无检测管线不可换绑（`isRemappable()` 已封装）。
4. `GestureEngine` 中部分状态机**吞帧**（提前 return 阻断后续检测器），部分**不吞帧**（旁路只发事件）。不吞帧的典型：两指并拢挥动（该姿势同时是常见"自然手型"，吞帧会阻断其他手势的释放检测，见 G29/G30 注释与回放测试 `restPose`）。

## 3. 今日完成工作（15 个提交，全部 2026-10-01，10:25–14:14）

| 提交 | 内容 |
|---|---|
| `95864c6`/`9e87168`/`95d5a2a`/`2e3186f` | 荣耀真机无障碍授权持久化闭环验收；荣耀/华为"启动管理白名单"引导；保持授权改为独立页面（`KeepAuthorizationActivity`，按 `Build.MANUFACTURER` 分品牌） |
| `80d3a63` | 自拍优化：保存后右下角缩略图预览约 3.2s 淡出、倒计时大数字与提示音/快门声、期间冻结全部手势（含光标） |
| `6930a68` | GestureEngine 可回放关键点样本回归测试（合成 21 关键点帧 × 50ms 步进，覆盖自拍/点击/静态保持/截图序列/挥动/防重触发） |
| `ca270da` | 首页手势卡片按姿势拆分展示；同一 feature 多个开关状态同步 |
| `46b11ac` | **映射可配置化**：`GesturePreferences` 覆盖表持久化、`GestureMappingManager(overrides)` 动态替换动作、执行器重构为动作中心分发、校准页新增"手势动作映射"配置区（17 手势 × 弹窗单选 + 恢复默认）、`mapping_*` 键变化运行中即时生效 |
| `ef0edf2` | 接入 7 项系统动作：通知栏/音量+/音量−/上一曲/下一曲/锁屏/语音助手，进入换绑选单（`ControlAccessibilityService` 新增 `globalAction()` 等入口） |
| `8f7b625`/`0c5ad16` | 仓库卫生：忽略 Word 锁文件与 `.codebuddy/` 目录 |
| `8157d5b` | **G24-G28 新手势**：左 L 返回 / L 形通知栏 / 爪形拖动（DRAG）/ C 形最近任务 / Love 形锁屏 |
| `b301d9e` | **G29/G30 两指并拢左右挥切歌**：食指中指并拢伸直、无名指小指收起，左挥=上一曲、右挥=下一曲；共用 `twoFingerMedia` 开关 |
| `2385186` | **修复真机误识别**：两指并拢被误判为 V 字自拍。改为以两指张开角度区分（并拢 ≤15° 且指距 ≤0.42 掌宽；V 字指距 >0.28 且角度 >15°），并在两指状态机活动期间抑制 V 字倒计时 |

另产出文档：`docs/手势功能目录与推荐星级_2026-10-01_v2.docx`（功能×手势×星级总表，用户筛选功能的依据）。

## 4. 手势矩阵现状（G01-G30）

| 状态 | 手势码 |
|---|---|
| 已实现（26） | G01 光标、G02 点击、G03-G06 上下滚动（食指/四指）、G07-G10 水平挥动（四指=返回/桌面、竖直食指=返回/桌面）、G11 V 字自拍、G12 比心点赞、G13 截图序列、G14 莲花→最近任务、G15 兰花→返回、G20 大拇指、G21 OK、G22 握拳播放/暂停、G24 左 L、G25 L 形、G26 爪形拖动、G27 C 形、G28 Love 形、G29/G30 两指左右挥切歌 |
| 未实现（5） | G16/G17（保持 3 秒打开指定 App）、G18/G19（画圈音量）、G23（张掌静止暂停识别）——仅枚举占位，无检测器/事件/映射 |

## 5. 动作目录（GestureAction，21 项，全部可换绑目标）

`MOVE_CURSOR`(固定) / `CLICK` / `SCROLL_UP` / `SCROLL_DOWN` / `BACK` / `HOME` / `SELFIE` / `LIKE`(=THUMBS_UP_LIKE) / `SCREENSHOT` / `CONFIRM` / `PLAY_PAUSE` / `RECENTS` / `NOTIFICATIONS` / `VOLUME_UP` / `VOLUME_DOWN` / `MEDIA_NEXT` / `MEDIA_PREVIOUS` / `LOCK_SCREEN` / `VOICE_ASSISTANT` / `DRAG`

## 6. 已知问题与待办（按优先级）

1. **真机阈值校准（最高优先）**：G14/G15（莲花/兰花 0.30 触碰阈值）及今日新增的 G24-G30 全部为合成初版阈值。测试机为华为 `ALP-AN00`（Android 14）。
2. **V 字/并拢角度边界待复测**：`2385186` 修复后已重装到手机，**用户尚未反馈复测结果**。若并拢姿势不触发切歌（角度误判 >15°），放宽到 18-20°；若 V 字自拍不触发，收紧角度或下调指距阈值。相关代码在 `GestureEngine.kt` `twoFingerIndexMiddleAngle` 附近，回放测试基线姿势 `vSign`/`restPose` 需同步调整。
3. **首页卡片动作标签是静态文案**：换绑后不联动。应改为读 `GestureMappingManager.actionFor(code)`。
4. G16/G17/G18/G19/G23 未开发（打开指定 App 需应用选择器 UI；画圈音量需圆弧轨迹状态机；G23 暂停识别是安全阀功能）。
5. `captureRollingScreenshot`（滚动长截图）已实现但未接入动作目录，激活成本低。
6. G12/G20 双击点赞用固定屏幕坐标，换 App/布局即失效，与"失败不伪装成功"底线有张力，考虑标注实验性或改为用户校准坐标。
7. 保持授权引导仅有荣耀/华为方案，小米/OPPO/vivo 待补。
8. 回放测试样本是程序合成帧，非真机录制；若做真机阈值校准，建议升级为录制样本回放。

## 7. 硬约束（不可违反）

1. **隐私底线**：`res/xml/accessibility_service.xml` 保持 `canRetrieveWindowContent="false"`、`typeWindowStateChanged`、`canPerformGestures="true"`——永远不做"按文字/控件查找节点点击"类功能。
2. **相机独占**：前台服务持续持有前置摄像头，任何"打开相机 App"的动作不可行，自拍走内部抓帧。
3. **失败不伪装成功**：所有动作注入必须带成功/失败回调，只有 success 才进全局冷却。
4. `.codebuddy/` 目录是项目数据，已在 `.gitignore`，勿提交、勿删除。

## 8. 构建与验证

```powershell
cd e:/2026/MagicGesture-v0.9
.\gradlew.bat testDebugUnitTest assembleDebug   # 全量验证（当前 35/35 通过）
.\gradlew.bat installDebug                       # 安装到已连接设备
D:\Android\Sdk\platform-tools\adb.exe devices    # adb 不在 PATH，用完整路径
```

手机测试路径：开相机权限 → 系统设置启用 `ControlAccessibilityService` → 应用内启动手势控制 → 播放音乐后摆"两指并拢"左/右挥验证 G29/G30。

## 9. Git 状态（截至 14:14）

- 分支 `main` 领先 `origin/main` **15 个提交**（今日全部工作），HEAD = `2385186`，工作区干净
- `docs/` 下两份 docx 与 `~$` 锁文件：docx 已跟踪，锁文件已忽略
- 未推送。接手者第一件事建议：真机复测 V 字/并拢修复，根据结果调整阈值后提交

## 10. 文件速查

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 全部姿势检测与状态机（V 字块在最前，注意与两指块的互斥条件） |
| `GestureArchitecture.kt` | 手势码/映射（伴生对象 `defaultMappings`）/门控/动作执行器 |
| `CameraProbeService.kt` | 前台服务：相机管线、偏好监听（`feature_*` 与 `mapping_*` 即时生效）、自拍链路 |
| `ControlAccessibilityService.kt` | 无障碍：`globalAction()`/`inject()`/`scrollDirectional()`/`confirmAtCursor()`/`likeVideo()`/媒体键 |
| `GesturePreferences.kt` | 开关（`feature_*`）与映射覆盖（`mapping_Gxx`）持久化 |
| `MainActivity.kt` / `CalibrationActivity.kt` | 首页卡片 / 校准+开关+映射配置 |
| `GlobalCooldownManager.kt` / `HandPipeline.kt` / `OverlayIndicator.kt` / `KeepAuthorizationActivity.kt` | 冷却 / MediaPipe 封装 / 悬浮反馈与缩略图 / 品牌授权引导 |
