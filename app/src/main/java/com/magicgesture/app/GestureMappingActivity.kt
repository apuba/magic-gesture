package com.magicgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.TextView
import android.widget.Toast

/**
 * 手势动作映射页：从“手势练习与校准”拆出的独立页面，只负责换绑动作。
 * 视觉沿用校准页的深色霓虹风格与 ui/切片/bg2.png 背景；功能开关、灵敏度与冷却仍在校准页。
 */
class GestureMappingActivity : Activity() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = COLOR_BG
        window.navigationBarColor = COLOR_BG
        unlockedCodes = GestureUnlockStore(this).entitlement().codes
        setContentView(buildContent())
    }

    private fun buildContent(): View {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(36))
        }

        content.addView(headerRow(), blockMargins(18))

        content.addView(body("每个手势都可以换成其他动作。列表按签到解锁顺序排列，灰色淡化的手势尚未解锁，不能换绑。点击右侧按钮选择动作，选“默认”恢复出厂设置；修改立即生效，无需重启手势控制。"))

        mappingButtons.clear()
        GestureUnlockPlan.groupedByStage(gestureNames.keys.toList()) { listOf(it) }.forEach { group ->
            val locked = group.items.any { it !in unlockedCodes }
            content.addView(stageHeader(group.stage, locked), blockMargins(10))
            group.items.forEach { code -> content.addView(mappingRow(code, locked), blockMargins(8)) }
        }

        content.addView(body("提示：功能开关控制的是手势本身（是否参与识别），映射控制的是触发后执行什么动作，两者相互独立；功能开关在“手势练习与校准”页。"), blockMargins(18))

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isFillViewport = true
            addView(FrameLayout(this@GestureMappingActivity).apply {
                addView(pageBackgroundView(), FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
                addView(content, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP))
            })
        }
        return FrameLayout(this).apply {
            setBackgroundColor(COLOR_BG)
            addView(scroll, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }

    /** 页面背景：ui/切片/bg2.png（841×1870），与校准页同一张素材。 */
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
        addView(TextView(this@GestureMappingActivity).apply {
            text = "‹"
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = pressable(
                rounded(Color.argb(170, 10, 32, 66), 22, COLOR_BORDER),
                rounded(Color.argb(220, 24, 62, 110), 22, COLOR_BORDER), 22
            )
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(44), dp(44)).apply { marginEnd = dp(12) })
        addView(LinearLayout(this@GestureMappingActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(title("手势动作映射").apply { textSize = 21f })
            addView(body("把已解锁的手势换成你更常用的动作，换绑后即时生效。").apply { setPadding(0, dp(3), 0, 0) })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    /** Gesture name on the left, current action on the right; tapping opens the action picker. */
    private fun mappingRow(code: GestureCode, locked: Boolean) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(8), dp(10), dp(8))
        background = rounded(if (locked) Color.argb(120, 10, 28, 58) else COLOR_SURFACE, 16, COLOR_BORDER)
        if (locked) alpha = LOCKED_ROW_ALPHA
        addView(TextView(this@GestureMappingActivity).apply {
            text = gestureNames.getValue(code)
            textSize = 14.5f
            setTextColor(if (locked) TEXT_MUTED else TEXT_PRIMARY)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val button = Button(this@GestureMappingActivity).apply {
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
        addView(TextView(this@GestureMappingActivity).apply {
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

    /** 接受 Drawable 版 pressable，用于已描边的圆角底等自定义背景。 */
    private fun pressable(normal: android.graphics.drawable.Drawable, pressed: android.graphics.drawable.Drawable, radius: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_focused), pressed)
        addState(intArrayOf(), normal)
    }

    private fun blockMargins(bottom: Int) = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(bottom)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        /** 未解锁配置行的淡化程度：内容仍可读，但一眼区别于已解锁。 */
        private const val LOCKED_ROW_ALPHA = 0.5f

        /** 与校准页同一套配色，两个页面必须看起来是同一个 App。 */
        private val COLOR_BG = Color.rgb(0, 6, 32)
        private val COLOR_SURFACE = Color.argb(210, 7, 31, 67)
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val COLOR_GREEN = Color.rgb(21, 224, 187)
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}
