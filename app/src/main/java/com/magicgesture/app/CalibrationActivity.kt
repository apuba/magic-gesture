package com.magicgesture.app

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

/**
 * 手势练习与校准页：保留识别灵敏度、左右反转、识别反馈、冷却时长、功能开关与收藏位置。
 * 换绑动作已拆到 GestureMappingActivity（首页“手势映射”入口）。
 */
class CalibrationActivity : Activity() {
    /** 当前已解锁的手势编号；Debug 构建全开，正式版按签到进度。 */

    private var selectedSensitivity = "normal"
    private lateinit var stableTile: SensitivityTile
    private lateinit var normalTile: SensitivityTile
    private lateinit var highTile: SensitivityTile
    private lateinit var reverseCard: SettingCard
    private lateinit var feedbackCard: SettingCard

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        selectedSensitivity = GesturePreferences.sensitivity(this)
        setContentView(buildContent())
        refreshSensitivityButtons()
    }

    private fun buildContent(): View {
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
            "当实际左右滑动与手势方向相反时开启。光标左右方向也会同步反转。开关即时保存，重新开启手势控制后生效。",
            GesturePreferences.reverseHorizontal(this)
        ).apply {
            toggle.setOnCheckedChangeListener { _, enabled ->
                GesturePreferences.saveReverseHorizontal(this@CalibrationActivity, enabled)
            }
        }
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

        content.addView(body("提示：手势是否参与识别，在首页手势列表的开关里调整，这里不再重复展示。"), blockMargins(18))

        content.addView(title("练习顺序"))
        content.addView(body("建议按顺序逐项测试。一次只做一个动作；触发后进入冷却期（时长见上方“手势冷却时长”设置），期间暂停全部手势判断，结束后重新识别。"))
        content.addView(practiceCard(R.drawable.gesture_point, "1  光标与点击", "食指移动光标；稳定约 0.2 秒后弯曲食指，再在 1 秒内重新伸直。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_four_fingers_together, "2  方向动作", "水平食指挑动，或将食指、中指、无名指和小指并拢后挥动；拇指不限，四指分开时不触发。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_v, "3  V 字自拍", "保持 V 字 2 秒确认，观察进度；随后有 3 秒时间放下手并调整姿势，倒计时期间不再识别任何手势。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_finger_heart, "4  比心双击点赞", "拇指压在食指第一关节处并与食指交叉，其余三指收拢握住，稳定保持约 0.6 秒。"), blockMargins(10))
        content.addView(practiceCard(R.drawable.gesture_palm, "5  截图组合", "五指明显分开并保持；看到提示后握拳，再次五指分开并保持完成截图。"), blockMargins(20))

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


    /** 设置卡：左侧（可选）图标 + 标题说明，右侧霓虹开关。 */
    private fun settingCard(iconRes: Int?, name: String, description: String, checked: Boolean) = SettingCard(iconRes, name, description, checked)

    private inner class SettingCard(iconRes: Int?, name: String, description: String, checked: Boolean) : LinearLayout(this@CalibrationActivity) {
        val toggle: NeonSwitch = NeonSwitch(this@CalibrationActivity).apply {
            isChecked = checked
            contentDescription = name
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

        /** 页面底色：背景图滚走之后露出的颜色，与首页一致。 */
        private val COLOR_BG = Color.rgb(0, 6, 32)

        /** 半透明霓虹卡片底色与描边，取色与首页保持同一套视觉语言。 */
        private val COLOR_SURFACE = Color.argb(210, 7, 31, 67)
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val COLOR_AMBER = Color.rgb(255, 184, 40)
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}

/**
 * 霓虹开关：轨道与滑块全部自绘。
 * 原生 Switch 会把滑块纵向拉伸到轨道高度，滑块因此被压成椭圆；这里轨道高度与滑块直径各自独立设定，
 * 形状在任何缩放与字体设置下都不会变形。
 */
private class NeonSwitch(context: Context) : View(context) {
    var isChecked: Boolean
        get() = checked
        set(value) {
            checked = value
            progress = if (value) 1f else 0f
            invalidate()
        }

    private var checked = false
    private var progress = 0f
    private var listener: ((View, Boolean) -> Unit)? = null
    private var animator: ValueAnimator? = null
    private val density = context.resources.displayMetrics.density
    private val trackW = (46f * density).toInt()
    private val trackH = (26f * density).toInt()
    private val paddingPx = (3f * density)
    private val thumbR = (10f * density).coerceAtMost((trackH / 2f) - paddingPx)
    private val evaluator = ArgbEvaluator()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    companion object {
        /** 轨道配色：开启青绿，关闭深蓝。 */
        private val TRACK_ON = Color.rgb(21, 224, 187)
        private val TRACK_OFF = Color.rgb(30, 49, 86)
    }

    init {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }

    fun setOnCheckedChangeListener(listener: (View, Boolean) -> Unit) {
        this.listener = listener
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(trackW, trackH)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val alpha = if (isEnabled) 255 else 110
        val radius = height / 2f
        trackPaint.color = evaluator.evaluate(progress, TRACK_OFF, TRACK_ON) as Int
        trackPaint.alpha = alpha
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), radius, radius, trackPaint)
        thumbPaint.alpha = alpha
        val cx = paddingPx + thumbR + progress * (width - 2 * paddingPx - 2 * thumbR)
        canvas.drawCircle(cx, height / 2f, thumbR, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_UP -> {
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> return true
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        toggle()
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
        animator = null
    }

    private fun toggle() {
        checked = !checked
        animateTo(if (checked) 1f else 0f)
        listener?.invoke(this, checked)
    }

    /** 只在用户切换时做 150ms 过渡；程序化赋值直接落到终态。 */
    private fun animateTo(target: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 150L
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
}
