# 魔法手势 V0.9

魔法手势是一款 Android 隔空手势控制应用。应用使用前置摄像头和随包内置的 MediaPipe 手部模型，在设备本机识别用户主动做出的手势，再通过 Android 无障碍服务执行点击、滑动、返回、媒体控制、截图等用户已配置动作。

## 当前版本

- 应用 ID：`com.magicgesture.app`
- 版本：`0.9.0`（`versionCode=9`）
- `minSdk=26`，`targetSdk=36`，`compileSdk=36`
- 正式范围：G01–G35；未经产品负责人重新立项，不增加新编号
- G03/G04/G09/G10 当前默认关闭且未绑定，保留给真实上线后的二期轨迹规划
- 正式 Release 初始开放 7 个基础编号，通过 12 次本机离线每日签到逐步解锁全部功能
- Debug 构建允许全部解锁；正式 Release 不提供付费、账号或隐藏全开入口

完整手势与动作映射见 [`docs/GESTURE_ACTION_MAPPING_CHECKLIST.md`](docs/GESTURE_ACTION_MAPPING_CHECKLIST.md)，当前开发与验证状态见 [`docs/IDE_DEVELOPMENT_HANDOFF.md`](docs/IDE_DEVELOPMENT_HANDOFF.md)。

## 隐私与权限

- 摄像头画面和手部关键点用于本机实时识别；日常识别帧不保存。
- 用户主动触发自拍、截图或滚动长截图时，结果保存到系统相册 `Pictures/MagicGesture/`。
- 无障碍服务只执行用户主动手势对应的确定性动作，`canRetrieveWindowContent=false`，不读取第三方页面文字或控件树。
- 手势开关、映射、签到进度、指定 App 包名和收藏按钮坐标保存在应用本机私有存储。
- 当前构建不声明 `INTERNET` 或 `ACCESS_NETWORK_STATE` 权限，不包含账号、广告、在线统计或服务端同步。
- 完整草案见 [`docs/PRIVACY_POLICY.md`](docs/PRIVACY_POLICY.md)。发布前必须补齐运营主体、联系方式、生效日期和公开访问网址。

## 构建

需要 JDK 17、Android SDK 36 和项目 Gradle Wrapper：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat assembleRelease bundleRelease lintRelease
```

主要产物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release.apk`
- Release AAB：`app/build/outputs/bundle/release/app-release.aab`

构建通过不能代替真机验收、商店审核或真实上线。正式签名文件和口令不得提交到 Git 或写入文档。

## 首次使用

1. 阅读应用内无障碍用途披露，自主决定是否继续。
2. 按系统引导授予摄像头、悬浮窗和无障碍能力；Android 13 及以上可授予通知权限以显示前台服务状态。
3. 点击“启动手势控制”。运行期间会显示前台服务通知和悬浮状态点。
4. 点击“停止所有控制”后，应用停止摄像头识别并移除悬浮控件。

## 上架准备

应用市场申报建议、权限用途、Data Safety 答案草案和审核视频清单见 [`docs/APP_STORE_REVIEW_GUIDE.md`](docs/APP_STORE_REVIEW_GUIDE.md)。任何申报内容都必须以最终上传的 AAB/APK、发布主体和实际隐私政策网址为准。
