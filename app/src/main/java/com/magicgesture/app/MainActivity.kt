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

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var startControlButton: Button
    private lateinit var overlayPermissionButton: Button
    private lateinit var accessibilityPermissionButton: Button
    private lateinit var setupStatusText: TextView
    private lateinit var setupGuideContainer: LinearLayout
    private val requestCamera = 100
    private var pendingControl = false
    private var waitingForOverlayPermission = false
    private var waitingForAccessibilityPermission = false
    private val featureSwitches = mutableMapOf<String, MutableList<Switch>>()
    private var updatingFeatureSwitches = false

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
        content.addView(label("手掌正对前置摄像头，保持在画面中央。准备姿势识别后请在 5 秒内完成动作；离散动作成功后进入 2 秒冷却期，期间光标仍可移动。", 13f, Color.rgb(104, 102, 126), false).apply {
            setPadding(0, dp(6), 0, dp(14))
        })

        content.addView(gestureCard(R.drawable.gesture_point, "食指移动", "控制光标", "伸出食指缓慢移动，青色光标会跟随指尖。", "◎", "cursor", features.cursor), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "食指弯曲再伸直", "确认点击", "只伸出食指稳定约 0.2 秒，弯曲食指后在 1 秒内重新伸直。", "✓", "click", features.click), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "食指挑动", "上下滚动页面", "伸出食指保持接近水平，上挑或下挑指尖，滚动当前页面。", "↕", "scroll", features.scroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "四指并拢上下挥", "上下滚动页面", "食指、中指、无名指和小指并拢后整只手上下挥动，拇指不限。", "↕", "scroll", features.scroll), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "四指并拢向左", "页面向左滑动", "食指、中指、无名指和小指并拢后向左挥，拇指不限。", "←", "back", features.back), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "竖直食指向左", "页面向左滑动", "只竖起食指，整只手向左移动。", "←", "back", features.back), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_four_fingers_together, "四指并拢向右", "页面向右滑动", "食指、中指、无名指和小指并拢后向右挥，拇指不限。", "→", "home", features.home), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "竖直食指向右", "页面向右滑动", "只竖起食指，整只手向右移动。", "→", "home", features.home), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_v, "V 字保持", "自拍", "食指和中指组成 V 字并稳定保持 2 秒，倒计时后保存前置摄像头画面。", "◎", "selfie", features.selfie), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_finger_heart, "手指比心保持", "双击点赞视频", "拇指与食指交叉形成小爱心，其余三指自然收拢，稳定保持约 0.6 秒。", "♥", "like", features.like), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_thumbs_up, "竖起大拇指", "双击点赞视频", "其余四指收拢，大拇指明显向上并稳定保持约 0.6 秒。", "👍", "thumbs_up", features.thumbsUp), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_ok, "OK 手势", "确认当前光标", "拇指与食指相触，其余三指伸直并保持约 0.6 秒。需要先启用并移动光标。", "OK", "ok", features.ok), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_fist, "握拳保持", "播放 / 暂停", "四指收拢形成握拳并稳定保持约 0.6 秒，控制当前媒体播放状态。", "▶", "play_pause", features.playPause), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_lotus, "莲花指", "最近任务", "拇指与无名指相触，食指、中指和小指伸展并保持约 0.6 秒。", "Ⅱ", "lotus_recents", features.lotusRecents), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_orchid, "兰花指", "返回", "拇指与中指相触，食指、无名指和小指伸展并保持约 0.6 秒。", "←", "orchid_back", features.orchidBack), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "左 L 手形", "返回", "食指向左伸直、大拇指向上，其余三指收拢，保持约 0.6 秒。识别阈值待真机校准。", "L", "left_l", features.leftL), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_point, "L 手形", "下拉通知栏", "食指向上伸直、大拇指向侧面伸出，其余三指收拢，保持约 0.6 秒。识别阈值待真机校准。", "L", "l_shape", features.lShape), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_fist, "爪形拖动", "拖动操作", "五指向内弯成爪形保持约 0.6 秒开始拖动，移动手掌后张开手指完成拖动。", "↔", "claw_drag", features.clawDrag), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_palm, "C 手形", "最近任务", "五指自然弯曲围成 C 形并保持约 0.6 秒。识别阈值待真机校准。", "C", "c_shape", features.cShape), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_lotus, "Love 手形", "锁屏", "大拇指、食指和小指伸展，中指与无名指收拢，保持约 0.6 秒。仅支持锁屏，解锁需系统验证。", "♥", "love_lock", features.loveLock), margins(bottom = 12))
        content.addView(gestureCard(R.drawable.gesture_v, "两指并拢挥动", "上一曲 / 下一曲", "食指与中指并拢伸直、其余手指收起，整只手向左挥动为上一曲、向右挥动为下一曲。识别阈值待真机校准。", "⏭", "two_finger_media", features.twoFingerMedia), margins(bottom = 12))
        content.addView(screenshotCard(features.screenshot), margins(bottom = 18))

        content.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = rounded(Color.rgb(239, 240, 255), 16)
            addView(label("使用提示", 15f, Color.rgb(66, 63, 160), true))
            addView(label("• 保持环境光线充足，手掌距离手机约 40–80 厘米\n• 动作清晰但不需要用力，完成后先让手势复位\n• 点击通知中的“停止”可立即关闭摄像头和全部控制", 13f, Color.rgb(78, 77, 111), false).apply {
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

    private fun gestureCard(image: Int, title: String, action: String, description: String, badge: String, feature: String, enabled: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(12), dp(15), dp(12))
        background = rounded(Color.WHITE, 20)
        elevation = dp(2).toFloat()
        addView(FrameLayout(this@MainActivity).apply {
            background = rounded(Color.rgb(245, 243, 255), 16)
            addView(ImageView(this@MainActivity).apply {
                setImageResource(image)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(dp(5), dp(5), dp(5), dp(5))
            }, FrameLayout.LayoutParams(dp(88), dp(88), Gravity.CENTER))
            addView(label(badge, 18f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                background = rounded(Color.rgb(91, 87, 218), 99)
            }, FrameLayout.LayoutParams(dp(30), dp(30), Gravity.BOTTOM or Gravity.END))
        }, LinearLayout.LayoutParams(dp(96), dp(96)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), 0, 0, 0)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(title, 17f, Color.rgb(38, 37, 59), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                addView(featureToggle(feature, enabled, title), LinearLayout.LayoutParams(dp(56), dp(48)))
            })
            addView(label(action, 13f, Color.rgb(91, 87, 218), true).apply { setPadding(0, dp(3), 0, 0) })
            addView(label(description, 12.5f, Color.rgb(105, 103, 124), false).apply {
                setPadding(0, dp(6), 0, 0)
                setLineSpacing(0f, 1.1f)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
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

    private fun screenshotCard(enabled: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(14), dp(14), dp(15))
        background = rounded(Color.WHITE, 20)
        elevation = dp(2).toFloat()
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.gesture_palm); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(72), dp(80)))
            addView(label("→", 18f, Color.rgb(91, 87, 218), true).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(38), dp(76)))
            addView(ImageView(this@MainActivity).apply { setImageResource(R.drawable.gesture_fist); scaleType = ImageView.ScaleType.CENTER_INSIDE }, LinearLayout.LayoutParams(dp(72), dp(80)))
        })
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("五指张开 → 握拳 → 五指张开", 17f, Color.rgb(38, 37, 59), true), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(featureToggle("screenshot", enabled), LinearLayout.LayoutParams(dp(56), dp(48)))
        })
        addView(label("五指必须明显分开并保持，看到提示后握拳，再次将五指明显分开并保持完成截图。手指并拢时不会触发。", 12.5f, Color.rgb(105, 103, 124), false).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(8), dp(6), dp(8), 0)
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
        "scroll" -> "上下滚动"
        "back" -> "向左滑动"
        "home" -> "向右滑动"
        "screenshot" -> "截图"
        "selfie" -> "V 字自拍"
        "like" -> "比心双击点赞"
        "thumbs_up" -> "大拇指点赞"
        "ok" -> "OK 确认"
        "play_pause" -> "握拳播放/暂停"
        "lotus_recents" -> "莲花指最近任务"
        "orchid_back" -> "兰花指返回"
        "left_l" -> "左 L 手形返回"
        "l_shape" -> "L 手形通知栏"
        "claw_drag" -> "爪形拖动"
        "c_shape" -> "C 手形最近任务"
        "love_lock" -> "Love 手形锁屏"
        "two_finger_media" -> "两指切换曲目"
        else -> "手势"
    }

    private fun refreshFeatureSwitches() {
        val features = GesturePreferences.features(this)
        val values = mapOf(
            "cursor" to features.cursor,
            "click" to features.click,
            "scroll" to features.scroll,
            "back" to features.back,
            "home" to features.home,
            "screenshot" to features.screenshot,
            "selfie" to features.selfie,
            "like" to features.like,
            "thumbs_up" to features.thumbsUp,
            "ok" to features.ok,
            "play_pause" to features.playPause,
            "lotus_recents" to features.lotusRecents,
            "orchid_back" to features.orchidBack,
            "left_l" to features.leftL,
            "l_shape" to features.lShape,
            "claw_drag" to features.clawDrag,
            "c_shape" to features.cShape,
            "love_lock" to features.loveLock,
            "two_finger_media" to features.twoFingerMedia
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
        refreshFeatureSwitches()
        refreshControlButton()
        refreshSetupGuide()
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
