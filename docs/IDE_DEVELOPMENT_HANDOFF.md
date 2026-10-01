# MagicGesture IDE 开发交接文档

> 本文档取代 `IDE_DEVELOPMENT_HANDOFF_2026-10-01.md` 与 `IDE_DEVELOPMENT_HANDOFF_2026-10-01_PM.md`（均已删除）。
> 需求基线见 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`；手势↔动作完整映射表见 `GESTURE_ACTION_MAPPING_CHECKLIST.md`（**无日期命名**；今后任何手势/动作绑定变更或新增，必须同步更新该文档——这是产品负责人的既定规则）。
> 接手前请通读第 2、6、7、9 节。

## 1. 项目概况

- **工程**：`e:/2026/MagicGesture-v0.9`，单模块 Android 应用（Kotlin，无 Compose，自绘 View）
- **身份**：`applicationId` / `namespace` = `com.magicgesture.app`；`versionName 0.9.0` / `versionCode 9`
- **SDK**：minSdk 26，target/compileSdk 36；依赖 MediaPipe Tasks Vision 0.10.21（手部关键点，模型 `app/src/main/assets/hand_landmarker.task`）
- **源码**：`app/src/main/java/com/magicgesture/app/` 下 13 个类，全部单层包结构
- **测试**：`app/src/test/` 下 4 个测试类，共 52 个用例，当前全绿

## 2. 核心架构

```text
HandPipeline（MediaPipe 20fps 关键点）
  → GestureEngine.kt        姿势/状态机检测，产出 GestureEvent（sealed，含 Feedback）
  → GestureMappingManager   手势码→动作：默认映射 + 用户覆盖表（持久化于 SharedPreferences 键 mapping_Gxx）
  → GestureFeatureGate      手势开关门控（跟手势走，不跟动作走）
  → GestureActionExecutor   以动作为中心的分发（不依赖原始事件类型，换绑后任何手势可执行任何动作）
       ├─ ControlAccessibilityService  全局动作 / dispatchGesture 注入 / 语音助手意图
       ├─ CameraProbeService 内部动作（自拍、缩略图、悬浮反馈、媒体键与音量）
       └─ 媒体/音量           KeyEvent 派发 + AudioManager（音量走 setStreamVolume）
GlobalCooldownManager：2000ms，仅动作成功回调后启动；冷却期间冻结全部识别（含光标）
```

关键设计决策（勿破坏）：

1. **执行器只看映射结果动作**，换绑后才不炸（如其他手势换绑 CLICK 会降级为点击当前光标位置）。
2. **功能开关管"手势是否识别"，映射管"触发后做什么"，二者独立**（UI 已有此文案）。
3. **G01 光标固定不可换绑**；G16/G17/G18/G19/G23 无检测管线不可换绑（G18/G19 画圈已移除）；`DRAG` 只有爪形手势能提供坐标，已从换绑选单移除但动作枚举保留（G26 默认不绑定，触发时提示未绑定）。
4. `GestureEngine` 中部分状态机**吞帧**（提前 return 阻断后续检测器），部分**不吞帧**（旁路只发事件）。不吞帧的典型：两指并拢系列 G29-G33（该姿势同时是常见"自然手型"，吞帧会阻断其他手势的释放检测，见状态机注释与回放测试 `restPose`）。
5. **冷却期冻结识别管线**：冷却期间光标也停（注意与早期版本"光标白名单"行为不同，首页文案已同步为准确描述）。

## 3. 本阶段完成工作（main 领先 origin/main 26 个提交，全部 2026-10-01）

### 2026-10-02 当前工作区总结（尚未提交、尚未装机）

- 首页 G16-G19 组合手势卡片改为“张掌 → 1/2/3/4 指”双图展示，并新增四张对应透明 PNG。
- “打开应用”由“应用一/二/三/四”槽位模式重构为按手势直接绑定：选择统一动作 `OPEN_APP` 后立即选择目标 App，界面显示“打开应用：App 名称”；旧槽位配置保留升级兼容。
- G28 Love 手形默认动作由锁屏调整为返回桌面；已有用户手动映射仍优先，选择“默认”后才采用新默认值。
- `GESTURE_ACTION_MAPPING_CHECKLIST.md` 与开发规格已同步上述变化。
- 已执行 `testDebugUnitTest assembleDebug`，构建成功；仅有既存 Android API/Gradle 弃用警告。代码完成不等于真机验收，当前 Debug APK 尚未安装测试。
- 第三方 App 收藏功能首版代码链路已完成，需求与验收基线见 `THIRD_PARTY_APP_FAVORITE_BUTTON_REQUIREMENTS.md`：G21 OK 默认触发；用户在目标 App 原页面标定收藏按钮坐标，按包名和横竖屏保存，多 App 自动匹配执行。当前仅通过构建/JVM 测试，尚未真机验收。

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
| `2e22e4d` | G22 握拳播放/暂停改**保持 2 秒**触发（带倒计时） |
| `c9c446e` | 音量每次调节改为**约 10% 量程**（细刻度机型一格无感知），`setStreamVolume` 直设目标值并记录日志 |

**注意**：`c9c446e`（音量 10% 档位）提交时手机已断开，**尚未安装到真机**；接手后第一件事是连接手机 `installDebug` 验证。

## 4. 手势矩阵现状（G01-G33）

| 状态 | 手势码 |
|---|---|
| 已实现（32） | G01 光标、G02 点击、G03-G10 八个方向滚动、G11 V 字自拍、G12 比心点赞、G13 截图序列、G14 莲花→返回桌面、G15 兰花→最近任务、**G16-G19 张掌后收成 1/2/3/4 指→打开指定 App（2026-10-02 新增）**、G20 大拇指点赞、G21 OK=收藏当前内容（位置标定链路完成，待真机）、G22 握拳保持 1.5s=播放/暂停、G24 左 L 返回、G25 L 形保持 2s=通知栏、G26 爪形（默认未绑定）、G27 C 形最近任务、G28 Love 形返回桌面（2026-10-02 调整）、G29/G30 两指左右切歌、G31/G32 两指保持持续音量、G33 两指双击=播放/暂停 |
| 未实现/已移除（1） | G23（张掌静止暂停识别）——仅枚举占位。**G18/G19 食指画圈音量已于 2026-10-01 彻底移除**（真机姿势冲突过高）；G16-G19 枚举已于 2026-10-02 以"张掌后收指打开指定 App"重新启用 |

两指系列 G29-G33 共用 `two_finger_media` 开关（显示名"两指媒体控制"），构成完整媒体控制家族；五向均为 DYNAMIC 类型、可换绑。

## 5. 动作目录（GestureAction，28 项）

`NONE`(暂不绑定动作，显式留空) / `MOVE_CURSOR`(固定) / `CLICK` / `SCROLL_UP` / `SCROLL_DOWN` / `SCROLL_LEFT` / `SCROLL_RIGHT` / `BACK` / `HOME` / `SELFIE` / `LIKE` / `SCREENSHOT` / `ROLLING_SCREENSHOT`(滚动长截图，无默认绑定) / `OPEN_APP`(打开应用，目标 App 直接绑定当前手势) / `FAVORITE_CURRENT`(收藏当前内容) / `THUMBS_UP_LIKE` / `CONFIRM` / `PLAY_PAUSE` / `RECENTS` / `NOTIFICATIONS` / `VOLUME_UP` / `VOLUME_DOWN` / `MEDIA_NEXT` / `MEDIA_PREVIOUS` / `LOCK_SCREEN` / `VOICE_ASSISTANT` / `DRAG`(仅爪形可提供坐标)。`OPEN_APP_1..4` 仅作旧配置兼容，不再出现在选择界面。“默认”删除覆盖并恢复出厂绑定，`NONE` 则持续保持不执行动作。

**G16-G19 打开指定 App**（2026-10-02 新增并重构）：张掌确认后收指到 1/2/3/4 指并保持约 0.6 秒，映射为统一的 `OPEN_APP` 动作。配置流程为“点击当前手势动作 → 选择打开应用 → 紧接着选择目标 App”，不再维护“应用一/二/三/四”独立配置区。包名按手势存为 `open_app_package_Gxx`，映射按钮及首页卡片显示“打开应用：App 名称”。原 `open_app_package_1..4` 和 `OPEN_APP_1..4` 自动兼容迁移。四个识别功能开关 `open_app_1..4` 仍独立保留；执行走 `getLaunchIntentForPackage` + `NEW_TASK`。

**G16-G19 首页图片**（2026-10-02 更新）：四张卡片不再共用单张 `gesture_palm`。每张卡片以“张掌 → 对应最终指型”的双图组合显示，最终姿势资源分别为 `gesture_open_app_1.png`～`gesture_open_app_4.png`，均为 1254×1254 ARGB 透明 PNG，与现有 3D 手势资产风格一致。新增或调整组合手势时，首页必须显示完整阶段图片，不能只用文字或数字角标代替。

音量动作实现：普通换绑动作仍由 `CameraProbeService.adjustVolume` 每次调整约 10%；G31/G32 两指保持会话约每 0.4 秒调整 5%，会话期间独占识别（不输出光标或其他手势），姿势改变或到达边界后结束并进入冷却（时长可配置，见下）。（G18/G19 食指画圈音量已移除。）

**手势冷却时长**（2026-10-02 新增）：动作执行成功后的全局锁定默认由 2 秒改为 **1.5 秒**，成为用户可配置项——校准页“显示识别反馈”下方新增拖动条（0.6–4 秒、100ms 步进），松手即保存；运行中的控制服务通过 `cooldown_ms` 偏好监听即时生效，冷却中的锁不会被变更打断。

## 6. 已知问题与待办（按优先级）

1. **装机验收**（2026-10-01/02 真机：荣耀 ALP-AN00）：C 形张掌误触发已修复（收口收紧）；启动慢已做模型加载/相机打开并行化；G18/G19 画圈按真机反馈默认禁用（姿势冲突高）；冷却时长默认 1.5 秒并可在校准页拖动配置（0.6–4 秒，即时生效）——以上均经用户测试通过（2026-10-02）。首页两指并拢卡拆分为三张独立卡。剩余：G31/G32 持续音量独占态、G22 握拳 1.5 秒节奏、G33 双击、G29/G30 切歌、G14/G15 若有手感问题继续微调阈值。
2. **真机阈值校准**：G14/G15（莲花/兰花 0.30 触碰阈值）、G24-G28、G29-G33 全部为合成初版阈值，按真机手感逐个微调，一次只改一个。
3. ~~首页卡片动作标签是静态文案~~ **已解决**（2026-10-01）：卡片动作标签改为 `actionLabelOf(Gxx)` 实时读取 `GestureMappingManager.actionFor(code)`（含用户换绑），`onResume` 统一刷新；G01 光标固定不可换绑保持静态文案。
4. ~~G16/G17 未开发（打开指定 App 需应用选择器 UI）~~ **已实现**（2026-10-02）：G16-G19 张掌后收成 1-4 指打开指定 App，应用选择器已进入校准页；见 §5。剩余 **G23**（张掌静止暂停识别，安全阀功能）未开发。
5. ~~`captureRollingScreenshot`（滚动长截图）已实现但未接入动作目录~~ **已接入**（2026-10-01）：新增动作 `ROLLING_SCREENSHOT`（滚动长截图），无默认绑定手势、已进入换绑选单；进度提示经悬浮反馈显示，需 Android 11+。
6. G12/G20 双击点赞用固定屏幕坐标，换 App/布局即失效，考虑标注实验性或改为用户校准坐标。
7. 保持授权引导仅有荣耀/华为方案，小米/OPPO/vivo 待补。
8. 回放测试样本是程序合成帧，非真机录制；做真机阈值校准时建议升级为录制样本回放。
9. V 字/两指并拢角度边界（`twoFingerIndexMiddleAngle` 附近）：并拢不触发放宽到 18-20°，V 字误触发则收紧。回放基线 `vSign`/`restPose` 需同步调整。
10. **第三方 App 收藏按钮位置标定（待真机验收）**：统一动作、G21 默认绑定、首次原页面悬浮标定、按包名与屏幕方向保存相对坐标、多 App 配置管理及执行独占态均已实现。需在快手/抖音/小红书分别验收首次提示、测试保存、再次直接点击、横竖屏隔离、删除配置和重复点击可能取消收藏的提示。

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
.\gradlew.bat testDebugUnitTest assembleDebug   # 全量验证（当前 49/49 通过）
.\gradlew.bat installDebug                       # 安装到已连接设备
D:\Android\Sdk\platform-tools\adb.exe devices    # adb 不在 PATH，用完整路径
```

手机测试路径：开相机权限 → 启用无障碍 → 启动控制 → 播放音乐。先以食指顺/逆时针画完整圆并继续旋转，确认方向、5% 圆弧步进、独占和停止退出；再测试两指左右切歌、上下拉住持续音量和双击播放暂停；最后验证握拳保持 1.5 秒。

荣耀机型注意：安装时保持手机解锁并确认 USB 安装弹窗；无障碍授权丢失时引导用户在 设置→应用→魔法手势→电池 关闭"自动管理"（详见 §8.1/§10.1 of 旧文档记录，或提交 95864c6 系列）。

## 9. Git 状态

- 分支 `main` 领先 `origin/main` **26 个提交**，HEAD = `c9c446e`，工作区干净
- 未推送；是否推送由产品负责人决定
- 本次交接重命名：`IDE_DEVELOPMENT_HANDOFF.md`（本文件）取代两份带日期旧版

## 10. 文件速查

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 全部姿势检测与状态机（V 字块在最前，注意与两指块的互斥；两指双击状态机在挥手状态机旁） |
| `GestureArchitecture.kt` | 手势码 G01-G33 / 映射（伴生对象 `defaultMappings`）/门控/动作执行器/`GestureAction` 与文案 |
| `CameraProbeService.kt` | 前台服务：相机管线、偏好监听（`feature_*` 与 `mapping_*` 即时生效）、自拍链路、媒体键与音量 |
| `ControlAccessibilityService.kt` | 无障碍：`globalAction()`/`inject()`/`scrollDirectional()`/`confirmAtCursor()`/`likeVideo()`/媒体键 |
| `GesturePreferences.kt` | 开关（`feature_*`）与映射覆盖（`mapping_Gxx`）持久化 |
| `MainActivity.kt` / `CalibrationActivity.kt` / `KeepAuthorizationActivity.kt` | 首页卡片 / 校准+开关+映射配置 / 品牌授权引导 |
| `GlobalCooldownManager.kt` / `HandPipeline.kt` / `OverlayIndicator.kt` | 冷却 / MediaPipe 封装 / 悬浮反馈与缩略图 |
| `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md` | 手势↔动作完整映射表（必随代码同步更新） |

## 11. 接手后的第一步

1. 连接荣耀手机（`AXYP6R4A30002818`，HONOR ALP-AN00，Android 14），`installDebug` 装上含音量 10% 修复的版本。
2. 按 §6 第 1 条清单做真机验收，记录手感问题。
3. 根据验收结果调阈值/档位，每次一个变量，提交并同步 `GESTURE_ACTION_MAPPING_CHECKLIST.md`。
