# 魔法手势 App 功能清单与完成状态总表

> 整理日期：2026-10-12
> 包名 `com.magicgesture.app`　版本 `1.0.0`（versionCode 10）　分支 `main`
> 数据来源：**本机源码**（`app/src/main/java/com/magicgesture/app/`、`AndroidManifest.xml`、`app/src/test/`）一手核对，辅以 `GESTURE_ACTION_MAPPING_CHECKLIST.md`、`GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md`、`REQUIREMENTS_CHANGELOG.md`、`GESTURE_REALTIME_ACCEPTANCE.md`、`IDE_DEVELOPMENT_HANDOFF.md`、`FEATURE_ROADMAP.md`
> 本文是**唯一功能主清单**；手势与动作的权威映射仍以 `GESTURE_ACTION_MAPPING_CHECKLIST.md` 为唯一来源，规划候选见 `FEATURE_ROADMAP.md`，二期边界见 `GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` §7
> 状态层级严格区分：**已实现**（有代码）/ **已真机验收**（人工确认）/ **待验收** / **未实现**。构建通过不等于真机通过，真机通过不等于已上线

---

## 0. 统计总览

| 维度 | 数量 | 说明 |
|---|---:|---|
| 手势编号 | 35 | G01–G35，已冻结，不得新增 G36 |
| 有出厂默认动作 | 29 | 不含识别锁与未绑定编号 |
| 系统级识别锁 | 1 | G28 Love 手势（非动作） |
| 出厂默认未绑定 | 5 | G03、G04、G09、G10、G34 |
| 出厂默认关闭开关 | 5 | G03/G04、G09、G10（轨迹预留）+ G26 抓取 |
| 动作枚举值 | 32 | 其中 4 个（`OPEN_APP_1..4`）仅作升级兼容 |
| 功能开关 | 29 | 4 组编号共用开关（G03/G04、G05/G06、G29/G30/G33、G31/G32） |
| 页面（Activity） | 6 | 首页、校准、手势映射、收藏位置、保持授权、隐私政策 |
| 服务 | 2 | 无障碍服务、前台相机服务 |
| 开箱即用编号 | 5 | G01、G02、G05、G06、G24 |
| 签到功能包 | 7 | 7 次有效签到解锁全部 G01–G35 |
| JVM 测试用例 | 146 | 7 个测试文件，当前全绿 |
| 已真机验收手势 | 35 | 人工确认（见 §2 表格），但多为单次抽验，不代表全机型 |
| 未实现（仅规划）功能 | 7 | 见 §4.1，均无代码 |
| 二期候选（禁止实施） | 10 | 见 §4.3，真实上线前不得开发 |

---

## 1. 产品定位与运行前提

- 前置摄像头识别手部关键点，隔空完成光标、点击、滚动、系统导航、媒体控制、自拍、收藏等操作。
- 全部推理在本机完成，模型随包（`app/src/main/assets/hand_landmarker.task`）；构建已用 `tools:node="remove"` 移除 `INTERNET` 与 `ACCESS_NETWORK_STATE`。无账号、无广告、无云同步、无联网埋点。
- 两项系统授权是使用前提：① 悬浮窗（光标、状态点、反馈、收藏标定面板）；② 无障碍服务（点击、滑动、截图、点赞等系统动作）。
- 摄像头仅在前台服务运行期间开启，停止控制后与悬浮控件一并关闭。

### 1.1 页面与服务清单

| 组件 | 状态 | 作用 |
|---|---|---|
| `MainActivity` | 已实现 | 首页：授权门控、启动/停止控制、签到卡、快捷菜单、手势指南、使用提示、隐私政策入口、解除识别锁定 |
| `CalibrationActivity` | 已实现 | 手势练习与校准：灵敏度、反转左右、识别反馈、冷却时长（均已改为点击即生效） |
| `GestureMappingActivity` | 已实现 | 手势动作映射（2026-10-10 从校准页拆出）：按签到批次分组、解锁淡化、动作选择器、带搜索的“打开应用”选择器 |
| `FavoriteLocationActivity` | 已实现 | 收藏按钮位置：查看/删除已标定坐标，显示应用图标、横竖屏与保存时间 |
| `KeepAuthorizationActivity` | 已实现 | 保持授权不丢失：荣耀/华为（真机实测）、小米/Redmi（公开路径整理，未实测）、其他品牌通用建议 |
| `PrivacyPolicyActivity` | 已实现 | 离线隐私政策全文（读 `res/raw/privacy_policy.txt`，不用 WebView） |
| `ControlAccessibilityService` | 已实现 | 无障碍：全局动作、`dispatchGesture` 注入、截图/长截图、双击点赞、前台包名识别 |
| `CameraProbeService` | 已实现 | 前台服务（camera 类型）：相机管线、手势编排、自拍、媒体键与音量、收藏、偏好热更新、识别锁 |

### 1.2 权限（最终 Manifest）

| 权限 | 用途 |
|---|---|
| `CAMERA` | 手势识别与自拍 |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_CAMERA` | 后台持续识别 |
| `POST_NOTIFICATIONS` | 运行通知与“停止”按钮 |
| `SYSTEM_ALERT_WINDOW` | 悬浮光标、状态点、反馈、标定面板 |
| `MODIFY_AUDIO_SETTINGS` | 2026-10-10 新增，写系统音量/静音 |
| `queries` 声明 | 查询已安装应用（打开应用）与默认音乐 App（播放兜底） |

---

## 2. 手势总表（G01–G35）

类型：`CONTINUOUS` 连续 / `DISCRETE` 离散 / `DYNAMIC` 动态 / `HOLD` 保持 / `SEQUENCE` 序列
解锁：`开箱`＝初始拥有；`第N次`＝第 N 次签到解锁（`GestureUnlock.kt` 的 `PACKAGES`）
真机：✅ 已人工确认（主要来自 `GESTURE_REALTIME_ACCEPTANCE.md` 与 2026-10-06/10-08 产品负责人确认）；⚠️ 已确认但有遗留

| 编号 | 名称 | 类型 | 出厂默认动作 | 功能开关（出厂） | 解锁 | 真机 |
|---|---|---|---|---|---|---|
| G01 | 指尖移动 | CONTINUOUS | 移动光标（固定不可改绑） | cursor（开） | 开箱 | ✅ |
| G02 | 指尖轻点 | DISCRETE | 点击 | click（开） | 开箱 | ✅ |
| G03 | 轨迹预留一 | DYNAMIC | 未绑定 | index_vertical_scroll（**关**） | 第7次 | ✅（旧挑指行为，已默认关闭） |
| G04 | 轨迹预留二 | DYNAMIC | 未绑定 | index_vertical_scroll（**关**） | 第7次 | ✅（同上） |
| G05 | 并掌上挥 | DYNAMIC | 向上滚动 | palm_vertical_scroll（开） | 开箱 | ✅ |
| G06 | 并掌下挥 | DYNAMIC | 向下滚动 | palm_vertical_scroll（开） | 开箱 | ✅ |
| G07 | 并掌左挥 | DYNAMIC | 向左滚动 | palm_left_scroll（开） | 第5次 | ✅ |
| G08 | 并掌右挥 | DYNAMIC | 向右滚动 | palm_right_scroll（开） | 第5次 | ✅ |
| G09 | 轨迹预留三 | DYNAMIC | 未绑定 | index_left_scroll（**关**） | 第7次 | ✅ |
| G10 | 轨迹预留四 | DYNAMIC | 未绑定 | index_right_scroll（**关**） | 第7次 | ✅ |
| G11 | V 手势 | HOLD | 自拍（保持 2 秒 + 3 秒倒计时） | selfie（开） | 第1次 | ✅（高清自拍 2026-10-04 验收） |
| G12 | 指尖比心 | HOLD | 双击点赞 | like（开） | 第3次 | ⚠️ 修阈值后通过；固定坐标可靠性有限 |
| G13 | 开合掌 | SEQUENCE | 截图 | screenshot（开） | 第7次 | ✅ |
| G14 | 莲花指 | HOLD | 返回桌面 | lotus_recents（开） | 第5次 | ✅ 阈值待校准 |
| G15 | 兰花指 | HOLD | 最近任务 | orchid_back（开） | 第5次 | ✅ 阈值待校准 |
| G16 | 张掌变一指 | SEQUENCE | 打开应用（可指定 App） | open_app_1（开） | 第4次 | ✅ |
| G17 | 张掌变二指 | SEQUENCE | 打开应用 | open_app_2（开） | 第4次 | ✅ |
| G18 | 张掌变三指 | SEQUENCE | 打开应用 | open_app_3（开） | 第4次 | ✅ |
| G19 | 张掌变四指 | SEQUENCE | 打开应用 | open_app_4（开） | 第4次 | ✅ |
| G20 | 拇指赞 | HOLD | 双击点赞 | thumbs_up（开） | 第3次 | ⚠️ 同 G12 |
| G21 | OK 手势 | HOLD | 收藏当前内容 | ok（开） | 第3次 | ✅ 需先标定位置 |
| G22 | 握拳 | HOLD | 播放/暂停（保持 1 秒） | play_pause（开） | 第1次 | ✅ |
| G23 | 小指手势 | HOLD | 静音开关（保持 1 秒） | pinky_mute（开） | 第1次 | ✅（vivo Y35 修复后**待复验**） |
| G24 | 单指枪·横向 | HOLD | 返回 | left_l（开） | 开箱 | ✅ 阈值待校准 |
| G25 | 单指枪·竖向 | HOLD | 向上滑动（0.6 秒） | l_shape（开） | 第6次 | ⚠️ 互换默认动作后手感待复验 |
| G26 | 抓取手势 | HOLD | 拖动 | claw_drag（**关**） | 第6次 | ✅（2026-10-04 起默认关闭，需手动开启） |
| G27 | C 手势 | HOLD | 最近任务 | c_shape（开） | 第6次 | ✅ 阈值待校准 |
| G28 | Love 手势 | HOLD | **识别锁（不是动作）** | 无（系统级，不可关闭/不可换绑） | 所有用户 | ⚠️ 2026-10-08 人工确认；1 秒/800ms 参数待复核 |
| G29 | 双指左挥 | DYNAMIC | 上一曲 | two_finger_track（开） | 第2次 | ✅ |
| G30 | 双指右挥 | DYNAMIC | 下一曲 | two_finger_track（开） | 第2次 | ✅ |
| G31 | 双指上拉 | DYNAMIC | 持续增加音量 | two_finger_volume（开） | 第2次 | ⚠️ vivo Y35 修复后**待复验** |
| G32 | 双指下拉 | DYNAMIC | 持续降低音量 | two_finger_volume（开） | 第2次 | ⚠️ 同上 |
| G33 | 双指双点 | DYNAMIC | 播放/暂停 | two_finger_track（开） | 第2次 | ✅ |
| G34 | 六六顺手势 | HOLD | 未绑定 | six666（开） | 第6次 | ✅（识别正常，无动作属预期） |
| G35 | 双指枪·竖向 | HOLD | 下拉通知栏（保持 1 秒） | two_finger_up（开） | 第7次 | ⚠️ 互换默认动作后手感待复验 |

补充规则：

- G28 是系统级识别锁：保持 1 秒切换锁定/解锁；锁定期间光标与其余 G01–G35 全部不识别、不输出；切换后姿势须连续消失 800ms；所有用户可用、不可关闭、不可换绑、不进入动作映射与全局冷却；首页与常驻通知保留手动解除入口。
- 保持类手势（G22/G23）触发后必须真正松手才能再次触发；冷却结束不会把持续保持变成第二次动作。
- G26 出厂关闭：爪形会吞掉大量半弯手形帧，用户需在校准页手动开启；默认绑定拖动，可换绑任意动作。
- G03/G04/G09/G10 当前代码里只是**旧单食指四向滑动检测器**（兼容保留），**没有任何 S / 圆圈 / V 轨迹识别实现**；默认关闭且未绑定，编号留给二期。
- 多人多手：视觉最近手独占控制权，后方手不参与识别；挑战手需掌部尺度领先 20% 且持续 250ms 才接管，接管时清空上一只手全部瞬态状态。
- 全局冷却：仅动作成功后进入，默认 1.5 秒（可配 0.6–4 秒）；冷却期间冻结整条管线（含光标、反馈、保持进度、轨迹、候选）。G01 无冷却，G26 释放后才冷却。

---

## 3. 已实现功能清单

### 3.1 识别与执行链路

| 能力 | 状态 | 说明 |
|---|---|---|
| MediaPipe 手部关键点管线 | 已实现 | `HandPipeline`，640×480，最多 2 只手，模型异步初始化带 generation guard 防泄漏 |
| 最近手选择与锁定 | 已实现 | `ActiveHandSelector`，掌部尺度判断前后，切换清空状态 |
| 手势判定状态机 | 已实现 | `GestureEngine`（约 100KB），含保持、序列、轨迹、双击、持续音量、识别锁、过渡姿势闸门 |
| 动作映射（换绑） | 已实现 | `GestureMappingManager`，除 G01 与 G28 外均可换绑，即时生效 |
| 功能开关门控 | 已实现 | `GestureFeatureGate` + `GesturePreferences.effectiveFeatures()`，与权益、映射三层独立 |
| 动作执行器 | 已实现 | `GestureActionExecutor` 以动作为中心分发，不依赖原始手势类型 |
| 全局冷却 | 已实现 | `GlobalCooldownManager`，仅成功回调启动，运行中可热改 |
| 过渡姿势闸门 | 已实现 | 手仍在变形时不计时，抑制过路形态误触发（阈值 3.0 掌宽/秒、静置 60ms，**待真机复核**） |
| 识别锁（G28） | 已实现 | 锁定前安全结束拖动/持续音量；摄像头不停、不降耗电 |
| 无障碍执行通道 | 已实现 | 点击、滚动、拖动、返回/桌面/最近任务/通知栏、截图、锁屏、语音助手、双击点赞、前台包名识别 |
| 前台包名稳定性处理 | 已实现 | 临时覆盖层忽略、桌面/设置置空、最近任务后 3 秒宽限、5 秒超时兜底；`canRetrieveWindowContent=false` 未变 |

### 3.2 动作目录（32 个枚举值，全部有真实执行代码，无占位）

| 动作 | 状态 | 限制/说明 |
|---|---|---|
| `NONE` | 已实现 | 触发后不执行；与“默认”不同 |
| `MOVE_CURSOR` | 已实现 | 仅 G01 固定，不出现在换绑选单 |
| `CLICK` / `CONFIRM` | 已实现 | G02 用食指坐标；其他手势换绑后点光标位置 |
| `SCROLL_UP/DOWN/LEFT/RIGHT` | 已实现 | 无障碍手势注入 |
| `BACK` / `HOME` / `RECENTS` / `NOTIFICATIONS` | 已实现 | 系统全局动作 |
| `LOCK_SCREEN` | 已实现 | `GLOBAL_ACTION_LOCK_SCREEN`，Android 9+ |
| `VOICE_ASSISTANT` | 已实现 | `ACTION_VOICE_COMMAND` |
| `SCREENSHOT` | 已实现 | 无障碍截图，Android 9+ |
| `ROLLING_SCREENSHOT` | 已实现 | 连续截屏拼接（最多 4 屏），Android 11+，期间独占识别；**未绑定任何默认手势** |
| `SELFIE` | 已实现 | App 内前置抓帧，优先最大 JPEG，失败回退预览帧，EXIF 方向烘焙 |
| `LIKE` / `THUMBS_UP_LIKE` | 已实现 | 固定屏幕区域双击，跨 App 可靠性有限 |
| `PLAY_PAUSE` / `MEDIA_NEXT` / `MEDIA_PREVIOUS` | 已实现 | 媒体键；无会话时打开默认音乐 App 补发（10 秒节流） |
| `VOLUME_UP` / `VOLUME_DOWN` | 已实现 | 单次约 10%；G31/G32 会话每 0.4 秒约 5%，写后回读校验 |
| `TOGGLE_MUTE` | 已实现 | 音量归零/恢复（2026-10-10 改），不再依赖 `isStreamMute` |
| `DRAG` | 已实现 | 仅 G26 提供坐标，不在通用选单 |
| `OPEN_APP` | 已实现 | 包名按手势存，选择器支持搜索 |
| `FAVORITE_CURRENT` | 已实现 | 按前台 App + 横竖屏读标定坐标点击 |
| `OPEN_APP_1..4` | 兼容 | 仅作旧配置迁移 |

### 3.3 交互、反馈与设置

| 能力 | 状态 | 说明 |
|---|---|---|
| 悬浮状态点 | 已实现 | 可拖动、自动贴边、点击回 App；显示运行/冷却/锁定/异常 |
| 识别反馈浮层 | 已实现 | 顶部胶囊显示动作名与保持进度，可开关，即时生效 |
| 保持倒计时 | 已实现 | G11/G22/G23/G35 等 ≥1 秒的手势显示倒计时 |
| 自拍预览与音效 | 已实现 | 缩略图短暂显示、倒计时滴答、快门音 |
| 长截图缩略图 | 已实现 | 成功后右下角显示最终长图并自动淡出 |
| 通知与“停止”按钮 | 已实现 | 前台服务通知显示状态，可一键停止并关闭摄像头 |
| 识别灵敏度三档 | 已实现 | 稳定 1.28 / 标准 1.00 / 灵敏 0.78，点击即时生效 |
| 反转左右方向 | 已实现 | 点击即写入，说明注明重新开启后生效 |
| 冷却时长滑杆 | 已实现 | 0.6–4.0 秒，步进 100ms，松手即存并热更新 |
| 手势动作映射页 | 已实现 | 按批次分组、未解锁淡化、动作选择器、应用选择器 |
| 收藏位置管理页 | 已实现 | 查看与删除（按 App + 横竖屏） |
| 练习顺序与说明弹窗 | 已实现 | 点手势行看完整做法 |

### 3.4 签到解锁

| 能力 | 状态 | 说明 |
|---|---|---|
| 离线签到状态机 | 已实现 | 本机 SharedPreferences，无账号/无联网 |
| 开箱日规则 | 已实现 | 安装当天不能签到，最早次日第 1 次；老用户升级记为昨天 |
| 一天一次 / 断签不清零 / 永久保留 | 已实现 | 时间回拨视为已领取；跨天跳变只发一个包 |
| 7 个功能包 | 已实现 | 见 §2 解锁列；G28 不进任何包 |
| 三层独立（权益/开关/映射） | 已实现 | 未解锁在识别入口与执行入口双重拦截 |
| Debug 全开隔离 | 已实现 | `BuildConfig.DEBUG` 控制，正式 Release 恒为 false |
| 签到后页面原地重建 | 已实现 | `preserveScroll=true`，授权引导不回显 |
| 真机全链路验收 | 已实现 | 2026-10-08 在正式 Release 包上跑完 7 次签到（含跳日期专项） |

7 个功能包（`GestureUnlock.PACKAGES`，与 `PACKAGE_LABELS` 一一对应）：

| 第 N 次 | 内容 |
|---|---|
| 1 | V 手势、握拳播放/暂停、小指静音（G11/G22/G23） |
| 2 | 两指媒体控制：切歌、音量与双击播放暂停（G31/G32/G29/G30/G33） |
| 3 | 点赞、拇指赞与 OK 收藏（G12/G20/G21） |
| 4 | 张掌收指打开常用与更多 App（G16–G19） |
| 5 | 桌面、最近任务与并掌左右滚动（G14/G15/G07/G08） |
| 6 | 单指枪竖向、C 手势、抓取与六六顺（G25/G27/G26/G34） |
| 7 | 开合掌、双指枪竖向与轨迹预留（G13/G35/G03/G04/G09/G10） |

### 3.5 隐私与合规

| 能力 | 状态 | 说明 |
|---|---|---|
| 首启隐私政策同意门 | 已实现 | 未同意不构建首页、不申请通知权限、不开启相机；带版本号，重大变更重征 |
| App 内离线政策页 | 已实现 | `PrivacyPolicyActivity` + `res/raw/privacy_policy.txt`，无 WebView、无联网 |
| 公开 HTTPS 政策页 | 已实现 | `https://magicgesture.mt4000.com/privacy-policy.html`，主体“魔法手势开发者（个人开发者）” |
| 无障碍隐私底线 | 已实现 | `canRetrieveWindowContent="false"`，不读窗口内容，不按文字/控件点击 |
| 移除网络权限 | 已实现 | Manifest 合并规则移除 `INTERNET`/`ACCESS_NETWORK_STATE` |
| 上架审核指南与权限声明 | 已实现 | `APP_STORE_REVIEW_GUIDE.md`、华为/荣耀/小米/腾讯清单、权限声明稿 |

### 3.6 构建、测试与产物

| 能力 | 状态 | 说明 |
|---|---|---|
| JVM 测试 146 例 | 已实现 | `GestureEngineReplayTest`(75)、`GestureUnlockTest`(29)、`GestureArchitectureTest`(26)、`ActiveHandSelectorTest`(5)、`CameraFrameOrientationTest`(4)、`SelfieFramingTest`(4)、`GlobalCooldownManagerTest`(3) |
| Debug / Release / AAB 构建 | 已实现 | Release APK 56,975,618 字节、AAB 44,979,505 字节（2026-10-12 由 HEAD 重建） |
| ABI 收敛 | 已实现 | 仅 `arm64-v8a` + `armeabi-v7a`，移除 32 位 `x86` 以满足 Google Play 64 位配对 |
| 正式签名与对齐 | 已实现 | `CN=Magic Gesture`，v2 签名有效，16KB 对齐通过，`lintRelease` 通过 |
| 深色霓虹 UI 改版 | 已实现 | 首页、校准页、保持授权页 + 设置页拆分为三页 |

---

## 4. 未完成与待开发

### 4.1 未实现功能（仅有规划，代码中无任何痕迹）

| 优先级 | 功能 | 执行通道 | 成本 | 建议手势位 | 收益/风险 |
|---:|---|---|---|---|---|
| 1 | 滚动长截图绑定默认手势 | 内部 `ROLLING_SCREENSHOT`（**代码已完成**） | 零 | G34 六六顺 | 截图能力闭环；需确认长截图期间的手势冻结策略 |
| 2 | 关闭通知栏 | `GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE`（API 30+） | 极低 | 冗余位（G14 或 G27） | 与 G35 配对形成完整通知流程 |
| 3 | 快捷设置面板 | `GLOBAL_ACTION_QUICK_SETTINGS` | 极低 | 冗余位 | 打开 WiFi/手电筒等入口 |
| 4 | 长按 | 无障碍单笔划 500ms 注入 | 低 | 冗余位 | 呼出菜单、多选；与 G26“避免长按”的取舍需重新确认 |
| 5 | 暂停识别（安全阀） | 内部引擎挂起/恢复 | 中 | 需新增或占用一位 | 手忙不过来时挂起识别；需设计恢复方式 |
| 6 | 快进 / 快退 / 停止 | 媒体按键 | 低 | 双指系列扩展 | 低频；与 G29/G30 姿势冲突 |
| 7 | 双指捏合 / 张开（缩放） | 无障碍双笔划 | 中 | 需新增手势 | 地图与相册缩放，小众 |

> 代码核查结论：`DISMISS_NOTIFICATION_SHADE`、`QUICK_SETTINGS`、`longPress` 等关键字在 `app/src` 内 **0 命中**；`GestureEngine` 内也没有 S / 圆圈 / V 轨迹识别实现。

### 4.2 已实现但尚未完成验收 / 材料待补

**P0 — 发布前必须处理**

| 项 | 状态 |
|---|---|
| 商店截图过期 | 首页与校准页已改版，`9x16/` 现有 7 张截图全部过期，上架前须用荣耀 ALP-AN00 真机重截；此前隐私复核仅 `07-selfie-countdown.jpg` 合格 |
| 最新 Release APK 装机复验 | 2026-10-12 重建的 `1.0.0(10)` **尚未安装到真机**；华为线上仍是 0.9.0(9) |
| 渠道提交 | 华为：1.0.0 升级待提交；荣耀：账号已通过、未提交；小米/腾讯/Google Play：未提交；OPPO/vivo：未评估 |
| 软著 | 材料（源程序 60 页 + 手册 19 页）已生成，仍需本人实名认证、在线填报与提交 |
| vivo Y35 媒体动作修复 | 静音/音量/播放暂停三项执行层修复代码已完成，`testDebugUnitTest` + `assembleDebug` 通过，**真机未验收** |
| 适龄分级与各市场权限声明 | 未完成；国内渠道横幅尺寸未核对 |

**P1 — 真机验收与兼容性**

| 项 | 状态 |
|---|---|
| G14/G15/G24/G25/G27/G28 识别阈值 | 已可用，阈值待真机校准（一次只改一个变量） |
| G25/G35 互换默认动作后的手感 | 待复验（0.6 秒翻页是否够快、1 秒是否能中途收手） |
| 运行态圆环旋转动效 | 代码已完成，真机目视未验收 |
| Love 锁定态 `lock.png` 图标 | 已实现，待产品做 Love 手势复验 |
| 高质量自拍性能 | 缺保存耗时、内存峰值、连续连拍；「高质量失败 → 回退预览帧」降级路径只做过代码与 JVM 覆盖 |
| 相机恢复与压力 | 启动后立即停止、快速连续启停、模型加载中退出的原生内存观察未做 |
| 真机录制样本回放 | 现有回放主要用程序合成关键点，不能替代真机验收；需建立真机录制样本与误触现场回归 |
| OPPO / vivo 保活方案 | 未补（荣耀/华为真机实测，小米/Redmi 依公开路径整理未实测） |
| 双手检测性能预算 | 帧率、CPU、耗电、发热未量化；是否扩大到三只手待定 |
| 机型兼容抽验 | 不同距离、弱光、大角度侧转、不同品牌未系统抽验 |

**已接受现状（产品负责人已确认，不再作为待办）**

- G12/G20 固定坐标双击点赞不看执行结果，跨 App 可靠性有限。
- 收藏位置 `appVersion` 未校验（目标 App 大版本更新后仍点旧坐标）。
- 收藏标定 30 秒超时仍为静默结束。
- 底部固定导航按产品要求暂时隐藏（`buildBottomNav()` 保留未删）；页面底部因此留有空白。
- 收藏位置说明卡的“星星齿轮”光效图标切片未取，当前为自绘占位。

### 4.3 待规划（二期，真实上线前禁止实施）

硬门槛：App 在至少一个目标市场**真实公开上架**并积累首批真实用户反馈后，才能重新立项。当前不得开发。

| 项 | 说明 |
|---|---|
| 账号体系 / 微信登录 | 游客升级、换机与多设备权益同步 |
| 付费解锁 / 订阅 | 一次性解锁全部稳定手势、恢复购买、退款 |
| 邀请分享与防刷 | 邀请码、链接、拉新奖励、每日上限 |
| 服务端与联网权益 | 线上权益同步、数据统计、埋点 |
| G03/G04/G09/G10 轨迹手势 | S、圆圈、V 等轨迹算法未开发；编号已预留，具体分配二期定 |
| 自定义组合手势规则 | 「基础手势 + 基本行为 → 动作功能」，用独立规则 ID，不占 G01–G35 |
| 用户录制个人手势模板 | 每个模板 3–5 轮采样，仅本机保存归一化关键点特征 |
| 按前台 App 自动切换场景 | 仅依据包名 + 本机关联，不读页面内容 |
| 双指水平快退/快进 | 与 G29/G30 姿势高度重合，须先解决区分维度 |
| 手持物抑制 | 控制手持物时冻结识别；MediaPipe 不直接输出持物类别，需评估本机分类与性能预算 |

> 另：当前仍严格冻结编号范围，**不得新增 G36 或后续编号**；「优化」默认指现有功能的稳定性、性能、交互、文档与上架准备。

### 4.4 待产品拍板事项

1. 双击点赞（G12/G20）是否降级为实验性功能或淘汰。
2. 冗余手势位是否释放：G14/G27（桌面/最近任务）与 G15 各保留其一，释放出的位置承接长按、快捷设置等新动作。
3. G34 六六顺绑定什么动作（建议优先承接滚动长截图）。
4. 是否新增「暂停识别」安全阀。
5. 收藏彻底方案（前台应用识别）是否需要：D1 开 `canRetrieveWindowContent`（触碰隐私底线）/ D2 新增 `PACKAGE_USAGE_STATS` 权限 / D3 手动指定目标 App。

---

## 5. 核心模块速查

| 文件 | 职责 |
|---|---|
| `HandPipeline.kt` | Camera2 + MediaPipe HandLandmarker，最多 2 只手，模型异步初始化防泄漏 |
| `ActiveHandSelector.kt` | 视觉最近手选择与接管 |
| `GestureEngine.kt` | 全部姿势判定与状态机（保持/序列/轨迹/双击/持续音量/识别锁/过渡闸门） |
| `GestureArchitecture.kt` | G01–G35 枚举、默认映射、门控、动作执行器、动作文案 |
| `GesturePreferences.kt` | 开关、映射覆盖、冷却、灵敏度、收藏坐标持久化 |
| `GestureUnlock.kt` | 解锁计划、签到状态机、本机存储、权益判定 |
| `GestureMappingActivity.kt` | 手势动作映射页 |
| `FavoriteLocationActivity.kt` | 收藏位置管理页 |
| `CameraProbeService.kt` | 前台服务编排：相机、自拍、媒体、收藏、热更新、识别锁 |
| `ControlAccessibilityService.kt` | 系统动作执行与前台包名识别 |
| `FavoriteButtonController.kt` | 收藏标定浮层与坐标点击 |
| `GlobalCooldownManager.kt` / `OverlayIndicator.kt` | 冷却 / 悬浮反馈、倒计时、缩略图 |
| `MainActivity.kt` / `CalibrationActivity.kt` / `KeepAuthorizationActivity.kt` | 首页 / 校准 / 品牌保活引导 |
| `PrivacyConsent.kt` / `PrivacyPolicyActivity.kt` | 同意状态 / 离线政策页 |
| `SelfieFraming.kt` / `CameraFrameOrientation.kt` | 自拍取景裁剪 / 方向与 EXIF 校正 |

---

## 6. 维护规则

- 手势编号、姿势、默认动作、开关的任何变更，必须同步 `docs/GESTURE_ACTION_MAPPING_CHECKLIST.md` 与本文 §2。
- 功能行为变更追加到 `docs/REQUIREMENTS_CHANGELOG.md`；当前状态与验证更新 `docs/IDE_DEVELOPMENT_HANDOFF.md`。
- 规划候选维护在 `docs/FEATURE_ROADMAP.md`；二期边界维护在 `docs/GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md` §7。
- 文档不得把「计划 / 代码完成 / 构建通过 / 真机通过 / 真实上线」混写为同一状态。
