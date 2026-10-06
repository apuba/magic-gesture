# 华为 AppGallery 上架执行清单（V0.9）

> 本文只服务「上架华为应用市场」这一个动作，记录包体事实、后台填写稿、素材清单、硬门槛与驳回应对。
> 真正提交必须由发布主体在 AppGallery Connect 后台完成（账号实名、APP 备案、上传包、填表、提交审核），本仓库与 AI 无法代替。
> 华为后台表单与素材规格会随政策调整，凡是本文与后台当页提示冲突的，**一律以后台当页提示为准**，并回来更新本文。

## 1. 状态结论

### 1.1 已具备，可立即用于提交

- 正式签名 Release APK（见 §2），权限、ABI、签名、16KB 对齐均已实测。
- 无障碍与悬浮窗用途说明文案：`docs/huawei-permission-statement.md`（可直接粘贴）。
- 个人信息收集清单、第三方 SDK 清单、权限逐条说明：`docs/store-listing-form-answers.md` §2。
- 隐私政策公开页 `https://magicgesture.mt4000.com/privacy-policy.html`，App 内另有离线隐私政策页。
- 商店截图 7 张、应用图标 216/512/1024、审核演示视频 1 份（见 §4）。

### 1.2 必须由人工完成（阻塞上架，无法由代码或文档替代）

| # | 事项 | 说明 |
|---|---|---|
| 1 | 华为开发者联盟账号 + 实名认证 | 个人主体需身份证正反面，主体姓名须与后续备案、隐私政策主体一致 |
| 2 | **APP 备案（工信部）** | 国内应用商店上架前置条件，后台通常要求填写备案号，未完成无法上架 |
| 3 | 软件著作权证书 | 华为对部分应用/申诉场景会要求提供；主体须与开发者账号一致 |
| 4 | 后台上传 APK、填写素材与问卷、提交审核 | 需账号本人操作，含短信/人脸等验证 |
| 5 | 审核反馈整改与申诉 | 若被驳回，按 §6 处理 |

### 1.3 待确认（不阻塞提交，但可能被审核追问）

- 华为渠道宣传横幅/特色图尺寸尚未核对；源图为 `E:\2026\MagicGesture-store-screens\feature-graphic-master-1795x876.png`（实际 1794×876，霓虹风格中文版），确认规格后从中导出，不要沿用 Google Play 的 1024×500 比例。
- 审核演示视频为 578×1280，低于 720p；若后台校验分辨率或码率，需按 `docs/review-video-script.md` 重录。
- 公开隐私政策未显示个人法定姓名，仅使用「魔法手势开发者（个人开发者）」与联系邮箱 `3603317@qq.com`；若华为要求公开个人信息处理者实名，须按审核反馈处理。

## 2. 包体自查（2026-10-06 实测，产物对应当前 HEAD `9b375fe`）

| 项目 | 实测值 |
|---|---|
| 上传文件 | `app/build/outputs/apk/release/app-release.apk`（**华为渠道传这个 APK**） |
| 大小 | 49,313,594 字节 |
| 文件 SHA-256 | `0408B95E7357F6D570F2D406295E91B4DE17E4CAC962556A691AE97DEA043339` |
| 签名证书 | `CN=Magic Gesture, OU=Mobile, O=Magic Gesture, L=Shanghai, ST=Shanghai, C=CN` |
| 证书 SHA-256 | `98efd9dbe4586388fb81a4e6187a37e7d1dda5db01c219f5f0163f76cb61348e` |
| 签名方案 | v2 通过；v1 为 false（minSdk 26 不需要）、v3/v3.1/v4 为 false |
| 包名 / 版本 | `com.magicgesture.app` / `versionCode=9` / `versionName=0.9.0` |
| minSdk / targetSdk | 26 / 36（compileSdk 36） |
| 权限 | `CAMERA`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_CAMERA`、`POST_NOTIFICATIONS`、`SYSTEM_ALERT_WINDOW`（另有无障碍服务绑定） |
| 网络权限 | **无** `INTERNET`、**无** `ACCESS_NETWORK_STATE` |
| ABI | `arm64-v8a`、`armeabi-v7a`（32/64 位配对完整，无 `x86`） |
| 16KB 对齐 | `zipalign -c -P 16 4` 通过 |
| AAB（备用） | `app/build/outputs/bundle/release/app-release.aab`，37,329,746 字节，SHA-256 `1AD1E2C4811F52B87F6B713122541218CA09BB52723DEAEAF0AF908B8643E8E5` |

校验命令（已实际执行）：

```powershell
& D:\Android\Sdk\build-tools\36.0.0\apksigner.bat verify --verbose app\build\outputs\apk\release\app-release.apk
& D:\Android\Sdk\build-tools\36.0.0\aapt2.exe dump badging app\build\outputs\apk\release\app-release.apk
& D:\Android\Sdk\build-tools\36.0.0\zipalign.exe -c -P 16 4 app\build\outputs\apk\release\app-release.apk
```

产物自 2026-10-06 构建后，仓库仅新增文档与素材说明，无源码改动，因此无需重新构建；后续若改动 `app/` 下任何源码，必须重新生成产物并刷新本文的哈希。

AAB 说明：华为支持 AAB，但通常需要启用华为应用签名服务，签名证书会变为华为持有。当前上架直接使用自有签名的 APK，保持签名指纹一致；除非产品另有决定，不传 AAB。

## 3. AppGallery Connect 后台填写稿

### 3.1 应用信息

| 字段 | 填写内容 |
|---|---|
| 应用名称 | 魔法手势 |
| 应用包名 | `com.magicgesture.app`（与包体一致，不可改） |
| 应用分类 | 实用工具 / 工具效率（以后台可选分类为准） |
| 默认语言 | 简体中文 |
| 发行地区 | 先只选中国大陆，通过后再考虑其他地区 |
| 是否含账号/付费 | 均无 |
| 隐私政策链接 | `https://magicgesture.mt4000.com/privacy-policy.html` |
| 联系方式 | `3603317@qq.com` |

### 3.2 应用介绍（直接使用 `docs/APP_STORE_REVIEW_GUIDE.md` §7.5 的完整描述草案）

该草案已包含无障碍能力说明、主要功能、权限用途、隐私说明、使用限制与隐私政策链接，符合华为「描述需与实际功能一致」的要求，不要自行删减无障碍与悬浮窗部分。

**一句话简介（80 字符内）**：用前置摄像头在本机识别手势，隔空完成光标、点击、滑动、返回、截图与媒体控制。

**版本更新说明（首版提交）**：

```text
首个上架版本。
- 前置摄像头本机识别隔空手势，支持光标、点击、滑动、返回、桌面、最近任务、截图与媒体控制
- 每个手势可换绑动作或绑定要打开的应用
- 多人多手同框时由最靠近摄像头的手独占控制权
- 每日签到一次逐步解锁更多手势，累计 12 次解锁当前全部功能包
- 摄像头画面与手部关键点只在设备本机处理，不上传；无账号、无广告、无联网
```

后续版本的更新说明以 `RELEASE_CHANGES.md` 当次内容为准，不得照抄首版文案。

### 3.3 分级问卷与个人信息

- 分级问卷：按 `docs/store-listing-form-answers.md` §1.1 的答案填写；**摄像头与无障碍能力必须如实申报**，不能因「本机处理」而省略。
- 个人信息收集：按 §2.1 填「不收集、不上传」，并如实列出本机处理项（摄像头画面、前台包名、用户主动保存的图片）。
- 第三方 SDK：按 §2.2 只列 MediaPipe tasks-vision 0.10.21 与 androidx.exifinterface 1.3.7，均标注不联网、不收集个人信息。

### 3.4 权限与敏感能力说明（华为审核最关键部分）

逐权限粘贴 `docs/huawei-permission-statement.md`：

- §1 无障碍服务用途说明（必要性论证、使用范围、明确不做的事、用户知情与控制）
- §2 悬浮窗用途说明
- §3 相机、通知与前台服务说明
- §4 隐私与数据处理声明

若后台有独立的「敏感权限申请」或补充说明入口，直接提交 §1、§2 全文，并附上审核演示视频与无障碍配置截图（`canRetrieveWindowContent=false`）。

## 4. 素材清单（目录 `E:\2026\MagicGesture-store-screens\`，不随 Git 提交）

| 素材 | 华为要求（需后台复核） | 现有文件 | 状态 |
|---|---|---|---|
| 应用图标 | 216×216 PNG，第三方口径为 ≤500KB 或 ≤2MB，通常要求不透明 | `icons\icon-216-bg.png`（114,646 字节，32 位带底） | 可用；若后台拒绝透明通道，需另存无 alpha 版本 |
| 高清图标（备用） | 部分页面要求 512/1024 | `icons\icon-512-bg.png`、`icons\icon-1024-bg.png` | 可用 |
| 应用介绍截图 | 竖版多张，常见口径 1080×1920 或 720×1280，PNG/JPG ≤2MB，张数 3~8 张（第三方口径存在 800×450 等说法，**以后台当页为准**） | `9x16\01-home.jpg` … `07-selfie-countdown.jpg` 共 7 张，200,470–369,468 字节 | 可用，上传前仍须逐张复核不得含人脸、相册、聊天、通知与第三方内容 |
| 审核演示视频 | 需能完整证明披露、授权与实际使用过程 | `审核演示视频.mp4`（82.2 秒，578×1280，H.264，音轨静音，10,855,608 字节） | 可用；若后台因分辨率或体积打回，按 `docs/review-video-script.md` 重录 |
| 宣传横幅/特色图 | 华为渠道尺寸未核对 | 源图 `feature-graphic-master-1795x876.png`（1794×876 霓虹风格中文版） | 待确认规格后导出，不可直接复用 Google Play 的 1024×500 |

## 5. 提交顺序（建议）

**当前进度（2026-10-06）**：已进入 AGC「应用信息 → 应用签名」，选择**方式二（使用本地已有签名密钥）**并**已完成**：

- 证书指纹：`98EFD9DBE4586388FB81A4E6187A37E7D1DDA5DB01C219F5F0163F76CB61348E`（即 `CN=Magic Gesture` 证书 SHA-256，与 Release APK 实测一致，已用 keytool 从 keystore 复核）。
- CSR 已生成：`E:\2026\MagicGesture-store-screens\huawei-signing\magicgesture-release.csr`（本次流程实际未用到，AGC 直接校验了 pepk zip）。
- pepk 流程已完成：官方 `pepk.jar`（9,136,653 字节，存放于同目录）+ 本地 keystore 生成 `magicgesture-sign.zip` 并上传成功；AGC 显示「此应用已加入应用签名计划」，登记指纹与本地 keystore 一致。pepk 必须在真实交互式 CMD 中运行（非交互环境 `System.console()` 为 null 会 NPE），口令手工输入。
- 页面上的「SHA256 证书扫描服务」配置提示：仅针对集成华为安全检测 SDK 的应用，本应用未集成，无需处理。
- **不要按页面示例用 jarsigner 重签 APK**：jarsigner 只产生 v1（JAR）签名，Android 11+ 对 targetSdk≥30 的应用强制要求 v2+ 签名，重签后无法安装。直接上传现有的 `app-release.apk`（v2 已验证），指纹一致即可通过校验。
- 「传统密钥」为可选项，跳过（不上传时默认使用签名密钥）。
- 下一步：左侧「版本升级」上传 `app-release.apk`，随后补齐素材与问卷。

1. 完成 §1.2 的账号实名与 APP 备案，取得备案号后再进后台建应用。
2. 建应用并填写 §3.1、§3.2、§3.3。
3. 上传 §4 的图标与截图，再上传 Release APK。
4. 填写权限与敏感能力说明（§3.4），附上视频与无障碍配置截图。
5. 自查：应用内可查看隐私政策、首启同意门、拒绝权限后不崩溃、停止控制后摄像头与悬浮窗关闭。
6. 提交审核，记录提交时间与版本号，等待反馈。

## 6. 常见驳回与应对（华为口径）

| 驳回表现 | 应对 |
|---|---|
| 权限与功能不匹配 / 过度索权 | 提交 §3.4 的逐权限说明，强调权限均为核心功能，无通讯录、定位、短信、存储读取类权限 |
| 无障碍或悬浮窗用途不明 | 提交 `docs/huawei-permission-statement.md` §1、§2 全文与演示视频，重申 `canRetrieveWindowContent=false`、不按页面文字点击、可随时关闭 |
| 拒绝权限后功能异常 | 说明首启隐私政策同意门（拒绝即退出）与无障碍未授权时的明确提示，并补录拒绝路径视频 |
| 隐私政策不可访问或内容不全 | 检查 HTTPS 页面可访问、含主体与联系方式、含第三方 SDK 清单、含用户删除方式；必要时按反馈补充实名信息 |
| 分级问卷未申报摄像头/无障碍 | 如实申报这两项能力，说明本机处理、不上传 |
| 演示材料不足 | 提交审核演示视频（含同意门、披露、系统授权页、跨应用点击、停止收尾） |
| 描述与实际功能不符 | 描述以 `docs/APP_STORE_REVIEW_GUIDE.md` §7.5 为准，不写「百分百准确」等绝对表述 |

## 7. 上架后的维护纪律

- 后续版本必须递增 `versionCode`，并使用同一 keystore 签名；换签名等于换应用。
- 每次华为审核反馈都要回填成本文或 `docs/huawei-permission-statement.md` 的补充说明，避免下次重复被驳回。
- 引入联网、统计、广告、账号或支付能力后，本文档 §2 的包体事实与 §3.3 的个人信息答案立即失效，必须重做。
