# 魔法手势（MagicGesture）工作交接文档

> 交接日期：2026-10-22
> 面向对象：**接手本项目开发的新同事**（人类开发者）
> 与已有文档的关系：本文档是**上手导航**，帮你在最短时间内跑起来、知道改哪里、避开已踩过的坑。
> 技术细节、需求基线和逐条变更记录仍以下列文档为准，本文档不复制它们的内容：
>
> | 文档 | 看什么 |
> |---|---|
> | `AGENTS.md`（仓库根目录） | **接手第一必读**，开发纪律与硬约束 |
> | `IDE_DEVELOPMENT_HANDOFF.md` | 开发过程全记录、最新构建产物、真机取证结论 |
> | `REQUIREMENTS_CHANGELOG.md` | 已确认的产品行为变更（按日期倒序） |
> | `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md` | 需求基线 |
> | `GESTURE_ACTION_MAPPING_CHECKLIST.md` | 手势↔动作唯一映射表（改绑定必须同步更新） |
> | `MAGIC_GESTURE_APP_FEATURE_LIST.md` | 功能清单 |
> | `PROJECT_PROGRESS_SUMMARY.md` | 一页式进度快照 |
> | `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` | 签到解锁正式规则 |

---

## 1. 这个项目是什么

隔空手势控制 App。用**前置摄像头**采集画面，经 **MediaPipe Tasks Vision** 得到 21 个手部关键点，再由自研状态机识别出手势，最后通过**无障碍服务**把手势翻译成系统动作（返回、Home、截屏、切歌、调音量、打开 App 等），全程**离线**、**无 INTERNET 权限**。

- 包名：`com.magicgesture.app`
- 版本：`versionName 1.0.0` / `versionCode 10`
- SDK：minSdk 26，target / compileSdk 36
- 关键依赖：MediaPipe Tasks Vision 0.10.21（模型随包：`app/src/main/assets/hand_landmarker.task`）
- ABI：仅 `arm64-v8a` + `armeabi-v7a`（32 位 `x86` 已移除，以满足 64 位配对要求）
- 上架状态：**已真实公开上架华为应用市场**（`https://appgallery.huawei.com/app/C119234419`）
- 当前分支：`V1.0.0`（远端 `origin`=Gitee，`github`=GitHub，均已推送到 `045fa02`）

### 手势编号现状

G01–G36 **全部已实现**；G36 已人工验收。**G37 及后续编号已冻结**——未经产品负责人重新立项，不得新增编号。动作枚举 32 个，功能开关 29 个。

### 二期（当前主线）：场景模式

按前台应用自动切换手势方案，三个场景：`GENERAL`（通用，默认与失败回退）、`SHORT_VIDEO`（短视频）、`MUSIC`（音乐）。用户可为每个 App 关联场景，也可手动临时切换。暂不收费，不做会员/账号/联网分类。

---

## 2. 代码结构（关键文件）

源码根目录：`app/src/main/java/com/magicgesture/app/`

### 2.1 两个服务（App 的核心）

| 文件 | 职责 |
|---|---|
| `CameraProbeService.kt` | **前台相机服务**。识别管线、自拍、媒体控制、收藏位置、识别锁、场景轮询与切换。App 的"心脏" |
| `ControlAccessibilityService.kt` | **无障碍服务**。把手势翻译成系统动作注入；判定前台包名 |

> 排查问题时的第一件事：确认 `CameraProbeService` 是否在运行，且用户已点"启动手势控制"。**只有该服务运行且控制开启**，才会有场景与手势日志。只有 `ControlAccessibilityService` 存活时日志为空——不要误判为"日志被系统限制"。

### 2.2 识别核心

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 手势状态机（**最复杂、最常改**）。所有 G 编号的判定阈值与时序都在这里 |
| `HandPipeline.kt` | MediaPipe 管线封装、YUV→ARGB 转换、帧背压 |
| `ActiveHandSelector.kt` | 多人多手时"视觉最近手独占控制权" |
| `GestureArchitecture.kt` | 动作枚举、功能开关、映射数据结构 |
| `GestureUnlock.kt` | 签到解锁权益 |
| `GlobalCooldownManager.kt` | 动作后全局冷却（默认 1000ms） |
| `CameraFrameOrientation.kt` / `SelfieFraming.kt` | 画面方向与自拍构图 |

### 2.3 场景模式（二期新增）

| 文件 | 职责 |
|---|---|
| `GestureScene.kt` | `GENERAL` / `SHORT_VIDEO` / `MUSIC` 定义与预设手势集 |
| `SceneManagementActivity.kt` | 场景管理页（状态、持久化、弹层、服务交接） |
| `ScenePageUi.kt` | 场景页视图（自动切换开关、关联列表、场景方案卡片） |
| `SceneAssociationPromptController.kt` | 首次进入未关联 App 的关联提示层 |
| `SceneSwitchPromptController.kt` | 悬浮球长按后的场景切换面板 |
| `GestureApplication.kt` | `Application` 子类，用 Activity 生命周期判定"自己的界面是否在前台" |

### 2.4 UI 与配置

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 首页（启动手势控制、四宫格入口） |
| `OverlayIndicator.kt` | **悬浮球**、手势反馈文字、自拍预览等 overlay 窗口 |
| `GesturePreferences.kt` | 所有持久化（SharedPreferences，文件 `gesture_settings`） |
| `CalibrationActivity.kt` | 校准页（灵敏度、冷却、功能开关） |
| `GestureMappingActivity.kt` | 手势↔动作映射 |
| `FavoriteLocationActivity.kt` / `FavoriteButtonController.kt` | 收藏位置标定 |
| `KeepAuthorizationActivity.kt` / `PrivacyConsent.kt` / `PrivacyPolicyActivity.kt` | 授权与隐私 |

> 注意：`MainActivity.kt.pre-neon-ui.bak` 是旧版 UI 备份，非编译文件。

---

## 3. 开发环境与构建

### 3.1 环境

- JDK **17**（本机只有 17）
- Gradle Wrapper（用 `.\gradlew.bat`，不要装全局 Gradle）
- Android SDK（compileSdk 36）

### 3.2 构建命令（**必须按这个写法**）

仓库根目录存在 `gradle/gradle-daemon-jvm.properties`，它是 Gradle 自动生成的，里面写着 `toolchainVersion=25` 和 foojay 下载 URL——本机 JDK 是 17，直接构建会尝试下载 JDK 25 而卡住/失败。

**该文件未纳入版本控制（未跟踪），构建前临时改名、构建后恢复**：

```powershell
$f='gradle\gradle-daemon-jvm.properties'; $ex=Test-Path $f
if($ex){ Move-Item $f 'gradle\gradle-daemon-jvm.properties.cb.bak' -Force }
try { .\gradlew.bat testDebugUnitTest assembleDebug --console=plain }
finally { if($ex){ Move-Item 'gradle\gradle-daemon-jvm.properties.cb.bak' $f -Force } }
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

> 建议：把它加入 `.gitignore`，避免误提交（每次 Gradle 同步都会重新生成）。

### 3.3 测试

`testDebugUnitTest`，当前 **205/205 通过**（0 failure / 0 error / 0 skipped）。

- 场景逻辑测试：`GestureSceneTest`（35 条）
- 页面预览：`ScenePagePreviewTest`（Robolectric，会渲染 PNG 到 `app/build/previews/`）
- Robolectric 依赖已加，`testOptions { unitTests.isIncludeAndroidResources = true }`

**改任何手势或场景逻辑后，必须跑完整测试**，并在提交前确认数量没有下降。

---

## 4. 真机调试

### 4.1 设备

荣耀 ALP-AN00，通过**无线调试**连接。设备名形如：

```
adb-AXYP6R4A30002818-Knd1vN._adb-tls-connect._tcp
```

> 无线 ADB **不稳定**：会掉线、mDNS 会出现重复条目（如 `...(2)._adb-tls-connect._tcp`）。掉线时 `adb devices` 为空，需要在手机上重新开启"设置 → 开发者选项 → 无线调试"，或改用 USB。

### 4.2 安装

```powershell
$s="adb-AXYP6R4A30002818-Knd1vN._adb-tls-connect._tcp"
adb -s $s install -r app\build\outputs\apk\debug\app-debug.apk
```

**常见失败**：`INSTALL_FAILED_ABORTED: User rejected permissions`——手机停在 AOD（息屏显示）或锁屏，确认弹窗看不见。先唤醒：

```powershell
adb -s $s shell input keyevent 224   # WAKEUP
```

### 4.3 抓日志（**重要踩坑**）

```powershell
# 不要只用 -s 过滤器！荣耀设备上 adb logcat -s CameraProbe 偶发抓不到（连 startup 日志都没有）
adb -s $s logcat -v time | Select-String -Pattern "CameraProbe"
```

日志 tag 为 `CameraProbe`。

### 4.4 常用取证命令

```powershell
# 服务是否在运行
adb -s $s shell dumpsys activity services com.magicgesture.app | Select-String "ServiceRecord"

# 当前前台
adb -s $s shell dumpsys window | Select-String "mCurrentFocus"

# 直接读持久化（验证 SharedPreferences 是否落地）
adb -s $s shell run-as com.magicgesture.app cat /data/data/com.magicgesture.app/shared_prefs/gesture_settings.xml

# 切换应用（模拟前台变化）
adb -s $s shell monkey -p com.ss.android.ugc.aweme -c android.intent.category.LAUNCHER 1

# 系统触感开关（影响 performHapticFeedback）
adb -s $s shell settings get system haptic_feedback_enabled
```

### 4.5 改包后服务会被杀

安装新 APK 后 `CameraProbeService` 会被系统杀掉，**必须让用户在 App 里重新点"启动手势控制"**，否则后续取证日志为空、得出错误结论。

---

## 5. 当前进度与待办

### 5.1 已完成（代码完成 + 已推送）

最近两个提交：

| Commit | 内容 |
|---|---|
| `37a1f2b` | 场景模式实现与悬浮球、前台判定修复（震动双脉冲、悬浮球位置持久化、抖音场景回退修复、提示条每应用一次、应用选择弹窗性能） |
| `045fa02` | 双指滑动切歌：增加可关闭的诊断埋点 |

已推送到 `origin`(Gitee) 与 `github`(GitHub)，远端无落后提交。

### 5.2 待办（按优先级）

| # | 事项 | 状态 | 说明 |
|---|---|---|---|
| 1 | **双指左右挥切歌：快速挥动不识别** | 诊断埋点已加，**未调参** | 见 §6.1，需真机取证后调参 |
| 2 | 悬浮球位置持久化 | 代码完成，**待真机验收** | 需验：拖动后关 App、重启手机，位置都保持 |
| 3 | 应用选择弹窗性能优化 | 代码完成，**待真机验收** | 需验：弹窗即时打开、列表随后填充、搜索可用 |
| 4 | 提示条"每个应用只提示一次" | 修复完成，**待真机验收** | 需验：切走再切回同一 App 不再提示 |
| 5 | `gradle-daemon-jvm.properties` 加入 `.gitignore` | 未做 | 建议做，避免误提交 |

### 5.3 状态层级的硬性纪律

本项目严格区分：**代码完成 / JVM 测试通过 / 构建通过 / 已装机 / 真机验收通过 / 已真实上线**。

**前一级成功不能替代后一级**。写文档或汇报时不得把"代码完成"说成"已验收"。

---

## 6. 正在解决的问题（接手后最可能立刻碰到的）

### 6.1 双指左右挥切歌（G29/G30）——快速挥动不识别

**现象**：双指并拢竖起，左右挥动切换上/下一曲。必须**慢慢挥**才有效，稍快一点就没反应。

**已做的分析**：

- 触发判定在 `GestureEngine.kt` 的 `advanceTwoFingerSwipe()`（`TwoFingerSwipeState.TRACKING` 分支）
- 判定条件：`|dx| >= twoFingerHorizontalTrigger`（`0.07f * movementScale`）且 `horizontal`（`|dx| > |dy|`）且 `wholeHand`（手腕位移 ≥ 掌心位移的 60%）且 `elapsed <= 5000`
- **结论：判定里没有速度下限**——`elapsed <= 5000` 只是上限。所以快挥理论**更容易**达阈值，真因不在阈值
- 已排除：`palm` 用的是**未平滑**的原始关键点（平滑只作用于光标），不是滤波滞后

**最可能的两个真因**：

1. **姿势中途丢失**——`TRACKING` 里 `if (!pose)` 直接把整段作废。快挥产生运动模糊，MediaPipe 关键点抖动 → 姿势判定丢失
2. **方向分量不达标**——`horizontal` 要求 `|dx| > |dy|`。快挥常带上下起伏，`dy` 一大就不算"左右挥"

**已埋的探针**（`GestureEngine.kt`，经 `diag` 回调输出，默认不启用、不影响识别）：

- `tf pose lost mid-wave el=…` → 姿势是否中途丢失
- `tf dx=… dy=… wdx=… horiz=… whole=… el=…` → 各项判定实际值

**接手后怎么做**：

1. 装包含探针的版本（注意：13:16:55 那次构建因 ADB 断连**未安装成功**，需重装）
2. 让用户：双指并拢 → **慢慢**左右挥一次 → 停 2 秒 → **稍快**左右挥一次
3. 抓日志对比两次的 `dx / horiz / whole / pose lost`
4. 据此调参——**不要盲改阈值**，盲改容易引入误触切歌

### 6.2 各手势阈值的调参原则

`GestureEngine.kt` 里每个阈值都有注释说明"当初为什么取这个值、实测数据是什么"。**改之前先读注释**，改完同步更新注释和 `REQUIREMENTS_CHANGELOG.md`。

---

## 7. 踩过的坑（避免重犯）

按"代价高低"排序，每一条都是真实发生过并已修复的：

| # | 坑 | 现象 | 正确做法 |
|---|---|---|---|
| 1 | **overlay 窗口归属自身包名** | 打开抖音切到短视频场景后立刻被切回通用 | 悬浮球/提示条/反馈都是 `TYPE_APPLICATION_OVERLAY`，包名同样是 `com.magicgesture.app`，用无障碍窗口事件判"自己在前台"会被它们误触发。改用 `GestureApplication` 的 Activity 生命周期判定 |
| 2 | **`performHapticFeedback` 被系统开关屏蔽** | 长按悬浮球完全不震动 | 受 `haptic_feedback_enabled` 控制（该机为 0）。改用 `Vibrator` 直接震动（需 `VIBRATE` 权限，normal 级） |
| 3 | **`VibrationEffect.MAX_AMPLITUDE` 不在公开 SDK** | 编译报 unresolved reference | 写 `255` |
| 4 | **`createWaveform` 不传 amplitudes 用平台默认强度** | 震动"太弱"感觉不到 | 显式传 `intArrayOf(0,255,0,255)` |
| 5 | **主线程做几百次 Binder 调用** | 应用选择弹窗打开极慢 | `queryIntentActivities` + 每个应用 `loadLabel()` 全部移后台；弹窗先 `show()` 再填充；标签只解析一次 |
| 6 | **只写持久化、没同步内存态** | "每个应用只提示一次"不生效 | 弹层结束同时更新服务内存的 `notified` 集合 |
| 7 | **`adb logcat -s <tag>` 在荣耀偶发失效** | 抓不到日志，误判为服务没跑 | 用全量 `logcat -v time` 再 grep |
| 8 | **装包后服务被杀** | 取证日志为空、得出错误结论 | 每次装包后必须重启手势控制，并先 `dumpsys` 确认服务 |
| 9 | **`gradle-daemon-jvm.properties` 要求 JDK 25** | 构建卡住 | 见 §3.2，构建时临时改名 |
| 10 | **AOD 息屏时安装被拒** | `INSTALL_FAILED_ABORTED` | 先 `input keyevent 224` 唤醒 |
| 11 | **编译失败却仍执行安装** | 装上旧包，误以为改动生效 | 先确认 `BUILD SUCCESSFUL` 再安装 |

---

## 8. 产品规则与硬约束

1. **G37 及后续编号冻结**——未经产品负责人重新立项不得新增。
2. **改任何手势↔动作绑定，必须同步更新 `GESTURE_ACTION_MAPPING_CHECKLIST.md`**——这是产品负责人的既定规则。
3. **隐私**：最终权限只有 `CAMERA`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_CAMERA`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`、`MODIFY_AUDIO_SETTINGS`、`VIBRATE`。**严禁加 `INTERNET` / `ACCESS_NETWORK_STATE`**；`canRetrieveWindowContent="false"` 保持不变。
4. **G28 Love 手势是系统级识别锁**：开箱即用、不计入基础动作编号、不可关闭、不可换绑。
5. **场景模式暂不收费**：不做会员、Pro、自定义场景、联网分类、账号同步或线上埋点。
6. **多人多手**：采用"视觉最近手独占控制权"。
7. **状态层级纪律**（见 §5.3）：不得跨级宣称。

---

## 9. 上手 checklist

接手后按顺序做一遍，确认环境可用：

- [ ] 读仓库根目录 `AGENTS.md`
- [ ] 用 §3.2 的命令跑一次完整 `testDebugUnitTest assembleDebug`，确认 **205/205** 且 `BUILD SUCCESSFUL`
- [ ] 连接无线 ADB（或 USB），`adb devices` 能看到设备
- [ ] 安装 APK 到手机（失败先 `input keyevent 224` 唤醒）
- [ ] 在手机上点"启动手势控制"，`dumpsys` 确认 `CameraProbeService` 在运行
- [ ] 抓一次全量 logcat 并 grep `CameraProbe`，确认能看到日志
- [ ] 用 `run-as` 读一次 `gesture_settings.xml`，确认能读到持久化
- [ ] 通读 `IDE_DEVELOPMENT_HANDOFF.md` 第 2、6、7、9 节
- [ ] 从 §5.2 待办清单里领一项

---

## 10. 交接时的仓库状态快照（2026-10-22）

| 项 | 值 |
|---|---|
| 分支 | `V1.0.0` |
| HEAD | `045fa02` |
| 已推送 | `origin`(Gitee) 与 `github`(GitHub)，均为 `6ef595c..045fa02` |
| 工作区 | 干净；仅 `gradle/gradle-daemon-jvm.properties` 未跟踪（环境生成物，建议加 `.gitignore`） |
| 测试 | 205/205 通过 |
| 最新 Debug APK | 62,474,268 bytes（2026-10-22 13:16:55，含双指诊断探针，**因 ADB 断连未安装成功**） |
| 手机当前实际运行版本 | 62,474,167 bytes（2026-10-22 13:09:30 安装，应用选择弹窗性能优化版，**不含**双指诊断探针） |

> 注意最后一行的差异：代码库 HEAD 比手机上跑的版本新。接手后如需做双指取证，**必须先重新安装**。
