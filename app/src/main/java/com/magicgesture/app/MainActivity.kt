package com.magicgesture.app

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var startControlButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var setupStatusText: TextView
    private lateinit var setupGuideContainer: LinearLayout
    private lateinit var checkInSummary: TextView
    private lateinit var checkInNext: TextView
    private lateinit var checkInButton: Button
    private val requestCamera = 100
    private var pendingControl = false
    private var waitingForOverlayPermission = false
    private var waitingForAccessibilityPermission = false
    private val featureSwitches = mutableMapOf<String, MutableList<Switch>>()
    private val actionLabelViews = mutableListOf<Pair<TextView, () -> String>>()
    private var updatingFeatureSwitches = false
    /** 当前已解锁的手势编号；Debug 构建全开，正式版按签到进度。 */
    private var unlockedCodes: Set<GestureCode> = GestureUnlockPlan.BASE_CODES.toSet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(72, 69, 198)
        window.navigationBarColor = Color.rgb(246, 245, 255)
        setContentView(buildContent())
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    private fun buildContent(): View {
        val features = GesturePreferences.features(this)
        unlockedCodes = GestureUnlockStore(this).entitlement().codes
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(36))
        }

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(24), dp(22), dp(22))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(74, 72, 211), Color.rgb(142, 91, 224))).apply {
                cornerRadius = dp(26).toFloat()
            }
            elevation = dp(6).toFloat()
            addView(label("魔法手势", 30f, Color.WHITE, true))
            addView(label("让手势成为你的隐形遥控器", 15f, Color.rgb(235, 233, 255), false).apply { setPadding(0, dp(6), 0, 0) })
            addView(label("前置摄像头识别 · 全局悬浮控制 · 本机处理", 12f, Color.rgb(214, 211, 255), false).apply { setPadding(0, dp(16), 0, 0) })
        }, margins(bottom = 14))

        status = label("●  准备就绪，等待启动", 14f, Color.rgb(35, 115, 78), true).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = rounded(Color.rgb(232, 249, 240), 16)
        }
        content.addView(status, margins(bottom = 12))
        content.addView(checkInCard(), margins(bottom = 12))

        setupGuideContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setupGuideContainer.addView(label("首次使用设置", 22f, Color.rgb(31, 31, 55), true))
        setupGuideContainer.addView(label("只需完成以下两项系统授权，即可让魔法手势在其他 App 中持续运行。", 13f, Color.rgb(104, 102, 126), false).apply {
            setPadding(0, dp(6), 0, dp(12))
        })

        overlayPermissionButton = actionButton("去开启悬浮窗权限", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        setupGuideContainer.addView(permissionCard(
            "1", "允许显示悬浮窗",
            "允许“魔法手势-隔空手势”显示在其他应用上层。开启后会显示一个小悬浮圆点，点击圆点可以重新打开 App。",
            "在系统页面中开启“允许显示在其他应用的上层”。",
            overlayPermissionButton
        ), margins(bottom = 10))

        accessibilityPermissionButton = actionButton("去开启无障碍服务", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
            showAccessibilityDisclosure()
        }
        setupGuideContainer.addView(permissionCard(
            "2", "开启无障碍服务",
            "无障碍服务用于执行点击、页面滑动、截图和视频双击等操作。V 字自拍由本 App 的前置摄像头直接保存，不读取其他应用内容。",
            "进入“已下载的应用”或“已安装的服务”，找到本 App 并开启“使用服务”。",
            accessibilityPermissionButton
        ), margins(bottom = 10))

        setupStatusText = label("请先完成以上两项设置，再启动手势控制。", 13f, Color.rgb(157, 92, 20), true).apply {
            setPadding(dp(15), dp(13), dp(15), dp(13))
            background = rounded(Color.rgb(255, 247, 226), 14, Color.rgb(244, 216, 157))
        }
        setupGuideContainer.addView(setupStatusText, margins(bottom = 10))
        setupGuideContainer.addView(label("无障碍服务仅用于执行你主动做出的手势操作，不会读取或上传聊天记录、密码及其他隐私内容。关闭手势控制后，摄像头和悬浮控件会同时停止。", 11.5f, Color.rgb(112, 110, 132), false).apply {
            setPadding(dp(4), 0, dp(4), dp(16))
        })
        content.addView(setupGuideContainer)

        startControlButton = actionButton("✦  启动手势控制", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
            pendingControl = true
            startProbe()
        }
        content.addView(startControlButton, margins(bottom = 10, height = 54))
        content.addView(actionButton("停止所有控制", Color.rgb(255, 240, 242), Color.rgb(190, 51, 67), Color.rgb(255, 216, 222), Color.rgb(255, 205, 213)) {
            stopService(Intent(this@MainActivity, CameraProbeService::class.java))
            status.text = "●  已请求停止，摄像头与悬浮控件即将关闭"
            status.postDelayed({ refreshControlButton() }, 250)
        }, margins(bottom = 10, height = 48))
        content.addView(actionButton("手势练习与校准", Color.WHITE, Color.rgb(37, 99, 235), Color.rgb(219, 234, 254), Color.rgb(191, 219, 254)) {
            startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
        }, margins(bottom = 10, height = 50))
        content.addView(actionButton("⚡  保持授权不丢失", Color.rgb(255, 247, 226), Color.rgb(157, 92, 20), Color.rgb(255, 243, 224), Color.rgb(244, 216, 157)) {
            startActivity(Intent(this@MainActivity, KeepAuthorizationActivity::class.java))
        }, margins(bottom = 24, height = 50))

        content.addView(label("手势使用指南", 22f, Color.rgb(31, 31, 55), true))
        val cooldownSeconds = GesturePreferences.cooldownMs(this) / 1000f
        val cooldownText = if (cooldownSeconds == cooldownSeconds.toLong().toFloat()) "${cooldownSeconds.toLong()}" else "%.1f".format(cooldownSeconds)
        content.addView(label("手掌正对前置摄像头，保持在画面中央。卡片紫色文字是当前绑定动作，换绑后会自动更新；灰色说明只描述手势做法。动作成功后进入 $cooldownText 秒冷却期，期间暂停全部手势识别（包括光标）。", 13f, Color.rgb(104, 102, 126), false).apply {
            setPadding(0, dp(6), 0, dp(14))
        })

        content.addView(gestureCard(R.drawable.gesture_point, "指尖移动", { "控制光标" }, "伸出食指缓慢移动，青色光标会跟随指尖。", "◎", "cursor", features.cursor), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "指尖轻点", actionLabelOf(GestureCode.G02), "只伸出食指稳定约 0.2 秒，弯曲食指后在 1 秒内重新伸直。", "✓", "click", features.click), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "指尖上挑 / 指尖下挑", actionLabelOf(GestureCode.G03, GestureCode.G04), "伸出食指保持接近水平，上挑或下挑指尖。", "↕", "index_vertical_scroll", features.indexVerticalScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "并掌上挥 / 并掌下挥", actionLabelOf(GestureCode.G05, GestureCode.G06), "食指、中指、无名指和小指并拢后整只手上下挥动，拇指不限。", "↕", "palm_vertical_scroll", features.palmVerticalScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "并掌左挥", actionLabelOf(GestureCode.G07), "食指、中指、无名指和小指并拢后向左挥，拇指不限。", "←", "palm_left_scroll", features.palmLeftScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "单指左挑", actionLabelOf(GestureCode.G09), "只竖起食指并接近水平，整只手向左轻挑即可。", "←", "index_left_scroll", features.indexLeftScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "并掌右挥", actionLabelOf(GestureCode.G08), "食指、中指、无名指和小指并拢后向右挥，拇指不限。", "→", "palm_right_scroll", features.palmRightScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "单指右挑", actionLabelOf(GestureCode.G10), "只竖起食指并接近水平，整只手向右轻挑即可。", "→", "index_right_scroll", features.indexRightScroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_v, "V 手势", actionLabelOf(GestureCode.G11), "食指和中指组成 V 字并稳定保持 2 秒，等待倒计时结束；倒计时期间暂停全部手势识别，可以立刻放下手。", "◎", "selfie", features.selfie), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_finger_heart, "指尖比心", actionLabelOf(GestureCode.G12), "拇指压在食指第一关节处并与食指交叉，其余三指收拢握住，稳定保持约 0.6 秒。", "♥", "like", features.like), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_thumbs_up, "拇指赞", actionLabelOf(GestureCode.G20), "其余四指收拢，大拇指明显向上并稳定保持约 0.6 秒。", "👍", "thumbs_up", features.thumbsUp), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_ok, "OK 手势", actionLabelOf(GestureCode.G21), "拇指与食指相触，其余三指伸直并保持约 0.6 秒。", "OK", "ok", features.ok), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_fist, "握拳", actionLabelOf(GestureCode.G22), "四指收拢形成握拳并稳定保持 1 秒，期间显示倒计时。", "拳", "play_pause", features.playPause), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_pinky, "小指手势", actionLabelOf(GestureCode.G23), "仅伸出小指，拇指、食指、中指和无名指收拢，稳定保持 1 秒；释放后才能再次触发。", "静", "pinky_mute", features.pinkyMute), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_lotus, "莲花指", actionLabelOf(GestureCode.G14), "拇指与无名指相触，食指、中指和小指伸展并保持约 0.6 秒。", "⌂", "lotus_recents", features.lotusRecents), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_orchid, "兰花指", actionLabelOf(GestureCode.G15), "拇指与中指相触，食指、无名指和小指伸展并保持约 0.6 秒。", "☰", "orchid_back", features.orchidBack), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_left_l, "单指枪·横向", actionLabelOf(GestureCode.G24), "食指向左伸直，大拇指在食指根部外侧竖起、不得贴近食指关节，两指夹角保持在 45°–90°，其余三指收拢并保持约 0.6 秒。", "L", "left_l", features.leftL), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_l_shape, "单指枪·竖向", actionLabelOf(GestureCode.G25), "食指向上伸直、大拇指向侧面伸出，其余三指收拢，保持 1 秒（有倒计时提示）。识别阈值待真机校准。", "L", "l_shape", features.lShape), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_claw, "抓取手势", actionLabelOf(GestureCode.G26), "手心正对摄像头，五根手指分别张开并向内弯曲，手指之间不能并拢。保持约 0.6 秒按下手指并持续拖动，移动手掌控制方向，张开手指结束；拖动时长不限。", "↔", "claw_drag", features.clawDrag), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_c_shape, "C 手势", actionLabelOf(GestureCode.G27), "食指、中指、无名指和小指并拢弯曲，与大拇指围成明显 C 形；手掌可适度倾斜，保持约 0.6 秒。", "C", "c_shape", features.cShape), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_love, "Love 手势", actionLabelOf(GestureCode.G28), "大拇指、食指和小指伸展，中指与无名指收拢，保持约 0.6 秒。", "♥", "love_lock", features.loveLock), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_666, "六六顺手势", actionLabelOf(GestureCode.G34), "大拇指与小指伸出，食指、中指与无名指握住，保持约 0.6 秒。默认未绑定动作，可在校准页映射中指定。", "6", "six666", features.six666), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_two_fingers_together, "双指左挥 / 双指右挥", actionLabelOf(GestureCode.G29, GestureCode.G30), "食指与中指并拢伸直、其余手指收起，整只手向左或向右轻挥；只动手指、手腕不跟着移动时不触发。", "2", "two_finger_media", features.twoFingerMedia), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_two_fingers_together, "双指上拉 / 双指下拉", actionLabelOf(GestureCode.G31, GestureCode.G32), "食指与中指并拢伸直，向上或向下拉动后保持姿势；改变姿势后结束保持状态。", "2", "two_finger_media", features.twoFingerMedia), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_two_fingers_together, "双指双点", actionLabelOf(GestureCode.G33), "食指与中指并拢伸直，两指快速弯下再伸直，连续完成两次。", "2", "two_finger_media", features.twoFingerMedia), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_two_finger_gun, "双指枪·竖向", actionLabelOf(GestureCode.G35), "食指与中指并拢向上，拇指向侧面伸出，无名指和小指收拢，稳定保持 1 秒；主要用于刷短视频时翻到下一个视频。", "↑", "two_finger_up", features.twoFingerUp), margins(bottom = 12))
        content.addView(palmSeriesCard(features), margins(bottom = 18))

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.rgb(239, 240, 255), 16)
            addView(label("使用提示", 15f, Color.rgb(66, 63, 160), true))
            addView(label("• 保持环境光线充足，避免逆光，手掌距离手机约 40–80 厘米\n• 屏幕顶部出现文字提示后即可开始动作；挥动、滚动类手势久等不会失效，会自动重新计时，无需重新摆姿势\n• 动作清晰但不需要用力，完成后先让手势复位\n• 手腕避免被袖口、手套或过宽的饰品遮挡，否则识别会明显变差\n• 识别不到或容易误触时，可到“手势练习与校准”中调整灵敏度\n• 点击通知中的“停止”可立即关闭摄像头和全部控制", 13f, Color.rgb(78, 77, 111), false).apply {
                setPadding(0, dp(7), 0, 0)
                setLineSpacing(0f, 1.2f)
            })
        })

        return ScrollView(this).apply {
            setBackgroundColor(Color.rgb(246, 245, 255))
            isFillViewport = true
            addView(content)
        }
    }

    /**
     * Builds a live action label provider from the gesture codes behind one card; multiple codes
     * (e.g. the two-finger media family) join their labels. Rereads user overrides on each call
     * so home cards stay in sync after remapping in CalibrationActivity.
     */
    private fun actionLabelOf(vararg codes: GestureCode): () -> String {
        // 未解锁的手势先显示解锁条件；用户仍然可以看到姿势与用途，但不能换绑或执行。
        val locked = codes.firstOrNull { it !in unlockedCodes }
        if (locked != null) return { lockLabel(locked) }
        return {
            val manager = GestureMappingManager(GesturePreferences.actionOverrides(this))
            val labels = codes.mapNotNull { code ->
                val action = manager.actionFor(code) ?: return@mapNotNull null
                if (action == GestureAction.OPEN_APP) {
                    val pkg = GesturePreferences.openAppPackage(this, code)
                    val appName = pkg?.let {
                        runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(it, 0)).toString() }.getOrNull()
                    }
                    "打开应用：${appName ?: "未选择"}"
                } else action.displayLabel()
            }.distinct()
            if (labels.isEmpty()) "未绑定动作" else labels.joinToString(" / ")
        }
    }

    /** 未解锁手势的紫色动作标签文案。 */
    private fun lockLabel(code: GestureCode): String {
        val required = GestureUnlockPlan.checkInRequiredFor(code)
        val name = GESTURE_DISPLAY_NAMES[code] ?: code.name
        return if (required == null) "$name 未解锁" else "$name 未解锁 · 第 $required 次签到后开放"
    }

    private fun gestureCard(image: Int, title: String, actionText: () -> String, description: String, badge: String, feature: String, enabled: Boolean, secondImage: Int? = null): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(15), dp(12))
        background = rounded(Color.WHITE, 20)
        elevation = dp(2).toFloat()
        val imageWidth = if (secondImage == null) 96 else 132
        addView(FrameLayout(this@MainActivity).apply {
            background = rounded(Color.rgb(245, 243, 255), 16)
            if (secondImage == null) {
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(image)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    setPadding(dp(5), dp(5), dp(5), dp(5))
                }, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
            } else {
                addView(LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(image)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }, LinearLayout.LayoutParams(dp(51), dp(82)))
                    addView(label("→", 16f, Color.rgb(91, 87, 218), true).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(18), dp(82)))
                    addView(ImageView(this@MainActivity).apply {
                        setImageResource(secondImage)
                        scaleType = ImageView.ScaleType.CENTER_INSIDE
                    }, LinearLayout.LayoutParams(dp(51), dp(82)))
                }, FrameLayout.LayoutParams(dp(124), dp(88), Gravity.CENTER))
            }
            addView(label(badge, 18f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(91, 87, 218), 99)
            }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END))
        }, LinearLayout.LayoutParams(dp(imageWidth), dp(96)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(title, 17f, Color.rgb(38, 37, 59), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(featureToggle(feature, enabled, title), LinearLayout.LayoutParams(dp(56), dp(48)))
            })
            val actionView = label(actionText(), 13f, Color.rgb(91, 87, 218), true).apply { setPadding(0, dp(3), 0, 0) }
            actionLabelViews += actionView to actionText
            addView(actionView)
            addView(label(description, 12.5f, Color.rgb(105, 103, 124), false).apply {
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(0f, 1.1f)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /**
     * 每日签到卡片：必须主动点击，一天最多一次，断签不清零，12 次签到后 G01–G35 全部拥有。
     * 权益只保存在本机，不联网、无账号、无付费入口。
     */
    private fun checkInCard(): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(15), dp(16), dp(15))
        background = rounded(Color.rgb(239, 240, 255), 20, Color.rgb(203, 213, 225))
        elevation = dp(2).toFloat()
        addView(label("每日签到解锁", 17f, Color.rgb(38, 37, 59), true))
        checkInSummary = label("", 13f, Color.rgb(91, 89, 113), false).apply {
            setPadding(0, dp(7), 0, dp(3))
            setLineSpacing(0f, 1.15f)
        }
        addView(checkInSummary)
        checkInNext = label("", 13f, Color.rgb(91, 87, 218), true).apply { setLineSpacing(0f, 1.15f) }
        addView(checkInNext)
        checkInButton = actionButton("今日签到", Color.WHITE, Color.rgb(83, 80, 214), Color.rgb(232, 230, 255), Color.rgb(204, 201, 239)) {
            performCheckIn()
        }
        addView(checkInButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)).apply {
            topMargin = dp(11)
        })
        addView(label("解锁记录只保存在本机，卸载或清除数据可能丢失。", 11.5f, Color.rgb(119, 116, 145), false).apply {
            setPadding(0, dp(9), 0, 0)
        })
        refreshCheckInCard()
    }

    private fun refreshCheckInCard() {
        if (!::checkInSummary.isInitialized || !::checkInNext.isInitialized || !::checkInButton.isInitialized) return
        val state = GestureUnlockStore(this).state()
        val total = GestureUnlockPlan.TOTAL_CHECK_INS
        val complete = GestureUnlockPlan.isComplete(state.checkInCount)
        checkInSummary.text = if (complete) {
            "已完成 $total 次签到，G01–G35 全部解锁。"
        } else {
            "已签到 ${state.checkInCount} / $total 次 · 每天主动签到一次，断签不清零，已解锁的功能永久保留。"
        }
        val next = GestureUnlockPlan.nextPackageLabel(state.checkInCount)
        checkInNext.text = if (next != null) "下一次解锁：$next" else "全部手势已解锁，可在校准页为每个手势指定动作。"
        val canCheckIn = !complete && GestureUnlockStore(this).canCheckInToday()
        checkInButton.text = when {
            complete -> "已全部解锁"
            canCheckIn -> "今日签到"
            else -> "今日已签到"
        }
        checkInButton.isEnabled = canCheckIn
    }

    private fun performCheckIn() {
        when (val result = GestureUnlockStore(this).checkIn()) {
            is CheckInResult.Unlocked -> {
                val names = result.newCodes.mapNotNull { GESTURE_DISPLAY_NAMES[it] }.distinct().joinToString("、")
                val unboundHint = if (result.newCodes.any { it == GestureCode.G26 || it == GestureCode.G34 }) {
                    "。其中部分手势默认未绑定动作，可在手势练习与校准页为其指定用途。"
                } else ""
                Toast.makeText(this, "签到成功，已解锁：$names$unboundHint", Toast.LENGTH_LONG).show()
                // 重建页面，让锁定卡片、动作标签与功能开关立即反映新的权益。
                recreate()
            }
            CheckInResult.AlreadyCheckedIn -> Toast.makeText(this, "今天已经签到过了，明天再来", Toast.LENGTH_SHORT).show()
            CheckInResult.Completed -> Toast.makeText(this, "全部手势已解锁", Toast.LENGTH_SHORT).show()
        }
    }

    private fun permissionCard(step: String, title: String, description: String, hint: String, button: Button): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(15), dp(15), dp(15))
        background = rounded(Color.WHITE, 18, Color.rgb(229, 227, 246))
        elevation = dp(2).toFloat()
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(step, 16f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(91, 87, 218), 99)
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            addView(label(title, 17f, Color.rgb(38, 37, 59), true).apply {
                setPadding(dp(11), 0, 0, 0)
            })
        })
        addView(label(description, 12.5f, Color.rgb(91, 89, 113), false).apply {
            setPadding(0, dp(10), 0, dp(7))
            setLineSpacing(0f, 1.12f)
        })
        addView(label("提示：$hint", 11.5f, Color.rgb(119, 116, 145), false).apply {
            setPadding(0, 0, 0, dp(10))
        })
        addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(46)))
    }

    /**
     * The open-palm family lives in one card: the G13 screenshot sequence and the four
     * "fold to N fingers" app openers all start from the same spread hand, so they are
     * grouped instead of being scattered through the general guide list.
     */
    private fun palmSeriesCard(features: GestureFeatureConfig): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(13))
        background = rounded(Color.rgb(239, 240, 255), 20)
        addView(label("五指张开系列手势", 17f, Color.rgb(38, 37, 59), true))
        addView(label("这些组合手势都从五指明显张开开始，再按卡片所示完成后续姿势。实际执行结果以每张卡片显示的当前绑定动作为准。", 12.5f, Color.rgb(105, 103, 124), false).apply {
            setPadding(0, dp(6), 0, dp(11))
            setLineSpacing(0f, 1.15f)
        })
        addView(screenshotCard(features.screenshot), margins(bottom = 12))
        addView(label("张掌变一指至四指", 14f, Color.rgb(66, 63, 160), true).apply {
            setPadding(0, dp(2), 0, dp(9))
        })
        addView(sequenceCard(
            listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_1), "张掌变一指",
            actionLabelOf(GestureCode.G16),
            "五指张开稳定后，收起其他手指只保留食指并保持约 0.6 秒。",
            "open_app_1", features.openApp1
        ), margins(bottom = 10))
        addView(sequenceCard(
            listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_2), "张掌变二指",
            actionLabelOf(GestureCode.G17),
            "五指张开稳定后，收起其他手指保留食指与中指并保持约 0.6 秒。",
            "open_app_2", features.openApp2
        ), margins(bottom = 10))
        addView(sequenceCard(
            listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_3), "张掌变三指",
            actionLabelOf(GestureCode.G18),
            "五指张开稳定后，保留食指、中指与无名指并保持约 0.6 秒。",
            "open_app_3", features.openApp3
        ), margins(bottom = 10))
        addView(sequenceCard(
            listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_4), "张掌变四指",
            actionLabelOf(GestureCode.G19),
            "五指张开稳定后，收起大拇指保留四指并保持约 0.6 秒。",
            "open_app_4", features.openApp4
        ), margins(bottom = 2))
    }

    /** G13: spread → fist → spread, so the open palm is shown twice. */
    private fun screenshotCard(enabled: Boolean): View = sequenceCard(
        listOf(R.drawable.gesture_palm, R.drawable.gesture_fist, R.drawable.gesture_palm),
        "开合掌",
        actionLabelOf(GestureCode.G13),
        "五指必须明显分开并保持，看到提示后握拳，再次将五指明显分开并保持。手指并拢时不会进入该组合。",
        "screenshot",
        enabled
    )

    /**
     * Sequence gestures (G13, G16-G19) put the step illustrations on top, then the title row,
     * the live action label and the description — the same layout the screenshot card uses.
     * [actionText] is null when the gesture has a fixed action.
     */
    private fun sequenceCard(
        steps: List<Int>,
        title: String,
        actionText: (() -> String)?,
        description: String,
        feature: String,
        enabled: Boolean
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(15))
        background = rounded(Color.WHITE, 20)
        elevation = dp(2).toFloat()
        // Three-step sequences need narrower frames so the whole row still fits a phone.
        val frameWidth = if (steps.size >= 3) dp(60) else dp(78)
        val arrowWidth = if (steps.size >= 3) dp(26) else dp(38)
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER
            steps.forEachIndexed { index, image ->
                if (index > 0) {
                    addView(label("→", 18f, Color.rgb(91, 87, 218), true).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(arrowWidth, dp(76)))
                }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(image)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                }, LinearLayout.LayoutParams(frameWidth, dp(80)))
            }
        })
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(title, 17f, Color.rgb(38, 37, 59), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(featureToggle(feature, enabled, title), LinearLayout.LayoutParams(dp(56), dp(48)))
        })
        if (actionText != null) {
            val actionView = label(actionText(), 13f, Color.rgb(91, 87, 218), true).apply { setPadding(0, dp(4), 0, 0) }
            actionLabelViews += actionView to actionText
            addView(actionView)
        }
        addView(label(description, 12.5f, Color.rgb(105, 103, 124), false).apply {
            setPadding(0, dp(7), 0, 0)
            setLineSpacing(0f, 1.1f)
        })
    }

    private fun actionButton(
        textValue: String,
        fill: Int,
        textColor: Int,
        pressedFill: Int,
        stroke: Int? = null,
        action: () -> Unit
    ) = Button(this).apply {
        text = textValue
        textSize = 15f
        setTextColor(textColor)
        isAllCaps = false
        typeface = Typeface.DEFAULT_BOLD
        background = pressable(fill, pressedFill, 16, stroke)
        stateListAnimator = null
        setOnClickListener { action() }
    }

    private fun pressable(fill: Int, pressedFill: Int, radiusDp: Int, stroke: Int? = null) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedFill, radiusDp, stroke))
        addState(intArrayOf(android.R.attr.state_focused), rounded(pressedFill, radiusDp, stroke))
        addState(intArrayOf(), rounded(fill, radiusDp, stroke))
    }

    private fun featureToggle(feature: String, enabled: Boolean, name: String? = null) = Switch(this).apply {
        val display = name ?: featureName(feature)
        isChecked = enabled
        minWidth = dp(56)
        minHeight = dp(48)
        contentDescription = "启用或关闭${display}"
        // 未解锁的手势不允许开启识别；开关值本身保持不动，解锁后自动恢复可用。
        isEnabled = GESTURE_CODES_BY_FEATURE[feature].orEmpty().none { it !in unlockedCodes }
        setOnCheckedChangeListener { _, checked ->
            if (!updatingFeatureSwitches) {
                GesturePreferences.setFeature(this@MainActivity, feature, checked)
                status.text = "●  ${display}已${if (checked) "开启" else "关闭"}，运行中的控制已即时更新"
                // Sibling switches sharing the same feature stay in sync.
                updatingFeatureSwitches = true
                featureSwitches[feature]?.forEach { if (it !== this) it.isChecked = checked }
                updatingFeatureSwitches = false
            }
        }
        featureSwitches.getOrPut(feature) { mutableListOf() }.add(this)
    }

    private fun featureName(feature: String) = when (feature) {
        "cursor" -> "食指光标"
        "click" -> "食指弯曲点击"
        "index_vertical_scroll" -> "指尖上挑 / 指尖下挑"
        "palm_vertical_scroll" -> "四指上下挥"
        "palm_left_scroll" -> "四指左挥"
        "index_left_scroll" -> "食指左挑"
        "palm_right_scroll" -> "四指右挥"
        "index_right_scroll" -> "食指右挑"
        "screenshot" -> "截图"
        "selfie" -> "V 字自拍"
        "like" -> "比心双击点赞"
        "thumbs_up" -> "拇指赞"
        "ok" -> "OK 收藏当前内容"
        "play_pause" -> "握拳播放/暂停"
        "pinky_mute" -> "小指手势"
        "lotus_recents" -> "莲花指最近任务"
        "orchid_back" -> "兰花指最近任务"
        "left_l" -> "单指枪·横向"
        "l_shape" -> "单指枪·竖向"
        "claw_drag" -> "抓取手势"
        "c_shape" -> "C 手势"
        "love_lock" -> "Love 手势"
        "six666" -> "六六顺手势"
        "two_finger_media" -> "双指媒体控制"
        "open_app_1" -> "张掌变一指"
        "open_app_2" -> "张掌变二指"
        "open_app_3" -> "张掌变三指"
        "open_app_4" -> "张掌变四指"
        else -> "手势"
    }

    private fun refreshFeatureSwitches() {
        val features = GesturePreferences.features(this)
        val values = mapOf(
            "cursor" to features.cursor,
            "click" to features.click,
            "index_vertical_scroll" to features.indexVerticalScroll,
            "palm_vertical_scroll" to features.palmVerticalScroll,
            "palm_left_scroll" to features.palmLeftScroll,
            "index_left_scroll" to features.indexLeftScroll,
            "palm_right_scroll" to features.palmRightScroll,
            "index_right_scroll" to features.indexRightScroll,
            "screenshot" to features.screenshot,
            "selfie" to features.selfie,
            "like" to features.like,
            "thumbs_up" to features.thumbsUp,
            "ok" to features.ok,
            "play_pause" to features.playPause,
            "pinky_mute" to features.pinkyMute,
            "lotus_recents" to features.lotusRecents,
            "orchid_back" to features.orchidBack,
            "left_l" to features.leftL,
            "l_shape" to features.lShape,
            "claw_drag" to features.clawDrag,
            "c_shape" to features.cShape,
            "love_lock" to features.loveLock,
            "six666" to features.six666,
            "two_finger_media" to features.twoFingerMedia,
            "open_app_1" to features.openApp1,
            "open_app_2" to features.openApp2,
            "open_app_3" to features.openApp3,
            "open_app_4" to features.openApp4
        )
        updatingFeatureSwitches = true
        values.forEach { (key, value) -> featureSwitches[key]?.forEach { it.isChecked = value } }
        updatingFeatureSwitches = false
    }

    private fun refreshControlButton() {
        if (!::startControlButton.isInitialized) return
        val running = CameraProbeService.isControlRunning
        startControlButton.text = if (running) "✓  手势控制运行中" else "✦  启动手势控制"
        startControlButton.setTextColor(if (running) Color.WHITE else Color.rgb(83, 80, 214))
        startControlButton.background = if (running) {
            pressable(Color.rgb(83, 80, 214), Color.rgb(55, 52, 178), 16)
        } else {
            pressable(Color.WHITE, Color.rgb(232, 230, 255), 16, Color.rgb(204, 201, 239))
        }
    }

    private fun refreshSetupGuide() {
        if (!::overlayPermissionButton.isInitialized || !::accessibilityPermissionButton.isInitialized) return
        val overlayReady = Settings.canDrawOverlays(this)
        val accessibilityReady = isAccessibilityServiceEnabled()
        if (::setupGuideContainer.isInitialized) {
            setupGuideContainer.visibility = if (overlayReady && accessibilityReady) View.GONE else View.VISIBLE
        }
        updatePermissionButton(overlayPermissionButton, overlayReady, "悬浮窗权限")
        updatePermissionButton(accessibilityPermissionButton, accessibilityReady, "无障碍服务")
        if (::setupStatusText.isInitialized) {
            if (overlayReady && accessibilityReady) {
                setupStatusText.text = "✓ 设置已完成\n现在可以启动手势控制，启动成功后 App 会自动隐藏并返回桌面。"
                setupStatusText.setTextColor(Color.rgb(35, 115, 78))
                setupStatusText.background = rounded(Color.rgb(232, 249, 240), 14, Color.rgb(177, 226, 199))
            } else {
                setupStatusText.text = "请先完成以上两项设置，再启动手势控制。"
                setupStatusText.setTextColor(Color.rgb(157, 92, 20))
                setupStatusText.background = rounded(Color.rgb(255, 247, 226), 14, Color.rgb(244, 216, 157))
            }
        }
    }

    private fun updatePermissionButton(button: Button, ready: Boolean, name: String) {
        button.text = if (ready) "✓ $name 已开启" else "去开启$name"
        button.setTextColor(if (ready) Color.rgb(35, 115, 78) else Color.rgb(83, 80, 214))
        button.background = if (ready) {
            pressable(Color.rgb(232, 249, 240), Color.rgb(216, 240, 227), 16, Color.rgb(177, 226, 199))
        } else {
            pressable(Color.WHITE, Color.rgb(232, 230, 255), 16, Color.rgb(204, 201, 239))
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(fill: Int, radiusDp: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun margins(bottom: Int = 0, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        if (height > 0) dp(height) else height
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Settings.canDrawOverlays(this)) startProbe()
            else status.text = "●  需要允许“显示在其他应用上层”才能显示悬浮点"
        }
        if (waitingForAccessibilityPermission) {
            waitingForAccessibilityPermission = false
            waitForAccessibilityAuthorization()
        }
        unlockedCodes = GestureUnlockStore(this).entitlement().codes
        refreshCheckInCard()
        refreshFeatureSwitches()
        refreshActionLabels()
        refreshControlButton()
        refreshSetupGuide()
    }

    /** Home cards show the live mapping (defaults + user overrides), so refresh after remapping. */
    private fun refreshActionLabels() {
        for ((view, text) in actionLabelViews) view.text = text()
    }

    private fun startProbe() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), requestCamera)
            return
        }
        try {
            if (!Settings.canDrawOverlays(this)) {
                waitingForOverlayPermission = true
                status.text = "●  请允许本 App 显示在其他应用上层"
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                return
            }
            if (pendingControl && !isAccessibilityServiceEnabled()) {
                waitingForAccessibilityPermission = true
                status.text = "●  请了解无障碍服务用途并手动开启，返回后会自动继续启动"
                showAccessibilityDisclosure()
                return
            }
            val controlMode = pendingControl
            startForegroundService(Intent(this, CameraProbeService::class.java).putExtra("control", controlMode))
            pendingControl = false
            status.text = "●  启动中，请等待悬浮圆点与通知出现"
            if (controlMode) {
                status.postDelayed({
                    startActivity(Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    moveTaskToBack(true)
                }, 350)
            }
        } catch (e: Exception) {
            status.text = "●  启动失败：${e.javaClass.simpleName}: ${e.message}"
        }
    }

    private fun showAccessibilityDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("开启无障碍服务前请确认")
            .setMessage(
                "魔法手势使用 Android 无障碍服务，将你主动做出的隔空手势转换为点击、滑动、返回桌面、截图和视频双击等操作。\n\n" +
                    "本 App 不通过无障碍服务读取聊天内容、密码或页面文字；摄像头手势识别在设备本机完成。你可以随时在系统设置中关闭该服务。"
            )
            .setNegativeButton("暂不开启", null)
            .setPositiveButton("我已了解并继续") { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    private fun waitForAccessibilityAuthorization(attempt: Int = 0) {
        if (isAccessibilityServiceEnabled()) {
            status.text = if (ControlAccessibilityService.active != null) {
                "●  无障碍服务已连接，正在启动手势控制"
            } else {
                "●  无障碍授权已保留，正在等待系统连接服务"
            }
            startProbe()
            return
        }
        if (attempt < 5) {
            status.postDelayed({ waitForAccessibilityAuthorization(attempt + 1) }, 300L)
        } else {
            status.text = "●  无障碍服务尚未开启，无法启动手势控制"
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = ComponentName(this, ControlAccessibilityService::class.java)
        val manager = getSystemService(AccessibilityManager::class.java)
        val enabledByManager = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName(it.resolveInfo.serviceInfo.packageName, it.resolveInfo.serviceInfo.name) == expected }
        if (enabledByManager) return true
        if (Settings.Secure.getInt(contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCamera && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) startProbe()
        else if (requestCode == requestCamera) status.text = "●  摄像头未授权，无法启动手势识别"
    }
}
