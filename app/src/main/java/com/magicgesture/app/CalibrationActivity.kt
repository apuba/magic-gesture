package com.magicgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

/**
 * 手势练习与校准页（深色霓虹版）。
 * 视觉：ui/切片/bg2.png 作为页面背景（841×1870，与首页同比例），卡片、开关、滑杆均为原生组件实现；
 * 业务逻辑（灵敏度、开关、映射、收藏位置、保存）与旧版完全一致。
 */
class CalibrationActivity : Activity() {
    /**
     * Remappable gestures in UI order. The G01 cursor stays fixed and is excluded on purpose;
     * system controls such as the G28 recognition lock are controls, not actions, so they have
     * nothing to remap.
     */
    private val gestureNames = GESTURE_DISPLAY_NAMES.filterKeys { it != GestureCode.G01 && it !in SYSTEM_CONTROL_CODES }

    /** 当前已解锁的手势编号；Debug 构建全开，正式版按签到进度。 */
    private var unlockedCodes: Set<GestureCode> = GestureUnlockPlan.BASE_CODES.toSet()

    /**
     * Actions offered in the picker. Cursor/likes-duplicate variants are excluded on purpose.
     * DRAG sits here like any other action: it is attached to a gesture through the mapping and
     * is not hardcoded to the claw. Its only extra need is a pair of coordinates, so
     * showActionPicker offers it solely to the gesture that produces them.
     */
    private val selectableActions = listOf(
        GestureAction.NONE,
        GestureAction.CLICK, GestureAction.SCROLL_UP, GestureAction.SCROLL_DOWN,
        GestureAction.SCROLL_LEFT, GestureAction.SCROLL_RIGHT,
        GestureAction.BACK, GestureAction.HOME, GestureAction.RECENTS, GestureAction.SCREENSHOT,
        GestureAction.ROLLING_SCREENSHOT, GestureAction.DRAG,
        GestureAction.OPEN_APP, GestureAction.FAVORITE_CURRENT,
        GestureAction.SELFIE, GestureAction.LIKE, GestureAction.CONFIRM, GestureAction.PLAY_PAUSE,
        GestureAction.NOTIFICATIONS, GestureAction.LOCK_SCREEN, GestureAction.VOICE_ASSISTANT,
        GestureAction.VOLUME_UP, GestureAction.VOLUME_DOWN, GestureAction.TOGGLE_MUTE,
        GestureAction.MEDIA_NEXT, GestureAction.MEDIA_PREVIOUS
    )

    private val mappingButtons = mutableMapOf<GestureCode, Button>()
    private lateinit var openApp1Card: SettingCard
    private lateinit var openApp2Card: SettingCard
    private lateinit var openApp3Card: SettingCard
    private lateinit var openApp4Card: SettingCard
    private var selectedSensitivity = "normal"
    private lateinit var stableTile: SensitivityTile
    private lateinit var normalTile: SensitivityTile
    private lateinit var highTile: SensitivityTile
    private lateinit var reverseCard: SettingCard
    private lateinit var feedbackCard: SettingCard
    private lateinit var cursorCard: SettingCard
    private lateinit var clickCard: SettingCard
    private lateinit var indexVerticalScrollCard: SettingCard
    private lateinit var palmVerticalScrollCard: SettingCard
    private lateinit var palmLeftScrollCard: SettingCard
    private lateinit var indexLeftScrollCard: SettingCard
    private lateinit var palmRightScrollCard: SettingCard
    private lateinit var indexRightScrollCard: SettingCard
    private lateinit var screenshotCard: SettingCard
    private lateinit var selfieCard: SettingCard
    private lateinit var likeCard: SettingCard
    private lateinit var thumbsUpCard: SettingCard
    private lateinit var okCard: SettingCard
    private lateinit var playPauseCard: SettingCard
    private lateinit var pinkyMuteCard: SettingCard
    private lateinit var lotusRecentsCard: SettingCard
    private lateinit var orchidBackCard: SettingCard
    private lateinit var leftLCard: SettingCard
    private lateinit var lShapeCard: SettingCard
    private lateinit var clawDragCard: SettingCard
    private lateinit var cShapeCard: SettingCard
    private lateinit var twoFingerTrackCard: SettingCard
    private lateinit var twoFingerVolumeCard: SettingCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        selectedSensitivity = GesturePreferences.sensitivity(this)
        setContentView(buildContent())
        refreshSensitivityButtons()
    }

    private fun buildContent(): View {
        val savedFeatures = GesturePreferences.features(this)
        unlockedCodes = GestureUnlockStore(this).entitlement().codes

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(36))
        }

        // 顶部：圆形返回按钮 + 标题 + 副标题
        content.addView(headerRow(), blockMargins(20))

        // 识别灵敏度卡片
        content.addView(sensitivityCard(), blockMargins(14))

        // 反转左右方向 / 显示识别反馈
        reverseCard = settingCard(
            R.drawable.ic_cal_reverse, "反转左右方向",
            "当实际左右滑动与手势方向相反时开启。光标左右方向也会同步反转。",
            GesturePreferences.reverseHorizontal(this)
        )
        content.addView(reverseCard, blockMargins(12))

        feedbackCard = settingCard(
            R.drawable.ic_cal_feedback, "显示识别反馈",
            "在屏幕顶部显示动作名称，以及 V 字保持进度；开关即时生效。",
            GesturePreferences.feedbackEnabled(this)
        ).apply {
            toggle.setOnCheckedChangeListener { _, enabled ->
                GesturePreferences.saveFeedbackEnabled(this@CalibrationActivity, enabled)
            }
        }
        content.addView(feedbackCard, blockMargins(12))

        // 手势冷却时长
        content.addView(cooldownCard(), blockMargins(26))

        content.addView(title("手势动作映射"))
        content.addView(body("每个手势都可以换成其他动作。列表按签到解锁顺序排列，灰色淡化的手势尚未解锁，不能换绑。点击右侧按钮选择动作，选“默认”恢复出厂设置；修改立即生效，无需重启手势控制。").apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        mappingButtons.clear()
        GestureUnlockPlan.groupedByStage(gestureNames.keys.toList()) { listOf(it) }.forEach { group ->
            val locked = group.items.any { it !in unlockedCodes }
            content.addView(stageHeader(group.stage, locked), blockMargins(10))
            group.items.forEach { code -> content.addView(mappingRow(code, locked), blockMargins(8)) }
        }
        content.addView(body("提示：功能开关控制的是手势本身（是否参与识别），映射控制的是触发后执行什么动作，两者相互独立。"), blockMargins(18))

        content.addView(title("收藏按钮位置"))
        content.addView(body("在第三方 App 中首次触发“收藏当前内容”时，按提示标记收藏按钮。这里可以查看或删除已保存的位置；重新定义请回到目标 App 再次触发。"), blockMargins(10))
        content.addView(favoriteProfilesView(), blockMargins(18))

        content.addView(title("手势功能开关"))
        content.addView(body("测试时可只开启一个手势，关闭的功能不会参与判断，也不会影响其他动作。列表按签到解锁顺序排列，灰色淡化的手势尚未解锁。").apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        cursorCard = featureCard("指尖移动", "显示并移动青色光标。", savedFeatures.cursor)
        clickCard = featureCard("指尖轻点", "只伸出食指稳定约 0.2 秒，弯曲后在 1 秒内重新伸直执行点击。", savedFeatures.click)
        indexVerticalScrollCard = featureCard("G03 / G04 轨迹预留", "当前版本默认关闭且未绑定动作；编号保留给二期轨迹手势。", savedFeatures.indexVerticalScroll)
        palmVerticalScrollCard = featureCard("并掌上挥 / 并掌下挥", "四指并拢后整只手向上或向下挥动。", savedFeatures.palmVerticalScroll)
        palmLeftScrollCard = featureCard("并掌左挥", "四指并拢后整只手向左挥动。", savedFeatures.palmLeftScroll)
        indexLeftScrollCard = featureCard("G09 轨迹预留", "当前版本默认关闭且未绑定动作；编号保留给二期轨迹手势。", savedFeatures.indexLeftScroll)
        palmRightScrollCard = featureCard("并掌右挥", "四指并拢后整只手向右挥动。", savedFeatures.palmRightScroll)
        indexRightScrollCard = featureCard("G10 轨迹预留", "当前版本默认关闭且未绑定动作；编号保留给二期轨迹手势。", savedFeatures.indexRightScroll)
        screenshotCard = featureCard("开合掌", "五指明显分开并保持，按提示握拳，再次张开五指完成截图。", savedFeatures.screenshot)
        selfieCard = featureCard("V 手势", "V 字保持 2 秒确认，倒计时后保存前置摄像头画面；倒计时期间暂停全部手势识别。", savedFeatures.selfie)
        likeCard = featureCard("指尖比心", "拇指压在食指第一关节处并与食指交叉，其余三指收拢握住，保持约 0.6 秒后双击视频。", savedFeatures.like)
        thumbsUpCard = featureCard("拇指赞", "竖起大拇指并保持约 0.6 秒后双击视频。", savedFeatures.thumbsUp)
        okCard = featureCard("OK 收藏当前内容", "做出 OK 手势并保持约 0.6 秒，点击当前 App 已标定的收藏按钮位置。", savedFeatures.ok)
        playPauseCard = featureCard("握拳", "握拳保持 1 秒（有倒计时提示），发送系统媒体播放/暂停指令。", savedFeatures.playPause)
        pinkyMuteCard = featureCard("小指手势", "仅伸出小指，其余四指收拢并保持 1 秒；每次重新做手势切换静音与恢复声音。", savedFeatures.pinkyMute)
        lotusRecentsCard = featureCard("莲花指返回桌面", "拇指与无名指相触，其余指定手指伸展并保持约 0.6 秒。", savedFeatures.lotusRecents)
        orchidBackCard = featureCard("兰花指最近任务", "拇指与中指相触，其余指定手指伸展并保持约 0.6 秒。", savedFeatures.orchidBack)
        leftLCard = featureCard("单指枪·横向", "食指向左伸直，大拇指在食指根部外侧竖起、不得贴近食指关节，两指夹角保持在 45°–90°，其余三指收拢并保持约 0.6 秒。", savedFeatures.leftL)
        lShapeCard = featureCard("单指枪·竖向", "食指向上伸直、大拇指向侧面伸出，其余三指收拢并保持约 0.6 秒。初版阈值，待真机校准。", savedFeatures.lShape)
        clawDragCard = featureCard("抓取手势", "手心正对摄像头，五根手指分别张开并向内弯曲，手指之间不能并拢；保持约 0.6 秒按下手指，移动手掌持续拖动，张开手指结束。默认动作为拖动，可在上方映射中更换。", savedFeatures.clawDrag)
        cShapeCard = featureCard("C 手势", "五指自然弯曲围成 C 形并保持约 0.6 秒。初版阈值，待真机校准。", savedFeatures.cShape)
        content.addView(body("Love 手势（识别锁）：大拇指、食指和小指伸展，中指与无名指收拢并稳定保持 1 秒，即可锁定或解锁全部手势识别。它对所有用户可用，不可关闭，也不能换成其他动作。").apply { setPadding(0, dp(4), 0, dp(10)) })
        twoFingerVolumeCard = featureCard("双指持续增减音量", "食指与中指并拢伸直、其余手指收起，向上或向下拉动后保持姿势，持续增减音量；改变姿势后停止。与切歌开关相互独立。", savedFeatures.twoFingerVolume)
        twoFingerTrackCard = featureCard("双指切歌与双击播放暂停", "食指与中指并拢伸直、其余手指收起：整只手左右轻挥切歌（只动手指不触发）；两指快速弯下再伸直、连点两下为播放/暂停。与音量开关相互独立。", savedFeatures.twoFingerTrack)
        openApp1Card = featureCard("张掌变一指", "五指张开稳定后，收起其他手指只保留食指并保持约 0.6 秒。", savedFeatures.openApp1)
        openApp2Card = featureCard("张掌变二指", "五指张开稳定后，收起其他手指保留食指与中指并保持约 0.6 秒。", savedFeatures.openApp2)
        openApp3Card = featureCard("张掌变三指", "五指张开稳定后，保留食指、中指与无名指并保持约 0.6 秒。", savedFeatures.openApp3)
        openApp4Card = featureCard("张掌变四指", "五指张开稳定后，收起大拇指保留四指并保持约 0.6 秒。", savedFeatures.openApp4)
        val switchEntries = listOf(
            cursorCard to listOf(GestureCode.G01),
            clickCard to listOf(GestureCode.G02),
            indexVerticalScrollCard to listOf(GestureCode.G03, GestureCode.G04),
            palmVerticalScrollCard to listOf(GestureCode.G05, GestureCode.G06),
            palmLeftScrollCard to listOf(GestureCode.G07),
            indexLeftScrollCard to listOf(GestureCode.G09),
            palmRightScrollCard to listOf(GestureCode.G08),
            indexRightScrollCard to listOf(GestureCode.G10),
            screenshotCard to listOf(GestureCode.G13),
            selfieCard to listOf(GestureCode.G11),
            likeCard to listOf(GestureCode.G12),
            thumbsUpCard to listOf(GestureCode.G20),
            okCard to listOf(GestureCode.G21),
            playPauseCard to listOf(GestureCode.G22),
            pinkyMuteCard to listOf(GestureCode.G23),
            lotusRecentsCard to listOf(GestureCode.G14),
            orchidBackCard to listOf(GestureCode.G15),
            leftLCard to listOf(GestureCode.G24),
            lShapeCard to listOf(GestureCode.G25),
            clawDragCard to listOf(GestureCode.G26),
            cShapeCard to listOf(GestureCode.G27),
            twoFingerTrackCard to listOf(GestureCode.G29, GestureCode.G30, GestureCode.G33),
            twoFingerVolumeCard to listOf(GestureCode.G31, GestureCode.G32),
            openApp1Card to listOf(GestureCode.G16),
            openApp2Card to listOf(GestureCode.G17),
            openApp3Card to listOf(GestureCode.G18),
            openApp4Card to listOf(GestureCode.G19)
        )
        GestureUnlockPlan.groupedByStage(switchEntries) { it.second }.forEach { group ->
            val locked = group.items.any { (_, codes) -> codes.any { it !in unlockedCodes } }
            content.addView(stageHeader(group.stage, locked), blockMargins(10))
            group.items.forEach { (card, codes) ->
                if (codes.any { it !in unlockedCodes }) {
                    // 未解锁的手势不能开启识别；开关值本身保留，解锁后自动可用。
                    card.toggle.isEnabled = false
                    card.note("未解锁：${GestureUnlockPlan.stageLockLabel(GestureUnlockPlan.stageOfGroup(codes))}。")
                    card.alpha = LOCKED_ROW_ALPHA
                }
                content.addView(card, blockMargins(8))
            }
        }
        content.addView(body("提示：如果只测试向下滑动，可关闭其余六项，保存后重新启动手势控制。"), blockMargins(22))

        content.addView(title("练习顺序"))
        content.addView(body("建议按顺序逐项测试。一次只做一个动作；触发后进入冷却期（时长见上方“手势冷却时长”设置），期间暂停全部手势判断，结束后重新识别。"))
        content.addView(practiceCard(R.drawable.gesture_point, "1  光标与点击", "食指移动光标；稳定约 0.2 秒后弯曲食指，再在 1 秒内重新伸直。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_four_fingers_together, "2  方向动作", "水平食指挑动，或将食指、中指、无名指和小指并拢后挥动；拇指不限，四指分开时不触发。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_v, "3  V 字自拍", "保持 V 字 2 秒确认，观察进度；随后有 3 秒时间放下手并调整姿势，倒计时期间不再识别任何手势。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_finger_heart, "4  比心双击点赞", "拇指压在食指第一关节处并与食指交叉，其余三指收拢握住，稳定保持约 0.6 秒。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_palm, "5  截图组合", "五指明显分开并保持；看到提示后握拳，再次五指分开并保持完成截图。"), blockMargins(20))

        content.addView(Button(this).apply {
            text = "保存设置并返回"
            textSize = 16f
            setTextColor(Color.WHITE)
            isAllCaps = false
            typeface = Typeface.DEFAULT_BOLD
            background = pressable(gradientButton(), gradientButtonPressed(), 16)
            stateListAnimator = null
            setOnClickListener {
                GesturePreferences.save(
                    this@CalibrationActivity,
                    selectedSensitivity,
                    reverseCard.toggle.isChecked,
                    feedbackCard.toggle.isChecked,
                    GestureFeatureConfig(
                        cursor = cursorCard.toggle.isChecked,
                        click = clickCard.toggle.isChecked,
                        scroll = indexVerticalScrollCard.toggle.isChecked && palmVerticalScrollCard.toggle.isChecked &&
                            palmLeftScrollCard.toggle.isChecked && indexLeftScrollCard.toggle.isChecked &&
                            palmRightScrollCard.toggle.isChecked && indexRightScrollCard.toggle.isChecked,
                        indexVerticalScroll = indexVerticalScrollCard.toggle.isChecked,
                        palmVerticalScroll = palmVerticalScrollCard.toggle.isChecked,
                        palmLeftScroll = palmLeftScrollCard.toggle.isChecked,
                        indexLeftScroll = indexLeftScrollCard.toggle.isChecked,
                        palmRightScroll = palmRightScrollCard.toggle.isChecked,
                        indexRightScroll = indexRightScrollCard.toggle.isChecked,
                        screenshot = screenshotCard.toggle.isChecked,
                        selfie = selfieCard.toggle.isChecked,
                        like = likeCard.toggle.isChecked,
                        thumbsUp = thumbsUpCard.toggle.isChecked,
                        ok = okCard.toggle.isChecked,
                        playPause = playPauseCard.toggle.isChecked,
                        pinkyMute = pinkyMuteCard.toggle.isChecked,
                        lotusRecents = lotusRecentsCard.toggle.isChecked,
                        orchidBack = orchidBackCard.toggle.isChecked,
                        leftL = leftLCard.toggle.isChecked,
                        lShape = lShapeCard.toggle.isChecked,
                        clawDrag = clawDragCard.toggle.isChecked,
                        cShape = cShapeCard.toggle.isChecked,
                        // 识别锁不可关闭：没有开关，保存时保留原有值，避免把配置写死成关闭。
                        loveLock = savedFeatures.loveLock,
                        twoFingerTrack = twoFingerTrackCard.toggle.isChecked,
                        twoFingerVolume = twoFingerVolumeCard.toggle.isChecked,
                        openApp1 = openApp1Card.toggle.isChecked,
                        openApp2 = openApp2Card.toggle.isChecked,
                        openApp3 = openApp3Card.toggle.isChecked,
                        openApp4 = openApp4Card.toggle.isChecked
                    )
                )
                Toast.makeText(this@CalibrationActivity, "设置已保存，下次启动手势控制时生效", Toast.LENGTH_SHORT).show()
                finish()
            }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(54)))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            // 背景图放进滚动容器随内容滚动，滚过之后露出 #000620 页底色（与首页同一做法）。
            addView(FrameLayout(this@CalibrationActivity).apply {
                addView(pageBackgroundView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            })
        }
        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** 页面背景：ui/切片/bg2.png（841×1870），按屏宽等比拉伸。 */
    private fun pageBackgroundView(): View = object : ImageView(this) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec)
            setMeasuredDimension(width, (width * 1870f / 841f).toInt())
        }
    }.apply {
        setImageResource(R.drawable.calibration_bg2)
        scaleType = ImageView.ScaleType.FIT_XY
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun headerRow() = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@CalibrationActivity).apply {
            text = "‹"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pressable(rounded(Color.argb(170, 10, 32, 66), 22, COLOR_BORDER), rounded(Color.argb(220, 24, 62, 110), 22, COLOR_BORDER), 22)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(title("手势练习与校准").apply { textSize = 21f })
            addView(body("通过练习校准识别灵敏度，获得更准确的手势控制体验").apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** 识别灵敏度卡片：图标 + 标题说明 + 一排档位选择。 */
    private fun sensitivityCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(14))
        background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconBadge(R.drawable.ic_cal_sensitivity), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            addView(LinearLayout(this@CalibrationActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(title("识别灵敏度").apply { textSize = 16f })
                addView(body("如果经常识别不到，请调高灵敏度；如果容易误触，请调低灵敏度。点击即时生效，无需先保存。").apply {
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            stableTile = SensitivityTile("稳定", "减少误触", "stable")
            normalTile = SensitivityTile("标准", "推荐", "normal")
            highTile = SensitivityTile("灵敏", "更易触发", "high")
            addView(stableTile, tileMargins(end = 6))
            addView(normalTile, tileMargins(start = 3, end = 3))
            addView(highTile, tileMargins(start = 6))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(12) })
    }

    private fun selectSensitivity(value: String) {
        if (selectedSensitivity == value) return
        selectedSensitivity = value
        refreshSensitivityButtons()
        // 点击即写入：运行中的手势控制通过偏好监听立刻重算所有位移与角度阈值。
        GesturePreferences.saveSensitivity(this, value)
        Toast.makeText(this, "识别灵敏度已即时生效", Toast.LENGTH_SHORT).show()
    }

    private fun refreshSensitivityButtons() {
        listOf(stableTile, normalTile, highTile).forEach { tile ->
            val selected = tile.value == selectedSensitivity
            tile.titleView.setTextColor(if (selected) Color.WHITE else TEXT_PRIMARY)
            tile.titleView.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tile.subView.setTextColor(if (selected) Color.argb(210, 219, 234, 254) else TEXT_MUTED)
            tile.checkView.visibility = if (selected) View.VISIBLE else View.GONE
            tile.background = if (selected) {
                rounded(Color.rgb(43, 110, 255), 14, Color.rgb(24, 215, 255))
            } else {
                rounded(Color.argb(150, 10, 32, 66), 14, COLOR_BORDER)
            }
        }
    }

    private inner class SensitivityTile(titleText: String, subText: String, val value: String) : FrameLayout(this@CalibrationActivity) {
        val titleView = TextView(this@CalibrationActivity).apply {
            text = titleText
            textSize = 14.5f
            gravity = Gravity.CENTER
        }
        val subView = TextView(this@CalibrationActivity).apply {
            text = subText
            textSize = 11.5f
            gravity = Gravity.CENTER
            setPadding(0, dp(2), 0, 0)
        }
        val checkView = TextView(this@CalibrationActivity).apply {
            text = "✓"
            textSize = 10f
            setTextColor(Color.rgb(43, 110, 255))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = circleBadge(Color.WHITE)
            visibility = View.GONE
        }

        init {
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                addView(titleView)
                addView(subView)
            }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
            addView(checkView, FrameLayout.LayoutParams(dp(15), dp(15), Gravity.END or Gravity.TOP).apply {
                marginEnd = dp(5)
                topMargin = dp(5)
            })
            isClickable = true
            setOnClickListener { selectSensitivity(value) }
        }
    }

    /** Gesture name on the left, current action on the right; tapping opens the action picker. */
    private fun mappingRow(code: GestureCode, locked: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(10), dp(8))
        background = rounded(if (locked) Color.argb(120, 10, 28, 58) else COLOR_SURFACE, 16, COLOR_BORDER)
        if (locked) alpha = LOCKED_ROW_ALPHA
        addView(TextView(this@CalibrationActivity).apply {
            text = gestureNames.getValue(code)
            textSize = 14.5f
            setTextColor(if (locked) TEXT_MUTED else TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val button = Button(this@CalibrationActivity).apply {
            textSize = 13f
            isAllCaps = false
            minWidth = dp(104)
            minHeight = dp(36)
            setPadding(dp(14), 0, dp(14), 0)
            stateListAnimator = null
        }
        mappingButtons[code] = button
        if (locked) {
            // 未解锁的手势可以查看名称，但不能换绑动作。
            button.text = "未解锁"
            button.isEnabled = false
            button.setTextColor(TEXT_MUTED)
            button.background = rounded(Color.argb(120, 16, 38, 72), 12, Color.rgb(30, 49, 86))
        } else {
            button.text = currentActionLabel(code)
            button.setTextColor(COLOR_CYAN)
            button.background = pressable(
                rounded(Color.argb(70, 24, 116, 220), 12, COLOR_BORDER),
                rounded(Color.argb(120, 24, 116, 220), 12, COLOR_BORDER), 12
            )
            button.setOnClickListener { showActionPicker(code) }
        }
        addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(40)))
    }

    /** 分组标题：说明本组是第几次签到解锁，以及该组当前是否已解锁。 */
    private fun stageHeader(stage: Int, locked: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, dp(10), 0, dp(2))
        addView(TextView(this@CalibrationActivity).apply {
            text = GestureUnlockPlan.stageTitle(stage)
            textSize = 14.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (locked) TEXT_MUTED else COLOR_CYAN)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (stage > GestureUnlockPlan.BASE_STAGE) addView(statusChip(if (locked) "未解锁" else "已解锁", locked))
    }

    private fun statusChip(text: String, locked: Boolean) = TextView(this).apply {
        this.text = text
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        setTextColor(if (locked) TEXT_MUTED else COLOR_GREEN)
        setPadding(dp(9), dp(3), dp(9), dp(3))
        background = rounded(
            if (locked) Color.argb(140, 26, 42, 72) else Color.argb(70, 21, 224, 187), 99,
            if (locked) Color.rgb(37, 88, 151) else Color.argb(120, 21, 224, 187)
        )
    }

    private fun currentAction(code: GestureCode): GestureAction? =
        GesturePreferences.actionOverrides(this)[code] ?: GestureMappingManager.defaultActionOf(code)

    private fun currentActionLabel(code: GestureCode): String {
        val action = currentAction(code) ?: return "未设置"
        if (action !in setOf(GestureAction.OPEN_APP, GestureAction.OPEN_APP_1, GestureAction.OPEN_APP_2, GestureAction.OPEN_APP_3, GestureAction.OPEN_APP_4)) {
            return action.displayLabel()
        }
        val pkg = GesturePreferences.openAppPackage(this, code) ?: return "打开应用：未选择"
        return try {
            "打开应用：${packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0))}"
        } catch (e: Exception) {
            "打开应用：已卸载"
        }
    }

    /**
     * Selects the target immediately after OPEN_APP is chosen, then saves both as one binding.
     * A search box filters by app label or package name — launchable lists on a real phone run
     * into the hundreds, and the plain single-choice list was unusable there.
     */
    private fun showAppPicker(code: GestureCode, actionOverride: GestureAction?) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val current = GesturePreferences.openAppPackage(this, code)
        val list = ListView(this).apply { choiceMode = ListView.CHOICE_MODE_SINGLE }
        var shown = apps
        fun render(query: String) {
            val q = query.trim().lowercase()
            shown = if (q.isEmpty()) apps else apps.filter {
                it.loadLabel(packageManager).toString().lowercase().contains(q) ||
                    it.activityInfo.packageName.lowercase().contains(q)
            }
            list.adapter = ArrayAdapter(
                this,
                android.R.layout.simple_list_item_single_choice,
                shown.map { it.loadLabel(packageManager).toString() }
            )
            val index = shown.indexOfFirst { it.activityInfo.packageName == current }
            if (index >= 0) {
                list.setItemChecked(index, true)
                list.setSelection(index)
            }
        }
        render("")
        val search = EditText(this).apply {
            hint = "搜索应用名称或包名"
            setSingleLine(true)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    render(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        val empty = TextView(this).apply {
            text = "没有匹配的应用"
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
            visibility = View.GONE
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(8), dp(18), dp(4))
            addView(search)
            addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)))
            addView(empty)
        }
        list.emptyView = empty
        lateinit var dialog: AlertDialog
        list.setOnItemClickListener { _, _, position, _ ->
            val resolved = shown[position]
            GesturePreferences.saveOpenAppPackage(this, code, resolved.activityInfo.packageName)
            GesturePreferences.setActionOverride(this, code, actionOverride)
            mappingButtons[code]?.text = currentActionLabel(code)
            Toast.makeText(this, "已绑定：${resolved.loadLabel(packageManager)}", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }
        dialog = AlertDialog.Builder(this)
            .setTitle("${gestureNames.getValue(code)} · 选择要打开的应用")
            .setView(container)
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showActionPicker(code: GestureCode) {
        // First option is null = restore the factory default for this gesture.
        // DRAG is mapped like any other action, it only needs coordinates to run; the claw is
        // the one gesture that supplies them, so it is the only one shown the option.
        val selectable = if (code == GestureCode.G26) selectableActions else selectableActions - GestureAction.DRAG
        val options = listOf<GestureAction?>(null) + selectable
        val defaultLabel = GestureMappingManager.defaultActionOf(code)?.displayLabel() ?: "无动作"
        val labels = options.map { it?.displayLabel() ?: "默认（$defaultLabel）" }.toTypedArray()
        val current = GesturePreferences.actionOverrides(this)[code]
        val checked = options.indexOf(current).takeIf { it >= 0 } ?: 0
        AlertDialog.Builder(this)
            .setTitle("${gestureNames.getValue(code)} · 选择动作")
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val selected = options[which]
                val effective = selected ?: GestureMappingManager.defaultActionOf(code)
                if (effective == GestureAction.OPEN_APP) {
                    dialog.dismiss()
                    showAppPicker(code, selected)
                    return@setSingleChoiceItems
                }
                GesturePreferences.setActionOverride(this, code, selected)
                mappingButtons[code]?.text = currentActionLabel(code)
                Toast.makeText(this, "映射已更新，运行中即时生效", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun favoriteProfilesView() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val profiles = GesturePreferences.favoriteProfiles(this@CalibrationActivity)
        if (profiles.isEmpty()) {
            addView(body("暂未定义任何应用的收藏按钮位置。"))
        } else {
            profiles.forEach { profile ->
                addView(LinearLayout(this@CalibrationActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(14), dp(8), dp(9), dp(8))
                    background = rounded(COLOR_SURFACE, 14, COLOR_BORDER)
                    addView(TextView(this@CalibrationActivity).apply {
                        text = "${profile.appLabel}\n${if (profile.portrait) "竖屏" else "横屏"} · 已定义"
                        textSize = 14f
                        setTextColor(TEXT_PRIMARY)
                    }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                    addView(Button(this@CalibrationActivity).apply {
                        text = "删除"
                        isAllCaps = false
                        setTextColor(Color.rgb(255, 122, 140))
                        background = pressable(Color.argb(90, 190, 51, 67), Color.argb(140, 190, 51, 67), 10)
                        setOnClickListener {
                            AlertDialog.Builder(this@CalibrationActivity)
                                .setTitle("删除收藏位置")
                                .setMessage("确定删除 ${profile.appLabel} 的${if (profile.portrait) "竖屏" else "横屏"}收藏位置吗？")
                                .setNegativeButton("取消", null)
                                .setPositiveButton("删除") { _, _ ->
                                    GesturePreferences.deleteFavoriteProfile(this@CalibrationActivity, profile.packageName, profile.portrait)
                                    Toast.makeText(this@CalibrationActivity, "已删除，请重新进入本页刷新", Toast.LENGTH_SHORT).show()
                                }.show()
                        }
                    }, LinearLayout.LayoutParams(dp(76), dp(42)))
                }, blockMargins(7))
            }
        }
    }

    /**
     * Post-action cooldown card (0.6s..4s, 100ms steps): −/+ 精确步进，拖动松手后保存，
     * 运行中的控制会话通过偏好监听实时应用新时长。
     */
    private fun cooldownCard() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(14), dp(13), dp(14), dp(16))
        background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(iconBadge(R.drawable.ic_cal_clock), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            addView(LinearLayout(this@CalibrationActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(title("手势冷却时长").apply { textSize = 16f })
                addView(body("动作执行后到重新识别下一个手势的最短间隔，太短容易误触，太长则响应较慢。").apply {
                    setPadding(0, dp(3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        })
        val current = TextView(this@CalibrationActivity).apply {
            textSize = 14f
            setTextColor(COLOR_CYAN)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(2), dp(10), 0, dp(2))
        }
        val slider = SeekBar(this@CalibrationActivity).apply {
            // 100ms steps from 0.6s to 4.0s.
            max = ((GesturePreferences.MAX_COOLDOWN_MS - GesturePreferences.MIN_COOLDOWN_MS) / 100L).toInt()
            progress = ((GesturePreferences.cooldownMs(context) - GesturePreferences.MIN_COOLDOWN_MS) / 100L).toInt()
            current.text = "当前：${formatCooldown(GesturePreferences.cooldownMs(context))}"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, value: Int, fromUser: Boolean) {
                    current.text = "当前：${formatCooldown(GesturePreferences.MIN_COOLDOWN_MS + value * 100L)}"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar) {
                    GesturePreferences.saveCooldownMs(context, GesturePreferences.MIN_COOLDOWN_MS + seekBar.progress * 100L)
                }
            })
        }
        styleSeekBar(slider)
        fun step(delta: Int) {
            val next = (slider.progress + delta).coerceIn(0, slider.max)
            if (next == slider.progress) return
            slider.progress = next
            current.text = "当前：${formatCooldown(GesturePreferences.MIN_COOLDOWN_MS + next * 100L)}"
            GesturePreferences.saveCooldownMs(this@CalibrationActivity, GesturePreferences.MIN_COOLDOWN_MS + next * 100L)
        }
        addView(current)
        addView(LinearLayout(this@CalibrationActivity).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(roundButton("－") { step(-1) }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginEnd = dp(10) })
            addView(slider, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(roundButton("＋") { step(1) }, LinearLayout.LayoutParams(dp(38), dp(38)).apply { marginStart = dp(10) })
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
    }

    private fun roundButton(symbol: String, onClick: () -> Unit) = TextView(this).apply {
        text = symbol
        textSize = 18f
        setTextColor(COLOR_CYAN)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = pressable(rounded(Color.argb(150, 10, 32, 66), 19, COLOR_BORDER), rounded(Color.argb(220, 24, 62, 110), 19, COLOR_BORDER), 19)
        setOnClickListener { onClick() }
    }

    private fun styleSeekBar(bar: SeekBar) {
        val trackBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(3).toFloat()
            setColor(Color.rgb(30, 49, 86))
            setSize(dp(6), dp(6))
        }
        val trackFg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(3).toFloat()
            setColor(COLOR_CYAN)
            setSize(dp(6), dp(6))
        }
        val layers = LayerDrawable(arrayOf(trackBg, ClipDrawable(trackFg, Gravity.START, ClipDrawable.HORIZONTAL))).apply {
            setId(0, android.R.id.background)
            setId(1, android.R.id.progress)
        }
        bar.progressDrawable = layers
        bar.thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.WHITE)
            setSize(dp(18), dp(18))
        }
        bar.thumbOffset = dp(9)
        bar.splitTrack = false
        bar.setPadding(0, 0, 0, 0)
    }

    private fun featureCard(name: String, description: String, checked: Boolean) = settingCard(null, name, description, checked)

    /** 设置卡：左侧（可选）图标 + 标题说明，右侧霓虹开关。 */
    private fun settingCard(iconRes: Int?, name: String, description: String, checked: Boolean) = SettingCard(iconRes, name, description, checked)

    private inner class SettingCard(iconRes: Int?, name: String, description: String, checked: Boolean) : LinearLayout(this@CalibrationActivity) {
        val toggle: Switch = Switch(this@CalibrationActivity).apply {
            isChecked = checked
            trackDrawable = InsetDrawable(switchTrack(), dp(3), dp(4), dp(3), dp(4))
            thumbDrawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.WHITE)
                setSize(dp(22), dp(22))
            }
        }
        private val noteView = TextView(this@CalibrationActivity).apply {
            textSize = 12f
            setTextColor(COLOR_AMBER)
            setLineSpacing(0f, 1.15f)
            visibility = View.GONE
        }

        init {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(13), dp(12), dp(13))
            background = rounded(COLOR_SURFACE, 16, COLOR_BORDER)
            if (iconRes != null) {
                addView(iconBadge(iconRes), LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(12) })
            }
            addView(LinearLayout(this@CalibrationActivity).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@CalibrationActivity).apply {
                    text = name
                    textSize = 15f
                    setTextColor(TEXT_PRIMARY)
                    typeface = Typeface.DEFAULT_BOLD
                })
                addView(TextView(this@CalibrationActivity).apply {
                    text = description
                    textSize = 12.5f
                    setTextColor(TEXT_MUTED)
                    setLineSpacing(0f, 1.15f)
                    setPadding(0, dp(3), dp(6), 0)
                })
                addView(noteView.apply { setPadding(0, dp(3), 0, 0) })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(toggle, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(10) })
        }

        /** 追加一行提示（如“未解锁：…”），默认隐藏。 */
        fun note(text: String) {
            noteView.text = text
            noteView.visibility = View.VISIBLE
        }
    }

    /** 霓虹开关轨道：开启为青绿色，关闭为深色，状态切换由系统驱动。 */
    private fun switchTrack() = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_checked), pill(COLOR_GREEN))
        addState(intArrayOf(), pill(Color.rgb(30, 49, 86)))
    }

    private fun pill(color: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(13).toFloat()
        setColor(color)
        setSize(dp(46), dp(26))
    }

    private fun practiceCard(image: Int, heading: String, description: String) = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(10), dp(10), dp(14), dp(10))
        background = rounded(COLOR_SURFACE, 18, COLOR_BORDER)
        addView(ImageView(this@CalibrationActivity).apply {
            setImageResource(image)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            contentDescription = heading
        }, LinearLayout.LayoutParams(dp(78), dp(78)))
        addView(LinearLayout(this@CalibrationActivity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
            addView(title(heading).apply { textSize = 16f })
            addView(body(description).apply { setPadding(0, dp(5), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** 图标底座：深色圆角方块，承托卡片左上角的线性图标。 */
    private fun iconBadge(res: Int) = ImageView(this).apply {
        setImageResource(res)
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(8), dp(8), dp(8), dp(8))
        background = rounded(Color.argb(110, 13, 42, 86), 12, COLOR_BORDER)
    }

    private fun title(value: String) = TextView(this).apply {
        text = value
        textSize = 17f
        setTextColor(TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 13f
        setTextColor(TEXT_MUTED)
        setLineSpacing(0f, 1.15f)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun pressable(fill: Int, pressedFill: Int, radius: Int, stroke: Int? = null) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(android.R.attr.state_focused), rounded(pressedFill, radius, stroke))
        addState(intArrayOf(), rounded(fill, radius, stroke))
    }

    /** 接受 Drawable 版 pressable，用于渐变按钮等非纯色背景。 */
    private fun pressable(normal: android.graphics.drawable.Drawable, pressed: android.graphics.drawable.Drawable, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_focused), pressed)
        addState(intArrayOf(), normal)
    }

    private fun gradientButton() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(Color.rgb(56, 128, 255), Color.rgb(30, 78, 214))
    }

    private fun gradientButtonPressed() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(16).toFloat()
        orientation = GradientDrawable.Orientation.TOP_BOTTOM
        colors = intArrayOf(Color.rgb(40, 104, 220), Color.rgb(22, 58, 170))
    }

    private fun circleBadge(fill: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(fill)
    }

    private fun tileMargins(start: Int = 0, end: Int = 0) = LinearLayout.LayoutParams(0, dp(62), 1f).apply {
        marginStart = dp(start)
        marginEnd = dp(end)
    }

    private fun blockMargins(bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(bottom)
    }

    private fun formatCooldown(ms: Long): String {
        val seconds = ms / 1000f
        return if (seconds == seconds.toLong().toFloat()) "${seconds.toLong()} 秒" else "%.1f 秒".format(seconds)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** 未解锁配置行的淡化程度：内容仍可读，但一眼区别于已解锁。 */
        private const val LOCKED_ROW_ALPHA = 0.5f

        /** 页面底色：背景图滚走之后露出的颜色，与首页一致。 */
        private val COLOR_BG = Color.rgb(0, 6, 32)

        /** 半透明霓虹卡片底色与描边，取色与首页保持同一套视觉语言。 */
        private val COLOR_SURFACE = Color.argb(210, 7, 31, 67)
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val COLOR_GREEN = Color.rgb(21, 224, 187)
        private val COLOR_AMBER = Color.rgb(255, 184, 40)
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}
