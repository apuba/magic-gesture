# 魔法手势 V0.9 上架整理版变更说明

本版本基于用户上传的 MVP 源码整理，未修改核心 MediaPipe 手势识别算法。

## 已修改

- applicationId / namespace：`com.magicgesture.app`
- targetSdk：35 → 36
- versionCode：9
- versionName：`0.9.0-beta`
- AccessibilityService：关闭窗口内容读取 `canRetrieveWindowContent=false`
- AccessibilityService：移除 `typeAllMask` 与 `flagReportViewIds`
- 删除未被调用的 `findLikeNode()` 页面节点扫描代码
- 无障碍服务入口增加独立用途披露与“我已了解并继续”主动确认
- 删除误建的 `app/src/main/asssets/` 重复模型目录
- 增加 release buildType 基础配置

## 正式上架前仍需完成

1. 在 Android Studio 本地 Sync 并运行真机回归测试。
2. 创建并永久妥善保管正式 release keystore。
3. 用该 keystore 生成签名 AAB（Google Play）与需要时的 Release APK。
4. 完成隐私政策、Google Play Data Safety、AccessibilityService 与前台摄像头服务声明。
5. 在 API 36 真机/模拟器及主流品牌 Android 设备验证后台、悬浮窗、通知、摄像头和无障碍行为。

## 构建说明

当前处理环境无法访问 `services.gradle.org`，因此无法下载 Gradle 9.3.0 来执行最终编译。源码已做静态一致性检查；请在可联网的 Android Studio 环境中执行 Gradle Sync 和 Build。
