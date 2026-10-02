# MagicGesture IDE 开发交接文档

> 本文档取代 `IDE_DEVELOPMENT_HANDOFF_2026-10-01.md` 与 `IDE_DEVELOPMENT_HANDOFF_2026-10-01_PM.md`（均已删除）。
> 需求基线见 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`；手势↔动作完整映射表见 `GESTURE_ACTION_MAPPING_CHECKLIST.md`（**无日期命名**；今后任何手势/动作绑定变更或新增，必须同步更新该文档——这是产品负责人的既定规则）。
> 所有 AI/开发者必须先读仓库根目录 `AGENTS.md`；当前正式版签到解锁需求见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`，二期账号、付费和分享须等 App 真实上线后重新规划。
> 接手前请通读第 2、6、7、9 节。

## 1. 项目概况

- **工程**：`e:/2026/MagicGesture-v0.9`，单模块 Android 应用（Kotlin，无 Compose，自绘 View）
- **身份**：`applicationId` / `namespace` = `com.magicgesture.app`；`versionName 0.9.0` / `versionCode 9`
- **SDK**：minSdk 26，target/compileSdk 36；依赖 MediaPipe Tasks Vision 0.10.21（手部关键点，模型 `app/src/main/assets/hand_landmarker.task`）
- **源码**：`app/src/main/java/com/magicgesture/app/` 下 14 个类，全部单层包结构（含 `GestureUnlock.kt`、`FavoriteButtonController.kt`）
- **测试**：`app/src/test/` 下 5 个测试类，共 83 个用例，当前全绿（Replay 40、Architecture 24、Unlock 14、Orientation 2、Cooldown 3）

## 2. 核心架构

```text
HandPipeline（MediaPipe 20fps 关键点）
  → GestureEngine.kt        姿势/状态机检测，产出 GestureEvent（sealed，含 Feedback）
  → GestureMappingManager   手势码→动作：默认映射 + 用户覆盖表（持久化于 SharedPreferences 键 mapping_Gxx）
  → GestureFeatureGate      手势开关门控（跟手势走，不跟动作走）
  → GestureActionExecutor   以动作为中心的分发（不依赖原始事件类型，换绑后任何手势可执行任何动作）
       ├─ ControlAccessibilityService  全局动作 / dispatchGesture 注入 / 语音助手意图 / 前台包名缓存
       ├─ CameraProbeService 内部动作（自拍、缩略图、悬浮反馈、媒体键与音量）
       ├─ FavoriteButtonController     收藏：前台包名 → 已存坐标 tapPixels，未存则弹出全屏标定浮层
       └─ 媒体/音量           KeyEvent 派发 + AudioManager（音量走 setStreamVolume）
GlobalCooldownManager：2000ms，仅动作成功回调后启动；冷却期间冻结全部识别（含光标）
```

关键设计决策（勿破坏）：

1. **执行器只看映射结果动作**，换绑后才不炸（如其他手势换绑 CLICK 会降级为点击当前光标位置）。
2. **功能开关管"手势是否识别"，映射管"触发后做什么"，二者独立**（UI 已有此文案）。
3. **G01 光标固定不可换绑**；其余已有检测管线的手势均可换绑。G18/G19 画圈识别已移除，但编号已改用于张掌组合手势；`DRAG` 只有爪形手势能提供坐标，已从换绑选单移除但动作枚举保留（G26 默认不绑定，触发时提示未绑定）。
4. `GestureEngine` 中部分状态机**吞帧**（提前 return 阻断后续检测器），部分**不吞帧**（旁路只发事件）。不吞帧的典型：两指并拢系列 G29-G33（该姿势同时是常见"自然手型"，吞帧会阻断其他手势的释放检测，见状态机注释与回放测试 `restPose`）。
5. **冷却期冻结识别管线**：冷却期间光标也停（注意与早期版本"光标白名单"行为不同，首页文案已同步为准确描述）。

## 3. 本阶段完成工作（截至 2026-10-02）

### 2026-10-02 G23 提交内容（已完成构建、尚未装机）

- **本次提交聚焦 G23 小指静音及其文档/测试**；此前打开应用、收藏、动作留空、G34 等功能已经进入本地提交历史。
- G23 调整为“仅伸出小指，其余四指收拢，保持 1 秒”，默认执行 `TOGGLE_MUTE`。保持期间只触发一次，释放后再次做手势可在媒体静音与恢复声音之间切换。
- `CameraProbeService.toggleMute()` 通过 `AudioManager.ADJUST_MUTE/ADJUST_UNMUTE` 操作 `STREAM_MUSIC`；执行后延迟 120ms 校验厂商系统的静音状态，只有确认状态发生变化才回调成功并进入全局冷却。
- 为解决“小指容易被识别为比心”，G12 比心增加中指、无名指、小指均收拢的约束；识别到小指专属姿势时主动清除比心候选状态。回放测试同时断言不得误发 `LoveLock`、`Like` 或 G34 `Six666`。
- 首页和校准页已增加 G23 卡片、独立功能开关、动态动作标签及换绑支持。新增透明图片 `app/src/main/res/drawable-nodpi/gesture_pinky.png`（1254×1254 ARGB）。
- 已同步更新 `GESTURE_ACTION_MAPPING_CHECKLIST.md`、本交接文档和 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`。
- 2026-10-02 已执行 `testDebugUnitTest assembleDebug`：61/61 测试通过，Debug 构建成功；仅有既存 Android API/Gradle 弃用警告。APK 已生成但本轮尚未安装到手机，代码完成不等于真机验收。

### 2026-10-02 自拍质量优化（尚未提交、待真机验收）

- 平时手势识别继续使用 640×480 YUV 流；相机会话额外准备前置摄像头支持的最大 JPEG 输出，但只在自拍倒计时结束时执行一次静态拍摄。
- 高质量 JPEG 以相机原始字节直接写入相册，`JPEG_QUALITY=100`，不再把 640×480 识别帧放大或二次压缩为所谓高清照片。
- 静态拍摄不会替换日常 repeating preview；拍完后原 640×480 手势识别流继续运行。若机型不支持最大 JPEG 与识别流并发、拍摄失败或 4 秒超时，自动回退到原预览帧自拍。
- 已执行 `testDebugUnitTest assembleDebug` 并通过；最大分辨率、照片方向、前置镜像、拍摄后识别恢复和失败降级仍需真机验收。

### 2026-10-02 每日签到解锁（需求已确认、代码已实现、尚未真机验收）

- 当前正式版本不设置收费用户或付费入口；所有正式用户统一从 7 个基础编号开始：G01、G02、G03、G04、G24、G11、G13。
- 用户每天主动签到，永久解锁下一个功能包；一天最多一次，断签不清零，共 12 次有效签到完成当前全部奖励。
- 所有正式 Release 用户遵循同一规则，不设置历史用户、管理员或隐藏口令全开；Debug/内部测试构建允许全部解锁，但不得把测试入口带入正式包。
- 微信登录、账号、付费全开、邀请分享、服务端和联网权益属于二期；必须等 App 真实公开上线后重新规划，当前不得开发。

**当前代码实现（2026-10-02）**：

- 新增 `GestureUnlock.kt`：`GestureUnlockPlan`（基础 7 个编号 + 12 个功能包，与需求 §6.1 顺序一致）、`GestureUnlockMachine`（纯逻辑签到状态机）、`GestureUnlockStore`（本机 SharedPreferences，键前缀 `unlock_`）、`GestureEntitlement`（拥有权判定）、`GestureFeatureConfig.restrictedTo()`（按权益收敛开关）、`GESTURE_CODES_BY_FEATURE`（开关键→编号）。
- 三层保持独立：解锁权益只决定“是否拥有”；`feature_*` 仍决定“是否识别”；`mapping_Gxx` 仍决定“执行什么动作”。用户关闭的开关不会被解锁覆盖，未解锁也不会写回开关值。
- 双重拦截：识别入口走 `GesturePreferences.effectiveFeatures()`（`HandPipeline` 初始化与运行中 `updateFeatures` 均使用），执行入口在 `CameraProbeService.executeMapped()` 与持续音量会话前判定 `entitlement.owns(code)`。
- 运行中即时生效：控制服务的偏好监听新增 `unlock_` 前缀分支，签到后立即刷新权益并下发引擎，无需重启控制。
- 日期规则：本地时区 epochDay；一天一次；断签不清零；系统时间回拨视为当日已领取，不增加也不清零；12 次后返回“已完成”。
- UI：首页状态条下方新增“每日签到解锁”卡片（进度、下一次解锁内容、签到按钮、本机保存提示）；未解锁手势的紫色标签显示“未解锁 · 第 N 次签到后开放”且识别开关不可点；校准页未解锁手势的映射按钮显示“未解锁”并禁用，功能开关附加解锁说明并禁用。
- 构建隔离：`app/build.gradle.kts` 开启 `buildFeatures.buildConfig`；`GestureUnlockStore` 默认参数 `BuildConfig.DEBUG` 控制全开。已验证 `assembleRelease` 生成 `DEBUG=false`、`assembleDebug` 为 `true`。
- 验证：`testDebugUnitTest` 75/75 通过（新增 `GestureUnlockTest` 14 例），`assembleDebug` 与 `assembleRelease` 均成功；真机签到流程、升级迁移与 G26/G34 解锁后引导尚未验收。

### 2026-10-02 左右挥整只手条件与自拍倒计时冻结

- **左右挥必须整只手轻挑**：`GestureEngine` 新增 `wholeHandHorizontalFlick()`，要求手腕位移与掌心位移同向且达到掌心位移的 60%（`wholeHandWristRatio`）。G29/G30 与 G09/G10 的左右判定都走这个条件，只动手指、手腕不跟着移动时不再触发。
- **两指左右挥灵敏度**：G29/G30 触发位移由 `.09f * movementScale` 下调为 `.07f * movementScale`（`twoFingerHorizontalTrigger`），解决真机上挥了没反应；上下拉音量阈值未改。G09/G10 阈值仍为 `.05f * movementScale`。
- **自拍倒计时冻结全部识别**：V 字确认进入倒计时即 `pipeline.pause()`（清空保持、轨迹与候选状态），帧回调期间丢弃全部事件（原来只丢弃非 `Feedback` 事件，导致倒计时仍显示手势提示）；拍照完成或预览回退后统一走 `endSelfieFreeze()` → `pipeline.resetTracking()`，从清空状态恢复，倒计时期间的动作不会在恢复后被补触发。
- 文案同步：首页两指左右挥卡片、V 字自拍卡片，校准页两指媒体控制与 V 字自拍开关、练习顺序第 3 项；`GESTURE_ACTION_MAPPING_CHECKLIST.md` 的 G09/G10/G11/G29/G30 与 §4 冻结规则。
- 验证：`testDebugUnitTest` 77/77 通过（回放测试新增 2 例：整只手小幅轻挑 0.078 位移仍能切歌；只动手指使掌心位移 0.096 但不触发）；`assembleDebug` 通过。**真机尚未验收**：两指左右挥灵敏度是否合适、整只手条件是否过严、倒计时期间是否彻底无手势提示。
- 2026-10-02 追加放宽（真机反馈"挥了没反应"）：方向判定抽成 `horizontalAxisDominance = 1.0f` / `verticalAxisDominance = 1.25f`。左右挥原来要求水平分量 > 垂直分量 × 1.25（夹角 <38°），带上下起伏的自然轻挑常被判成"不是左右挥"；现只要求水平分量不小于垂直分量（夹角 ≤45°）。上下拉音量仍要求垂直分量 × 1.25 占优，两条判定互斥，放宽左右不会抢走音量。新增回放用例 `slantedWholeHandFlickStillSwitchesTracks`（约 40° 斜向轻挑，旧规则不触发、新规则触发且不产生音量事件），回放测试 78/78 通过，`assembleDebug` 通过。
- 同批次修复：播放手势唤不起音乐应用（见下节）。

### 2026-10-02 播放手势唤不起音乐应用（代码完成、待真机验收）

- 现象：播放/暂停有时能唤起系统默认音乐播放器，有时毫无反应。
- 根因：`AudioManager.dispatchMediaKeyEvent()` 只把按键发给**当前持有活跃 MediaSession 的应用**。有 session（刚播过、后台仍存活）时系统能恢复播放；无任何 session 时系统静默丢弃按键，App 侧仍返回"派发成功"，所以表现为时好时坏。
- 修复（`CameraProbeService.dispatchMediaKey`）：播放键派发前记录 `isMusicActive()`；只有"手势前没在播放"（用户意图是播放）才进入回退。回退链为 800ms 后确认是否有声音 → 再 700ms 二次确认（避免冷启动缓冲被误判为无响应）→ 仍无声则用 `CATEGORY_APP_MUSIC` 打开默认音乐应用 → 1.2s 后补发 `KEYCODE_MEDIA_PLAY` → 2s 后仍无声则提示"未能唤起音乐应用，请先打开音乐 App 再试"。暂停场景（手势前正在播放）不进入回退，绝不会误打开音乐 App；失败统一给明确提示，不伪装成功。
- `AndroidManifest.xml` 的 `queries` 增加 `MAIN + APP_MUSIC`，保证 Android 11+ 包可见性下能解析默认音乐应用。
- 验证：`assembleDebug` 通过，JVM 测试无回归。**真机待验收**：无音乐播放时播放手势能否打开默认播放器并真正开始播放；播放中做暂停手势不会误打开 App；Android 10+ 后台启动 Activity 限制是否会在该机型上拦截（若被拦截会看到"未能唤起"提示）。

### 2026-10-02 识别灵敏度点击即时生效

- 原因：灵敏度（`movementScale`）只在 `HandPipeline` 构造时读取一次，校准页把它和功能开关一起放在"保存设置并返回"里，运行中改不了；功能开关、冷却时长、动作映射都已即时生效，灵敏度是唯一例外。
- 改动：`GestureEngine.movementScale` 改为可变并新增 `updateMovementScale()`（同步重算 `twoFingerHorizontalTrigger`、清空轨迹与保持状态，避免旧轨迹按新阈值触发）；`HandPipeline.updateSensitivity()`；`GesturePreferences.SENSITIVITY` 改为公开并新增 `saveSensitivity()`；`CameraProbeService` 偏好监听新增 `sensitivity` 分支，运行中下发并提示"识别灵敏度已即时更新"。
- 校准页：点击"稳定 / 标准 / 灵敏"立即写入偏好并 Toast"识别灵敏度已即时生效"，分组说明补"点击即时生效，无需先保存"；底部保存按钮行为不变（仍写入当前选中值，不冲突）。
- 验证：新增回放用例 `sensitivityChangeRetunesThresholdsWithoutRestart`（0.06 位移在标准档不触发，换到"灵敏"档后立即触发），全部测试 79/79 通过，`assembleDebug` 通过。**真机待验收**：手势控制运行中切档是否立刻改变灵敏度。
- 未改："左右反向"仍随保存按钮生效，如需同样即时生效可照此处理。

### 2026-10-02 两指双击（G33）经常失效 / 被判成调音量

- 现象：两指并拢双击经常没反应，有时反而开始调音量。
- 原因一（失效）：状态机要求每一段"弯下 / 伸直"至少 120ms，真实快速双击的一段常只有 100ms 左右，一旦不够就整段作废回到 IDLE。已改为 `twoFingerTapSegmentMs = 80L`（约 2 帧，仍可挡住单帧抖动）。
- 原因二（误判音量）：弯下再伸直会带着掌心上下移动，达到音量阈值（0.055/0.065 × movementScale）且垂直占优时，切歌/音量机器先于双击触发音量并进入独占会话，双击彻底失效。已改为：双击后半程（`POSED_SECOND` / `BENT_TWICE` / `FIRED_WAIT`）不产生音量事件。
- 刻意不屏蔽左右挥：松手后重新挥手与"双击第二下"形态完全相同，屏蔽它会让正常切歌失灵（回归测试 `twoFingerSwipeLeftFiresPreviousAndRightFiresNext` 已暴露过一次）。
- 验证：新增回放用例 `fastTwoFingerDoubleTapStillTogglesPlayPause`（100ms 伸直段，放宽前失败）、`doubleTapWithPalmDriftNeverStartsVolume`（双击带 0.08 掌心位移，只出双击不出音量），全部测试 81/81 通过，`assembleDebug` 通过。**真机待验收**：双击成功率与是否还会转成调音量。

### 2026-10-02 轨迹类手势超时改为重置基准

- 诉求：摆好姿势后 5 秒没有动作就失效，必须松手重新摆姿势，体验很差。
- 改动：新增 `stageTimeoutMs = 5000L`。轨迹型机器（两指切歌/音量、张掌挥动与滚动、食指左右滚动、食指上下挑）超过这个窗口不再进入 `WAIT_RELEASE`，而是把位移基准（食指上下挑是角度基准）刷新到当前位置并重新计时；张掌机器同时保留已经确定的方向轴。基准始终新鲜，不会因为很久以前的位置突然算出一段位移而误触发。
- 未改：序列型手势（截图组合、张掌后收指打开应用）保持原超时退出——它们有明确的阶段语义，久等不动作就该退出。
- 文案：首页"使用提示"中"请在 5 秒内完成对应动作，超时后需重新进入准备姿势"改为"挥动、滚动类手势久等不会失效，会自动重新计时，无需重新摆姿势"。
- 验证：新增回放用例 `twoFingerSwipeStillWorksAfterIdleTimeout`、`palmWaveStillWorksAfterIdleTimeout`（静止 6 秒后再做动作仍能触发），全部测试 83/83 通过，`assembleDebug` 通过。**真机待验收**。

### 2026-10-02 OK 收藏功能检查与修复（代码完成、待真机验收）

- 链路：G21 OK 保持 0.6 秒 → `GestureEvent.Ok` → `GestureAction.FAVORITE_CURRENT` → `CameraProbeService.favoriteCurrentContent` → `FavoriteButtonController`：已有位置直接 `tapPixels`，没有则弹出全屏标定浮层（准星 → 测试点击 → 确认保存）。
- 修复一（**最可能的"收藏没反应"根因**）：`ControlAccessibilityService` 只在 `onAccessibilityEvent` 里缓存 `event.packageName`，而无障碍配置只订阅 `typeWindowStateChanged`。于是下拉通知栏、弹出输入法、权限框时，缓存被改成 `com.android.systemui` / 输入法包名；`isAllowedTarget` 随后拒绝它们，OK 手势就一直失败，直到用户重新切换应用或 Activity。现在分三类处理：临时覆盖层（systemui / 输入法 / 权限框 / 安装器）**忽略并保留上一个真正的应用**；桌面与设置**置空**（明确没有可收藏目标）；其余正常记录。`canRetrieveWindowContent` 仍为 `false`，不读窗口内容，隐私策略不变。
- 修复二：没有悬浮窗权限时标定浮层无法显示，原来是静默 `finish(false)`，用户只看到"当前应用未定义收藏位置或点击失败"。现在提示"需要允许显示悬浮窗，才能定义收藏按钮位置"。
- 修复三：`FavoriteButtonController` 增加 `onMessage`（接到 `overlayIndicator`）。无可用前台应用时提示"未识别到可收藏的应用，请先切换到要收藏的页面"；保存位置时提示"收藏位置已保存，再做一次 OK 手势即可收藏"——原来一律报"已点击收藏位置"，但保存这一轮并没有真的点击，文案误导。
- 验证：`assembleDebug` 通过，全部测试 83/83 通过。**真机待验收**：下拉通知栏/弹出输入法后再做 OK 手势仍能收藏；桌面与设置页做 OK 手势不再误点上一个应用；首次标定与保存提示是否清晰。
- 未改（待确认）：`FavoriteButtonProfile` 记录了 `appVersion` 但从不校验，目标 App 大版本更新、按钮位置变化后仍点旧坐标；标定 30 秒超时仍是静默 `finish(false)`。这两项需要你确认是否要做。

### 2026-10-02 OK 收藏仍报"当前应用未定义收藏位置或点击失败"——历史诊断（最近任务场景已修复，其他场景仍可参考）

真机反馈：OK 手势报上述提示，用户判断"大概率因为当前顶部浮着一个被暂停的播放器"（PIP / 悬浮播放器）。本轮只出方案、未动代码，用户正在装机复测。

**失败文案的来源**：`GestureArchitecture.failureMessage()` 中 `FAVORITE_CURRENT -> "当前应用未定义收藏位置或点击失败"` 是**统一兜底**，`FavoriteButtonController` 的每条 false 分支都走它，所以看文案分不清失败环节。

**三条候选根因**

| 候选 | 机制 | 与"顶部悬浮播放器"的关系 | 如何验证 |
|---|---|---|---|
| ① 前台应用判定被悬浮层抢走 | 无障碍只订阅 `typeWindowStateChanged`，`foregroundPackage()` 取最近一次事件的包名；PIP/悬浮播放器出现或"暂停↔播放"会发窗口事件，包名可能是播放器自身或 `com.android.systemui`，随后 `isAllowedTarget` 拒绝 → 立刻 false | 下拉通知栏、弹输入法同理（14:05 版已修 systemui/输入法，此处特指**播放器包名**这种情况） | 是否出现新增提示"未识别到可收藏的应用…"；旧 APK 不会有这句 |
| ② `ControlAccessibilityService.busy` 卡死 | `dispatch()` 置 `busy = true`，只有 `onCompleted` / `onCancelled` 复位；系统在 PIP/悬浮窗场景可能丢弃 `dispatchGesture` 且不回调 → busy 永不复位 → 此后 `tapPixels` 进门就 false | 表现为"先偶尔失败、后来一直失败"，收藏与点赞、光标点击同时失效 | 失败后回到**没有**悬浮播放器的普通页面再做 OK：若仍失败即为 ② |
| ③ 标定浮层 `addView` 被 ROM 拦截 | `replaceOverlay` 的 catch 为空，加窗抛异常就静默 `finish(false)` | MIUI / ColorOS 等对后台全屏悬浮窗有限制，别家有悬浮窗在最上层时更易被拦 | 无日志，必须捕获异常才能证实 |

注意区分另一种现象：若坐标恰好被悬浮播放器遮住，点击会报**成功**但没收藏（打在悬浮窗上），与"失败提示"不是同一回事，可先排除。

**待选方案（须用户确认后才写代码）**

- **方案 A（推荐先做，用于定位）**：把 `execute` / `tap` / `replaceOverlay` 每个 false 分支各给一句明确提示并写 logcat：`无障碍服务未连接` / `未识别到可收藏应用（附被判定包名）` / `当前页面不支持收藏` / `点击注入被系统取消（可能有悬浮窗遮挡）` / `标定浮层无法弹出（附异常名）`。约 20 行，只动 `FavoriteButtonController`，一次装机即可确定 ①②③。
- **方案 B（针对 ②）**：给 `dispatch()` 的 `busy` 加 1.5–2 秒超时兜底（未回调即强制复位并按失败回调），点击前检测卡死先复位。约 10 行，顺带解决"某手势卡死后全部点击失效"的通病。
- **方案 C（针对 ③）**：捕获 `addView` 异常，提示"请在设置中允许后台弹出界面 / 先关闭悬浮播放器"，不再静默失败。约 5 行。
- **方案 D（针对 ①，彻底方案，三选一）**：D1 打开 `canRetrieveWindowContent` 用 `rootInActiveWindow.packageName`（最准，但**触碰 §7 硬约束第 1 条隐私底线**，必须改无障碍服务说明文案并经产品负责人同意）；D2 用 `UsageStatsManager` 查真实前台 App（准确、不读窗口内容，但要新增 `PACKAGE_USAGE_STATS` 权限与授权引导）；D3 校准页加"为指定应用定义收藏位置"入口，自动识别失败时回退到手动指定目标（零权限代价，多一次交互）。

### 2026-10-02 最近任务切换 App 后 OK 收藏失败修复（代码完成、真机测试通过）

- 真机复现范围进一步收窄：用手势打开最近任务并切换到另一个 App 后，OK 收藏很大概率失败。根因是无障碍原来只订阅 `typeWindowStateChanged`；部分 Android 系统恢复已有任务时只发 `TYPE_WINDOWS_CHANGED`，导致前台包名继续是旧 App 或为空。
- `accessibility_service.xml` 现同时订阅 `typeWindowStateChanged|typeWindowsChanged`，仍保持 `canRetrieveWindowContent=false`，不读取第三方窗口内容，也没有新增权限。
- `ControlAccessibilityService.globalAction(RECENTS)` 成功后立即清空旧前台包名并进入“等待目标 App”状态；收到非系统覆盖层、非桌面/设置的真实窗口事件后才接受新包名，避免按旧 App 坐标误点。
- `FavoriteButtonController` 在最近任务切换尚未确认目标时，每 200ms 重试一次，最多 3 次；期间提示“正在确认当前应用，请稍候”。仍无法确认则明确提示未识别目标，不回退到旧包名。
- 点击注入失败新增明确提示“收藏位置点击被系统取消，请确认页面没有被其他窗口遮挡”；无障碍断连也有独立提示。
- 2026-10-02 产品负责人反馈真机测试通过：从最近任务切换 App 后再使用 OK 收藏已能正常工作。仍需在其他机型和 PIP/悬浮播放器场景继续做发布前回归。

**后续顺序**：先安装 23:18 之后的新 APK 验证最近任务切换修复；若普通页面或 PIP 场景仍复现，再按 A 定位，命中 ② 做 B、③ 做 C，其他前台识别问题再从 D1/D2/D3 中选。

### 前半段（95864c6 → 2385186，详见被取代的 PM 版文档）

荣耀真机无障碍授权持久化闭环验收与启动管理白名单引导（`KeepAuthorizationActivity`）；自拍体验优化（缩略图、倒计时音效、期间冻结手势）；可回放关键点样本回归测试；首页卡片拆分；**映射可配置化**（覆盖表 + 校准页配置区 + 运行中即时生效）；接入 7 项系统动作；G24-G28 新手势；G29/G30 两指并拢切歌；V 字/两指并拢误识别修复（角度区分）。

### 后半段（016c267 → c9c446e，共 10 个提交）

| 提交 | 内容 |
|---|---|
| `016c267` | 首页文案与冷却行为一致（冷却期间暂停全部手势识别，含光标） |
| `0dc7fb7` | 两指识别真机修复：容忍并拢 splay 与中指遮挡、入口提示；冷却期冻结识别管线；放宽爪形/C 形阈值；新增手势卡片图标与映射检查清单 |
| `73f9394` | 按产品确认调整默认映射：G07/G08 四指横挥改左右滚动（新增 `SCROLL_LEFT/RIGHT`）、G15 兰花指改最近任务、G26 爪形默认留空、DRAG 移出选单 |
| `080615c` | 莲花指（G14）改**返回桌面**；两指并拢新增**上挥音量+/下挥音量−**（G31/G32） |
| `19a2470` | **G33 两指并拢双击=播放/暂停**；映射文档去日期重命名为 `GESTURE_ACTION_MAPPING_CHECKLIST.md` |
| `56870b2` | G09/G10 竖直食指左/右挑改**页面左右滚动**（阈值 0.05 轻挑触发）；移除失效 back/home 功能开关 |
| `fab6047` | 音量改走 `adjustStreamVolume`（`dispatchMediaKeyEvent` 在新系统忽略音量键码）；两指双击节奏放宽至 120ms/相位；竖直挥动阈值对齐四指滚动；增加手势执行日志 |
| `98f56be` | G25 L 手形改**保持 2 秒**触发（带倒计时反馈） |
| `2e22e4d` | G22 握拳播放/暂停改为保持触发（当前统一为**保持 1 秒**，带倒计时） |
| `c9c446e` | 音量每次调节改为**约 10% 量程**（细刻度机型一格无感知），`setStreamVolume` 直设目标值并记录日志 |

**注意**：`c9c446e`（音量 10% 档位）提交时手机已断开，**尚未安装到真机**；接手后第一件事是连接手机 `installDebug` 验证。

## 4. 手势矩阵现状（G01-G34）

| 状态 | 手势码 |
|---|---|
| 已实现（34） | G01 光标、G02 点击、G03-G10 八个方向滚动、G11 V 字自拍、G12 比心点赞、G13 截图序列、G14 莲花→返回桌面、G15 兰花→最近任务、G16-G19 张掌后收成 1/2/3/4 指→打开指定 App、G20 大拇指点赞、G21 OK=收藏当前内容、G22 握拳保持 1s=播放/暂停、**G23 伸出小指=静音开关**、G24 左 L 返回、G25 L 形保持 2s=通知栏、G26 爪形（默认未绑定）、G27 C 形最近任务、G28 Love 形返回桌面、G29/G30 两指左右切歌、G31/G32 两指保持持续音量、G33 两指双击=播放/暂停、G34 666（默认未绑定） |
| 已移除 | G18/G19 食指画圈音量已移除；编号后续已复用于张掌收指打开 App |

两指系列 G29-G33 共用 `two_finger_media` 开关（显示名"两指媒体控制"），构成完整媒体控制家族；五向均为 DYNAMIC 类型、可换绑。

## 5. 动作目录（GestureAction，28 项）

`NONE`(暂不绑定动作) / `MOVE_CURSOR`(固定) / `CLICK` / `SCROLL_UP` / `SCROLL_DOWN` / `SCROLL_LEFT` / `SCROLL_RIGHT` / `BACK` / `HOME` / `SELFIE` / `LIKE` / `SCREENSHOT` / `ROLLING_SCREENSHOT` / `OPEN_APP` / `FAVORITE_CURRENT` / `THUMBS_UP_LIKE` / `CONFIRM` / `PLAY_PAUSE` / `RECENTS` / `NOTIFICATIONS` / `VOLUME_UP` / `VOLUME_DOWN` / `TOGGLE_MUTE`(媒体静音/恢复) / `MEDIA_NEXT` / `MEDIA_PREVIOUS` / `LOCK_SCREEN` / `VOICE_ASSISTANT` / `DRAG`。`OPEN_APP_1..4` 仅作旧配置兼容。

**G16-G19 打开指定 App**（2026-10-02 新增并重构）：张掌确认后收指到 1/2/3/4 指并保持约 0.6 秒，映射为统一的 `OPEN_APP` 动作。配置流程为“点击当前手势动作 → 选择打开应用 → 紧接着选择目标 App”，不再维护“应用一/二/三/四”独立配置区。包名按手势存为 `open_app_package_Gxx`，映射按钮及首页卡片显示“打开应用：App 名称”。原 `open_app_package_1..4` 和 `OPEN_APP_1..4` 自动兼容迁移。四个识别功能开关 `open_app_1..4` 仍独立保留；执行走 `getLaunchIntentForPackage` + `NEW_TASK`。

**G16-G19 首页图片**（2026-10-02 更新）：四张卡片不再共用单张 `gesture_palm`。每张卡片以“张掌 → 对应最终指型”的双图组合显示，最终姿势资源分别为 `gesture_open_app_1.png`～`gesture_open_app_4.png`，均为 1254×1254 ARGB 透明 PNG，与现有 3D 手势资产风格一致。新增或调整组合手势时，首页必须显示完整阶段图片，不能只用文字或数字角标代替。

音量动作实现：普通换绑动作仍由 `CameraProbeService.adjustVolume` 每次调整约 10%；G31/G32 两指保持会话约每 0.4 秒调整 5%，会话期间独占识别（不输出光标或其他手势），姿势改变或到达边界后结束并进入冷却（时长可配置，见下）。（G18/G19 食指画圈音量已移除。）

**G23 小指静音开关**（2026-10-02 新增）：仅小指伸直，拇指、食指、中指和无名指收拢，保持 1 秒触发 `TOGGLE_MUTE`；执行 `AudioManager.ADJUST_MUTE/ADJUST_UNMUTE` 切换媒体流静音状态。保持不重复触发，必须释放后重新做手势。比心 G12 已收紧为中指、无名指、小指必须收拢；G23 出现时会清除比心候选，避免小指被误判为比心。首页图片为 `gesture_pinky.png`，识别阈值和各厂商静音行为待真机验收。

**手势冷却时长**（2026-10-02 新增）：动作执行成功后的全局锁定默认由 2 秒改为 **1.5 秒**，成为用户可配置项——校准页“显示识别反馈”下方新增拖动条（0.6–4 秒、100ms 步进），松手即保存；运行中的控制服务通过 `cooldown_ms` 偏好监听即时生效，冷却中的锁不会被变更打断。

## 6. 已知问题与待办（按优先级）

1. **G23 首要装机验收**（荣耀 ALP-AN00）：验证仅伸出小指保持 1 秒可切换静音，不足 1 秒不得提前触发；持续保持不重复执行；释放后再次执行恢复声音；不得误发 G12 比心、G20 点赞或 G34 666。还需确认荣耀系统 `AudioManager` 静音状态的 120ms 延迟校验是否稳定。
2. **其余装机验收**：C 形张掌误触发已修复（收口收紧）；启动慢已做模型加载/相机打开并行化；G18/G19 画圈已移除；冷却时长默认 1.5 秒并可在校准页拖动配置（0.6–4 秒，即时生效）。继续验收 G31/G32 持续音量独占态、G22 握拳 1 秒节奏、G33 双击、G29/G30 切歌、G14/G15 手感。
3. **真机阈值校准**：G14/G15（莲花/兰花 0.30 触碰阈值）、G23-G28、G29-G33 全部需要按真机手感逐个微调，一次只改一个。
4. ~~首页卡片动作标签是静态文案~~ **已解决**（2026-10-01）：卡片动作标签改为 `actionLabelOf(Gxx)` 实时读取 `GestureMappingManager.actionFor(code)`（含用户换绑），`onResume` 统一刷新；G01 光标固定不可换绑保持静态文案。
5. ~~G16/G17 未开发（打开指定 App 需应用选择器 UI）~~ **已实现**（2026-10-02）：G16-G19 张掌后收成 1-4 指打开指定 App，应用选择器已进入校准页；见 §5。原 G23 张掌安全阀方案已取消，G23 已调整为伸出小指切换媒体静音并完成代码链路。
6. ~~`captureRollingScreenshot`（滚动长截图）已实现但未接入动作目录~~ **已接入**（2026-10-01）：新增动作 `ROLLING_SCREENSHOT`（滚动长截图），无默认绑定手势、已进入换绑选单；进度提示经悬浮反馈显示，需 Android 11+。
7. G12/G20 双击点赞用固定屏幕坐标，换 App/布局即失效，考虑标注实验性或改为用户校准坐标。
8. 保持授权引导仅有荣耀/华为方案，小米/OPPO/vivo 待补。
9. 回放测试样本是程序合成帧，非真机录制；做真机阈值校准时建议升级为录制样本回放。
10. V 字/两指并拢角度边界（`twoFingerIndexMiddleAngle` 附近）：并拢不触发放宽到 18-20°，V 字误触发则收紧。回放基线 `vSign`/`restPose` 需同步调整。
11. **OK 收藏失败定位与验收（当前最高优先级，见 §3 末节）**：`FavoriteButtonController` 的各 false 分支共用一句兜底文案，无法区分失败环节。待真机确认是①前台判定被悬浮播放器/systemui 抢走、②`busy` 卡死还是③标定浮层 `addView` 被 ROM 拦截，再按方案 A/B/C/D 动代码（尚未执行任何方案）。
12. **第三方 App 收藏按钮位置标定（待真机验收）**：统一动作、G21 默认绑定、首次原页面悬浮标定、按包名与屏幕方向保存相对坐标、多 App 配置管理及执行独占态均已实现。需在快手/抖音/小红书分别验收首次提示、测试保存、再次直接点击、横竖屏隔离、删除配置和重复点击可能取消收藏的提示。
13. **收藏位置的健壮性（待产品确认）**：`FavoriteButtonProfile` 存了 `appVersion` 但从不校验，目标 App 大版本更新后仍点旧坐标；标定 30 秒超时仍是静默 `finish(false)`。
14. **高质量自拍待真机验收**：确认荣耀前置摄像头选中的最大 JPEG 尺寸、照片横竖方向与镜像符合预期；拍摄完成后 640×480 手势识别流继续工作；模拟高质量捕获失败时应自动保存预览帧且服务不退出。
15. **每日签到解锁真机验收（代码已实现）**：首次安装只开放 7 个基础编号；签到后运行中的控制服务立即生效（无需重启）；同一天重复点击被拒绝；把系统日期改到过去不增加也不清零；12 次后 G01–G34 全部拥有；卸载重装回到 7 个；Release 包不得出现全开入口。

## 7. 硬约束（不可违反）

1. **隐私底线**：`res/xml/accessibility_service.xml` 保持 `canRetrieveWindowContent="false"`、`typeWindowStateChanged`、`canPerformGestures="true"`——永远不做"按文字/控件查找节点点击"类功能。
2. **相机独占**：前台服务持续持有前置摄像头，任何"打开相机 App"的动作不可行，自拍走内部抓帧。
3. **失败不伪装成功**：所有动作注入必须带成功/失败回调，只有 success 才进全局冷却。
4. `.codebuddy/` 目录是项目数据，已在 `.gitignore`，勿提交、勿删除。
5. **文档同步规则**：手势/动作绑定任何变更，必须同步更新 `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md`。
6. **首页指南文案规则**：紫色动作标签由 `actionLabelOf(Gxx)` 实时读取映射并在 `onResume` 刷新；灰色说明只写手势姿势/节奏，禁止写死默认执行结果。G13 及 G16-G19 组合卡也必须使用动态动作标签。
7. Release 签名：`magic-gesture-release.jks` / `keystore.properties` 可能含真实口令，不要复制到聊天、日志或公开仓库；不要替换现有正式密钥。

## 8. 构建与验证

```powershell
cd e:/2026/MagicGesture-v0.9
.\gradlew.bat testDebugUnitTest assembleDebug   # 全量验证（当前 83/83 通过）
.\gradlew.bat installDebug                       # 安装到已连接设备
D:\Android\Sdk\platform-tools\adb.exe devices    # adb 不在 PATH，用完整路径
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

最近一次产物：`app\build\outputs\apk\debug\app-debug.apk`，2026-10-02 **23:18** 生成，约 68.9 MB，Debug（全部手势解锁）。**该版本已包含**：最近任务切换后前台包名重新确认与短暂重试、G22/G23 保持 1 秒、G34 独立 666 图片，以及此前 systemui/输入法/桌面/设置判定、收藏提示、两指左右挥放宽和播放手势唤起音乐应用回退链。真机每次反馈前先确认 APK 时间是否晚于源码改动时间。

手机测试路径：开相机权限 → 启用无障碍 → 启动控制 → 播放音乐。先验证 G23 小指静音/恢复、保持不重触发、释放后可再触发以及不误判比心；再测试两指左右切歌、上下拉住持续音量和双击播放暂停；最后验证握拳保持 1 秒。G18/G19 画圈识别已经移除，不再测试旧画圈音量流程。

荣耀机型注意：安装时保持手机解锁并确认 USB 安装弹窗；无障碍授权丢失时引导用户在 设置→应用→魔法手势→电池 关闭"自动管理"（详见 §8.1/§10.1 of 旧文档记录，或提交 95864c6 系列）。

## 9. Git 状态（2026-10-02 14:10 核对）

- 分支 `main`，HEAD = `1b0288c`，与 `origin/main` **完全同步**（无本地领先提交、无未推送内容）。用户未要求提交前**不要推送远程**。
- **未提交的工作区改动（15 个已改文件 + 2 个新增文件，约 711 行新增 / 127 行删除）**：
  - 新增：`GestureUnlock.kt`、`GestureUnlockTest.kt`
  - 源码：`CameraProbeService.kt`、`ControlAccessibilityService.kt`、`FavoriteButtonController.kt`、`GestureEngine.kt`、`GestureArchitecture.kt`、`GesturePreferences.kt`、`HandPipeline.kt`、`MainActivity.kt`、`CalibrationActivity.kt`
  - 配置：`app/build.gradle.kts`、`AndroidManifest.xml`（`queries` 增加 `MAIN + APP_MUSIC`）
  - 文档：`GESTURE_ACTION_MAPPING_CHECKLIST.md`、`GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`、本交接文档
  - 测试：`GestureEngineReplayTest.kt`
- 这批改动**尚未提交**的原因：连日阈值调参与真机验收尚未收口（含本轮收藏判断），等真机结论回来后一次性提交更安全。若中途需要安全点，可仅做本地提交、不动远程。
- `git diff --check` 只报 Git 的 LF→CRLF 工作区换行提示，无实质空白错误；`testDebugUnitTest assembleDebug` 已通过（83/83）。

## 10. 文件速查

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 全部姿势检测与状态机（V 字块在最前，注意与两指块的互斥；两指双击状态机在挥手状态机旁） |
| `GestureArchitecture.kt` | 手势码 G01-G34 / 映射（伴生对象 `defaultMappings`）/门控/动作执行器/`GestureAction` 与文案 |
| `CameraProbeService.kt` | 前台服务：相机管线、偏好监听（`feature_*` 与 `mapping_*` 即时生效）、自拍链路、媒体键与音量 |
| `ControlAccessibilityService.kt` | 无障碍：`globalAction()`/`inject()`/`tapPixels()`/`foregroundPackage()`/媒体键；`busy` 标志是全局注入锁（疑似卡死点，见 §3 末节②） |
| `FavoriteButtonController.kt` | 收藏标定与坐标点击：首次标定浮层、准星、`saveFavoriteProfile` 读取；**失败分支共用兜底文案**（当前待定位） |
| `GesturePreferences.kt` | 开关（`feature_*`）与映射覆盖（`mapping_Gxx`）持久化 |
| `MainActivity.kt` / `CalibrationActivity.kt` / `KeepAuthorizationActivity.kt` | 首页卡片 / 校准+开关+映射配置 / 品牌授权引导 |
| `GlobalCooldownManager.kt` / `HandPipeline.kt` / `OverlayIndicator.kt` | 冷却 / MediaPipe 封装 / 悬浮反馈与缩略图 |
| `GestureUnlock.kt` | 签到解锁：解锁计划、签到状态机、本机存储、权益判定与开关收敛 |
| `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md` | 手势↔动作完整映射表（必随代码同步更新） |

## 11. 接手后的第一步（按当前实际进度）

1. **先问用户要 OK 收藏的复测结果**（§3 末节：三条候选根因 + 方案 A/B/C/D）。这是当前唯一的阻塞项；拿到结果前不要改收藏相关代码（用户已明确"确认了再改代码"）。
2. 确认手机装的 APK 是 **14:05 版**（`app\build\outputs\apk\debug\app-debug.apk`，67.9 MB）；不是就重装。连接荣耀手机（历史设备 `AXYP6R4A30002818`，HONOR ALP-AN00，Android 14），`adb devices -l` 确认后再安装。
3. 结果回来后按推荐顺序动刀：**A 定位 → 命中②做 B、③做 C、①再做 D**（D1 触碰 §7 隐私底线，须产品负责人同意）。每次只改一个变量，改完重跑 `testDebugUnitTest assembleDebug` 再让用户复测。
4. 其余待验真机项（§6）：G23 小指静音回环、播放手势唤起默认音乐应用、两指左右挥放宽后的手感、自拍倒计时无提示、持续音量与双击、签到解锁 Release 行为。
5. 改动同步 `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md` 与本交接文档；**未获明确指示不要推送远程**，提交前先 `git diff --check`。
