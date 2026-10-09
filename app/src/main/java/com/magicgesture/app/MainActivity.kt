package com.magicgesture.app

import android.Manifest
import android.animation.ObjectAnimator
import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.app.Dialog
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.RelativeSizeSpan
import android.view.animation.LinearInterpolator
import android.view.accessibility.AccessibilityManager
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
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
    private lateinit var startControlButton: TextView
    /** 中央按钮上方的状态图标（播放三角 / 停止徽标 / 警示图标），独立成行以便精确控制间距。 */
    private lateinit var heroControlIcon: ImageView
    private lateinit var overlayPermissionButton: View
    private lateinit var accessibilityPermissionButton: View
    private lateinit var setupGuideContainer: LinearLayout
    /** 未授权时需要隐藏的区块：中间快捷菜单（手势校准/手势映射等）与每日签到卡。 */
    private lateinit var quickAccessRow: View
    private lateinit var checkInCardView: View
    private lateinit var checkInSummary: TextView
    private val requestCamera = 100
    private var pendingControl = false
    private var waitingForOverlayPermission = false
    private var waitingForAccessibilityPermission = false
    private val featureSwitches = mutableMapOf<String, MutableList<Switch>>()
    private val actionLabelViews = mutableListOf<Pair<TextView, () -> String>>()
    /** Latest home ScrollView, used to keep the scroll position when the page is rebuilt. */
    private var contentScroll: ScrollView? = null
    private var tipsAnchor: View? = null
    /** “常用手势”标题行锚点，底部导航“手势列表”滚动目标。 */
    private var gesturesAnchor: View? = null
    /** 中央状态环：外圈、辉光圈、彩色渐变环，颜色随真实控制状态切换。 */
    private lateinit var heroOuterRing: View
    private lateinit var heroGlowRing: View
    private lateinit var heroColorRing: View
    /** 运行态中央渐变环的旋转动画；非运行态与页面不可见时必须停掉并归零。 */
    private var heroRingSpinAnimator: ObjectAnimator? = null
    private var updatingFeatureSwitches = false
    /** 当前已解锁的手势编号；Debug 构建全开，正式版按签到进度。 */
    private var unlockedCodes: Set<GestureCode> = GestureUnlockPlan.BASE_CODES.toSet()
    /** 首页内容是否已构建；未同意隐私政策时为 false，此时 onResume 不能刷新首页控件。 */
    private var homeContentReady = false
    /** 首启隐私政策弹窗；Activity 销毁时必须关闭，避免窗口泄漏。 */
    private var consentDialog: Dialog? = null
    /**
     * 识别锁由后台服务切换，首页停在前台时不会收到 onResume。这里监听服务的锁状态广播，
     * 保证解除入口与状态文字在锁定/解锁的瞬间就能刷新，而不用等用户退出页面再进来。
     */
    private val recognitionLockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != CameraProbeService.ACTION_RECOGNITION_LOCK_CHANGED) return
            if (!homeContentReady) return
            runOnUiThread {
                refreshControlButton()
                refreshStatusText(force = true)
            }
        }
    }
    /** 锁状态广播是否已在 onResume 注册；onPause 时据此安全注销。 */
    private var recognitionLockReceiverRegistered = false

    companion object {
        /** 未解锁卡片的淡化程度：内容仍可读，但一眼就能与已解锁区分。 */
        private const val LOCKED_CARD_ALPHA = 0.45f
        /** 运行态中央渐变环转一圈的时长：慢到能看出在转，又不会抢视线。 */
        private const val HERO_RING_SPIN_MS = 3200L
        /** 未解锁卡片的图标去色，与淡化一起构成明显的锁定样式。 */
        private val GRAYSCALE_FILTER = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        /** 页面底色 #000620：背景图随页面滚走之后露出的就是这个颜色。 */
        private val COLOR_BG = Color.rgb(0, 6, 32)
        private val COLOR_SURFACE = Color.argb(210, 7, 31, 67)
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val COLOR_MAGENTA = Color.rgb(202, 66, 255)
        /** 手势卡里的动作文字沿用旧版紫色（与卡内「›」箭头同色）。 */
        private val COLOR_ACTION = Color.rgb(91, 87, 218)
        private val COLOR_GREEN = Color.rgb(21, 224, 187)
        private val COLOR_AMBER = Color.rgb(255, 184, 40)
        private val TEXT_SECONDARY = Color.rgb(214, 225, 245)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
        /** 就绪态的中央状态环渐变（与设计稿蓝紫霓虹一致），复用避免每次刷新重建数组。 */
        private val HERO_RING_READY = intArrayOf(
            Color.rgb(10, 218, 255),
            Color.rgb(33, 116, 255),
            Color.rgb(180, 52, 255),
            Color.rgb(255, 107, 235),
            Color.rgb(71, 69, 255),
            Color.rgb(10, 218, 255)
        )

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 隐私政策必须先于任何权限申请和相机/无障碍能力生效：未同意时不构建首页。
        if (!PrivacyConsent.isAccepted(this)) {
            // 未构建首页时给窗口一个纯色背景，避免透明 Activity 透出桌面壁纸。
            window.setBackgroundDrawable(ColorDrawable(Color.rgb(246, 245, 255)))
            showPrivacyConsentDialog()
            return
        }
        enterHome()
    }

    /** Builds the home page. Only reached after the privacy policy has been accepted. */
    private fun enterHome() {
        // 首页是深色霓虹风格；首启同意弹窗仍是浅色主题，因此系统栏颜色在进入首页时再切换。
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        setContentView(buildContent())
        homeContentReady = true
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
        }
    }

    /**
     * First-launch privacy gate. It is shown before the home page exists so nothing else on the
     * screen can request permissions or start the camera while consent is still undecided.
     */
    private fun showPrivacyConsentDialog() {
        val dialog = Dialog(this)
        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)
        dialog.setContentView(buildConsentView(
            onAgree = {
                PrivacyConsent.accept(this@MainActivity)
                consentDialog = null
                dialog.dismiss()
                enterHome()
            },
            onDisagree = {
                Toast.makeText(this@MainActivity, "需要同意隐私政策后才能使用魔法手势", Toast.LENGTH_LONG).show()
                consentDialog = null
                dialog.dismiss()
                finishAffinity()
            }
        ))
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        consentDialog = dialog
        dialog.show()
        // 固定为屏宽的 92%，避免不同机型上文字撑满整屏导致圆角贴边。
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun buildConsentView(onAgree: () -> Unit, onDisagree: () -> Unit): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(22), dp(22), dp(18))
            background = rounded(Color.WHITE, 24)
        }
        root.addView(label("欢迎使用魔法手势", 22f, Color.rgb(31, 31, 55), true))
        root.addView(label("首次使用请先阅读并同意《隐私政策》。同意后不会再重复弹出。", 14f, Color.rgb(104, 102, 126), false).apply {
            setPadding(0, dp(8), 0, dp(14))
        })

        val points = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = rounded(Color.rgb(239, 240, 255), 16)
        }
        for (line in listOf(
            "• 摄像头画面只在手机本机处理，用于识别你的手势，不保存、不上传",
            "• 无障碍服务只执行你主动做出的手势动作，不读取聊天记录、密码和页面文字",
            "• 悬浮窗只用于显示光标、状态点和识别反馈",
            "• 只有你主动触发自拍或截图时，才会把图片保存到本机相册",
            "• 当前版本没有账号、广告、在线统计和云同步"
        )) {
            points.addView(label(line, 13f, Color.rgb(78, 77, 111), false).apply {
                setLineSpacing(0f, 1.25f)
                setPadding(0, dp(4), 0, dp(4))
            })
        }
        root.addView(points, margins(bottom = 12))

        root.addView(label("查看完整《隐私政策》", 14f, Color.rgb(83, 80, 214), true).apply {
            gravity = Gravity.CENTER
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setOnClickListener { startActivity(Intent(this@MainActivity, PrivacyPolicyActivity::class.java)) }
        }, margins(bottom = 4))
        root.addView(label("不同意将无法使用本应用，也不会开启摄像头。", 12f, Color.rgb(157, 92, 20), false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(6), dp(10), dp(14))
        })

        root.addView(actionButton("同意并继续", Color.rgb(83, 80, 214), Color.WHITE, Color.rgb(55, 52, 178), null, onAgree), margins(bottom = 8, height = 50))
        root.addView(actionButton("不同意，退出应用", Color.WHITE, Color.rgb(157, 92, 20), Color.rgb(255, 243, 224), Color.rgb(244, 216, 157), onDisagree), margins(height = 46))
        return ScrollView(this).apply { addView(root) }
    }

    private fun buildContent(): View {
        val features = GesturePreferences.features(this)
        unlockedCodes = GestureUnlockStore(this).entitlement().codes
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(15), dp(18), dp(15), dp(104))
        }

        content.addView(buildBrandHeader(), margins(bottom = 4))
        content.addView(buildStatusHero(), margins(bottom = 10))

        // 授权引导区：两张并排授权卡；两项授权都完成后整块隐藏。
        // 产品要求：红色授权横幅（banner_authorization）、状态提示条与隐私说明文字暂不展示。
        setupGuideContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // 设计稿切片：悬浮窗、无障碍两张图片按钮（图标与文案已烘焙在切片内），整块可点。
        // 未授权时用灰色切片（floating_d / accessible_d），已授权时换回亮色切片。
        overlayPermissionButton = permissionImageButton(
            R.drawable.banner_floating, R.drawable.banner_floating_disabled, "开启悬浮窗"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        accessibilityPermissionButton = permissionImageButton(
            R.drawable.banner_accessible, R.drawable.banner_accessible_disabled, "开启无障碍"
        ) {
            showAccessibilityDisclosure()
        }
        setupGuideContainer.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // 固定 65dp 高：两套切片统一尺寸，状态切换时避免行高跳动。
            addView(overlayPermissionButton,
                LinearLayout.LayoutParams(0, dp(65), 1f).apply { marginEnd = dp(7) })
            addView(accessibilityPermissionButton,
                LinearLayout.LayoutParams(0, dp(65), 1f).apply { marginStart = dp(7) })
        }, margins(bottom = 10))

        content.addView(setupGuideContainer, margins(bottom = 8))
        quickAccessRow = buildQuickAccessGrid()
        content.addView(quickAccessRow, margins(bottom = 12))
        checkInCardView = checkInCard()
        content.addView(checkInCardView, margins(bottom = 14))
        // 建好就按当前授权状态定一次可见性，避免首屏闪现未授权的菜单与签到卡。
        applyPermissionGating()

        val gestureHeader = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label("常用手势", 20f, Color.WHITE, true),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        gesturesAnchor = gestureHeader
        content.addView(gestureHeader)
        val cooldownSeconds = GesturePreferences.cooldownMs(this) / 1000f
        val cooldownText = if (cooldownSeconds == cooldownSeconds.toLong().toFloat()) "${cooldownSeconds.toLong()}" else "%.1f".format(cooldownSeconds)
        content.addView(label(
            "1. 手掌对准前置摄像头，保持在画面中央\n" +
                "2. 做出手势即触发对应动作，成功后冷却 $cooldownText 秒\n" +
                "3. 点手势行的灰字，看该手势的完整做法",
            13f, TEXT_SECONDARY, false
        ).apply {
            // 整段说明放进带边框的面板里，与下方卡片区分开；三步要点分行，一眼看清怎么操作。
            setPadding(dp(13), dp(11), dp(13), dp(11))
            background = neonPanel(14)
            setLineSpacing(dp(4).toFloat(), 1f)
        }, margins(bottom = 14))

        // 分组与排序只依赖签到批次；卡片文案与开关值在这里一次性装配。
        val groups = GestureUnlockPlan.groupedByStage(guideCards(features)) { it.codes }
        groups.forEach { group ->
            val locked = group.items.any { card -> card.codes.any { it !in unlockedCodes } }
            // 开箱即用那一组不再显示「开箱即用 · 已解锁」标题，直接列卡片。
            if (group.stage > GestureUnlockPlan.BASE_STAGE) content.addView(stageHeader(group.stage, locked))
            group.items.forEach { card ->
                content.addView(guideCardView(card, locked), margins(bottom = 12))
            }
        }

        tipsAnchor = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = neonPanel(16)
            addView(label("使用提示", 15f, COLOR_CYAN, true))
            addView(label("• 保持环境光线充足，避免逆光，手掌距离手机约 40–80 厘米\n• 屏幕顶部出现文字提示后即可开始动作；挥动、滚动类手势久等不会失效，会自动重新计时，无需重新摆姿势\n• 动作清晰但不需要用力，完成后先让手势复位\n• 手腕避免被袖口、手套或过宽的饰品遮挡，否则识别会明显变差\n• 识别不到或容易误触时，可到“手势练习与校准”中调整灵敏度\n• 点击通知中的“停止”可立即关闭摄像头和全部控制", 13f, TEXT_SECONDARY, false).apply {
                setPadding(0, dp(7), 0, 0)
                setLineSpacing(0f, 1.2f)
            })
        }
        content.addView(tipsAnchor)

        content.addView(label("隐私政策与权限说明", 13f, COLOR_CYAN, false).apply {
            gravity = Gravity.CENTER
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
            setPadding(dp(12), dp(20), dp(12), dp(4))
            setOnClickListener {
                startActivity(Intent(this@MainActivity, PrivacyPolicyActivity::class.java))
            }
        })

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            // 背景图放进滚动容器，随页面一起上下滚动；图片滚过之后露出 #000620 页底色。
            addView(FrameLayout(this@MainActivity).apply {
                addView(homeBackgroundView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            })
        }
        contentScroll = scroll
        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
            // 底部固定导航按产品要求暂时隐藏：`buildBottomNav()` 保留，需要时把下面这行注释取消即可恢复。
            // addView(buildBottomNav(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
        }
    }

    private fun homeBackgroundView(): View = object : ImageView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            val height = (width * 1870f / 841f).toInt()
            setMeasuredDimension(width, height)
        }
    }.apply {
        setImageResource(R.drawable.home_neon_bg)
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun buildBrandHeader(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(6), dp(4), dp(6))
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.app_icon)
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = "魔法手势"
            background = rounded(Color.argb(80, 27, 71, 140), 14, Color.rgb(35, 146, 255))
            clipToOutline = true
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, dp(8), 0)
            addView(label("魔法手势", 19f, Color.WHITE, true))
            addView(label("用手势，掌控你的手机", 11.5f, TEXT_SECONDARY, false).apply {
                setPadding(0, dp(2), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        // 设计稿切片：图标 + 文字一体的霓虹按钮，直接整图展示。
        addView(headerImageAction(R.drawable.header_trick, "使用技巧") { scrollTo(tipsAnchor) },
            LinearLayout.LayoutParams(dp(54), dp(57)).apply { marginEnd = dp(7) })
        addView(headerImageAction(R.drawable.header_setting, "设置") {
            startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
        }, LinearLayout.LayoutParams(dp(54), dp(57)))
    }

    private fun headerImageAction(drawableRes: Int, description: String, action: () -> Unit) =
        ImageView(this).apply {
            setImageResource(drawableRes)
            scaleType = ImageView.ScaleType.FIT_CENTER
            contentDescription = description
            setOnClickListener { action() }
        }

    private fun buildStatusHero(): View = FrameLayout(this).apply {
        heroOuterRing = View(this@MainActivity).apply {
            background = oval(Color.argb(16, 20, 70, 180), Color.argb(190, 17, 199, 255), 1)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        // 圆环栈整体收缩到原来的 80%（252/238/218/188 → 202/190/174/150）。
        addView(heroOuterRing, FrameLayout.LayoutParams(dp(202), dp(202), Gravity.CENTER))
        heroGlowRing = View(this@MainActivity).apply {
            background = oval(Color.argb(20, 34, 53, 186), Color.argb(135, 37, 137, 255), 1)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(heroGlowRing, FrameLayout.LayoutParams(dp(190), dp(190), Gravity.CENTER))
        heroColorRing = View(this@MainActivity).apply {
            background = ovalGradient(HERO_RING_READY)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        addView(heroColorRing, FrameLayout.LayoutParams(dp(174), dp(174), Gravity.CENTER))
        addView(View(this@MainActivity).apply {
            background = oval(Color.rgb(8, 23, 87), Color.argb(185, 125, 103, 255), 1)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            // 警告态图标 68dp + 文字需要完整排布，放开裁剪作为兜底。
            clipChildren = false
            clipToPadding = false
            val startAction = {
                if (CameraProbeService.recognitionLocked) {
                    startService(Intent(this@MainActivity, CameraProbeService::class.java).setAction(CameraProbeService.ACTION_UNLOCK_RECOGNITION))
                    status.text = "正在解除识别锁定"
                    status.postDelayed({ refreshControlButton() }, 250)
                } else if (CameraProbeService.isControlRunning) {
                    stopService(Intent(this@MainActivity, CameraProbeService::class.java))
                    status.text = "已请求停止，摄像头与悬浮控件即将关闭"
                    status.postDelayed({ refreshControlButton() }, 250)
                } else if (!(Settings.canDrawOverlays(this@MainActivity) && isAccessibilityServiceEnabled())) {
                    // 设计稿：未授权时中央按钮把用户带到下方授权区，由授权卡逐项完成授权。
                    status.text = "请先完成下方悬浮窗与无障碍授权"
                    scrollTo(setupGuideContainer)
                } else {
                    pendingControl = true
                    startProbe()
                }
            }
            // 整块（图标 + 标题 + 副标题）都可点：图标区与副标题区由容器接管，标题由按钮自身接管。
            setOnClickListener { startAction() }
            // 状态图标单独成行：图标与文字的间距由这里的 margin 精确控制
            // （此前用复合 drawable，TextView 的排版把两者间距算成了 34dp）。
            heroControlIcon = ImageView(this@MainActivity).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            // 图标与标题的间距：这里统一给 4dp（原 8dp 减半）。
            addView(heroControlIcon, LinearLayout.LayoutParams(dp(32), dp(32)).apply { bottomMargin = dp(4) })
            // 标题不用 Button：Button 默认 48dp 最小高度与自身排版留白会把图标↔标题、
            // 标题↔说明各撑开十几 dp；改用 TextView，点击由上面的圆环容器统一接管。
            startControlButton = label("开始手势", 17f, Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                includeFontPadding = false
                setLineSpacing(dp(2).toFloat(), 1f)
                setPadding(dp(14), dp(2), dp(14), dp(2))
                background = ovalPressable(Color.TRANSPARENT, Color.argb(75, 67, 51, 190))
            }
            addView(startControlButton, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
            status = label(heroStatusText(), 11.5f, Color.rgb(226, 232, 255), false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(4), 0, dp(4), 0)
            }
            // 标题与说明行的间距同样减半：6dp → 3dp。
            addView(status, LinearLayout.LayoutParams(dp(150), LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(3) })
        }, FrameLayout.LayoutParams(dp(150), dp(150), Gravity.CENTER))
    }.also {
        it.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(216))
    }

    private fun buildQuickAccessGrid(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER
        addView(menuImageButton(R.drawable.menu_calibration, "手势校准") {
            startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
        }, menuButtonParams(end = 4))
        addView(menuImageButton(R.drawable.menu_mapping, "手势映射") {
            startActivity(Intent(this@MainActivity, GestureMappingActivity::class.java))
        }, menuButtonParams(start = 4, end = 4))
        addView(menuImageButton(R.drawable.menu_authorization, "保持授权") {
            startActivity(Intent(this@MainActivity, KeepAuthorizationActivity::class.java))
        }, menuButtonParams(start = 4, end = 4))
        addView(menuImageButton(R.drawable.menu_favorite, "收藏位置") {
            startActivity(Intent(this@MainActivity, FavoriteLocationActivity::class.java))
        }, menuButtonParams(start = 4))
    }

    private fun menuImageButton(image: Int, label: String, action: () -> Unit) = ImageView(this).apply {
        setImageResource(image)
        scaleType = ImageView.ScaleType.FIT_CENTER
        adjustViewBounds = false
        contentDescription = label
        isClickable = true
        isFocusable = true
        foreground = pressable(Color.TRANSPARENT, Color.argb(75, 255, 255, 255), 18)
        setOnClickListener { action() }
    }

    private fun menuButtonParams(start: Int = 0, end: Int = 0) =
        LinearLayout.LayoutParams(0, dp(102), 1f).apply {
            marginStart = dp(start)
            marginEnd = dp(end)
        }

    /**
     * 固定底部导航：控制（回到顶部状态区）、手势列表（滚到常用手势）、设置（校准页）。
     * 设计稿第三个标签是“我的”，当前版本没有个人中心页面，按既定决策使用“设置”。
     */
    private fun buildBottomNav(): View {
        val activeColor = Color.rgb(122, 168, 255)
        val idleColor = TEXT_MUTED
        data class NavItem(val container: LinearLayout, val iconView: TextView, val textView: TextView, val selectable: Boolean)
        val holders = mutableListOf<NavItem>()
        var activeIndex = 0
        fun repaint() {
            holders.forEachIndexed { index, item ->
                val color = if (index == activeIndex) activeColor else idleColor
                item.iconView.setTextColor(color)
                item.textView.setTextColor(color)
                item.container.background = if (index == activeIndex) rounded(Color.argb(70, 24, 62, 140), 16) else null
            }
        }
        fun item(icon: String, title: String, selectable: Boolean, action: () -> Unit): View =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(9), 0, dp(9))
                val iconView = label(icon, 19f, idleColor, true).apply { gravity = Gravity.CENTER }
                val textView = label(title, 11.5f, idleColor, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(0, dp(2), 0, 0)
                }
                addView(iconView)
                addView(textView)
                setOnClickListener {
                    if (selectable) {
                        val index = holders.indexOfFirst { it.container === this }
                        if (index >= 0) {
                            activeIndex = index
                            repaint()
                        }
                    }
                    action()
                }
                holders += NavItem(this, iconView, textView, selectable)
            }
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Color.argb(242, 4, 15, 38))
                cornerRadii = floatArrayOf(
                    dp(22).toFloat(), dp(22).toFloat(),
                    dp(22).toFloat(), dp(22).toFloat(),
                    0f, 0f, 0f, 0f
                )
                setStroke(dp(1), COLOR_BORDER)
            }
            addView(item("⌂", "控制", true) { contentScroll?.smoothScrollTo(0, 0) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(item("☰", "手势列表", true) { scrollTo(gesturesAnchor) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            addView(item("⚙", "设置", false) {
                startActivity(Intent(this@MainActivity, CalibrationActivity::class.java))
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            repaint()
        }
    }

    private fun scrollTo(target: View?) {
        val scroll = contentScroll ?: return
        scroll.post { scroll.smoothScrollTo(0, target?.top ?: 0) }
    }

    /**
     * Rebuilds the whole page while keeping the current scroll position.
     * Needed after a successful check-in: locked cards, action labels and feature switches are
     * decided when each view is created, so simply refreshing the text is not enough to show the
     * newly unlocked gestures. `recreate()` would jump the page back to the top and hide the
     * check-in card, so the content is rebuilt in place instead.
     */
    private fun rebuildContent(preserveScroll: Boolean) {
        val previousScrollY = contentScroll?.scrollY ?: 0
        actionLabelViews.clear()
        featureSwitches.clear()
        // 旧视图马上要被替换，先停掉挂在旧圆环上的动画。
        setHeroRingSpinning(false)
        setContentView(buildContent())
        // Rebuilt views start from their default state, so re-apply live control and permission
        // state. Without the permission refresh, a successful check-in temporarily brought the
        // already-completed two-step setup guide back onto the page.
        refreshControlButton()
        refreshSetupGuide()
        refreshCheckInCard()
        if (preserveScroll) {
            contentScroll?.post { contentScroll?.scrollTo(0, previousScrollY) }
        }
    }

    /**
     * Builds a live action label provider from the gesture codes behind one card; multiple codes
     * (e.g. the two-finger media family) join their labels. Rereads user overrides on each call
     * so home cards stay in sync after remapping in CalibrationActivity.
     */
    private fun actionLabelOf(vararg codes: GestureCode): () -> String {
        // 未解锁的手势先显示解锁条件；用户仍然可以看到姿势与用途，但不能换绑或执行。
        // 多编号卡片按其中最晚的批次提示，避免出现“部分解锁”的错觉。
        if (codes.any { it !in unlockedCodes }) {
            return { GestureUnlockPlan.stageLockLabel(GestureUnlockPlan.stageOfGroup(codes.toList())) }
        }
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

    /**
     * 常用手势行（按设计稿重排）：左侧手势图，中间「标题 + 副标题 + 功能类型标签」，
     * 右侧开关与箭头。副标题开头是当前绑定动作（换绑后自动更新），其后带手势做法摘要。
     * [buildIcon] 负责往 64dp 高的图标区里放手势图（单图 / 双图 / 序列步骤图）。
     */
    /**
     * 一条手势行：标题 + 紫色动作标签（当前绑定动作）+ 两行做法说明。
     * [actionText] 为 null 表示固定动作由外部分配，此时不显示动作标签。
     */
    private fun guideRow(
        title: String,
        description: String,
        actionText: (() -> String)?,
        feature: String,
        enabled: Boolean,
        showSwitch: Boolean,
        locked: Boolean,
        buildIcon: (LinearLayout) -> Unit
    ): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(9), dp(12), dp(9))
        background = rounded(if (locked) Color.argb(190, 19, 35, 62) else COLOR_SURFACE, 18, COLOR_BORDER)
        elevation = if (locked) 0f else dp(2).toFloat()
        if (locked) alpha = LOCKED_CARD_ALPHA
        // 换绑动作已拆到映射页，手势卡整行点击进映射页。
        setOnClickListener { startActivity(Intent(this@MainActivity, GestureMappingActivity::class.java)) }
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER
            buildIcon(this)
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(64)))
        addView(LinearLayout(this@MainActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(11), 0, dp(4), 0)
            addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(label(title, 15.5f, if (locked) Color.rgb(137, 151, 174) else Color.WHITE, true),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (locked) addView(statusChip("未解锁", Color.rgb(112, 110, 132), Color.rgb(233, 233, 238)), chipParams())
            })
            // 做法说明默认两行；整行点击是进校准页，这里单独给说明文字挂点击弹出完整说明
            //（子 View 会消费掉点击，不会冒泡到整行的跳转）。
            val sub = label(description, 12f, if (locked) Color.rgb(128, 145, 171) else Color.rgb(197, 206, 226), false).apply {
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(2), 0, 0)
                setOnClickListener { showGestureGuide(title, description, actionText) }
            }
            addView(sub)
            if (actionText != null) {
                // 动作标签实时读取当前映射，换绑后由 refreshActionLabels() 统一刷新；
                // 刷新文本也要带小图标前缀，否则 onResume 后图标会被纯动作名覆盖。
                val icon = if (feature in appShortcutFeatures) "⚡" else "⚙"
                val tagText = { "$icon ${actionText()}" }
                val tag = actionTag(feature, tagText(), locked)
                actionLabelViews += tag to tagText
                // 外层横向容器给出宽度上限，动作名过长时标签内省略号生效，不会挤压右侧开关。
                addView(LinearLayout(this@MainActivity).apply {
                    addView(tag, LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(5) })
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (showSwitch) addView(featureToggle(feature, enabled, title), LinearLayout.LayoutParams(dp(56), dp(42)))
    }

    /**
     * 动作标签：保留设计稿的小图标（⚙ 系统控制 / ⚡ 应用快捷）在前面，后面接该手势当前绑定的
     * 具体动作（点击、返回、控制光标…），白字 + 设计稿胶囊底色（系统控制蓝 / 应用快捷紫）。
     */
    private fun actionTag(feature: String, text: String, locked: Boolean): TextView {
        val app = feature in appShortcutFeatures
        return label("${if (app) "⚡" else "⚙"} $text", 10.5f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(7), dp(2), dp(7), dp(2))
            background = rounded(
                when {
                    locked -> Color.argb(150, 92, 102, 124)
                    app -> Color.argb(235, 116, 62, 236)
                    else -> Color.argb(235, 33, 92, 226)
                },
                99
            )
        }
    }

    /** 归入「应用快捷」的功能开关，其余均为系统控制。 */
    private val appShortcutFeatures = setOf("screenshot", "selfie", "like", "thumbs_up", "ok")

    private fun gestureCard(
        image: Int, title: String, actionText: () -> String, description: String,
        feature: String, enabled: Boolean, secondImage: Int? = null, showSwitch: Boolean = true,
        locked: Boolean = false
    ): View = guideRow(
        title = title,
        description = description,
        actionText = actionText,
        feature = feature,
        enabled = enabled,
        showSwitch = showSwitch,
        locked = locked
    ) { host ->
        if (secondImage == null) {
            host.addView(ImageView(this@MainActivity).apply {
                setImageResource(image)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                if (locked) colorFilter = GRAYSCALE_FILTER
            }, LinearLayout.LayoutParams(dp(56), dp(56)))
        } else {
            host.addView(LinearLayout(this@MainActivity).apply {
                gravity = Gravity.CENTER
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(image)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    if (locked) colorFilter = GRAYSCALE_FILTER
                }, LinearLayout.LayoutParams(dp(27), dp(56)))
                addView(label("›", 14f, if (locked) Color.rgb(156, 163, 175) else Color.rgb(91, 87, 218), true).apply {
                    gravity = Gravity.CENTER
                }, LinearLayout.LayoutParams(dp(12), dp(56)))
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(secondImage)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    if (locked) colorFilter = GRAYSCALE_FILTER
                }, LinearLayout.LayoutParams(dp(27), dp(56)))
            })
        }
    }

    /** 标题行里的状态标签：与右侧开关之间留出间距。 */
    private fun chipParams() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginEnd = dp(6) }

    /**
     * 每日签到按钮：设计稿霓虹横幅切片，点击即签到，一天最多一次，断签不清零，7 次签到后 G01–G35 全部拥有。
     * 权益只保存在本机，不联网、无账号、无付费入口。
     */
    private fun checkInCard(): View = FrameLayout(this).apply {
        addView(ImageView(this@MainActivity).apply {
            setImageResource(R.drawable.banner_signin)
            adjustViewBounds = true
            contentDescription = "连续签到，解锁更多功能和手势"
        })
        // 进度文字叠在横幅内的空白区（左侧日历图标与右侧「去签到」按钮之间、副标题上方）：
        // 位置按产品标注图红框实测——距横幅左 78dp、上 12dp，无背景无边框。
        checkInSummary = label("", 13.5f, Color.WHITE, true)
        addView(checkInSummary, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ).apply {
            marginStart = dp(78)
            topMargin = dp(12)
        })
        setOnClickListener { performCheckIn() }
        refreshCheckInCard()
    }

    private fun refreshCheckInCard() {
        if (!::checkInSummary.isInitialized) return
        val state = GestureUnlockStore(this).state()
        val total = GestureUnlockPlan.TOTAL_CHECK_INS
        checkInSummary.text = "每日签到 ${state.checkInCount}/$total 次"
    }

    private fun performCheckIn() {
        when (val result = GestureUnlockStore(this).checkIn()) {
            is CheckInResult.Unlocked -> {
                val names = result.newCodes.mapNotNull { GESTURE_DISPLAY_NAMES[it] }.distinct().joinToString("、")
                val unboundHint = if (result.newCodes.any { it == GestureCode.G26 || it == GestureCode.G34 }) {
                    "。其中部分手势默认未绑定动作，可在手势练习与校准页为其指定用途。"
                } else ""
                Toast.makeText(this, "签到成功，已解锁：$names$unboundHint", Toast.LENGTH_LONG).show()
                // 原地重建页面并保留滚动位置，让锁定卡片、动作标签与功能开关立即反映新的权益。
                rebuildContent(preserveScroll = true)
            }
            CheckInResult.AlreadyCheckedIn -> Toast.makeText(this, "今天已经签到过了，明天再来", Toast.LENGTH_SHORT).show()
            CheckInResult.OpeningDay -> Toast.makeText(this, "今天是开箱第一天，明天起可以开始第 1 次签到", Toast.LENGTH_SHORT).show()
            CheckInResult.Completed -> Toast.makeText(this, "全部手势已解锁", Toast.LENGTH_SHORT).show()
        }
    }

    /** 设计稿中的授权入口卡：标题 + 用途说明 + 真实授权按钮，按钮状态由 refreshSetupGuide 维护。 */
    /**
     * 授权区图片按钮：整张设计稿切片即按钮，未授权显示灰色切片、已授权显示亮色切片。
     * 两套切片比例不同（亮色 2.5:1、灰色 2.64:1），因此用固定高度 + FIT_CENTER，
     * 否则按 `adjustViewBounds` 自适应会让状态切换时行高跳动约 3dp。
     */
    private fun permissionImageButton(
        enabledRes: Int,
        disabledRes: Int,
        description: String,
        action: () -> Unit
    ) = ImageView(this).apply {
        setImageResource(disabledRes)
        scaleType = ImageView.ScaleType.FIT_CENTER
        // 两张切片都挂在 tag 上，供状态刷新时按授权情况互换。
        tag = intArrayOf(enabledRes, disabledRes)
        contentDescription = description
        setOnClickListener { action() }
    }

    /**
     * 首页手势指南的一张卡片。[codes] 决定它归属第几次签到，也决定它显示为已解锁还是淡化锁定。
     *
     * 五指张开系列（G13、G16–G19）原先收在一个大容器里，跨了第 0、6、7 批，无法按签到顺序排列；
     * 现在拆成独立卡片，说明里自带“从五指明显张开开始”，开合掌留在开箱即用组，
     * 张掌变指按第 6、7 次签到排到对应分组。
     */
    private data class GuideCard(
        val codes: List<GestureCode>,
        val title: String,
        val description: String,
        val badge: String,
        val feature: String,
        val switchOn: Boolean,
        /** 序列手势（开合掌、张掌变指）用多张步骤图，此时 [image] 不使用。 */
        val image: Int,
        val steps: List<Int>? = null,
        val secondImage: Int? = null,
        val showSwitch: Boolean = true,
        val fixedAction: (() -> String)? = null
    )

    /** 手势指南卡片清单：按签到批次顺序书写，渲染时再按批次分组。 */
    private fun guideCards(features: GestureFeatureConfig): List<GuideCard> = listOf(
        // 开箱即用：7 个基础编号 + 系统级识别锁 G28。
        GuideCard(listOf(GestureCode.G01), "指尖移动", "伸出食指缓慢移动，青色光标会跟随指尖。",
            "◎", "cursor", features.cursor, R.drawable.gesture_point, fixedAction = { "控制光标" }),
        GuideCard(listOf(GestureCode.G02), "指尖轻点", "只伸出食指稳定约 0.2 秒，弯曲食指后在 1 秒内重新伸直。",
            "✓", "click", features.click, R.drawable.gesture_point),
        // G03/G04/G09/G10 are reserved trajectory slots: hidden from this guide, still listed in the practice screen.
        GuideCard(listOf(GestureCode.G05, GestureCode.G06), "并掌上挥 / 并掌下挥", "食指、中指、无名指和小指并拢后整只手上下挥动，拇指不限。",
            "↕", "palm_vertical_scroll", features.palmVerticalScroll, R.drawable.gesture_four_fingers_together),
        GuideCard(listOf(GestureCode.G24), "单指枪·横向", "食指向左伸直，大拇指在食指根部外侧竖起、不得贴近食指关节，两指夹角保持在 45°–90°，其余三指收拢并保持约 0.6 秒。",
            "L", "left_l", features.leftL, R.drawable.gesture_left_l),
        // G28 是系统级识别锁：所有用户可用、不可关闭、不可换绑，因此卡片没有开关。
        GuideCard(listOf(GestureCode.G28), "Love 手势（识别锁）", "大拇指、食指和小指伸展，中指与无名指收拢，稳定保持 1 秒即可锁定识别；再次保持 1 秒解锁。锁定期间只有该手势可用，姿势消失约 0.8 秒后才能再次切换。",
            "♥", "love_lock", features.loveLock, R.drawable.gesture_love, showSwitch = false, fixedAction = { "锁定 / 解锁全部识别" }),
        // 第 1 次签到
        GuideCard(listOf(GestureCode.G11), "V 手势", "食指和中指组成 V 字并稳定保持 2 秒，等待倒计时结束；倒计时期间暂停全部手势识别，可以立刻放下手。",
            "◎", "selfie", features.selfie, R.drawable.gesture_v),
        GuideCard(listOf(GestureCode.G22), "握拳", "四指收拢形成握拳并稳定保持 1 秒，期间显示倒计时。",
            "拳", "play_pause", features.playPause, R.drawable.gesture_fist),
        // 第 2 次签到：双指上下拉持续增减音量，独立开关。
        GuideCard(listOf(GestureCode.G31, GestureCode.G32), "双指上拉 / 双指下拉", "食指与中指并拢伸直，向上或向下拉动后保持姿势；改变姿势后结束保持状态。",
            "2", "two_finger_volume", features.twoFingerVolume, R.drawable.gesture_two_fingers_together),
        // 第 3 次签到：双指左右挥切歌与双指双点播放/暂停，共用切歌开关。
        GuideCard(listOf(GestureCode.G29, GestureCode.G30), "双指左挥 / 双指右挥", "食指与中指并拢伸直、其余手指收起，整只手向左或向右轻挥；只动手指、手腕不跟着移动时不触发。",
            "2", "two_finger_track", features.twoFingerTrack, R.drawable.gesture_two_fingers_together),
        GuideCard(listOf(GestureCode.G33), "双指双点", "食指与中指并拢伸直，两指快速弯下再伸直，连续完成两次。",
            "2", "two_finger_track", features.twoFingerTrack, R.drawable.gesture_two_fingers_together),
        // 第 4 次签到
        GuideCard(listOf(GestureCode.G23), "小指手势", "仅伸出小指，拇指、食指、中指和无名指收拢，稳定保持 1 秒；释放后才能再次触发。",
            "静", "pinky_mute", features.pinkyMute, R.drawable.gesture_pinky),
        // 第 5 次签到
        GuideCard(listOf(GestureCode.G21), "OK 手势", "拇指与食指相触，其余三指伸直并保持约 0.6 秒。",
            "OK", "ok", features.ok, R.drawable.gesture_ok),
        // 第 6 次签到
        GuideCard(listOf(GestureCode.G16), "张掌变一指", "从五指明显张开开始：稳定后收起其他手指只保留食指并保持约 0.6 秒。",
            "1", "open_app_1", features.openApp1, 0, steps = listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_1)),
        GuideCard(listOf(GestureCode.G17), "张掌变二指", "从五指明显张开开始：稳定后收起其他手指保留食指与中指并保持约 0.6 秒。",
            "2", "open_app_2", features.openApp2, 0, steps = listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_2)),
        // 第 7 次签到
        GuideCard(listOf(GestureCode.G18), "张掌变三指", "从五指明显张开开始：稳定后保留食指、中指与无名指并保持约 0.6 秒。",
            "3", "open_app_3", features.openApp3, 0, steps = listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_3)),
        GuideCard(listOf(GestureCode.G19), "张掌变四指", "从五指明显张开开始：稳定后收起大拇指保留四指并保持约 0.6 秒。",
            "4", "open_app_4", features.openApp4, 0, steps = listOf(R.drawable.gesture_palm, R.drawable.gesture_open_app_4)),
        // 第 8 次签到
        GuideCard(listOf(GestureCode.G14), "莲花指", "拇指与无名指相触，食指、中指和小指伸展并保持约 0.6 秒。",
            "⌂", "lotus_recents", features.lotusRecents, R.drawable.gesture_lotus),
        GuideCard(listOf(GestureCode.G15), "兰花指", "拇指与中指相触，食指、无名指和小指伸展并保持约 0.6 秒。",
            "☰", "orchid_back", features.orchidBack, R.drawable.gesture_orchid),
        // 第 9 次签到
        GuideCard(listOf(GestureCode.G07), "并掌左挥", "食指、中指、无名指和小指并拢后向左挥，拇指不限。",
            "←", "palm_left_scroll", features.palmLeftScroll, R.drawable.gesture_four_fingers_together),
        GuideCard(listOf(GestureCode.G08), "并掌右挥", "食指、中指、无名指和小指并拢后向右挥，拇指不限。",
            "→", "palm_right_scroll", features.palmRightScroll, R.drawable.gesture_four_fingers_together),
        // 第 10 次签到
        GuideCard(listOf(GestureCode.G25), "单指枪·竖向", "食指向上伸直、大拇指向侧面伸出，其余三指收拢，保持约 0.6 秒。识别阈值待真机校准。",
            "L", "l_shape", features.lShape, R.drawable.gesture_l_shape),
        GuideCard(listOf(GestureCode.G27), "C 手势", "食指、中指、无名指和小指并拢弯曲，与大拇指围成明显 C 形；手掌可适度倾斜，保持约 0.6 秒。",
            "C", "c_shape", features.cShape, R.drawable.gesture_c_shape),
        // 第 11 次签到
        GuideCard(listOf(GestureCode.G12), "指尖比心", "拇指压在食指第一关节处并与食指交叉，其余三指收拢握住，稳定保持约 0.6 秒。",
            "♥", "like", features.like, R.drawable.gesture_finger_heart),
        GuideCard(listOf(GestureCode.G20), "拇指赞", "其余四指收拢，大拇指明显向上并稳定保持约 0.6 秒。",
            "👍", "thumbs_up", features.thumbsUp, R.drawable.gesture_thumbs_up),
        // 第 7 次签到：G03/G04/G09/G10 为轨迹预留，首页不展示。
        GuideCard(listOf(GestureCode.G13), "开合掌", "五指必须明显分开并保持，看到提示后握拳，再次将五指明显分开并保持。手指并拢时不会进入该组合。",
            "✋", "screenshot", features.screenshot, 0, steps = listOf(R.drawable.gesture_palm, R.drawable.gesture_fist, R.drawable.gesture_palm)),
        GuideCard(listOf(GestureCode.G26), "抓取手势", "手心正对摄像头，五根手指分别张开并向内弯曲，手指之间不能并拢。保持约 0.6 秒按下手指并持续拖动，移动手掌控制方向，张开手指结束；拖动时长不限。",
            "↔", "claw_drag", features.clawDrag, R.drawable.gesture_claw),
        GuideCard(listOf(GestureCode.G34), "六六顺手势", "大拇指与小指伸出，食指、中指与无名指握住，保持约 0.6 秒。默认未绑定动作，可在校准页映射中指定。",
            "6", "six666", features.six666, R.drawable.gesture_666),
        GuideCard(listOf(GestureCode.G35), "双指枪·竖向", "食指与中指并拢向上，拇指明显向外侧伸出并与食指保持 45°–90°夹角：右手拇指向右，左手拇指向左；无名指和小指收拢，稳定保持 1 秒。",
            "↑", "two_finger_up", features.twoFingerUp, R.drawable.gesture_two_finger_gun)
    )

    /** 渲染一张指南卡片：未解锁时整卡淡化，开关以关闭且不可操作的状态显示。 */
    private fun guideCardView(card: GuideCard, locked: Boolean): View {
        val actionText = card.fixedAction ?: actionLabelOf(*card.codes.toTypedArray())
        // 未解锁手势不参与识别，开关值保持原样，这里只把显示值收敛为关闭。
        val switchOn = card.switchOn && !locked
        return if (card.steps != null) {
            sequenceCard(card.steps, card.title, actionText, card.description, card.feature, switchOn, locked)
        } else {
            gestureCard(card.image, card.title, actionText, card.description, card.feature, switchOn, card.secondImage, card.showSwitch, locked)
        }
    }

    /**
     * 分组标题：说明本组是第几次签到解锁，以及该组当前是否已解锁。
     * 开箱即用组恒为已解锁，标题本身已写明，不再重复加状态标签。
     */
    private fun stageHeader(stage: Int, locked: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, dp(16), 0, dp(8))
        addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(label(
                GestureUnlockPlan.stageTitle(stage), 15.5f,
                if (locked) Color.rgb(128, 145, 171) else Color.WHITE, true
            ), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (stage > GestureUnlockPlan.BASE_STAGE) {
                addView(statusChip(
                    if (locked) "未解锁" else "已解锁",
                    if (locked) Color.rgb(112, 110, 132) else Color.rgb(35, 115, 78),
                    if (locked) Color.rgb(233, 233, 238) else Color.rgb(216, 240, 227)
                ))
            }
        })
        if (locked) {
            addView(label("继续每日签到即可解锁本组手势。", 12f, TEXT_MUTED, false).apply {
                setPadding(0, dp(3), 0, 0)
            })
        }
    }

    private fun statusChip(text: String, textColor: Int, fill: Int) = label(text, 11.5f, textColor, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(9), dp(3), dp(9), dp(3))
        background = rounded(fill, 99)
    }

    /**
     * Sequence gestures (G13, G16-G19) use the same compact row as single gestures,
     * with the step illustrations shrunk into the 64dp icon slot (arrows in between).
     * [actionText] is null when the gesture has a fixed action.
     */
    private fun sequenceCard(
        steps: List<Int>,
        title: String,
        actionText: (() -> String)?,
        description: String,
        feature: String,
        enabled: Boolean,
        locked: Boolean = false
    ): View = guideRow(
        title = title,
        description = description,
        actionText = actionText,
        feature = feature,
        enabled = enabled,
        showSwitch = true,
        locked = locked
    ) { host ->
        host.addView(LinearLayout(this@MainActivity).apply {
            gravity = Gravity.CENTER
            steps.forEachIndexed { index, image ->
                if (index > 0) {
                    addView(label("›", 13f, if (locked) Color.rgb(156, 163, 175) else Color.rgb(91, 87, 218), true).apply {
                        gravity = Gravity.CENTER
                    }, LinearLayout.LayoutParams(dp(10), dp(56)))
                }
                addView(ImageView(this@MainActivity).apply {
                    setImageResource(image)
                    scaleType = ImageView.ScaleType.CENTER_INSIDE
                    if (locked) colorFilter = GRAYSCALE_FILTER
                }, LinearLayout.LayoutParams(dp(24), dp(56)))
            }
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
        "index_vertical_scroll" -> "G03 / G04 轨迹预留"
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
        "two_finger_track" -> "双指切歌与双击播放暂停"
        "two_finger_volume" -> "双指持续增减音量"
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
            "two_finger_track" to features.twoFingerTrack,
            "two_finger_volume" to features.twoFingerVolume,
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
        val permissionsReady = Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()
        when {
            CameraProbeService.recognitionLocked -> {
                // 设计稿：识别锁定态中央图标改用 lock.png（原图 1254x1254，画面本身带约两成留白，
                // 按 36dp 显示与其它状态的图标视觉大小一致），标题只留「解除锁定」。
                startControlButton.text = "解除锁定"
                setHeroControlIcon(resources.getDrawable(R.drawable.lock, theme), 36)
            }
            running -> {
                // 设计稿：运行态 = 深绿圆徽 + 白色圆角方块 + 加粗「运行中」，
                // 副标题「点击停止手势控制」由下方状态行显示。
                startControlButton.text = "运行中"
                setHeroControlIcon(stopIcon(50), 50)
            }
            pendingControl -> {
                startControlButton.text = controlButtonLabel("…", "正在启动")
                setHeroControlIcon(null)
            }
            !permissionsReady -> {
                // 设计稿：未授权时中央按钮改用 warning.png 警示图标，文字只留标题。
                startControlButton.text = "去完成授权"
                setHeroControlIcon(resources.getDrawable(R.drawable.warning, theme), 68, spacingDp = 0)
            }
            else -> {
                // 设计稿：就绪态 = 白色播放三角 + 加粗「开始手势」，副标题「一键启动手势控制」
                // 由下方状态行显示，整体留在内圈圆环里。
                startControlButton.text = "开始手势"
                setHeroControlIcon(playIcon(), 32)
            }
        }
        startControlButton.setTextColor(Color.WHITE)
        startControlButton.background = ovalPressable(Color.TRANSPARENT, Color.argb(75, 67, 51, 190))
        applyHeroState(running, permissionsReady)
        refreshStatusText()
    }

    /**
     * 按设计稿让中央状态环随真实控制状态变色：
     * 未授权红橙、启动中蓝、运行中青绿、识别锁定琥珀、就绪蓝紫。
     * 只改颜色，不改尺寸与层级，避免布局跳动。
     */
    private fun applyHeroState(running: Boolean, permissionsReady: Boolean) {
        if (!::heroColorRing.isInitialized || !::heroGlowRing.isInitialized || !::heroOuterRing.isInitialized) return
        val glow: Int
        val rim: Int
        val ring: IntArray
        when {
            CameraProbeService.recognitionLocked -> {
                glow = Color.argb(120, 255, 184, 40)
                rim = Color.argb(200, 255, 184, 40)
                ring = intArrayOf(Color.rgb(255, 205, 90), Color.rgb(255, 150, 40), Color.rgb(255, 95, 60), Color.rgb(255, 205, 90))
            }
            running -> {
                // 渐变改用「开始手势」那一套蓝紫霓虹（HERO_RING_READY）；
                // 内外描边仍是运行态的青绿，让人一眼看出控制正在运行。
                glow = Color.argb(120, 60, 255, 190)
                rim = Color.argb(200, 60, 255, 190)
                ring = HERO_RING_READY
            }
            pendingControl -> {
                glow = Color.argb(120, 60, 170, 255)
                rim = Color.argb(200, 60, 170, 255)
                ring = intArrayOf(Color.rgb(90, 190, 255), Color.rgb(40, 120, 255), Color.rgb(120, 90, 255), Color.rgb(90, 190, 255))
            }
            !permissionsReady -> {
                glow = Color.argb(130, 255, 90, 70)
                rim = Color.argb(210, 255, 90, 70)
                // 警告态渐变环：按设计稿从顶部橙经粉、品红到红的过渡（取自设计图环带采样；
                // SweepGradient 起始角有固定偏移，色序整体前移一档使顶部落在橙色、右侧落在粉色）。
                ring = intArrayOf(
                    Color.rgb(252, 97, 162),
                    Color.rgb(244, 25, 93),
                    Color.rgb(251, 75, 59),
                    Color.rgb(254, 124, 48),
                    Color.rgb(254, 163, 42),
                    Color.rgb(252, 97, 162)
                )
            }
            else -> {
                glow = Color.argb(190, 17, 199, 255)
                rim = Color.argb(135, 37, 137, 255)
                ring = HERO_RING_READY
            }
        }
        (heroOuterRing.background as GradientDrawable).setStroke(dp(1), rim)
        (heroGlowRing.background as GradientDrawable).setStroke(dp(1), glow)
        heroColorRing.background = ovalGradient(ring)
        setHeroRingSpinning(running)
    }

    /**
     * 手势控制运行时，让中央那圈扫掠渐变环匀速旋转，作为“正在运行”的动态指示。
     *
     * 圆环与内部的图标、标题、说明行是同一个 FrameLayout 里的同级视图，
     * 所以旋转圆环不会带动内容：圆环一直转，文字与图标始终正向。
     * 停下状态请求的动画会立刻取消并把角度归零，避免留在倾斜位置。
     */
    private fun setHeroRingSpinning(spinning: Boolean) {
        if (!::heroColorRing.isInitialized) return
        if (spinning) {
            val existing = heroRingSpinAnimator
            if (existing?.isRunning == true) return
            // 只是暂停过就接着转，避免每次刷新都从 0° 重新开始。
            if (existing != null && existing.isPaused) {
                existing.resume()
                return
            }
            heroRingSpinAnimator = ObjectAnimator.ofFloat(heroColorRing, View.ROTATION, 0f, 360f).apply {
                duration = HERO_RING_SPIN_MS
                interpolator = LinearInterpolator()
                // 无限重复、每圈都从 0° 重新开始（RESTART）：只要控制还在运行，
                // 圆环就一圈接一圈不停下来，不会来回摆动也不会转一轮就停。
                repeatCount = ObjectAnimator.INFINITE
                repeatMode = ObjectAnimator.RESTART
                start()
            }
            return
        }
        heroRingSpinAnimator?.cancel()
        heroRingSpinAnimator = null
        heroColorRing.rotation = 0f
    }

    /**
     * 把顶部状态文字拉回与真实服务状态一致。服务启动成功后不会回调首页，
     * 不回写的话状态会一直停在“准备就绪，等待启动”。只覆盖陈旧文案，
     * 避免冲掉签到、权限等即时提示。
     */
    private fun refreshStatusText(force: Boolean = false) {
        if (!::status.isInitialized) return
        val current = status.text?.toString().orEmpty()
        val stale = current.contains("准备就绪") || current.contains("启动中") || current.contains("已请求停止")
        // 锁状态变化是用户必须立刻看到的信息，此时无条件覆盖；其余场景只清理陈旧文案。
        if (!force && !stale) return
        status.text = heroStatusText()
    }

    /** 中央状态环副标题：始终由真实控制与授权状态推导。 */
    private fun heroStatusText(): String = when {
        CameraProbeService.recognitionLocked -> "识别已锁定，仅 Love 手势可以解锁"
        CameraProbeService.isControlRunning -> "点击停止手势控制"
        !Settings.canDrawOverlays(this) || !isAccessibilityServiceEnabled() -> "需要完成必要授权\n才能使用手势控制"
        else -> "一键启动手势控制"
    }

    /**
     * 授权门控：两项授权没做完时，只保留授权入口，中间的快捷菜单（手势校准/手势映射/保持授权/收藏位置）
     * 与每日签到卡都隐藏；两项授权齐全后授权按钮区隐藏、菜单与签到卡恢复显示。
     */
    private fun applyPermissionGating() {
        val ready = Settings.canDrawOverlays(this) && isAccessibilityServiceEnabled()
        if (::setupGuideContainer.isInitialized) {
            setupGuideContainer.visibility = if (ready) View.GONE else View.VISIBLE
        }
        if (::quickAccessRow.isInitialized) {
            quickAccessRow.visibility = if (ready) View.VISIBLE else View.GONE
        }
        if (::checkInCardView.isInitialized) {
            checkInCardView.visibility = if (ready) View.VISIBLE else View.GONE
        }
    }

    private fun refreshSetupGuide() {
        if (!::overlayPermissionButton.isInitialized || !::accessibilityPermissionButton.isInitialized) return
        val overlayReady = Settings.canDrawOverlays(this)
        val accessibilityReady = isAccessibilityServiceEnabled()
        applyPermissionGating()
        updatePermissionButton(overlayPermissionButton, overlayReady, "悬浮窗")
        updatePermissionButton(accessibilityPermissionButton, accessibilityReady, "无障碍")
    }

    /** 未授权显示灰色切片，已授权显示亮色切片；不再用透明度弱化。 */
    private fun updatePermissionButton(button: View, ready: Boolean, name: String) {
        val ids = button.tag as? IntArray
        if (ids != null && button is ImageView) {
            button.setImageResource(if (ready) ids[0] else ids[1])
        }
        button.contentDescription = if (ready) "$name 已开启" else "去开启$name"
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

    private fun neonPanel(radiusDp: Int) = rounded(COLOR_SURFACE, radiusDp, COLOR_BORDER)

    /** 纯色圆形徽标底（授权横幅警示符等简单线性图标使用）。 */
    private fun circleBadge(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun oval(fill: Int, stroke: Int, strokeWidthDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
        setStroke(dp(strokeWidthDp), stroke)
    }

    private fun ovalGradient(colors: IntArray) = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        shape = GradientDrawable.OVAL
        gradientType = GradientDrawable.SWEEP_GRADIENT
    }

    private fun ovalPressable(fill: Int, pressedFill: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(pressedFill)
        })
        addState(intArrayOf(), GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(fill)
        })
    }

    private fun controlButtonLabel(icon: String, title: String): CharSequence = SpannableString("$icon\n$title").apply {
        setSpan(RelativeSizeSpan(1.72f), 0, icon.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** 设计稿主按钮的白色播放三角（就绪态），按 dp 绘制，铺满给定尺寸。 */
    private fun playIcon(sizeDp: Int = 32): Drawable {
        val size = dp(sizeDp).toFloat()
        val path = Path().apply {
            moveTo(size * 0.10f, size * 0.06f)
            lineTo(size * 0.94f, size * 0.50f)
            lineTo(size * 0.10f, size * 0.94f)
            close()
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        return object : Drawable() {
            override fun draw(canvas: Canvas) = canvas.drawPath(path, paint)
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
            // 必须上报固有尺寸，否则 TextView 按 -1 计算复合 drawable 的排版，图标与文字的相对位置会错。
            override fun getIntrinsicWidth(): Int = dp(sizeDp)
            override fun getIntrinsicHeight(): Int = dp(sizeDp)
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }.apply { setBounds(0, 0, dp(sizeDp), dp(sizeDp)) }
    }

    /** 设计稿运行态按钮：深绿圆徽底 + 白色圆角方块（停止符）。 */
    private fun stopIcon(badgeDp: Int = 52): Drawable {
        val size = dp(badgeDp).toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        return object : Drawable() {
            override fun draw(canvas: Canvas) {
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(235, 12, 74, 56)
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
                val side = size * 0.40f
                paint.color = Color.WHITE
                canvas.drawRoundRect(
                    size / 2f - side / 2f, size / 2f - side / 2f,
                    size / 2f + side / 2f, size / 2f + side / 2f,
                    side * 0.28f, side * 0.28f, paint
                )
            }
            override fun setAlpha(alpha: Int) { paint.alpha = alpha }
            override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
            // 必须上报固有尺寸，否则 TextView 按 -1 计算复合 drawable 的排版，图标与文字的相对位置会错。
            override fun getIntrinsicWidth(): Int = dp(badgeDp)
            override fun getIntrinsicHeight(): Int = dp(badgeDp)
            @Deprecated("Deprecated in Java")
            override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
        }.apply { setBounds(0, 0, dp(badgeDp), dp(badgeDp)) }
    }

    /**
     * 未授权时给中央按钮挂上设计稿的 warning.png（原图 1278x1230，必须按 dp 显式缩放，
     * 否则会按原始像素尺寸撑爆按钮）。
     */
    /**
     * 中央按钮上方的状态图标：就绪=白色播放三角，运行=深绿圆徽停止符，
     * 识别锁定=lock.png，未授权=warning.png（两张都是一千多像素的原图，
     * 由 ImageView 按 dp 缩放，不必再手动 setBounds）。
     * 图标与标题的间距由它在竖向容器里的 bottomMargin 统一控制（10dp）。
     */
    private fun setHeroControlIcon(drawable: Drawable?, sizeDp: Int = 0, spacingDp: Int = 8) {
        if (!::heroControlIcon.isInitialized) return
        if (drawable == null) {
            heroControlIcon.visibility = View.GONE
            return
        }
        heroControlIcon.visibility = View.VISIBLE
        heroControlIcon.setImageDrawable(drawable)
        val params = heroControlIcon.layoutParams
        params.width = dp(sizeDp)
        params.height = dp(sizeDp)
        // 图标行与标题的间距按状态给：警示图标画面本身留白大，用 0 让视觉间距与其他状态一致。
        (params as LinearLayout.LayoutParams).bottomMargin = dp(spacingDp)
        heroControlIcon.layoutParams = params
    }

    private fun margins(bottom: Int = 0, height: Int = LinearLayout.LayoutParams.WRAP_CONTENT) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        if (height > 0) dp(height) else height
    ).apply { bottomMargin = dp(bottom) }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        // 未同意隐私政策时首页尚未构建，此时不能刷新首页控件。
        if (!homeContentReady) return
        // 签到导致的重建会提前 return，所以注册必须放在刷新之前。
        registerRecognitionLockReceiver()
        if (waitingForOverlayPermission) {
            waitingForOverlayPermission = false
            if (Settings.canDrawOverlays(this)) startProbe()
            else status.text = "●  需要允许“显示在其他应用上层”才能显示悬浮点"
        }
        if (waitingForAccessibilityPermission) {
            waitingForAccessibilityPermission = false
            waitForAccessibilityAuthorization()
        }
        val previousUnlocked = unlockedCodes
        unlockedCodes = GestureUnlockStore(this).entitlement().codes
        // Locked cards, switches and labels are decided when their views are created, so a change
        // in entitlements (a check-in done elsewhere, or a fresh install) needs a rebuild.
        if (unlockedCodes != previousUnlocked) {
            rebuildContent(preserveScroll = true)
            return
        }
        refreshCheckInCard()
        refreshFeatureSwitches()
        refreshActionLabels()
        refreshControlButton()
        refreshSetupGuide()
        registerRecognitionLockReceiver()
    }

    /** 只在本应用内接收锁状态广播；Android 13+ 必须显式声明不导出。 */
    private fun registerRecognitionLockReceiver() {
        if (recognitionLockReceiverRegistered) return
        val filter = IntentFilter(CameraProbeService.ACTION_RECOGNITION_LOCK_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(recognitionLockReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(recognitionLockReceiver, filter)
        }
        recognitionLockReceiverRegistered = true
    }

    override fun onPause() {
        if (recognitionLockReceiverRegistered) {
            try {
                unregisterReceiver(recognitionLockReceiver)
            } catch (_: IllegalArgumentException) {
                // 已经注销过或从未注册成功，忽略即可。
            }
            recognitionLockReceiverRegistered = false
        }
        // 页面不可见时不转环：省电，也不会让“运行中”的动画在后台空转。
        setHeroRingSpinning(false)
        super.onPause()
    }

    override fun onDestroy() {
        consentDialog?.dismiss()
        consentDialog = null
        setHeroRingSpinning(false)
        super.onDestroy()
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
            // 服务启动成功后不会回调首页，延迟回读真实状态：
            // 让用户留在首页时也能看到按钮切到「运行中」、圆环开始转。
            status.postDelayed({ refreshControlButton() }, 1200L)
            status.postDelayed({ refreshControlButton() }, 2600L)
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

    /** 手势做法较长，列表里只放两行，点说明文字弹出完整内容。 */
    private fun showGestureGuide(title: String, description: String, actionText: (() -> String)?) {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), dp(4))
            if (actionText != null) {
                addView(label("当前动作：${actionText()}", 13f, COLOR_ACTION, true).apply {
                    setPadding(0, 0, 0, dp(8))
                })
            }
            // 弹窗是浅色主题（白底），说明文字用深色，不能用深色页面上的浅色文字。
            addView(label(description, 14f, Color.rgb(55, 63, 81), false).apply {
                setLineSpacing(dp(3).toFloat(), 1f)
            })
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(panel)
            .setPositiveButton("知道了", null)
            .show()
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
