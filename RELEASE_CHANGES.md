# 魔法手势 V0.9 上架整理版变更说明

本版本基于用户上传的 MVP 源码整理，未修改核心 MediaPipe 手势识别算法。

## 已修改

- applicationId / namespace：`com.magicgesture.app`
- targetSdk：35 → 36
- versionCode：9
- versionName：`0.9.0`
- AccessibilityService：关闭窗口内容读取 `canRetrieveWindowContent=false`
- AccessibilityService：移除 `typeAllMask` 与 `flagReportViewIds`
- 删除未被调用的 `findLikeNode()` 页面节点扫描代码
- 无障碍服务入口增加独立用途披露与“我已了解并继续”主动确认
- 删除误建的 `app/src/main/asssets/` 重复模型目录
- 增加 release buildType 基础配置

## 正式上架前仍需完成

1. 永久妥善备份正式 release keystore 与本地签名配置。
2. 完成隐私政策、Google Play Data Safety、AccessibilityService 与前台摄像头服务声明。
3. 在 API 36 真机/模拟器及主流品牌 Android 设备继续验证后台、悬浮窗、通知、摄像头和无障碍行为。
4. 上架前重新生成并归档最终签名 AAB/APK。

## 构建说明

已在 JDK 17、Android SDK 36 与 Gradle 9.3.0 环境完成 Debug/Release Lint、APK 和 AAB 构建；Release APK/AAB 已通过本地签名验证。
