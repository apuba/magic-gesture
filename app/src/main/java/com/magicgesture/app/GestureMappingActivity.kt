package com.magicgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
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
    /** 应用图标解码线程与缓存：图标数量多，不能在主线程取。 */
    private val iconLoader = java.util.concurrent.Executors.newSingleThreadExecutor()
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val iconCache = mutableMapOf<String, android.graphics.drawable.Drawable?>()

    override fun onDestroy() {
        super.onDestroy()
        iconLoader.shutdownNow()
    }

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
     * 面板与列表全部按深色霓虹风格自绘，取代系统浅色 AlertDialog。
     */
    private fun showAppPicker(code: GestureCode, actionOverride: GestureAction?) {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        val current = GesturePreferences.openAppPackage(this, code)
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        lateinit var dialog: AlertDialog
        fun render(query: String) {
            rows.removeAllViews()
            val q = query.trim().lowercase()
            val shown = if (q.isEmpty()) apps else apps.filter {
                it.loadLabel(packageManager).toString().lowercase().contains(q) ||
                    it.activityInfo.packageName.lowercase().contains(q)
            }
            if (shown.isEmpty()) {
                rows.addView(TextView(this@GestureMappingActivity).apply {
                    text = "没有匹配的应用"
                    textSize = 13f
                    setTextColor(TEXT_MUTED)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(20), 0, dp(20))
                })
                return
            }
            shown.forEach { resolved ->
                rows.addView(
                    appRow(resolved.activityInfo.packageName, resolved.loadLabel(packageManager).toString(), resolved.activityInfo.packageName == current) {
                        dialog.dismiss()
                        GesturePreferences.saveOpenAppPackage(this, code, resolved.activityInfo.packageName)
                        GesturePreferences.setActionOverride(this, code, actionOverride)
                        mappingButtons[code]?.text = currentActionLabel(code)
                        Toast.makeText(this, "已绑定：${resolved.loadLabel(packageManager)}", Toast.LENGTH_SHORT).show()
                    },
                    rowMargins()
                )
            }
        }
        val search = EditText(this).apply {
            hint = "搜索应用名称或包名"
            setSingleLine(true)
            setTextColor(TEXT_PRIMARY)
            setHintTextColor(TEXT_MUTED)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = rounded(Color.argb(120, 10, 28, 58), 12, COLOR_BORDER)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    render(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(search)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)).apply { topMargin = dp(10) })
        }
        dialog = neonDialog("${gestureNames.getValue(code)} · 选择要打开的应用", body)
        render("")
        dialog.show()
    }

    private fun showActionPicker(code: GestureCode) {
        // First option is null = restore the factory default for this gesture.
        // DRAG is mapped like any other action, it only needs coordinates to run; the claw is
        // the one gesture that supplies them, so it is the only one shown the option.
        val selectable = if (code == GestureCode.G26) selectableActions else selectableActions - GestureAction.DRAG
        val options = listOf<GestureAction?>(null) + selectable
        val defaultLabel = GestureMappingManager.defaultActionOf(code)?.displayLabel() ?: "无动作"
        val current = GesturePreferences.actionOverrides(this)[code]
        val checked = options.indexOf(current).takeIf { it >= 0 } ?: 0
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        lateinit var dialog: AlertDialog
        options.forEachIndexed { index, action ->
            body.addView(
                dialogRow(action?.displayLabel() ?: "默认（$defaultLabel）", index == checked) {
                    dialog.dismiss()
                    val selected = options[index]
                    val effective = selected ?: GestureMappingManager.defaultActionOf(code)
                    if (effective == GestureAction.OPEN_APP) {
                        showAppPicker(code, selected)
                        return@dialogRow
                    }
                    GesturePreferences.setActionOverride(this, code, selected)
                    mappingButtons[code]?.text = currentActionLabel(code)
                    Toast.makeText(this, "映射已更新，运行中即时生效", Toast.LENGTH_SHORT).show()
                },
                rowMargins()
            )
        }
        dialog = neonDialog("${gestureNames.getValue(code)} · 选择动作", body)
        dialog.show()
    }

    /** 深色霓虹弹窗：自绘面板 + 透明系统背景，取消按钮为描边款。 */
    private fun neonDialog(titleText: String, body: View): AlertDialog {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
            background = rounded(Color.rgb(8, 28, 58), 20, COLOR_BORDER)
        }
        panel.addView(TextView(this).apply {
            text = titleText
            textSize = 16f
            setTextColor(TEXT_PRIMARY)
            typeface = Typeface.DEFAULT_BOLD
        })
        panel.addView(body)
        val cancel = TextView(this).apply {
            text = "取消"
            textSize = 14.5f
            setTextColor(COLOR_CYAN)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(11), 0, dp(11))
            background = pressable(Color.argb(130, 10, 32, 66), Color.argb(190, 24, 62, 110), 12, COLOR_BORDER)
        }
        panel.addView(cancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(14) })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        cancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }

    /** 应用选择行：左侧应用图标 + 名称，选中项显示青色文字与 ✓。 */
    private fun appRow(packageName: String, label: String, selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(9), dp(13), dp(9))
        background = pressable(
            rounded(if (selected) Color.argb(110, 24, 116, 220) else Color.argb(120, 10, 28, 58), 12, if (selected) COLOR_CYAN else COLOR_BORDER),
            rounded(Color.argb(190, 24, 62, 110), 12, COLOR_BORDER), 12
        )
        val icon = ImageView(this@GestureMappingActivity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(Color.argb(120, 13, 42, 86), 10)
            clipToOutline = true
            tag = packageName
        }
        addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
        loadAppIcon(packageName, icon)
        addView(TextView(this@GestureMappingActivity).apply {
            text = label
            textSize = 14.5f
            setTextColor(if (selected) COLOR_CYAN else TEXT_PRIMARY)
            if (selected) typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) addView(TextView(this@GestureMappingActivity).apply {
            text = "✓"
            textSize = 13f
            setTextColor(COLOR_CYAN)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        setOnClickListener { onClick() }
    }

    /**
     * 应用图标在后台线程解码并缓存，避免几百个应用一次性加载卡住主线程；
     * 取不到图标时保留中性色块占位。
     */
    private fun loadAppIcon(packageName: String, target: ImageView) {
        if (iconCache.containsKey(packageName)) {
            target.setImageDrawable(iconCache[packageName])
            return
        }
        iconLoader.execute {
            val drawable = try {
                packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
            iconCache[packageName] = drawable
            mainHandler.post {
                // 行在搜索重建时会被替换，用 tag 确认仍是同一个应用再设置。
                if (target.tag == packageName) target.setImageDrawable(drawable)
            }
        }
    }

    /** 弹窗内的一行选项：选中项显示青色文字与 ✓。 */
    private fun dialogRow(label: String, selected: Boolean, onClick: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(13), dp(10), dp(13), dp(10))
        background = pressable(
            rounded(if (selected) Color.argb(110, 24, 116, 220) else Color.argb(120, 10, 28, 58), 12, if (selected) COLOR_CYAN else COLOR_BORDER),
            rounded(Color.argb(190, 24, 62, 110), 12, COLOR_BORDER), 12
        )
        addView(TextView(this@GestureMappingActivity).apply {
            text = label
            textSize = 14.5f
            setTextColor(if (selected) COLOR_CYAN else TEXT_PRIMARY)
            if (selected) typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (selected) addView(TextView(this@GestureMappingActivity).apply {
            text = "✓"
            textSize = 13f
            setTextColor(COLOR_CYAN)
            typeface = Typeface.DEFAULT_BOLD
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginStart = dp(8) })
        setOnClickListener { onClick() }
    }

    private fun rowMargins() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
        bottomMargin = dp(6)
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
