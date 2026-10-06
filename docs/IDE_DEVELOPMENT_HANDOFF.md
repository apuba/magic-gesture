# MagicGesture IDE 开发交接文档

> 本文档取代 `IDE_DEVELOPMENT_HANDOFF_2026-10-01.md` 与 `IDE_DEVELOPMENT_HANDOFF_2026-10-01_PM.md`（均已删除）。
> 需求基线见 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`；手势↔动作完整映射表见 `GESTURE_ACTION_MAPPING_CHECKLIST.md`（**无日期命名**；今后任何手势/动作绑定变更或新增，必须同步更新该文档——这是产品负责人的既定规则）。
> 所有 AI/开发者必须先读仓库根目录 `AGENTS.md`；当前正式版签到解锁需求见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`，二期账号、付费和分享须等 App 真实上线后重新规划。
> 已确认的现有功能行为变更统一记录在 `REQUIREMENTS_CHANGELOG.md`；多人多手场景采用"视觉最近手独占控制权"。
> 接手前请通读第 2、6、7、9 节。

## 0. 当前交接摘要（2026-10-05）

### 0.1 环境与 Git

- 工程路径：`E:\2026\MagicGesture-v0.9`；Windows / PowerShell；JDK 17、Android SDK 36、Gradle Wrapper 9.3.0。
- 当前分支：`main`；HEAD：`e5d614e → dfd35f4 → 4a08b93 → 本次文档提交`，详见 §0.4。
- 远程：`origin = https://gitee.com/3603317/magic-gesture.git`，`github = https://github.com/apuba/magic-gesture.git`。
- 本轮 4 个提交已完成本地提交；相对 `origin/main` 领先但未推送。不得把“继续”“下一步”“提交一下”理解为远程同步授权，push 必须由产品负责人明确要求。

### 0.2 本轮需求与产品边界

- 重写 README，使其只描述当前正式产品，不再保留早期技术验证和分阶段接入表述。
- 生成与当前 V0.9 行为一致的公开隐私政策 HTML，公开主体统一写为“魔法手势开发者（个人开发者）”，隐私联系邮箱为 `3603317@qq.com`，公开文件不出现法定实名。
- 正式隐私政策地址：`https://magicgesture.mt4000.com/privacy-policy.html`。用户已提供浏览器截图，确认 HTTPS 页面可正常访问；此前命令行环境无法访问属于执行环境网络路径限制，不能据此判定公网不可用。
- App 首页全部内容最下方提供“隐私政策与权限说明”文字链接，不在“手势练习与校准”区域显示为按钮；正文随 APK 打包，无网络也能查看。2026-10-06 已移除应用内重复的“查看在线版本”按钮，公开 HTTPS 页面只供应用商店后台和未安装 App 的用户访问。
- 当前仍严格限定 G01–G35；G03/G04/G09/G10 继续默认关闭且未绑定，只为真实上线后二期轨迹规划保留。本轮没有新增手势、账号、支付、分享、埋点或联网业务。
- 本轮追加 ABI 专项：为满足 Google Play 64 位配对要求移除 32 位 `x86` 原生库，只调整打包规则，没有改 Manifest、依赖、手势与功能（详见 §0.5.1）。
- 2026-10-06 根据真机误判反馈收紧 G35：普通竖起并拢双指不得触发；拇指必须明显侧伸、与食指保持 45°–90°夹角、远离食指 MCP/PIP，并满足右手向右、左手向左的方向要求。手别从 `HandPipeline` 经 `ActiveHandSelector` 传入 `GestureEngine`，方向相反或手别缺失时失败关闭。编号、保持 1 秒、默认动作和签到包均不变。
- 2026-10-06 修复签到后“首次使用设置”重新出现：签到触发首页原地重建后现在会立即刷新悬浮窗与无障碍授权状态，已完成两项授权时设置区域保持隐藏。
- 2026-10-06 新增首次启动隐私政策同意门：首次打开 App 先弹隐私政策弹窗（政策要点摘要 + “查看完整《隐私政策》”入口），未同意前不构建首页、不申请通知权限、不开启摄像头；同意记录带版本号写入本机，只需同意一次，政策重大变更提升版本号后重新征求同意；`CameraProbeService` 入口增加兜底，未同意不得开启相机。本轮没有新增手势、账号、支付、分享、埋点或联网业务。

### 0.3 本轮代码和文档入口

- `MainActivity.kt`：首页全部内容最下方的“隐私政策与权限说明”文字链接入口。
- `PrivacyConsent.kt`：隐私政策同意状态与政策版本号的本机读写（同意一次即长期有效，版本号变更后重新征求）。
- `MainActivity.kt`：首页构建前的隐私政策同意弹窗；未同意时不构建首页、不申请通知权限，点击“不同意，退出应用”时 `finishAffinity()`。
- `CameraProbeService.kt`：`onStartCommand` 入口同意校验兜底，未同意直接 `stopSelf()`。
- `PrivacyPolicyActivity.kt`：原生离线政策页面与返回按钮，不提供在线版本跳转。
- `res/raw/privacy_policy.txt`：随 APK 打包的离线政策正文。
- `AndroidManifest.xml`：注册未导出的 `PrivacyPolicyActivity`；继续通过 Manifest 合并规则移除第三方依赖带入的 `INTERNET` 与 `ACCESS_NETWORK_STATE`。
- `docs/privacy-policy.html`：可部署的公开 HTTPS 网页源文件。
- `docs/PRIVACY_POLICY.md`：政策 Markdown 版本；`docs/APP_STORE_REVIEW_GUIDE.md`：权限、Data Safety、Accessibility API 与审核证据清单。
- `README.md`、`RELEASE_CHANGES.md`：当前产品介绍和本轮发布说明。

### 0.4 本轮提交拆分（2026-10-05 本地提交完成）

本轮工作已按功能拆分为 4 个本地提交，**全部未推送**：

| 提交 | 内容 |
|---|---|
| `e5d614e` | App 内隐私政策入口与首页轨迹预留卡片收敛：`PrivacyPolicyActivity.kt`、`res/raw/privacy_policy.txt`、Manifest 注册、`MainActivity.kt` 入口链接；同时移除首页 G03/G04/G09/G10 轨迹预留卡片 |
| `dfd35f4` | 隐私政策、上架审核指南与 README 重写：`docs/PRIVACY_POLICY.md`、`docs/privacy-policy.html`、`docs/APP_STORE_REVIEW_GUIDE.md`、`README.md` |
| `4a08b93` | 移除 32 位 `x86` 原生库以满足 Google Play 64 位要求：`app/build.gradle.kts` |
| 本文件所在提交 | 交接文档与发布说明：`docs/IDE_DEVELOPMENT_HANDOFF.md`、`RELEASE_CHANGES.md` |

提交前已逐文件审查完整差异，未夹带签名文件、口令、构建产物或其他无关文件。

### 0.5 已执行验证

- `testDebugUnitTest assembleDebug assembleRelease bundleRelease lintRelease --rerun-tasks`：成功，124/124 JVM 测试通过。
- 入口改为首页最下方文字链接后再次执行 `testDebugUnitTest assembleDebug`：成功，124/124 JVM 测试通过；`git diff --check` 通过，仅有 Windows LF→CRLF 提示。
- Release APK 最终权限：仅 `CAMERA`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_CAMERA`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`；不含 `INTERNET` 或 `ACCESS_NETWORK_STATE`。
- Release APK：v2 签名验证通过；16KB 页面兼容对齐检查通过。
- `git diff --check`：通过，仅有 Windows LF→CRLF 提示。
- 已核对 Debug/Release Manifest 包含未导出的 `PrivacyPolicyActivity`，两种构建均包含 `privacy_policy` 原始资源。
- **产品负责人真机验收通过（2026-10-05）**：隐私政策入口与页面目视项（首页全部内容最下方文字链接位置与显示、进入政策页面、正文完整滚动、返回和断网查看）。当时验收过的在线跳转已于 2026-10-06 按产品要求移除。
- **产品负责人真机验收通过（2026-10-05）**：无网络权限状态下的 MediaPipe 识别、自拍、截图、前台服务与相机恢复回归，未见因移除 `INTERNET`/`ACCESS_NETWORK_STATE` 产生的机型问题。
- ABI 专项（2026-10-05）：`testDebugUnitTest assembleRelease bundleRelease` 均成功，124/124 JVM 测试通过；移除 x86 后 Release APK/AAB 与 Debug APK 的原生库仅剩 `arm64-v8a` 与 `armeabi-v7a`。Release APK 正式签名有效（`CN=Magic Gesture`），`zipalign -c -P 16 4` 16KB 对齐检查通过。
- **卸载重装后的 ABI 冒烟已通过（2026-10-05，荣耀 ALP-AN00 / Android 14）**：产品负责人卸载旧包后，由本轮重新安装最新 Debug APK 并恢复相机、悬浮窗、通知与无障碍授权；`CameraProbeService` 前台服务运行中，`dumpsys media.camera` 显示 device 1 由本应用以 `ACTIVE` 状态占用（说明 `libmediapipe_tasks_vision_jni.so` 在 arm64 上加载成功、模型初始化成功），悬浮圆点窗口已显示，logcat 无 `FATAL` / `ANR` / `UnsatisfiedLinkError`。至此可以确认移除 32 位 `x86` 不影响 arm64 真机运行。
- **重装后手势功能真机验收通过（2026-10-05，产品负责人确认）**：启动手势控制、识别管线、光标跟随指尖、G02 指尖轻点、G22 握拳保持 1 秒播放/暂停、G23 小指静音/恢复均正常。
- 签到链路已在重装后的 Debug 包上验证：点击“今日签到”会弹出 Toast“签到成功，已解锁：XXX”（产品负责人已确认看到提示），`adb shell run-as com.magicgesture.app cat shared_prefs/gesture_settings.xml` 显示 `unlock_checkin_count=1`、`unlock_last_day=20731`。界面上手势卡片仍全部显示已解锁，是 Debug 构建全开权益的预期表现，不是签到失败。
- G35 角度与左右手方向修复后执行针对性 `GestureEngineReplayTest`、`ActiveHandSelectorTest` 及完整 `testDebugUnitTest assembleDebug`：成功，126/126 JVM 测试通过；新增贴掌拇指、大于 90°、左右手方向相反及手别缺失不得触发 G35 的回放覆盖。
- **G35 真机验收通过（2026-10-06，产品负责人确认）**：荣耀 ALP-AN00 最新 Debug 包中，右手拇指向右、左手拇指向左可以正常触发；方向相反以及普通竖起双指不再误判为 G35。
- 签到后授权引导回显修复执行完整 `testDebugUnitTest assembleDebug`：成功，126/126 JVM 测试通过；最新 Debug APK 已覆盖安装。由于设备当天已签到，尚待下一次可签到时确认两项授权均完成后设置区域保持隐藏。
- 移除应用内“查看在线版本”按钮后再次执行 `testDebugUnitTest assembleDebug`：成功，126/126 JVM 测试通过；按钮、系统浏览器跳转代码及离线正文中的在线地址均已移除，公开 HTML 和 HTTPS 地址继续保留供商店后台使用。
- 首次启动隐私政策同意门（2026-10-06）执行 `testDebugUnitTest assembleDebug`：成功，126/126 JVM 测试通过；`git diff --check` 通过。真机验收已于 2026-10-06 在荣耀 ALP-AN00（Android 14）完成。

### 0.5.1 ABI 架构决策（2026-10-05）

- **结论**：`MediaPipe tasks-vision 0.10.21` 只提供 `arm64-v8a`、`armeabi-v7a`、`x86` 三套原生库，**依赖本身没有 `x86_64`**，因此无法通过“补齐 x86_64”满足 Google Play 规则，改为丢弃 32 位 `x86`。
- **改动位置**：`app/build.gradle.kts` 的 `android { packaging { jniLibs { excludes += setOf("**/x86/**") } } }`，对所有构建变体生效。
- **Google Play 规则满足情况**：每个上架的 32 位 ABI 都必须有对应 64 位版本；`armeabi-v7a` 由 `arm64-v8a` 配对，`x86` 已被移除，不再要求 `x86_64`。
- **收益**：Release APK 由 70,607,192 字节降至 49,313,418 字节；Release AAB 由 45,882,341 字节降至 37,327,422 字节。
- **代价与后续观察**：32 位 x86 设备与 x86 模拟器镜像将无法安装。若后续需要用模拟器验证，必须使用 `arm64-v8a` 系统镜像。
- **保留 `armeabi-v7a` 的原因**：仍有一定量的老旧 32 位 ARM 设备，保留它不影响 64 位合规，只是体积代价。若后续决定极致瘦身，可再评估是否只保留 `arm64-v8a`。
- 本轮未改 Manifest 与依赖，Release APK 权限集合保持不变（相机、前台服务、通知、悬浮窗）。

### 0.6 产物

> Debug 为 2026-10-06 G35 角度修复后的最新产物；Release APK/AAB 仍是 2026-10-05 的上架候选产物，不包含本次 G35 修复，后续发布前必须重建。

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，51,501,461 字节，SHA-256 `C5B3D40E8BEE86FD60D7C7BDA4924D4D198024589233E87DF2A1D270D68028DE`；2026-10-06 已覆盖安装到荣耀 ALP-AN00，包含 G35 真机通过版本、签到页面修复及隐私页面在线按钮移除。
- Release APK：`app/build/outputs/apk/release/app-release.apk`，49,313,418 字节，SHA-256 `BDAFB9C1876BDB730F09BC4061D29EBF991D434D8AC2F0F2EAF22E1C5433915E`，正式 Release 签名（`CN=Magic Gesture`），16KB 对齐通过。
- Release AAB：`app/build/outputs/bundle/release/app-release.aab`，37,327,720 字节，SHA-256 `8E23A279964C694F78606E4A38647FE170A247069F65E22FB048B65DE9E3CCF8`，正式 Release 签名。
- 三个产物均只含 `arm64-v8a` 与 `armeabi-v7a`；Release 与 AAB 尚未安装到真机，本轮未提交应用市场。
- 已失效的旧产物，不要再引用：Debug `668ADC6D…`（72,794,792 字节，含 x86）、Debug `CBC2AF7E…`（72,794,706 字节）、Release `A39BA70F…`（70,607,192 字节，含 x86）、Release `26A1393F…`（49,313,418 字节，签到改造前）、AAB `D2443784…`（45,882,341 字节，含 x86）、AAB `9B85B202…`（37,327,422 字节，签到改造前）。
- 注意：Debug APK 体积从 72,794,706 字节降到 51,271,022 字节，与 Release 减少幅度一致，说明此前那次 Debug 打包并未真正应用 `x86` 排除规则；以当前产物为准。

### 0.7 尚未完成与风险

- ~~**首次启动隐私政策同意门待真机验收（2026-10-06 新增）**~~ **已于 2026-10-06 通过**：荣耀 ALP-AN00（Android 14）冷启动弹窗宽度/圆角/文字正常；点击“查看完整《隐私政策》”进入离线正文、返回后弹窗仍在；点击“同意并继续”进入首页并随后才弹出系统通知权限请求；点击“不同意，退出应用”显示 Toast 后直接退出、未开启摄像头；同意一次后 force-stop 再冷启动不再弹窗；覆盖安装升级后同意记录仍有效。
- ~~隐私政策入口与页面的 8 项真机目视验收~~ **已于 2026-10-05 由产品负责人真机确认通过**。
- ~~无网络权限新包回归启动控制、MediaPipe 识别、自拍、截图、前台服务和相机恢复~~ **已于 2026-10-05 由产品负责人真机确认通过**。
- ~~ABI 专项后真机冒烟~~ **已于 2026-10-05 通过**：卸载重装最新 Debug APK 后，MediaPipe 原生库在 arm64 加载成功、相机以 ACTIVE 状态被占用、悬浮圆点显示、无崩溃与 ANR；光标跟随、G02、G22、G23 等手势功能也已确认正常。详见 §0.5。
- **签到反馈改造（代码已完成，待真机验收）**：此前 Debug 权益全开时签到成功只有 Toast，且 `performCheckIn()` 的 `recreate()` 会把首页滚动位置重置到顶部，签到卡片位于页面中段导致计数变化被错过。现改为 `rebuildContent(preserveScroll = true)` 原地重建并恢复滚动位置，`onResume` 检测到权益变化时同样原地重建。**尚未装机验证**：需确认滚动位置真的保留、签到计数与开关状态立即更新、控制按钮状态不回退。
- **签到后授权引导回显修复待真机确认**：原地重建后已补 `refreshSetupGuide()`；需在悬浮窗和无障碍均已授权的状态下完成下一次签到，确认“首次使用设置”的两个选项不再出现。
- **正式 Release 包上的签到体验仍待单独确认**：本轮重装的是 Debug 包；正式包只有 7 个基础编号且无全开权益，虽然 2026-10-04 已在 Release 包完成 12 次签到验收（见 `RELEASE_CHANGES.md`），但当前 ABI 产物对应的 Release 包尚未装机复验。
- ~~G35 左右手方向与普通双指误触复验~~ **已于 2026-10-06 通过**：右手向右、左手向左可触发，反向与普通双指不触发。不同距离、弱光和大角度侧转仍属于后续兼容性抽验项。
- **Release APK/AAB 尚未安装**：两个正式产物都没有装机；正式 Release 包只有 7 个基础编号，替换安装会改变当前测试环境，装机前先确认是否接受卸载重装。
- 公开主体没有显示个人法定姓名。Google Play 方向可使用公开开发者名称和隐私联系渠道；国内市场若要求公开个人信息处理者实名，须按目标市场审核反馈处理。
- 继续逐家确认 Google Play、华为、小米、OPPO、vivo 对无障碍、后台前置摄像头和悬浮窗的准入与材料要求。
- ~~ABI 与 Google Play 64 位配对要求~~ **已处理**：`x86` 已移除，产物仅含 `arm64-v8a` 与 `armeabi-v7a`；详见 §0.5.1。保留 `armeabi-v7a` 是决策取舍而非疏漏，若以后决定只上 64 位 ARM 另需产品确认。
- ~~商店截图与应用图标~~ **已于 2026-10-06 全部产出**：7 张 1080×1920 截图（含自拍倒计时悬浮反馈）、4 个尺寸图标、Google Play 特色图 1024×500 正式稿已就位，清单见 `docs/APP_STORE_REVIEW_GUIDE.md` §7.1；素材目录在工作区之外 `E:\2026\MagicGesture-store-screens\`，不随 Git 提交。截图仍须按隐私要求逐张复核后才能上传。**审核演示视频已录制并复核通过，决定按原片直接上传**（82 秒、578×1280、音轨静音，内容覆盖同意弹窗拒绝路径、披露、系统授权、跨 App 操作与停止收尾）。中文特色图 master（霓虹风格）已更新并重新导出 1024×500；英文版横幅仅留作以后海外渠道使用。**适龄分级、各市场权限声明尚未完成**；国内渠道横幅尺寸未核对。

### 0.8 建议接手顺序

1. 重新连接真机后安装最新 Debug APK，验收签到反馈改造：滚动位置保留、计数与开关状态立即更新、控制按钮状态不回退；随后安装 Release APK 复验正式包的 7 个基础编号与签到解锁。
2. 准备各市场权限声明、适龄分级和审核演示视频，并确认 Google Play、华为、小米、OPPO、vivo 的准入要求；截图与图标已产出但需逐张隐私复核后再上传，国内渠道横幅尺寸需按各家后台现行规范核对后从 master 导出。
3. 全部材料确认后，再决定是否安装 Release APK/AAB 做最终装机回归（正式包只有 7 个基础编号，替换需卸载重装），并提交应用市场。
4. 之后回到剩余高风险真机项：多人多手、G29–G33 媒体控制、G21 收藏标定、G35 图片与算法姿势差异。
5. 本轮 4 个提交仍在本地；未获产品负责人明确要求前不要 push。

## 1. 项目概况

- **工程**：`e:/2026/MagicGesture-v0.9`，单模块 Android 应用（Kotlin，无 Compose，自绘 View）
- **身份**：`applicationId` / `namespace` = `com.magicgesture.app`；`versionName 0.9.0` / `versionCode 9`
- **SDK**：minSdk 26，target/compileSdk 36；依赖 MediaPipe Tasks Vision 0.10.21（手部关键点，模型 `app/src/main/assets/hand_landmarker.task`）
- **源码**：`app/src/main/java/com/magicgesture/app/` 单层包结构；本轮新增 `PrivacyPolicyActivity.kt`
- **测试**：当前 124 个 JVM 用例全绿（含最近手选择、G24/PIP 与比心互斥、G35、自拍 EXIF、抓取拖动独占、小指释放防重复等回放）

### 1.1 G01–G35 最新统一名称（2026-10-03 产品确认）

| 编号 | 最新名称 | 编号 | 最新名称 | 编号 | 最新名称 |
|---|---|---|---|---|---|
| G01 | 指尖移动 | G13 | 开合掌 | G25 | 单指枪·竖向 |
| G02 | 指尖轻点 | G14 | 莲花指 | G26 | 抓取手势 |
| G03 | 轨迹预留一 | G15 | 兰花指 | G27 | C 手势 |
| G04 | 轨迹预留二 | G16 | 张掌变一指 | G28 | Love 手势 |
| G05 | 并掌上挥 | G17 | 张掌变二指 | G29 | 双指左挥 |
| G06 | 并掌下挥 | G18 | 张掌变三指 | G30 | 双指右挥 |
| G07 | 并掌左挥 | G19 | 张掌变四指 | G31 | 双指上拉 |
| G08 | 并掌右挥 | G20 | 拇指赞 | G32 | 双指下拉 |
| G09 | 轨迹预留三 | G21 | OK 手势 | G33 | 双指双点 |
| G10 | 轨迹预留四 | G22 | 握拳 | G34 | 六六顺手势 |
| G11 | V 手势 | G23 | 小指手势 | G35 | 双指枪·竖向 |
| G12 | 指尖比心 | G24 | 单指枪·横向 |  |  |

- 代码中的 `GESTURE_DISPLAY_NAMES`、首页指南、校准页功能开关和映射清单必须使用本表，不得继续使用旧名称。
- G35 首页指南已改用独立透明图片 `gesture_two_finger_gun.png`，不再复用 `gesture_two_fingers_together.png`。
- **待产品确认的图形/算法差异**：新 G35 图片按"食指与中指水平向左、拇指竖起、其余二指收拢"生成；当前识别算法仍判定"两指并拢向上、拇指侧伸、保持 1 秒"。本轮只获准更新名称与指南图片，没有修改 G35 识别姿势，后续不得擅自把图片描述当作算法变更授权。

## 2. 核心架构

```text
HandPipeline（MediaPipe 20fps，最多两只手）
  → ActiveHandSelector      掌部尺度判断视觉最近手、身份锁定与接管
  → GestureEngine.kt        姿势/状态机检测，产出 GestureEvent（sealed，含 Feedback）
  → GestureMappingManager   手势码→动作：默认映射 + 用户覆盖表（持久化于 SharedPreferences 键 mapping_Gxx）
  → GestureFeatureGate      手势开关门控（跟手势走，不跟动作走）
  → GestureActionExecutor   以动作为中心的分发（不依赖原始事件类型，换绑后任何手势可执行任何动作）
       ├─ ControlAccessibilityService  全局动作 / dispatchGesture 注入 / 语音助手意图 / 前台包名缓存
       ├─ CameraProbeService 内部动作（自拍、缩略图、悬浮反馈、媒体键与音量）
       ├─ FavoriteButtonController     收藏：前台包名 → 已存坐标 tapPixels，未存则弹出全屏标定浮层
       └─ 媒体/音量           KeyEvent 派发 + AudioManager（音量走 setStreamVolume）
GlobalCooldownManager：时长由用户配置、当前默认 1500ms，仅动作成功回调后启动；冷却期间冻结全部识别（含光标）
```

关键设计决策（勿破坏）：

1. **执行器只看映射结果动作**，换绑后才不炸（如其他手势换绑 CLICK 会降级为点击当前光标位置）。
2. **功能开关管"手势是否识别"，映射管"触发后做什么"，二者独立**（UI 已有此文案）。
3. **G01 光标固定不可换绑**；其余已有检测管线的手势均可换绑。G18/G19 画圈识别已移除，但编号已改用于张掌组合手势；`DRAG` 是普通动作、靠映射挂到手势上（2026-10-03 起回到换绑选单），只是它要坐标才能执行，而坐标目前只有爪形事件提供，所以选单里仅对 G26 显示。
4. `GestureEngine` 中部分状态机**吞帧**（提前 return 阻断后续检测器），部分**不吞帧**（旁路只发事件）。不吞帧的典型：两指并拢系列 G29-G33（该姿势同时是常见"自然手型"，吞帧会阻断其他手势的释放检测，见状态机注释与回放测试 `restPose`）。
5. **冷却期冻结识别管线**：冷却期间光标也停（注意与早期版本"光标白名单"行为不同，首页文案已同步为准确描述）。
6. **最近手独占控制权**：后方手永远不进入 `GestureEngine`；挑战手需明显且持续靠前才接管，接管时清空上一只手全部瞬态状态。完整规则见 `REQUIREMENTS_CHANGELOG.md`。

### 2026-10-02 多人多手最近手优先（代码完成、待真机验收）

- 新增 `ActiveHandSelector.kt`，MediaPipe `numHands` 从 1 调整为 2；距离代理只使用手腕 0 与掌指关节 5/9/13/17 的掌宽、掌高，不把随姿势变化的指尖范围用于前后判断。
- 无控制手时，视觉最近手稳定 200ms 后取得控制权；控制手存在时，即使它不做有效手势，后方手也完全不参与识别。
- 挑战手掌部尺度至少为当前手的 1.20 倍并持续 250ms 才接管；当前手连续丢失 300ms 后才允许画面中的最近手接管。
- 结合掌心位置、掌部尺度和 MediaPipe 左右手分类保持身份；相反左右手不会被直接当成原控制手。
- 接管时通过 `engine.stop()` / `resume()` 清除静态保持、轨迹、双击、组合、持续音量和光标平滑状态，新手不能继承上一只手已经积累的进度。
- 新增 `ActiveHandSelectorTest` 5 例，覆盖最近手独占、后方手动作无效、20%/250ms 接管、尺度波动不抖动、丢失 300ms 与重置重新获取。当前全部 88 个 JVM 测试通过，Debug APK 构建成功。
- 尚未真机验收：40/60/80/100cm、两人同框、两手交叉、不同大小手掌、左右手组合，以及双手检测对帧率、耗电和发热的影响。

### 2026-10-03 G24 单指枪·横向及其与指尖比心互斥（代码完成、待真机验收）

- G24 最新对外名称为"单指枪·横向"；默认动作仍为返回，内部事件和偏好键保留原名以兼容已有用户配置。
- 在"食指水平向左、拇指向上、其余三指收拢、保持约0.6秒、夹角45°–90°"基础上，拇指尖以食指第二关节 PIP 为位置基准，必须位于 PIP 外侧的掌心/手腕方向，不再要求越过第三关节 MCP；与食指 MCP/PIP 均保持至少约35%掌宽距离。
- G12 拇指压食指关节时不再满足手枪条件；G12 同时显式排除完整手枪姿势。新增水平食指比心不得返回、标准手枪不得点赞的双向互斥回放。
- 同步修复G12关节接触与释放条件不一致：释放现在要求拇指同时远离食指尖、PIP和MCP，不能再因"拇指压PIP但离食指尖较远"而在220ms后取消候选。
- 2026-10-03 已执行针对性 `GestureEngineReplayTest` 及完整 `testDebugUnitTest assembleDebug --rerun-tasks`，98/98测试通过并成功生成Debug APK；尚未真机验收拇指外侧阈值和左右手/镜像表现。
- 角度按拇指根部→拇指尖与食指根部→食指尖两条向量计算；小于45°或大于90°不会进入 G24 保持状态，G25 等其他手势不受影响。
- 2026-10-03 根据真机反馈将位置基准由第三关节 MCP 放宽到第二关节 PIP；新增"拇指位于 PIP 与 MCP 之间仍可识别"的回放用例，完整 `testDebugUnitTest assembleDebug` 已通过（99/99），尚待重新真机验证手感。
- 首页、校准页、映射清单、开发规格和需求变更记录已同步。
- 新增回放同时验证标准90°可触发、小于45°不触发、大于90°不触发；当前89项测试全部通过。仍需真机确认45°和90°附近的关键点抖动容忍度。

### 2026-10-03 G12 手指比心收紧（**代码已于同日回滚，以下内容仅作历史记录**）

> **2026-10-03 回滚说明**：经两轮真机反馈（第一次"完全识别不出来"，第二次"识别进度卡着不动"）确认该方向为负优化，用户要求还原到昨天以前的代码。本节涉及的 `GestureEngine.kt`、`GestureEngineReplayTest.kt`、`MainActivity.kt`、`CalibrationActivity.kt`、`GESTURE_ACTION_MAPPING_CHECKLIST.md`、`MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md` 已用 `git checkout --` 回到当时 HEAD（含这之前的提交状态），`REQUIREMENTS_CHANGELOG.md` 中"G12 手指比心识别收紧"整段及其"真机反馈修正"小节已删除（**收藏段与 G24 段保留**）。**收藏功能相关文件（`FavoriteButtonController.kt`、收藏需求文档）未被改动**。回滚前完整 diff 备份在仓库外 `e:/2026/MagicGesture-backup-20261003/g12-before-revert.patch`。回滚后 `testDebugUnitTest assembleDebug` 通过，89/89 用例绿。如需再次尝试，建议从"只调 `ratio` 阈值"或"只调释放复位时长"这类单一变量入手，不要叠加多个几何条件。

- G12 改为拇指与食指指尖交叉或紧密贴近、食指至少半伸展、中指/无名指/小指收拢；指尖距离不超过掌宽35%，两根手指末端须交叉或方向接近。
- 与 OK、握拳、竖拇指、小指、Love、666 明确互斥，普通捏合不进入比心；OK 原有500ms所有权保护继续保留。
- 保持必须累计满有效姿势600ms；最多容忍约100ms关键点抖动，抖动时间不计入保持进度，超过即从零开始。触发后指尖明显分开约200ms才允许再次识别。
- 新增普通捏合、短抖动与长中断回放测试；2026-10-03 已执行 `testDebugUnitTest assembleDebug --rerun-tasks`，92/92 测试通过并成功生成 Debug APK。仍需真机确认左右手、镜像、80cm内外、侧转和弱光下的成功率与误触率。
- 后续真机反馈"完全识别不出来"：已撤回食指半伸展硬门槛和末端二维夹角/相交门槛，指尖距离恢复至掌宽40%，允许食指自然弯曲，仅排除深度收拢；连续有效保持、100ms抖动处理、200ms释放复位及互斥仍保留。需以新 APK 重新真机验收。
- 再次真机反馈"识别进度卡着不动"：继续移除食指伸展比例门槛，并把候选抖动策略改为短抖动不暂停0.6秒计时、连续约250ms不合格才清零。这样一两帧关键点波动不会卡死，真正松开仍会取消候选。此前文档中的100ms暂停计时规则由本条取代。

### 2026-10-03 收藏标定底部按钮被遮挡（代码完成、待真机验收）

- `FavoriteButtonController` 的标定操作面板原来固定在屏幕底部，会遮挡部分 App 位于最底部的收藏按钮，同时拦截该区域触摸。
- 操作行新增"面板上移/面板下移"，可把整块操作面板切到屏幕另一端；移到顶部时隐藏顶部说明卡片，保证不重叠。切换面板不改变准星坐标、不点击底层页面，测试与保存流程不变。
- 根据后续反馈已移除操作面板中的"重置"按钮，当前仅保留"取消、面板上移/下移、测试位置"。
- 需真机验证竖屏、横屏及系统手势导航下的底部极限位置能否准确取点，四按钮在小屏/大字体下是否完整显示。

## 3. 本阶段完成工作（截至 2026-10-03）

### 2026-10-03 MediaPipe 模型异步初始化泄露修复（代码完成、待真机压力验收）

- **根因**：`HandPipeline` 在独立 `model-init` 线程中创建。若用户在模型创建完成前停止服务，`onDestroy()` 只能关闭当时已经写入 `pipeline` 的实例；初始化线程晚到后仍可能把持有 `HandLandmarker` 原生资源的新实例写回已销毁的 Service。重复启动命令还可能在 `pipeline == null` 阶段并行创建多个模型。
- **修复**：模型初始化增加单实例锁、初始化中标记和 generation guard；同一 Service 同时只允许一个模型初始化任务。模型创建完成后必须在锁内确认 Service 未停止、代次仍有效且尚无已发布实例，才能写入 `pipeline`。
- **资源释放**：`onDestroy()` 先标记停止并递增模型代次，再取出已发布的 `pipeline` 关闭；销毁后才完成的未发布实例由初始化线程立即 `close()`。过期初始化失败不再向已经销毁的 Service 投递错误 UI。
- **线程可见性**：`stopped` 与 `pipeline` 改为 `@Volatile`，避免模型线程、相机线程和主线程读取陈旧状态。
- **验证**：已执行 `testDebugUnitTest assembleDebug`，全部 JVM 测试通过且 Debug APK 构建成功。仍需真机连续执行"启动后立即停止、快速连续启停、模型加载中退出"，结合日志和内存观察确认只创建一个有效模型且停止后原生内存可以回落。

### 2026-10-02 G23 提交内容（已完成构建、尚未装机）

- **本次提交聚焦 G23 小指静音及其文档/测试**；此前打开应用、收藏、动作留空、G34 等功能已经进入本地提交历史。
- G23 调整为"仅伸出小指，其余四指收拢，保持 1 秒"，默认执行 `TOGGLE_MUTE`。保持期间只触发一次，释放后再次做手势可在媒体静音与恢复声音之间切换。
- `CameraProbeService.toggleMute()` 通过 `AudioManager.ADJUST_MUTE/ADJUST_UNMUTE` 操作 `STREAM_MUSIC`；执行后延迟 120ms 校验厂商系统的静音状态，只有确认状态发生变化才回调成功并进入全局冷却。
- 为解决"小指容易被识别为比心"，G12 比心增加中指、无名指、小指均收拢的约束；识别到小指专属姿势时主动清除比心候选状态。回放测试同时断言不得误发 `LoveLock`、`Like` 或 G34 `Six666`。
- 首页和校准页已增加 G23 卡片、独立功能开关、动态动作标签及换绑支持。新增透明图片 `app/src/main/res/drawable-nodpi/gesture_pinky.png`（1254×1254 ARGB）。
- 已同步更新 `GESTURE_ACTION_MAPPING_CHECKLIST.md`、本交接文档和 `MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md`。
- 2026-10-02 已执行 `testDebugUnitTest assembleDebug`：61/61 测试通过，Debug 构建成功；仅有既存 Android API/Gradle 弃用警告。APK 已生成但本轮尚未安装到手机，代码完成不等于真机验收。

### 2026-10-04 自拍固定适配屏幕比例（代码完成、待真机验收）

- 平时手势识别继续使用 640×480 YUV 流；自拍倒计时结束时单独请求高质量 JPEG。相机按像素面积从高到低尝试 JPEG 尺寸，选择能与识别流稳定建立会话的最高分辨率，全部失败后才回退 640×480 预览帧。
- 拍摄开始时记录物理屏幕像素比例；JPEG 摆正后按该比例居中裁剪，只裁多余边缘，不拉伸，也不缩小到屏幕分辨率。例如 3000×4000 照片适配 1080×2400 屏幕时保存约 1800×4000。
- 裁剪后的 Bitmap 以 JPEG 95 保存。若高清解码、旋转或裁剪失败，优先保存方向正确的原始高清 JPEG，不能因适配屏幕失败丢失照片。
- 新增纯 Kotlin `SelfieFraming` 和 4 项裁剪几何测试，覆盖竖屏裁左右、横屏裁上下、同比例不裁以及无效屏幕尺寸回退。
- 2026-10-04 已执行针对性 `SelfieFramingTest` + `CameraFrameOrientationTest`，随后执行完整 `testDebugUnitTest assembleDebug`：123/123 JVM 测试通过，Debug APK 构建成功；仅有既存 Camera2/Gradle 弃用警告。
- 静态拍摄不会替换日常 repeating preview；拍完后原 640×480 手势识别流继续运行。最大分辨率、最终比例、裁边构图、照片方向、前置镜像、保存内存/耗时、连续拍摄、识别恢复和失败降级仍需真机验收。

### 2026-10-02 每日签到解锁（需求已确认、代码已实现、尚未真机验收）

- 当前正式版本不设置收费用户或付费入口；所有正式用户统一从 7 个基础编号开始：G01、G02、G05、G06、G24、G11、G13。
- 用户每天主动签到，永久解锁下一个功能包；一天最多一次，断签不清零，共 12 次有效签到完成当前全部奖励。
- 所有正式 Release 用户遵循同一规则，不设置历史用户、管理员或隐藏口令全开；Debug/内部测试构建允许全部解锁，但不得把测试入口带入正式包。
- 微信登录、账号、付费全开、邀请分享、服务端和联网权益属于二期；必须等 App 真实公开上线后重新规划，当前不得开发。

**当前代码实现（2026-10-02）**：

- 新增 `GestureUnlock.kt`：`GestureUnlockPlan`（基础 7 个编号 + 12 个功能包，与需求 §6.1 顺序一致）、`GestureUnlockMachine`（纯逻辑签到状态机）、`GestureUnlockStore`（本机 SharedPreferences，键前缀 `unlock_`）、`GestureEntitlement`（拥有权判定）、`GestureFeatureConfig.restrictedTo()`（按权益收敛开关）、`GESTURE_CODES_BY_FEATURE`（开关键→编号）。
- 三层保持独立：解锁权益只决定"是否拥有"；`feature_*` 仍决定"是否识别"；`mapping_Gxx` 仍决定"执行什么动作"。用户关闭的开关不会被解锁覆盖，未解锁也不会写回开关值。
- 双重拦截：识别入口走 `GesturePreferences.effectiveFeatures()`（`HandPipeline` 初始化与运行中 `updateFeatures` 均使用），执行入口在 `CameraProbeService.executeMapped()` 与持续音量会话前判定 `entitlement.owns(code)`。
- 运行中即时生效：控制服务的偏好监听新增 `unlock_` 前缀分支，签到后立即刷新权益并下发引擎，无需重启控制。
- 日期规则：本地时区 epochDay；一天一次；断签不清零；系统时间回拨视为当日已领取，不增加也不清零；12 次后返回"已完成"。
- UI：首页状态条下方新增"每日签到解锁"卡片（进度、下一次解锁内容、签到按钮、本机保存提示）；未解锁手势的紫色标签显示"未解锁 · 第 N 次签到后开放"且识别开关不可点；校准页未解锁手势的映射按钮显示"未解锁"并禁用，功能开关附加解锁说明并禁用。
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
- 2026-10-04 复核：三档缩放固定为灵敏 `0.78`、标准 `1.00`、稳定 `1.28`，新增纯映射顺序测试防止三档退化为相同值；“显示识别反馈”改为开关即保存并通过 Service 偏好监听实时更新 `OverlayIndicator`，关闭时立即移除正在显示的反馈，开启时显示确认提示。完整 JVM 测试现为 124/124 通过；即时开关仍待真机目视验收。
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
- `ControlAccessibilityService.globalAction(RECENTS)` 成功后立即清空旧前台包名并进入"等待目标 App"状态；收到非系统覆盖层、非桌面/设置的真实窗口事件后才接受新包名，避免按旧 App 坐标误点。
- `FavoriteButtonController` 在最近任务切换尚未确认目标时重试等待；期间提示"正在确认当前应用，请稍候"。仍无法确认则明确提示未识别目标，不回退到旧包名。（重试窗口 2026-10-04 由 3 次 × 200ms 调整为 10 次 × 500ms，见下节。）
- 点击注入失败新增明确提示"收藏位置点击被系统取消，请确认页面没有被其他窗口遮挡"；无障碍断连也有独立提示。
- 2026-10-02 产品负责人反馈真机测试通过：从最近任务切换 App 后再使用 OK 收藏已能正常工作。仍需在其他机型和 PIP/悬浮播放器场景继续做发布前回归。

**后续顺序**：先安装 23:18 之后的新 APK 验证最近任务切换修复；若普通页面或 PIP 场景仍复现，再按 A 定位，命中 ② 做 B、③ 做 C，其他前台识别问题再从 D1/D2/D3 中选。

### 2026-10-04 最近任务切换后 OK 收藏再次失败修复（荣耀机型，代码完成、真机验证通过）

- **真机现象**：在抖音用手势打开最近任务 → 切换到另一个 App → 再用 C 手势（G27）打开最近任务 → 切回抖音 → 做 OK 收藏手势，提示"当前应用未定义收藏位置或点击失败"。时好时坏，且一旦失败会持续到用户切换到别的 App。
- **定位方式**：临时在 `ControlAccessibilityService` 与 `FavoriteButtonController` 加 `MG_DIAG` tag 日志，定位后已全部移除，`git diff` 无残留。注意相机 HAL 每帧日志会在几分钟内填满 logcat 环形缓冲区，必须用后台持续抓取（`adb logcat -v time -s MG_DIAG > file`）才留得住记录，事后 `logcat -d` 读不到。
- **根因**（与上一节不同：上一节是"目标 App 窗口事件不来"，本次是"事件来了又被抹掉"）：荣耀 Magic UI 把最近任务界面归属给桌面包名 `com.hihonor.android.launcher`，且在目标 App 窗口事件到达后约 0.3 秒**补发一次 launcher 事件**：

```text
21:06:02.140 recents opened -> awaiting=true
21:06:02.494 pkg=com.ss.android.ugc.aweme -> foreground awaiting=false   ← 抖音已确认
21:06:02.766 pkg=com.hihonor.android.launcher -> foreground=null         ← 0.27 秒后被清空
21:06:07.435 fav attempt fg=null                                        ← OK 手势拿到空包名
```

此时 `awaitingRecentsTarget` 已被抖音事件解除，上一节的等待保护不再生效，launcher 分支照常清空前台，因此失败是粘性的。

- **修复**（只动 `ControlAccessibilityService.kt` 与 `FavoriteButtonController.kt`）：
  - 最近任务后 3 秒内（`RECENTS_LAUNCHER_GRACE_MS`）launcher/设置事件一律不作数，保护刚恢复的目标 App 不被补发事件抹掉；等待期间同样忽略。
  - 等待目标加 5 秒超时兜底（`RECENTS_TARGET_TIMEOUT_MS`）：目标事件始终不来时自动复位，不再永久卡死。
  - 收藏重试窗口由 3 次 × 200ms 调整为 10 次 × 500ms，覆盖整个等待窗口；原来的 600ms 比一次任务切换还短。
  - 未新增权限，`canRetrieveWindowContent` 仍为 `false`。
- **验证**：日志显示三次 OK 手势全部 `fav target=com.ss.android.ugc.aweme hasProfile=true` 且 `tap success=true`，期间 launcher 事件均被忽略；产品负责人真机确认收藏动作已正常激活。测试 124/124 通过，`assembleDebug` 通过，APK 已安装。
- **遗留与观察点**：本修复依赖时序宽限，对 ROM 行为敏感，仍需日常使用观察。若再次失败，按提示文案定性——「未识别到可收藏的应用」= 前台包名仍拿不到（回到方案 D）；「收藏位置点击被系统取消」= 注入被遮挡；「无障碍服务未连接」= 服务掉线。彻底方案仍为 D1/D2/D3，均需产品负责人确认（D1 触碰 §5 隐私底线，D2 需新增 `PACKAGE_USAGE_STATS` 权限与授权引导）。

### 2026-10-03 G35「双指枪·竖向」（代码完成、图片与算法姿势待确认）

产品负责人已再次确认：G35 已完成开发并正式纳入当前版本；现有范围为 G01–G35，后续继续冻结 G36 及新手势概念。

> **2026-10-04 默认动作互换（产品负责人确认）**：G35 与 G25 的默认绑定动作对调——
> **G35 由「向上滑动」改为「下拉通知栏」**，**G25 由「下拉通知栏」改为「向上滑动」**。
> 变更位置：`GestureArchitecture.kt` 的 `defaultMappings`，以及 `GestureArchitectureTest`
> 中两条默认值断言。二者都是普通绑定，用户仍可在校准页换绑任意动作；解锁包、名称、识别算法均未变。

- **判定**（`GestureEngine.kt`）：在既有「两指并拢」姿势之上再加两个条件——`thumbSideways`（拇指明显向侧面伸出）与「两指尖各自高于对应掌指关节 0.25 掌宽」。复用既有 `twoFingerTogetherPose` 是为了天然互斥 V 字（V 要求两指分开 > 0.32 且张开角度 > 25°），因此不需要额外排斥逻辑。
- **节奏**：走 `advanceStaticHold`，`holdMs = 1000`（与 G22/G23 一致；产品负责人当天要求 G11/G25 由 2 秒、G35 由 1.5 秒统一改为 1 秒；**G25 已于 2026-10-04 再由 1 秒改为 0.6 秒**，G11 已于当天改回 2 秒），带浮层倒计时「两指并拢向上保持：还需 N 秒」；触发一次后必须放开才能再次触发；`resetTransient` 已清理状态。
- **G11 V 字自拍**：当天先改为 1 秒，**用户试用后要求改回 2 秒**（1 秒太短、容易误拍），现已还原 `2000L`。记住这块没有走 `advanceStaticHold`，时长硬编码在 **3 处**（进度百分比、剩余秒数文案、触发判定），必须三处一起改，改一处会导致进度条与触发点不一致。确认后的「3 秒拍照倒计时」来自 `CameraProbeService`（冻结/解冻结），与保持时长无关。

### 2026-10-03 爪形（G26）识别不出来、被当成握拳（`GestureEngine`）

现象：手心正对摄像头、五指张开内弯的爪形几乎不触发，反而经常触发「握拳保持」的播放/暂停。

- **根因**：`fist` 的判据只是「四指指尖都缩回各自 PIP 内侧」（`dist(tip,0) < dist(pip,0) * 1.08`）。**爪形也是把手指弯起来的**，所以真机帧里 `fist` 几乎恒为真 —— 既被 `clawPose` 的 `!fist` 挡在门外，又去驱动 G22 的握拳保持。弯曲程度根本区分不了这两个姿势。
- **修复**：新增 `closedFist = fist && (指尖聚拢 || 缩进掌心)`，即 `cFingerTipGaps.average() < .28` 或 `tipPalmRatios.average() < .45`。爪形的指尖是**扇形张开在掌心前方**的（间距大、离掌心远），两项都不满足，因此不再是握拳；真握拳指尖成团或贴着掌心，仍然算握拳。
  - `clawPose` / `cShapePose` / G13 截图序列 / G22 握拳保持，四处统一改判 `closedFist`。
- **踩过的坑（别再犯）**：我同时把 claw 的 `tipPalmRatios.count { in .45..1.25 } >= 3` 放宽成 `>= 2`，结果两指并拢滑动（G29/G30）和单指上挑被爪形管道抢走，2 个用例立刻变红 —— **该条件已回退为 `>= 3`**。这里是爪形与两指类手势的分界，动它必挂。
- **测试**：`deepClawPose()` 复现真机帧（指尖弯过自己的 PIP、但仍扇形张开），新增用例断言它既不触发 PlayPause 也不触发 C，而是进入爪形拖动。
- **真机验收（2026-10-03 13:00，用户实测通过）**：装最新 debug APK 后，爪形能稳定识别并进入拖动；做爪形期间不再触发 G22。全程日志仅 1 次 `gesture G22 -> PLAY_PAUSE`，且落在用户主动做握拳对照的时间点（13:00:22），其余只有 G01 光标跟踪。修复确认有效。
  - 验收方法（下次照做）：启动手势控制 → 打开「显示识别反馈」开关 → 爪形保持约 1 秒后平移手掌，看屏幕顶部提示；同时 `adb logcat | grep CameraProbe` 观察 `gesture Gxx -> ACTION` 判断是否误触发 G22。
  - **别用「有没有执行动作」判断爪形识别**：G26 默认未绑定动作（`claw_drag`），识别成功也只出提示、不执行任何操作，这是设计如此。

### 2026-10-03 爪形改为持续拖动，且拖动是映射来的动作不是死绑

需求：爪形绑定拖动；**进入拖动后一直执行、不受时长限制**（可能拖 20 秒），手势释放后才进入冷却。并明确：**拖动是动作，与手势是映射关系，不是死绑**。

- **原实现做不到**：`ClawDrag` 只在释放时发一次事件，`injectDrag` 用单个 `GestureDescription` 跑一条固定 Path（350–900ms 甩动）。单条手势必须**事先声明完整路径**，既不能实时跟手，也不可能持续几十秒。
- **改法**：`ClawDrag` 增加 `DragPhase { START, MOVE, END }`（沿用既有 `TwoFingerVolumeHold` 的 START/TICK/END 套路）。引擎进入拖动立刻 START（手指按下）、保持期间按 90ms 节流喂 MOVE、释放时 END（抬起）。无障碍侧用 `StrokeDescription.continueStroke(path, 0, 90ms, willContinue)` **把多段手势串成一条**：`willContinue=true` 表示手指不抬起，最后一段置 false 收尾，以此突破单条手势的时长限制。
- **冷却是最大的坑**：G26 的 `cooldownPolicy` 必须是 `NONE`。沿用 `GLOBAL_AFTER_SUCCESS` 的话，**第一个 MOVE 就会 `finishAction` → `pipeline.pause()`，把拖动当场冻死**。改成 `NONE` 后由 `executeMapped` 单独判断「DRAG 且 phase==END」才触发冷却，正好对应「释放后才进冷却」。
- **映射关系务必保持**：G26 只是**默认**映射到 `GestureAction.DRAG`，用户可在校准页换成任意动作；`DRAG` 已放回 `selectableActions`，但它需要坐标，所以仅对 G26 显示（`selectableActions - DRAG`）。**不要把拖动逻辑写进爪形里**，否则就退化成死绑了。
- 派生细节：MOVE 不写日志（否则一次长拖动刷满 logcat）；拖动全程 `busy=true`，其他手势无法中途注入；原地释放等同一次点击，会提示「拖动距离太短，相当于一次点击」。
- **测试**：`clawDragStaysPressedWhileHeldAndLiftsOnlyOnRelease` 断言保持 20 秒（400 帧）期间不产生 END、手掌移动产生 MOVE、张开手指才 END。全量 101 例通过。

### 2026-10-03 拖动改为滚动语义 + 画圈容错 + G07/G08 与 G35 互斥（**真机验收通过**）

承接上一节，真机试用暴露了三个问题，逐个修完并由用户实测通过。

- **拖网页变成选中文字**：不是识别问题，是触摸语义错了。上一版为了让「手指一直按住」用 `continueStroke` 让触点常驻屏幕，而手掌没动的那几百毫秒里触点**静止不动**，Chrome 判定为长按 → 进入文本选择。
  - **改法**：`beginDrag` 只锚定位置、**不再按下**；每次 `moveDrag` 注入一条 120ms 的**独立短滑动**（DOWN→MOVE→UP 一次完成）；`endDrag` 只关闭状态。`dragStroke` 链整体删除。
  - 手指不再常驻就没有静止的长按机会；每次滑动保留手掌真实位移，所以手掌快页面滚得快、慢就慢慢滚，仍然跟手、仍然不受时长限制。
  - **取舍已与产品负责人确认**：现在是「滚动页面」语义，代价是**不能长按拖图标/拖滑块**——那种必须手指按住不动，与避免长按矛盾。若要支持，需给 G26 加模式开关。
- **画圈拖动被断掉**：两个原因叠加。① 画圈时手腕旋转，掌心朝向与指尖间距持续掉帧，撑过容错窗口就被当成松手；② 手指撞到屏幕边缘后被 `coerceIn` 截断，就**卡在边上不动**了。
  - **维持条件改成「进入严格、保持宽松」**：拖动中只有**张开手指**或**握拳**才立即结束（`released || fist`），姿态形变的兜底窗口 800ms → **2000ms**；严格 `pose` 只用于进入拖动。
  - **边界重锚**：`clawDragPoint` 不再 clamp，由 DRAGGING 判断越界后在边上重新锚定（同步更新 `clawAnchor`/`clawPalmAt`），圈的后半段继续生效；新增 `clawOrigin` 记录真正起点，供 END 的「距离太短」判定使用（否则重锚会让该提示失真）。
- **G07/G08（四指并拢左右挥）不灵敏且与 G35 混**：
  - 进不去：原要求四指**完全伸直**（`> PIP × 1.10`）且指间夹角 **≤5°**。真手无名指小指会自然微弯、指间总有夹角 → 几乎进不了候选。改为 `fourFingersBlade`（`> PIP × 1.02`）+ 夹角 ≤12°。
  - **轴判错一次就锁死**：原判定 `absDx >= absDy * 1.45` 且只在 `openPalmAxis == NONE` 时判一次，起手若有上下晃动就锁成纵向，**之后怎么左右挥都触发不了**。改为 1.15 且**允许反复改判**直到真正触发；触发位移 0.075 → 0.065。
  - 与 G35 混：四指并拢时无名指小指微弯被判成「收拢」，正好落进 G35 前提（两指并拢 = 食中伸 + 无名指小指收），而 G35 保持 1 秒会**独占画面**。G35 加 `!directionPalm` 互斥。
- **判定顺序（易踩）**：挥手序列原排在 OK/莲花/兰花等保持类**之前**，放宽 `directionPalm` 后把它们的帧全吃掉了（2 个用例立刻变红）。改为**保持类优先于挥手序列**；但**已激活的序列必须优先**（`screenshotSequence != IDLE` 时先推进），否则截图序列的握拳阶段会被「握拳保持」抢走。
- **G35 姿势按「双指枪」补全**（产品负责人描述：食中指并拢为枪管朝上，拇指向侧面伸出为握把，拇指与食指成明显夹角）：新增 `thumbIndexAngle >= 45f`（与 G24 单指枪同一把尺子，G24 用 45–90）与竖直角 `>= 50f`。**与 G24 天然互斥**：G24 要求中指收拢，G35 要求中指与食指并拢伸直。
  - **坑**：`indexAngleDegrees` 因屏幕 y 轴向下的定义是「**向上为负**」，必须写 `abs(indexAngleDegrees) >= 50f`（G24/L 形都用 abs）。我第一版漏了 abs，G35 直接失效，被用例抓出。
- **测试**：全量 101 例通过。**真机验收（用户实测通过）**：G07/G08 左右挥正常、G35 正常触发且不再与左右挥互相抢。

### 2026-10-03 自拍照片被旋转 90°（`CameraProbeService`）

现象：V 字自拍保存出来的照片，App 弹出的预览（以及部分查看器里）是横躺的，正好差 90°。

- **根因不是公式**：高清路径拿到的 JPEG **原样写入相册**（`saveSelfie(ByteArray)`）并直接用 `BitmapFactory.decodeByteArray` 做预览。各设备对 `CaptureRequest.JPEG_ORIENTATION` 的实现不一致——**规范允许设备旋转像素或直接写一个 EXIF 方向标签**。相册/图库会读这个标签把图转正，而 `BitmapFactory` 不会，于是同一张照片在不同消费者眼里的朝向不一致。
- **修复**：保存前用 `ExifInterface` 读出方向标签，交给 `CameraFrameOrientation.exifRotationDegrees()` 换算成需要旋转的角度，把旋转**烘焙进像素**再落盘（`rotateBitmap`），同时 App 内的预览缩略图走同一个校正。Tag 为 0/1（像素已正）时不重编码，保留原始画质。新增依赖 `androidx.exifinterface:exifinterface:1.3.7`。
- **预览兜底路径无需改**：那条路走 `HandPipeline.submit` 生成的 bitmap，里面已经 `postRotate(frameRotation)` + `postScale(-1f,1f)` 校正过。
- **便于下次定位**：保存时会打 `CameraProbe: selfie orientation tag=N -> rotating X deg`。若自拍仍然歪，看这行即可判定：tag 是 6/8/3 → 属于本次修复范围（若仍歪，说明还差额外的镜像或 180°）；tag 是 0/1 且仍歪 → 说明该设备的 JPEG_ORIENTATION 基准与我们算的差 90°，那时应放弃依赖 HAL，改为把 `JPEG_ORIENTATION` 置 0、完全由 App 按 `frameRotation` 自行旋转。
- 附注：真机 vendor 的 `SENSOR_ORIENTATION` 各家不同（常见 270），`JPEG_ORIENTATION` 的正确基准只能实机验证，不要凭公式推算后直接改Universal 常量。
- **链路位置**：放在光标输出之后、V 字倒计时与两指音量/切歌链路之前。这样保持期间由 G35 独占帧，避免用户稳住不动时那些**位移型**手势来抢；手真的移动了才有机会进入音量链路。注意 `advanceActiveVolumeHold` 仍在最前面，已进入音量独占态时不会误触发 G35。
- **默认动作**：`GestureMapping(defaultMappings G35)` = `HOLD` + `NOTIFICATIONS` + `GLOBAL_AFTER_SUCCESS`（2026-10-04 起；此前为 `SCROLL_UP`，已换给 G25）。下拉通知栏走系统全局动作 `GLOBAL_ACTION_NOTIFICATIONS`，无需改 `ControlAccessibilityService`；原 `SCROLL_UP` 走 `scrollDirectional` 的路径现在由 G25 使用。可在校准页换绑任意动作。
- **开关与解锁**：新增 `GestureFeatureConfig.twoFingerUp`（偏好键 `feature_two_finger_up`），`GestureFeatureGate` 中 `G35 -> twoFingerUp`，`GESTURE_CODES_BY_FEATURE` 增加 `two_finger_up`。G35 归入第 12 次签到的功能包（原「点赞手势与高级自定义手势」已改名为「点赞手势与高级手势」）——`GestureUnlockTest` 断言所有编号必须被覆盖，因此新增编号**必须**同时进包。
- **UI**：首页新增卡片（沿用 `gesture_two_fingers_together` 图）；校准页手势列表由 `GESTURE_DISPLAY_NAMES` 自动生成，无需改。
- **验证**：`testDebugUnitTest` 95/95 通过（新增 4 例：1 秒触发且不重复、释放后可再次触发、不误判 V/两指媒体/比心、普通两指并拢姿势不触发、开关关闭不触发；架构层 1 例），`assembleDebug` 成功。**真机待验收**：刷短视频 App 保持 1 秒是否真的翻页；做上下拉音量、V 字自拍、比心时是否互不干扰；左右手与 40–80cm 距离下 `thumbSideways`（0.45 掌宽）是否过严——这是后续最可能需要按真机手感放松的阈值。
- 文档已同步 `GESTURE_ACTION_MAPPING_CHECKLIST.md`（表格、编号范围、保持节奏）与本文档第 1、4 节。

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

## 4. 手势矩阵现状（G01-G35）

| 状态 | 手势码 |
|---|---|
| 已实现（35 个编号） | G01 指尖移动、G02 指尖轻点；G03/G04/G09/G10 保留识别兼容入口但默认关闭且未绑定，作为二期轨迹预留；G05-G08 并掌四向挥动；G11 V 手势自拍、G12 指尖比心点赞、G13 开合掌截图、G14 莲花指→返回桌面、G15 兰花指→最近任务、G16-G19 张掌变一/二/三/四指→打开指定 App、G20 拇指赞、G21 OK 手势=收藏当前内容、G22 握拳保持 1s=播放/暂停、**G23 小指手势=静音开关**、G24 单指枪·横向返回、G25 单指枪·竖向保持 0.6s=向上滑动、G26 抓取手势（默认未绑定）、G27 C 手势=最近任务、G28 Love 手势=返回桌面、G29/G30 双指左右挥=切歌、G31/G32 双指上下拉=持续音量、G33 双指双点=播放/暂停、G34 六六顺手势（默认未绑定）、**G35 双指枪·竖向保持 1s=下拉通知栏** |
| 已移除 | G18/G19 食指画圈音量已移除；编号后续已复用于张掌收指打开 App |

两指系列 G29-G33 共用 `two_finger_media` 开关（显示名"两指媒体控制"），构成完整媒体控制家族；五向均为 DYNAMIC 类型、可换绑。

## 5. 动作目录（GestureAction，28 项）

`NONE`(暂不绑定动作) / `MOVE_CURSOR`(固定) / `CLICK` / `SCROLL_UP` / `SCROLL_DOWN` / `SCROLL_LEFT` / `SCROLL_RIGHT` / `BACK` / `HOME` / `SELFIE` / `LIKE` / `SCREENSHOT` / `ROLLING_SCREENSHOT` / `OPEN_APP` / `FAVORITE_CURRENT` / `THUMBS_UP_LIKE` / `CONFIRM` / `PLAY_PAUSE` / `RECENTS` / `NOTIFICATIONS` / `VOLUME_UP` / `VOLUME_DOWN` / `TOGGLE_MUTE`(媒体静音/恢复) / `MEDIA_NEXT` / `MEDIA_PREVIOUS` / `LOCK_SCREEN` / `VOICE_ASSISTANT` / `DRAG`。`OPEN_APP_1..4` 仅作旧配置兼容。

**G16-G19 打开指定 App**（2026-10-02 新增并重构）：张掌确认后收指到 1/2/3/4 指并保持约 0.6 秒，映射为统一的 `OPEN_APP` 动作。配置流程为"点击当前手势动作 → 选择打开应用 → 紧接着选择目标 App"，不再维护"应用一/二/三/四"独立配置区。包名按手势存为 `open_app_package_Gxx`，映射按钮及首页卡片显示"打开应用：App 名称"。原 `open_app_package_1..4` 和 `OPEN_APP_1..4` 自动兼容迁移。四个识别功能开关 `open_app_1..4` 仍独立保留；执行走 `getLaunchIntentForPackage` + `NEW_TASK`。

**G16-G19 首页图片**（2026-10-02 更新）：四张卡片不再共用单张 `gesture_palm`。每张卡片以"张掌 → 对应最终指型"的双图组合显示，最终姿势资源分别为 `gesture_open_app_1.png`～`gesture_open_app_4.png`，均为 1254×1254 ARGB 透明 PNG，与现有 3D 手势资产风格一致。新增或调整组合手势时，首页必须显示完整阶段图片，不能只用文字或数字角标代替。

音量动作实现：普通换绑动作仍由 `CameraProbeService.adjustVolume` 每次调整约 10%；G31/G32 两指保持会话约每 0.4 秒调整 5%，会话期间独占识别（不输出光标或其他手势），姿势改变或到达边界后结束并进入冷却（时长可配置，见下）。（G18/G19 食指画圈音量已移除。）

**G23 小指静音开关**（2026-10-02 新增）：仅小指伸直，拇指、食指、中指和无名指收拢，保持 1 秒触发 `TOGGLE_MUTE`；执行 `AudioManager.ADJUST_MUTE/ADJUST_UNMUTE` 切换媒体流静音状态。保持不重复触发，必须释放后重新做手势。比心 G12 已收紧为中指、无名指、小指必须收拢；G23 出现时会清除比心候选，避免小指被误判为比心。首页图片为 `gesture_pinky.png`，识别阈值和各厂商静音行为待真机验收。

**手势冷却时长**（2026-10-02 新增）：动作执行成功后的全局锁定默认由 2 秒改为 **1.5 秒**，成为用户可配置项——校准页"显示识别反馈"下方新增拖动条（0.6–4 秒、100ms 步进），松手即保存；运行中的控制服务通过 `cooldown_ms` 偏好监听即时生效，冷却中的锁不会被变更打断。

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
14. **高质量自拍：✅ 2026-10-04 已真机验收**（证据见 `GESTURE_REALTIME_ACCEPTANCE.md` 6.5）：荣耀前置选中的最大 JPEG 为 `3264×2448`，按 EXIF 转正并按物理屏幕比例居中裁边后输出 `1476×3264`（704KB），方向与前置镜像经操作者确认正确；拍摄完成后 640×480 识别流继续工作。仍缺：保存耗时、内存峰值、连续连拍，以及「高质量捕获失败 → 自动保存预览帧」的真机触发（目前只有代码与 JVM 覆盖）。
15. **每日签到解锁真机验收（代码已实现）**：首次安装只开放 7 个基础编号；签到后运行中的控制服务立即生效（无需重启）；同一天重复点击被拒绝；把系统日期改到过去不增加也不清零；12 次后 G01–G35 全部拥有；卸载重装回到 7 个；Release 包不得出现全开入口。
16. **二期新手势候选（仅记录，真实上线前禁止开发）**：双指水平摆放，向左持续快退、向右持续快进，持续机制参考 G31/G32 音量调节。尚未分配编号，也未进入当前解锁和发布范围；与 G29/G30 双指左右挥切歌存在直接姿势冲突，二期重新立项时必须先定义速度、位移、保持时长和互斥优先级。详细记录见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` §7.4。
17. **自定义组合手势与个人模板（已列入二期待开发，当前未授权实施）**：产品模型为"基础手势 + 基本行为 -> 动作功能"；用户个人模板先支持对预设基础手势录制 3～5 轮进行本机校准，稳定后再评估完全自由的新手形。二期必须规划模板质量、正反例验证、相邻姿势冲突、默认模板回退、重新录制/删除、独占状态、释放复位、轨迹容错和真机验收；当前不新增 G36、不进入代码开发。详细记录见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` §7.5，个人模板见 §7.5.5。
18. **按前台 App 自动切换手势场景（下个里程碑候选，当前仅记录）**：依据前台应用包名及本机保存的 App 场景关联，自动启用短视频、媒体、阅读或通用等场景的有效手势集合，以减少跨场景冲突；不得读取第三方页面内容或控件树。需规划用户确认/手动覆盖、通用场景回退、切换时清空状态、独占动作安全结束以及同一 App 多业务页面无法仅凭包名区分的限制。详细记录见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` §7.6。
19. **G03/G04/G09/G10 二期轨迹预留（当前版本代码已完成，轨迹未开发）**：一期单食指收敛为光标、单击和后续双击，四向滑动由 G05–G08 四指并掌覆盖。四个编号已改为默认关闭、默认未绑定并移入第 12 个签到包；升级时一次性关闭旧开关但保留已有动作映射。首页与校准页明确标为轨迹预留。S、圆圈、V 等轨迹识别、具体编号分配和验收仍必须等 App 真实公开上线后重新规划。

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
.\gradlew.bat testDebugUnitTest assembleDebug   # 全量验证（当前 124/124 通过）
.\gradlew.bat installDebug                       # 安装到已连接设备
D:\Android\Sdk\platform-tools\adb.exe devices    # adb 不在 PATH，用完整路径
D:\Android\Sdk\platform-tools\adb.exe install -r app\build\outputs\apk\debug\app-debug.apk
```

最近一次发布产物（2026-10-05 签到反馈改造后重新生成）：`app\build\outputs\apk\release\app-release.apk`（49,313,418 字节，SHA-256 `BDAFB9C1876BDB730F09BC4061D29EBF991D434D8AC2F0F2EAF22E1C5433915E`）与 `app\build\outputs\bundle\release\app-release.aab`（37,327,720 字节，SHA-256 `8E23A279964C694F78606E4A38647FE170A247069F65E22FB048B65DE9E3CCF8`）。原生库只含 `arm64-v8a` 与 `armeabi-v7a`，不再包含 32 位 `x86`（详见 §0.5.1）。这两个旧 Release 产物早于 2026-10-06 的 G35、签到页面和隐私页面修改，不能作为最新发布包，后续必须重建。

最新 Debug APK（51,271,022 字节，SHA-256 `FEB2F339472F5BB745C8D989CF0635BCA53623A1677D0E13E36E1B25919651B8`）已构建但**设备离线，尚未安装**；设备恢复连接后需要重新装机验证签到反馈，并另行安装 Release APK 做正式包复验。

上一轮隐私政策构建时还执行过 `lintRelease` 并通过；本轮只改打包配置，未重跑 lint。

Debug APK 已于 2026-10-05 ABI 专项后覆盖安装到荣耀 ALP-AN00，尚未做本机冒烟；Release APK/AAB **尚未安装到真机，也未提交应用市场**。

上架文案材料：根目录 `README.md` 已按当前版本重写；新增 `docs/PRIVACY_POLICY.md`、`docs/privacy-policy.html` 与 `docs/APP_STORE_REVIEW_GUIDE.md`。公开隐私政策使用“魔法手势开发者（个人开发者）”和联系邮箱 `3603317@qq.com`，不公开法定实名，正式地址为 `https://magicgesture.mt4000.com/privacy-policy.html`。App 首页提供离线“隐私政策与权限说明”入口，由 `PrivacyPolicyActivity` 读取随包 `res/raw/privacy_policy.txt`，不使用 WebView 或网络；2026-10-06 已移除应用内在线版本按钮，公开网页仅供商店后台使用。

手机测试路径：开相机权限 → 启用无障碍 → 启动控制 → 播放音乐。先验证 G23 小指静音/恢复、保持不重触发、释放后可再触发以及不误判比心；再测试两指左右切歌、上下拉住持续音量和双击播放暂停；最后验证握拳保持 1 秒。G18/G19 画圈识别已经移除，不再测试旧画圈音量流程。

荣耀机型注意：安装时保持手机解锁并确认 USB 安装弹窗；无障碍授权丢失时引导用户在 设置→应用→魔法手势→电池 关闭"自动管理"（详见 §8.1/§10.1 of 旧文档记录，或提交 95864c6 系列）。

## 9. Git 状态（2026-10-05 本地提交后核对）

- 工程：`E:\2026\MagicGesture-v0.9`；分支 `main`，HEAD 为文档同步提交（前序 `e5d614e` / `dfd35f4` / `4a08b93`）；远程 `origin` 为 Gitee、`github` 为 GitHub。
- 隐私政策链路、上架材料、ABI 收敛与交接文档均已本地提交；相对 `origin/main` 领先若干提交，**尚未推送**。
- push 必须由产品负责人明确要求；未获明确同步远程意图前只能本地 commit。
- 当前验证和未验证项以 §0.5、§0.7 为准，避免继续引用旧产物或旧真机状态。

## 10. 文件速查

| 文件 | 职责 |
|---|---|
| `GestureEngine.kt` | 全部姿势检测与状态机（V 字块在最前，注意与两指块的互斥；两指双击状态机在挥手状态机旁） |
| `GestureArchitecture.kt` | 手势码 G01-G35 / 映射（伴生对象 `defaultMappings`）/门控/动作执行器/`GestureAction` 与文案 |
| `CameraProbeService.kt` | 前台服务：相机管线、偏好监听（`feature_*` 与 `mapping_*` 即时生效）、自拍链路、媒体键与音量 |
| `ControlAccessibilityService.kt` | 无障碍：`globalAction()`/`inject()`/`tapPixels()`/`foregroundPackage()`/媒体键；`busy` 标志是全局注入锁（疑似卡死点，见 §3 末节②） |
| `FavoriteButtonController.kt` | 收藏标定与坐标点击：首次标定浮层、准星、`saveFavoriteProfile` 读取；**失败分支共用兜底文案**（当前待定位） |
| `GesturePreferences.kt` | 开关（`feature_*`）与映射覆盖（`mapping_Gxx`）持久化 |
| `MainActivity.kt` / `CalibrationActivity.kt` / `KeepAuthorizationActivity.kt` | 首页卡片 / 校准+开关+映射配置 / 品牌授权引导 |
| `PrivacyPolicyActivity.kt` / `res/raw/privacy_policy.txt` | App 内离线隐私政策页面 / 随包政策正文；公开网页不在 App 内重复跳转 |
| `GlobalCooldownManager.kt` / `HandPipeline.kt` / `OverlayIndicator.kt` | 冷却 / MediaPipe 封装 / 悬浮反馈与缩略图 |
| `app/build.gradle.kts` | 构建配置：BuildConfig 隔离全解锁、**移除 32 位 `x86` 原生库以满足 Google Play 64 位配对要求** |
| `GestureUnlock.kt` | 签到解锁：解锁计划、签到状态机、本机存储、权益判定与开关收敛 |
| `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md` | 手势↔动作完整映射表（必随代码同步更新） |

## 11. 接手后的第一步（按当前实际进度）

1. 用已覆盖安装到荣耀 ALP-AN00 的新 Debug APK 做 ABI 冒烟：启动手势控制确认 `libmediapipe_tasks_vision_jni.so` 正常加载，并抽验识别、自拍、截图、前台服务与相机恢复。
2. 审查 §0.4 的完整差异（隐私政策链路 + ABI 打包）；确认产品文案和公开主体策略后再本地提交，**未获明确推送授权不要同步远程**。
3. ABI 已通过 §0.5.1 的结论处理完毕，不需要再动；除非产品负责人重新决定只保留 `arm64-v8a`。
4. 准备各市场权限声明、适龄分级和审核演示视频，并确认 Google Play、华为、小米、OPPO、vivo 的准入要求；商店截图与图标已于 2026-10-06 产出（见 §7.1 清单），剩余工作是逐张隐私复核与国内渠道横幅导出。
5. 最后才回到剩余高风险真机项：G35 图形/算法姿势差异、多人多手、G29–G33、G21 收藏标定。
