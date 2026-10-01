# 魔法手势 Android 工程 IDE 开发交接文档

交接日期：2026-10-01  
工程目录：`E:\2026\MagicGesture-v0.9`  
当前分支：`main`  
当前 HEAD：`4a8556e feat: 新增多种手势动作与 V 字自拍，重构冷却与映射管线`

## 1. 交接结论

该工程已经从早期“后台摄像头可行性探针”发展为可编译运行的 Android 手势控制原型。目前代码包含 MediaPipe 单手关键点识别、前台摄像头服务、悬浮光标、Android 无障碍动作、手势功能开关、统一动作映射架构和全局动作冷却。

此前的功能代码已经提交到本地 `main`。当前分支比 `origin/main` 超前 2 个提交；尚未确认推送。2026-10-01 后续完成的手势图片、无障碍授权检测和自动重连修改仍未提交，交接文档本身也仍是未跟踪文件。接手时必须一并保存，不要直接执行 `git reset --hard`、`git checkout -- .` 或覆盖整个目录。

当前状态只能表述为：

- Kotlin 编译、单元测试和 Debug APK 构建通过。
- G01～G15、G20～G22 已具备代码链路。
- 莲花指、兰花指、竖大拇指已经换成独立透明 PNG，资源与名称已校正。
- 无障碍授权检测和系统重绑定逻辑已补强，已通过编译，但“杀进程后无需再次授权”仍需真机完成闭环验收。
- 新增静态手势、自拍、传统指型仍未完成真机识别验收。
- G16～G19、G23 尚未开发。
- 当前版本仍为 `0.9.0`，不是已经达到上架标准的正式 V1.0。

## 2. 新 IDE 环境

推荐使用 Android Studio，并安装：

- JDK 17
- Android SDK Platform 36
- Android Build Tools 对应版本
- Gradle Wrapper 9.3.0
- Android Gradle Plugin 8.13.2
- Kotlin 2.2.20

工程配置：

| 项目 | 当前值 |
|---|---|
| applicationId / namespace | `com.magicgesture.app` |
| minSdk | 26 |
| targetSdk / compileSdk | 36 |
| versionName | `0.9.0` |
| versionCode | 9 |
| MediaPipe | `com.google.mediapipe:tasks-vision:0.10.21` |
| 单元测试 | JUnit 4.13.2 |
| 手部模型 | `app/src/main/assets/hand_landmarker.task` |

首次打开后依次执行：

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Debug APK 输出：

```text
app/build/outputs/apk/debug/app-debug.apk
```

最后一次完整验证结果：`testDebugUnitTest` 与 `assembleDebug` 成功，`git diff --check` 通过。后续图片归位后再次执行 `assembleDebug` 也成功。构建仅有 Android/Gradle API 弃用警告，没有编译错误。

若 Gradle Wrapper 下载出现：

```text
java.net.SocketException: Permission denied: getsockopt
```

这是网络或执行权限问题，不是源码错误。允许访问 `services.gradle.org`、使用已有 Gradle 缓存，或在 Android Studio 中重新同步即可。

## 3. Git 与工作区状态

当前状态：

```text
## main...origin/main [ahead 2]
 M app/src/main/java/com/magicgesture/app/ControlAccessibilityService.kt
 M app/src/main/java/com/magicgesture/app/MainActivity.kt
 M docs/MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md
?? app/src/main/res/drawable-nodpi/gesture_lotus.png
?? app/src/main/res/drawable-nodpi/gesture_orchid.png
?? app/src/main/res/drawable-nodpi/gesture_thumbs_up.png
?? docs/IDE_DEVELOPMENT_HANDOFF_2026-10-01.md
```

功能提交为：

```text
4a8556e feat: 新增多种手势动作与 V 字自拍，重构冷却与映射管线
462d451 docs: refresh v0.9 release status
```

远端 `origin/main` 当前停在 `ec128b4`。这表示功能代码只确认在本地提交，不能表述为已同步远程仓库。接手后先运行：

```powershell
git status --short
git status -sb
git log -5 --oneline --decorate
git diff --check
```

以上状态为本次交接更新时的实测结果。确认图片、无障碍重连和文档内容后，应把这些未提交修改一起纳入新的提交。是否推送本地领先提交，应由项目负责人明确决定；不要把“本地已提交”和“远端已同步”混为一谈。

## 4. 核心文件与职责

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 纯关键点识别和手势状态机；不应直接执行 Android 系统动作 |
| `GestureArchitecture.kt` | `GestureCode`、映射、FeatureGate、ActionExecutor 和冷却策略声明 |
| `GlobalCooldownManager.kt` | 成功动作后的 2000ms 全局离散动作锁 |
| `HandPipeline.kt` | MediaPipe 初始化、YUV 转换、旋转镜像、关键点输入和自拍帧提取 |
| `CameraProbeService.kt` | 前台摄像头生命周期、事件调度、动作成功反馈、自拍保存、媒体控制 |
| `ControlAccessibilityService.kt` | 悬浮光标、点击/滑动、返回/桌面/最近任务、截图等 Android 边界；维护连接、解绑和重绑定状态 |
| `GesturePreferences.kt` | 本机手势开关和灵敏度配置 |
| `MainActivity.kt` | 首次权限引导、运行入口、手势卡片和即时开关 |
| `CalibrationActivity.kt` | 灵敏度、方向、反馈和手势独立开关 |
| `OverlayIndicator.kt` | 悬浮状态球、动作反馈和冷却环 |
| `docs/MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md` | 当前 V1.0 产品和开发规格 |

## 5. 当前事件执行链路

```text
前置摄像头 ImageReader
-> HandPipeline / MediaPipe Hand Landmarker
-> GestureEngine 状态机
-> GestureEvent
-> GestureMappingManager
-> GestureFeatureGate
-> GlobalCooldownManager
-> GestureActionExecutor
-> Android 动作或内部自拍
-> 成功回调
-> 2000ms 全局动作冷却
```

重要规则：

1. 全局冷却只在动作成功后开始。
2. 冷却期间禁止其他离散动作，但 G01 光标和识别反馈继续工作。
3. 静态/HOLD 手势触发后必须解除姿势，才能再次触发。
4. 动态手势使用 `WAIT_RELEASE`，回程不能触发相反动作。
5. 识别保持时间、本地 Repeat Guard 和全局动作冷却是三个不同概念，不要重新混在一起。

## 6. 手势实现状态

| Code | 当前手势 | 当前动作 | 状态 |
|---|---|---|---|
| G01 | 单食指 | 移动光标 | 代码完成 |
| G02 | 食指弯曲后伸直 | 点击光标位置 | 代码完成 |
| G03/G04 | 水平食指上挑/下挑 | 上下滑动 | 代码完成 |
| G05/G06 | 四指并拢上挥/下挥 | 上下滑动 | 代码完成 |
| G07/G08 | 四指并拢左挥/右挥 | 返回/桌面 | 代码完成 |
| G09/G10 | 竖直食指左移/右移 | 返回/桌面 | 代码完成 |
| G11 | V 字保持 | 3 秒倒计时自拍 | 代码完成，需真机验收 |
| G12 | 手指比心 | 双击点赞 | 代码完成 |
| G13 | 张掌→握拳→张掌 | 系统截图 | 代码完成 |
| G14 | 莲花指 | 最近任务 | 初版完成，需真机校准 |
| G15 | 兰花指 | 返回 | 初版完成，需真机校准 |
| G16 | 单指保持 3 秒 | 打开指定 App | 未开发 |
| G17 | 双指保持 3 秒 | 打开指定 App | 未开发 |
| G18/G19 | 食指顺/逆时针画圈 | 音量增减 | 未开发 |
| G20 | 大拇指 | 双击点赞 | 初版完成，需真机校准 |
| G21 | OK | 点击当前光标 | 初版完成，需真机校准 |
| G22 | 握拳 | 媒体播放/暂停 | 初版完成，需真机校准 |
| G23 | 张掌静止 | 暂停识别 | 未开发 |

G14/G15 当前采用的暂定定义：

- 莲花指：拇指与无名指相触，食指、中指、小指伸展。
- 兰花指：拇指与中指相触，食指、无名指、小指伸展。

这两种传统指型存在个体差异，当前阈值只是一套可执行初版，不能直接当作最终识别标准。

手势图片资源已经按以下定义归位：

| 文件 | 正确含义 |
|---|---|
| `gesture_lotus.png` | 莲花指：大拇指与无名指相触 |
| `gesture_orchid.png` | 兰花指：大拇指与中指相触 |
| `gesture_thumbs_up.png` | 竖起大拇指，其余四指收拢 |

三张图片均为 `1254 × 1254` 的透明 PNG，并已在 `MainActivity` 对应手势卡片中引用。以后不要仅交换界面标题；图片文件名、界面名称、识别定义和动作映射必须同步核对。

## 7. G11 自拍实现说明

不能依赖打开第三方相机并点击快门，因为本 App 的前台服务本身持续占用前置摄像头，外部相机通常无法同时打开。

当前实现为：

1. V 字稳定保持 2 秒。
2. 显示 3、2、1 倒计时。
3. 倒计时期间阻止其他离散手势，但光标继续工作。
4. `HandPipeline` 复制下一帧已旋转、镜像的前置画面。
5. 保存 JPEG 到系统相册；Android 10+ 使用 `Pictures/MagicGesture`。
6. 保存成功后才显示成功并进入全局冷却。

需要真机验证：前置镜像方向、传感器旋转、照片裁切、画质、Android 8/9 MediaStore 行为、相册可见性和连续触发。

## 8. 权限和隐私边界

当前权限：

- CAMERA
- FOREGROUND_SERVICE / FOREGROUND_SERVICE_CAMERA
- POST_NOTIFICATIONS
- SYSTEM_ALERT_WINDOW
- AccessibilityService

无障碍配置明确为：

```xml
android:canRetrieveWindowContent="false"
android:accessibilityEventTypes="typeWindowStateChanged"
android:canPerformGestures="true"
```

不要重新引入页面节点扫描、读取页面文字、聊天内容或密码的逻辑。点赞目前是固定屏幕区域双击，不依赖节点文本查找。

### 8.1 无障碍授权持久化与重连

Android 无障碍授权正常情况下由系统持久保存，普通划掉后台或进程被系统回收后不应要求再次授权。当前代码已完成以下补强：

- `MainActivity.isAccessibilityServiceEnabled()` 优先使用 `AccessibilityManager.getEnabledAccessibilityServiceList()` 判断授权，并保留 `Settings.Secure` 兼容回退。
- 从系统设置返回时最多等待约 1.5 秒，让系统完成授权状态同步，避免瞬时误判为“未开启”。
- `ControlAccessibilityService` 在 `onUnbind()` 清理活动实例和悬浮光标，在 `onRebind()` 恢复活动实例。
- 界面文案区分“已有授权、等待系统连接”和“授权确实未开启”。

安全边界：App 不能绕过 Android 安全机制自行开启无障碍。用户主动“强行停止”、重新安装、清除数据，或手机安全管家关闭服务后，仍可能需要手动重新授权。

## 9. Release 签名安全

仓库存在：

```text
magic-gesture-release.jks
keystore.properties
keystore.properties.example
```

`keystore.properties` 包含真实签名口令的可能性很高，不要复制到聊天、日志、提交信息或公开仓库。新 IDE 中只需确认本地路径有效，不要重新生成或替换现有正式密钥，除非产品负责人明确要求。

提交前必须检查 `.gitignore` 是否覆盖真实签名配置，并确认没有新增口令、Token、私钥或绝对用户目录。

## 10. 真机验收清单

当前已连接并安装最新 Debug APK 的设备：

| 项目 | 当前值 |
|---|---|
| 厂商 / 型号 | HONOR ALP-AN00 |
| Android / API | Android 14 / API 34 |
| ADB 序列号 | `AXYP6R4A30002818` |
| ADB 路径 | `D:\Android\Sdk\platform-tools\adb.exe` |
| 已安装包 | `com.magicgesture.app` |
| 已安装版本 | `0.9.0` / versionCode `9` |
| 安装方式 | `adb install -r`，覆盖安装成功并保留数据 |

该荣耀设备对 ADB Shell 和 USB 安装有手机端确认流程。安装命令长时间无输出时，保持手机解锁，并确认“允许 USB 调试”“通过 USB 安装”或“继续安装”弹窗。

### 10.1 无障碍授权持久化实测记录（2026-10-01）

在 HONOR ALP-AN00（Android 14 / API 34）上通过 ADB 完成强杀对照实验，证据链如下：

| 步骤 | 命令 | 系统状态 |
|---|---|---|
| 手动授权后 | `settings get secure enabled_accessibility_services` | 返回本服务组件，`accessibility_enabled=1` |
| 模拟强行停止 | `am force-stop com.magicgesture.app` | 授权被系统整体清空，返回 `null`，`accessibility_enabled=0`，App 进入 stopped 状态 |
| 重新启动 App（已确认 MainActivity 在前台） | 同上 | 授权**未自动恢复**，仍为 `null`，必须手动重新授权 |

结论：

1. 荣耀 MagicOS 在 App 被强行停止（含等效的强杀路径）时会主动撤销无障碍授权，重新打开 App 不会自动恢复。
2. 这是系统安全机制，App 无法也不应绕过，与 §8.1 的预判边界一致；`isAccessibilityServiceEnabled()` 检测逻辑本身工作正常，属于"系统真的撤销了授权"，不是误判。
3. **对照实验已完成（2026-10-01）**：手动重新授权确认基线后，仅"从最近任务上划划掉"并重新打开 App，`enabled_accessibility_services` 同样变为 `null`、`accessibility_enabled=0`。即荣耀 MagicOS 把上划划掉也当作强杀处理，无障碍授权直接被清空——这偏离标准 Android 行为（标准行为中无障碍服务由系统绑定，划掉 App 不撤销授权），属于 iAware 后台管理激进策略。
4. **缓解方案已验证有效（2026-10-01）**：在 设置 → 应用 → 魔法手势 → 电池 中关闭"自动管理"改为"手动管理"，并允许自启动、关联启动和后台活动后，重复"上划划掉 → 重开 App"实验，`enabled_accessibility_services` 保持包含本服务、`accessibility_enabled=1`，授权不再丢失。结论：荣耀机型需要引导用户配置启动管理白名单，该步骤应写入首次设置向导的机型适配文案（至少覆盖 HONOR MagicOS；华为 HarmonyOS 预计同理，待验证）。
5. 本实验过程会清掉测试机上的无障碍授权，重测前需手动重新开启（可顺带验证重授权引导流程）。

至少准备 Android 12、Android 14、Android 15/16 真机，分别验证：

- 首次相机、悬浮窗、通知、无障碍授权流程。
- 开启一次无障碍后，分别测试划掉最近任务、结束 App 进程、锁屏再解锁、重新进入 App；确认设置页不再重复出现，并区分普通杀进程与系统"强行停止"（参见 10.1：荣耀设备强行停止会撤销授权，属预期系统行为）。
- 重进 App 后观察“无障碍授权已保留，正在等待系统连接服务”是否能自动过渡为可用状态，再实际执行一次点击或滑动。
- 切到其他 App 后持续识别 10 分钟。
- 锁屏/解锁、来电、视频通话或其他 App 抢占相机后的恢复。
- 停止服务后摄像头指示、模型、悬浮球和光标全部消失。
- 每个手势独立关闭后确实不参与识别。
- 冷却期间光标继续移动，其他离散动作不执行。
- 静态手势持续保持不会跨冷却期重复触发。
- G11 自拍保存、镜像、旋转和相册路径。
- G14/G15/G20/G21/G22 的识别率与互斥误触。
- G21 没有有效光标时不点击、不进入冷却。
- G22 在主流音乐/视频 App 中播放与暂停是否都生效。

建议为每个新静态手势采集不同光线、距离、左右手、肤色和背景下的关键点日志，再调阈值。不要只靠单台设备肉眼试一次。

## 11. 下一阶段建议

优先顺序（2026-10-01 当日进展更新）：

1. [已完成 2026-10-01] 在 HONOR ALP-AN00 上完成无障碍授权持久化闭环验收，实测结论与缓解方案见 §10.1；并新增按品牌显示的“保持授权不丢失”独立引导页（`KeepAuthorizationActivity`），荣耀/华为方案已收录，小米/OPPO/vivo 方案待补充。
2. [已完成 2026-10-01] 真机手势基线验收（G11、G14/G15、G20-G22）由负责人确认完成。同日对 G11 自拍做了体验优化：保存后右下角缩略图预览（约 3.2 秒淡出）、屏幕中央大数字倒计时（3-2-1 严格每秒一跳，主线程调度）、每秒提示音与快门声、倒计时期间冻结全部手势（含光标），手势触发后即可放下。
3. [进行中] 为 `GestureEngine` 增加可回放关键点样本测试，避免只测试映射层。
4. 实现 G16/G17：应用选择器、包名持久化、3 秒保持状态和安全启动失败反馈。
5. 实现 G18/G19：连续控制会话、角度累计、150～300ms 步进限速、会话结束后冷却。
6. 实现 G23：暂停/恢复必须有明确且可恢复的交互，避免用户只能强制停止服务。
7. 完成隐私政策、Data Safety、AccessibilityService 声明和 Release AAB 验收。

已确认但暂缓开发：将 G21 从“确认当前光标”升级为“按前台 App 执行收藏”。目标支持多 App 独立包名、横竖屏坐标和校准配置；在收藏管理与校准页面完成前，保留现有 G21 行为，不使用固定通用坐标冒充收藏成功。

不建议下一步立即继续堆手势。当前最有价值的工作是拿真机数据校准已经实现的五个新能力，并补识别层回归测试。

## 12. 新 IDE 接手后的第一小时

1. 完整复制当前目录，并确认未跟踪的本交接文档也被带到新 IDE。
2. 使用 JDK 17 打开工程并等待 Gradle Sync。
3. 检查 `hand_landmarker.task` 存在且不是 0 字节。
4. 运行 `testDebugUnitTest` 和 `assembleDebug`。
5. 对照本文件检查 `git status -sb` 和最近提交，确认本地领先的两个提交及交接文档没有丢失。
6. 若继续使用当前荣耀手机，可直接覆盖安装最新 APK；安装后先验证无障碍一次授权与进程重连。
7. 只开启一个新手势做隔离测试。
8. 记录机型、系统、光线、距离、左右手、识别成功率和误触手势。
9. 真机基线稳定后再开始 G16/G17，不要同时修改所有阈值。

## 13. 相关文档

- `docs/MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`
- `README.md`
- `RELEASE_CHANGES.md`
- `keystore.properties.example`
