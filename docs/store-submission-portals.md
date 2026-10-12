# 各应用商店上架后台入口清单（2026-10-12）

> 用途：上架时按品牌逐家提交，本文件只收录「后台入口」，各渠道的资质、素材、权限说明与逐页填写稿见对应的 checklist 文档。
> 通用材料：应用名称「魔法手势」、包名 `com.magicgesture.app`、当前版本 1.0.0（versionCode 10）、
> APK `app\build\outputs\apk\release\app-release.apk`（56,975,586 字节，SHA-256 `2C2E03E2C057A74BB8E088512D1C953CC53EF893DD81932F7A3322F44D386092`）、
> 正式 Release 签名（`CN=Magic Gesture`，证书 SHA-256 `98efd9db…`）、隐私政策 `https://magicgesture.mt4000.com/privacy-policy.html`。

图例：✅ 已完成｜🔶 进行中/待办动作明确｜⬜ 未开始

## 1. 国内主流（手机厂商）

| 品牌 | 平台 | 后台入口 | 状态 | 备注 |
|---|---|---|---|---|
| 华为 | AppGallery Connect | `https://developer.huawei.com/consumer/cn/service/josp/agc/index.html`（开发者联盟 `https://developer.huawei.com/consumer/cn/`） | ✅ 已上架 0.9.0(9)，🔶 待升级 1.0.0(10) | 升级走 AGC「版本升级」，沿用已登记 pepk 签名，不重传密钥 |
| 荣耀 | 荣耀开发者 | `https://developer.honor.com/cn/manageCenter/appManager` | ⛔ **需企业/组织开发者账号** | 2026-10-12 确认：个人开发者无应用分发权益，账号已认证也无法上架，见 `docs/honor-store-submission-checklist.md` §2.2 |
| 小米 | 小米开放平台 | `https://dev.mi.com/` | ⬜ 未开始 | 需备案号；权限申请指南 `https://dev.mi.com/xiaomihyperos/documentation/detail?pId=2117` |
| OPPO | OPPO 开放平台 | `https://open.oppomobile.com/` | ⬜ 未开始 | 海外版另见 `https://developers.oppomobile.com/` |
| vivo | vivo 开放平台 | `https://dev.vivo.com.cn/` | ⬜ 未开始 | 沙箱环境 `https://sandbox-dev.vivo.com.cn/` |
| 腾讯 / 应用宝 | 腾讯开放平台 | `https://open.tencent.com/`，开发者中心 `https://open.tencent.com/developer/center`（备用 `https://app.open.qq.com/`、`https://open.qq.com/app_plus`） | 🔶 待软著下证 | 走 QQ 账号体系；材料见 `docs/software-copyright.md` |
| 魅族 | Flyme 开放平台 | `https://open.flyme.cn/` | ⬜ 未开始 | — |
| 三星（中国） | 三星开发者 | `https://dev.samsung.net.cn/` | ⬜ 未开始 | 全球 Seller Portal `https://seller.samsungapps.com/` |

OPPO 与 vivo 入口为 2026-10-12 联网核实结果；荣耀、小米、腾讯入口与仓库已有 checklist 一致。

## 2. 海外 / 跨平台

| 渠道 | 后台入口 | 状态 | 备注 |
|---|---|---|---|
| Google Play | `https://play.google.com/console/` | ⬜ 未开始 | 必须传 AAB（44,977,957 字节，SHA-256 `BB0A456E8F91AC9412512E719966CF23A789D0E3564C0861FA31618CEF17BBD`）；需一次性注册费 25 USD；Data Safety、权限声明、个性化/广告声明须逐项填 |
| 华为 AppGallery（海外） | 同 AGC，按语言/地区发布 | 同国内 | 与国内共用账号与后台 |
| 三星 Galaxy Store（全球） | `https://seller.samsungapps.com/` | ⬜ 未开始 | 与三星中国分开运营 |

## 3. 第三方分发渠道（可选补充）

| 渠道 | 后台入口 | 状态 |
|---|---|---|
| 360 手机助手 / 360 开放平台 | `https://dev.360.cn/`（`https://open.360.cn/`） | ⬜ |
| 百度手机助手 | `https://app.baidu.com/` | ⬜ |
| 豌豆荚 / 阿里应用分发 | `https://www.wandoujia.com/developer`（阿里侧 `https://open.uc.cn/`） | ⬜ |
| 联想乐商店 | `https://www.lenovomm.com/` | ⬜ |

第三方渠道入口偶有改版，打开后以官网实际页面为准。

## 4. 关联文档

| 文档 | 内容 |
|---|---|
| `docs/huawei-appgallery-submission-checklist.md` | 华为逐页填写稿、硬门槛、驳回应对 |
| `docs/huawei-permission-statement.md` | 华为敏感权限说明文案与包体自查 |
| `docs/honor-store-submission-checklist.md` | 荣耀提交清单与素材 |
| `docs/xiaomi-store-submission-checklist.md` | 小米资质、素材与敏感权限申请 |
| `docs/tencent-appstore-submission-checklist.md` | 腾讯/应用宝资质与素材 |
| `docs/store-listing-form-answers.md` | 各市场通用填表稿与版本更新说明文案 §0.5 |
| `docs/software-copyright.md` | 软著申请材料与填报稿 |
| `docs/APP_STORE_REVIEW_GUIDE.md` | 权限申报、隐私要求、素材规格与驳回历史 |

## 5. 建议推进顺序

1. **荣耀**：账号已通过，最近期；先修正 SHA256 指纹格式并申请应用市场模块权限。
2. **华为**：把线上 0.9.0(9) 升级到 1.0.0(10)。
3. **小米 / OPPO / vivo**：三家并进，均需 APP 备案号（已办理）；注意 UI 已改版，现有商店截图过期，须用荣耀 ALP-AN00 真机重截。
4. **腾讯应用宝**：待软著下证后提交。
5. **Google Play**：需 AAB 与英文素材，敏感权限需英文说明。
6. **第三方渠道**：按需补充分发面。
