# 腾讯应用宝上架执行清单（V0.9）

> 应用宝是国内第三方应用商店中月活最高的渠道（艾媒 2022 年度应用分发类 APP 榜单第 2，仅次于苹果 App Store，位列所有手机厂商商店之前）。本渠道复用华为已上架的同一 APK、签名、备案号与文案。
> 后台规范以后台当页为准；通用事实卡见 `docs/store-listing-form-answers.md`，敏感权限文案见 `docs/huawei-permission-statement.md`。

## 1. 已具备的材料

| 材料 | 内容 |
|---|---|
| APK | `app\build\outputs\apk\release\app-release.apk`（49,313,594 字节，0.9.0/9，签名 v2） |
| 签名指纹 | MD5 `e26b95ecd7c5711b5753f2082279159b`、SHA‑1 `a5a0e143…`、SHA‑256 `98efd9db…`、公钥 1024 位十六进制 |
| 隐私政策 | `https://magicgesture.mt4000.com/privacy-policy.html` |
| APP 备案 | 腾讯云办理（侯兴章，个人），备案号全国通用 |
| 素材 | 图标 216/512/1024，截图 1080×1920 共 7 张，演示视频 1 份 |
| 文案 | `docs/APP_STORE_REVIEW_GUIDE.md` §7.5 |

## 2. 前置门槛

| # | 事项 | 说明 |
|---|---|---|
| 1 | 腾讯开放平台账号 | 用 **QQ 登录**（应用宝走 QQ 体系，不是微信） |
| 2 | 个人实名认证 | ✅ **个人开发者受支持**：身份证 + **人脸校验** + **本人手持身份证照片** |
| 3 | APP 备案号 | 已办理，直接填 |
| 4 | 软著 | 视后台要求 |

入口：`https://open.tencent.com/`，开发者中心 `https://open.tencent.com/developer/center`
（备用域名：`https://app.open.qq.com/`、`https://open.qq.com/app_plus`）

## 3. 填写与素材

| 项 | 内容 |
|---|---|
| 应用名称 | 魔法手势 |
| 包名 | `com.magicgesture.app` |
| 分类 | 工具 / 效率（选最贴近的类目） |
| 隐私政策 | `https://magicgesture.mt4000.com/privacy-policy.html` |
| 关键词 | 隔空手势、手势控制、免触控、悬浮窗控制、无障碍、隔空截图、隔空自拍、媒体控制 |
| 图标/截图 | 按后台当页规格上传（已备 512/1024 图标与 1080×1920 截图） |

文案复用 `docs/APP_STORE_REVIEW_GUIDE.md` §7.5 的完整描述与首版更新说明。

## 4. 权限说明

按 `docs/store-listing-form-answers.md` §2.3 逐权限填写，敏感能力（无障碍、悬浮窗、后台相机）用 `docs/huawei-permission-statement.md` §1–§3 的完整文案，并附演示视频。

应用宝对**应用质量要求偏高**，功能不完善容易被拒；本应用已在华为应用市场过审并上架，属加分材料，可在备注中说明。

## 5. 提交顺序

1. 注册/登录 → **个人实名认证**（人脸 + 手持身份证）
2. 开发者中心 → 应用管理 → 创建应用 → 选「移动应用」
3. 上传 APK，等待自动检测（安全/合规/兼容性）
4. 填基本信息、素材、隐私政策、备案号、权限说明
5. 提交审核（约 2–5 个工作日）

## 6. 上架后

- 下载安装验证商店包全链路
- 核对详情页展示（图标、截图、介绍、开发者主体、备案号）
- 结果回填本文与 `docs/IDE_DEVELOPMENT_HANDOFF.md`
