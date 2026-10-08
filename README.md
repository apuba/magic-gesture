# 魔法手势

魔法手势是一款 Android 隔空手势控制应用。应用通过前置摄像头在设备本机识别用户主动做出的手势，并将手势映射为光标、点击、页面滑动、返回、媒体控制、截图、自拍和打开应用等操作。

## 当前版本

| 项目 | 当前状态 |
|---|---|
| 版本 | V0.9.0（`versionCode=9`） |
| 应用 ID | `com.magicgesture.app` |
| Android 版本 | `minSdk=26`，`targetSdk=36`，`compileSdk=36` |
| 手势范围 | G01–G35，不新增 G36 及后续编号 |
| 正式版初始开放 | G01、G02、G05、G06、G24、G11、G13 |
| 解锁方式 | 每日主动签到一次，累计 7 次后解锁全部现有功能包 |
| 账号与付费 | 当前无账号、登录、订阅、支付和付费解锁 |
| 网络 | 当前最终安装包不声明网络及网络状态权限 |

G03、G04、G09、G10 当前默认关闭且未绑定动作，编号保留给应用真实公开上线后的二期轨迹规划。当前版本不包含画 S、画圆、画 V 等轨迹识别。

## 主要功能

- 单食指控制全局光标，并通过手势完成点击。
- 四指并拢向上、下、左、右移动，执行对应方向的页面滑动。
- 支持返回、桌面、最近任务、通知栏和系统截图等系统动作。
- 支持播放/暂停、静音、切歌和持续音量控制。
- 支持使用应用内部前置摄像头自拍，不打开第三方相机。
- 支持普通截图和滚动长截图，并保存到系统相册。
- 支持为手势换绑动作；首页动作标签实时显示当前绑定结果。
- 支持为指定手势选择需要打开的应用。
- 支持在第三方 App 中标定收藏按钮位置，由用户主动手势触发确定性点击。
- 多手同时出现时，由视觉上最靠近摄像头的手独占控制权。

完整编号、姿势、默认动作和换绑范围以 [手势与动作映射清单](docs/GESTURE_ACTION_MAPPING_CHECKLIST.md) 为准。

## 首次使用

1. 在首页阅读无障碍服务用途说明，自主决定是否继续。
2. 按系统引导开启摄像头、悬浮窗和无障碍服务。
3. Android 13 及以上可授予通知权限，用于显示前台服务运行状态和停止入口。
4. 点击“启动手势控制”，等待悬浮状态点和前台服务通知出现。
5. 进入“手势练习与校准”，按实际使用习惯选择灵敏度、开关识别反馈并调整动作映射。
6. 不再使用时点击“停止所有控制”，应用将停止摄像头识别并移除悬浮控件。

隔空识别效果会受到光线、背景、手部距离、动作稳定性、摄像头规格和设备性能影响。构建成功不代表所有机型上的识别效果都已通过验收。

## 隐私与权限

- 摄像头画面和手部关键点只用于设备本机实时识别；日常识别帧不保存为照片或视频。
- 只有用户主动触发自拍、截图或滚动长截图时，应用才会把图片保存到系统相册 `Pictures/MagicGesture/`。
- 无障碍服务配置保持 `canRetrieveWindowContent=false`，不读取第三方页面文字、密码、聊天内容或控件树。
- 无障碍服务只执行用户主动手势对应的确定性动作，不根据页面文字自主点击。
- 手势开关、灵敏度、动作映射、签到进度、指定应用包名和收藏坐标保存在应用本机私有存储。
- 清除应用数据或卸载应用会删除本机配置与签到记录，但不会自动删除已经保存到系统相册的图片。
- 当前版本没有账号、广告、在线统计、云同步或服务端权益功能。
- 当前最终 Debug/Release APK 均不包含 `INTERNET` 和 `ACCESS_NETWORK_STATE` 权限。

[隐私政策](docs/PRIVACY_POLICY.md) 已补充公开主体名和隐私联系邮箱，正式在线地址为 [https://magicgesture.mt4000.com/privacy-policy.html](https://magicgesture.mt4000.com/privacy-policy.html)，供应用商店后台和未安装 App 的用户访问。App 首页提供离线“隐私政策与权限说明”入口，完整正文随 APK 打包，不依赖网络，应用内不再提供重复的在线版本按钮。

## 正式版解锁规则

- 所有正式 Release 用户使用同一套规则。
- 初始开放 5 个基础手势编号（开箱当天不能签到，第 1 次签到最早次日）。
- 用户每天主动签到一次，永久解锁下一个功能包。
- 一天最多一次，断签不清零，已经解锁的功能不会因后续断签被收回。
- 累计 7 次有效签到后，拥有 G01–G35 全部现有编号。
- 解锁记录只保存在本机，卸载、清除数据或换机后不会自动恢复。
- Debug/内部测试构建可全部解锁，但该能力通过构建类型隔离，不进入正式 Release。

详细产品规则见 [签到解锁需求](docs/GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md)。

## 开发环境与构建

推荐环境：

- JDK 17
- Android SDK 36
- 项目自带 Gradle Wrapper
- 具备前置摄像头的 Android 真机

基础验证：

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

发布验证：

```powershell
.\gradlew.bat assembleRelease bundleRelease lintRelease
```

构建产物：

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`
- Release APK：`app/build/outputs/apk/release/app-release.apk`
- Release AAB：`app/build/outputs/bundle/release/app-release.aab`

正式签名文件、密码、Token 和私钥不得写入文档、日志、聊天记录或 Git。构建通过、APK 安装、真机验收、应用市场审核和真实上线是不同状态，不得互相替代。

## 文档导航

- [开发与接手状态](docs/IDE_DEVELOPMENT_HANDOFF.md)
- [G01–G35 手势与动作映射](docs/GESTURE_ACTION_MAPPING_CHECKLIST.md)
- [Android V1 开发规格](docs/MAGIC_GESTURE_ANDROID_V1_DEVELOPMENT_SPEC.md)
- [签到解锁产品需求](docs/GESTURE_UNLOCK_PRODUCT_REQUIREMENTS.md)
- [现有功能需求变更记录](docs/REQUIREMENTS_CHANGELOG.md)
- [真机验收记录](docs/GESTURE_REALTIME_ACCEPTANCE.md)
- [隐私政策草案](docs/PRIVACY_POLICY.md)
- [可部署的隐私政策 HTML](docs/privacy-policy.html)
- [应用市场审核材料清单](docs/APP_STORE_REVIEW_GUIDE.md)

## 当前发布边界

当前阶段只处理 G01–G35 的真机测试、缺陷修复、兼容性优化、性能优化和应用市场上架准备。二期自定义手势、轨迹动作、账号、支付、分享邀请和服务端能力必须等应用真实公开上线后，根据实际用户反馈重新规划。
