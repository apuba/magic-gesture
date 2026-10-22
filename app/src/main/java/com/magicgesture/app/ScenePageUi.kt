package com.magicgesture.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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

/**
 * Everything the scene management page needs to draw itself. The caller resolves preferences and
 * the foreground package and hands the result over, so the page can also be rendered on the JVM
 * by preview tests without starting an activity, a camera pipeline or the accessibility service.
 */
data class ScenePageState(
    val autoSwitchEnabled: Boolean,
    val currentPackage: String?,
    val associations: List<Pair<String, GestureScene>>,
    /** Gestures the user removed from each preset scene; GENERAL is never editable. */
    val removedCodes: Map<GestureScene, Set<GestureCode>> = emptyMap(),
    /** Gestures the user added to each preset scene from outside its factory preset. */
    val addedCodes: Map<GestureScene, Set<GestureCode>> = emptyMap()
)

/** Page level interactions. The activity owns persistence, dialogs and toasts. */
interface ScenePageCallbacks {
    fun onBack()
    fun onAutoSwitchChanged(enabled: Boolean)
    fun onSelectScene(scene: GestureScene)
    fun onEditSceneGestures(scene: GestureScene)
    fun onEditAssociation(packageName: String)
    fun onAddAssociation()
    fun onDeleteAssociation(packageName: String)
}

/** Interactions of the per-scene gesture editor sheet. */
interface SceneGestureEditorCallbacks {
    fun onToggle(code: GestureCode, enabled: Boolean)
    fun onRestoreDefaults()
    fun onDone()
}

/** Interactions of the manual scene switcher opened by a long press on the floating dot. */
interface SceneSwitchCallbacks {
    fun onPick(scene: GestureScene)
    /** Saves the current app as a permanent default for that scene, not just for this visit. */
    fun onRememberDefault(scene: GestureScene)
    fun onDismiss()
}

/** Interactions of the scene choice sheet. */
interface SceneChoiceCallbacks {
    fun onTemporary(scene: GestureScene)
    fun onSetDefault(scene: GestureScene)
    fun onDismiss()
}

/**
 * Pure view construction for the scene management page. It never touches preferences, services or
 * the accessibility layer, which keeps runtime behaviour unchanged while allowing JVM rendering.
 */
object ScenePageUi {
    private val bg = Color.rgb(0, 6, 32)
    private val surface = Color.argb(220, 7, 31, 67)
    private val border = Color.rgb(37, 88, 151)
    private val cyan = Color.rgb(24, 215, 255)
    private val violet = Color.rgb(152, 55, 255)
    private val secondary = Color.rgb(205, 220, 244)
    private val muted = Color.rgb(145, 169, 205)

    fun build(context: Context, state: ScenePageState, callbacks: ScenePageCallbacks): View {
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 16), dp(context, 18), dp(context, 16), dp(context, 36))
            addView(header(context, callbacks))
            addView(autoSwitchCard(context, state.autoSwitchEnabled, callbacks), blockParams(context, 16))
            addView(currentAppCard(context, state, callbacks), blockParams(context, 12))
            addView(text(context, "场景方案", 19f, Color.WHITE, true), blockParams(context, 22, 8))
            val currentScene = state.currentPackage?.let { pkg ->
                state.associations.firstOrNull { it.first == pkg }?.second
            } ?: GestureScene.GENERAL
            GestureScene.entries.forEach { scene ->
                val customised = state.removedCodes[scene].orEmpty().isNotEmpty() ||
                    state.addedCodes[scene].orEmpty().isNotEmpty()
                addView(
                    scenePresetCard(context, scene, scene == currentScene, customised, callbacks),
                    blockParams(context, 0, 8)
                )
            }
            addView(text(context, "我的应用关联", 19f, Color.WHITE, true), blockParams(context, 22, 8))
            if (state.associations.isEmpty()) {
                addView(emptyCard(context), blockParams(context, 0, 8))
            } else {
                state.associations.forEach { (pkg, scene) ->
                    addView(associationRow(context, pkg, scene, callbacks), blockParams(context, 0, 8))
                }
            }
            addView(addAssociationCard(context, callbacks), blockParams(context, 2, 8))
            addView(text(context, "ⓘ 仅识别前台应用包名，不读取页面内容", 12f, muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(context, 6), dp(context, 14), dp(context, 6), 0)
            })
        }
        return FrameLayout(context).apply {
            setBackgroundColor(bg)
            addView(ImageView(context).apply {
                setImageResource(R.drawable.calibration_bg2)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.48f
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(ScrollView(context).apply {
                isFillViewport = true
                addView(content)
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }
    }

    private fun scenePresetCard(
        context: Context,
        scene: GestureScene,
        selected: Boolean,
        customised: Boolean,
        callbacks: ScenePageCallbacks
    ) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 14), dp(context, 12), dp(context, 12), dp(context, 12))
        background = rounded(
            context,
            if (selected) Color.argb(225, 25, 49, 117) else surface,
            15,
            if (selected) cyan else border
        )
        addView(scenePresetIcon(context, scene), LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)).apply {
            marginEnd = dp(context, 12)
        })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(context, sceneLabel(scene), 15f, Color.WHITE, true))
            addView(text(context, sceneDescription(scene), 11f, secondary, false).apply {
                setPadding(0, dp(context, 4), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Only the focused presets can be trimmed; GENERAL is the user's own global setup.
            if (GestureSceneCatalog.isEditable(scene)) {
                addView(text(context, if (customised) "已调整 ✎" else "编辑 ✎", 11f, if (customised) violet else cyan, true).apply {
                    gravity = Gravity.CENTER
                    setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                    background = rounded(context, Color.argb(120, 36, 78, 145), 14, if (customised) violet else border)
                    contentDescription = "编辑${sceneLabel(scene)}包含的手势"
                    setOnClickListener { callbacks.onEditSceneGestures(scene) }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(context, 8) })
            }
            addView(text(context, if (selected) "当前" else "选择 ›", 11f, if (selected) cyan else Color.WHITE, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
                background = rounded(
                    context,
                    if (selected) Color.argb(95, 24, 215, 255) else Color.argb(120, 36, 78, 145),
                    14,
                    if (selected) cyan else border
                )
            })
        })
        contentDescription = "${sceneLabel(scene)}，${sceneDescription(scene)}"
        setOnClickListener { callbacks.onSelectScene(scene) }
    }

    private fun scenePresetIcon(context: Context, scene: GestureScene) = TextView(context).apply {
        text = when (scene) {
            GestureScene.GENERAL -> "◎"
            GestureScene.SHORT_VIDEO -> "▶"
            GestureScene.MUSIC -> "♫"
        }
        textSize = 23f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            when (scene) {
                GestureScene.GENERAL -> intArrayOf(Color.rgb(26, 91, 180), Color.rgb(62, 73, 176))
                GestureScene.SHORT_VIDEO -> intArrayOf(Color.rgb(90, 43, 235), violet)
                GestureScene.MUSIC -> intArrayOf(Color.rgb(19, 119, 239), Color.rgb(25, 203, 232))
            }
        ).apply { cornerRadius = dp(context, 12).toFloat() }
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun header(context: Context, callbacks: ScenePageCallbacks) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(context).apply {
            text = "‹"
            textSize = 34f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            background = rounded(context, Color.argb(210, 8, 48, 91), 22, border)
            contentDescription = "返回"
            setOnClickListener { callbacks.onBack() }
        }, LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)).apply { marginEnd = dp(context, 14) })
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(text(context, "场景管理", 24f, Color.WHITE, true))
            addView(text(context, "根据当前应用自动切换手势方案", 12f, secondary, false).apply {
                setPadding(0, dp(context, 3), 0, 0)
            })
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    }

    @Suppress("UseSwitchCompatOrMaterialCode")
    private fun autoSwitchCard(context: Context, enabled: Boolean, callbacks: ScenePageCallbacks) =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 15), dp(context, 14), dp(context, 14), dp(context, 14))
            background = rounded(context, surface, 17, cyan)
            addView(sceneIcon(context), LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)).apply { marginEnd = dp(context, 12) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, "自动切换场景", 16f, Color.WHITE, true))
                addView(text(context, "前台应用稳定后自动切换", 11f, secondary, false).apply { setPadding(0, dp(context, 4), 0, 0) })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Switch(context).apply {
                isChecked = enabled
                thumbTintList = ColorStateList.valueOf(Color.WHITE)
                trackTintList = ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                    intArrayOf(Color.rgb(21, 224, 187), Color.rgb(63, 83, 122))
                )
                setOnCheckedChangeListener { _, value -> callbacks.onAutoSwitchChanged(value) }
            })
        }

    private fun currentAppCard(context: Context, state: ScenePageState, callbacks: ScenePageCallbacks): View {
        val pkg = state.currentPackage
        val scene = state.associations.firstOrNull { it.first == pkg }?.second ?: GestureScene.GENERAL
        return LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 15), dp(context, 12), dp(context, 14), dp(context, 12))
            background = rounded(context, surface, 15, border)
            addView(appIcon(context, pkg), LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)).apply { marginEnd = dp(context, 12) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, if (pkg == null) "当前：通用场景" else appLabel(context, pkg), 15f, Color.WHITE, true))
                addView(text(context, if (pkg == null) "未识别到可关联应用" else sceneLabel(scene), 11f, secondary, false).apply {
                    setPadding(0, dp(context, 3), 0, 0)
                })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill(context, if (pkg == null) "未关联应用" else sceneLabel(scene)))
            if (pkg != null) setOnClickListener { callbacks.onEditAssociation(pkg) }
        }
    }

    private fun associationRow(context: Context, pkg: String, scene: GestureScene, callbacks: ScenePageCallbacks) =
        LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 13), dp(context, 12), dp(context, 10), dp(context, 12))
            background = rounded(context, surface, 15, border)
            addView(appIcon(context, pkg), LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)).apply { marginEnd = dp(context, 11) })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(text(context, appLabel(context, pkg), 15f, Color.WHITE, true).apply { maxLines = 1 })
                addView(text(context, pkg, 9.5f, muted, false).apply { maxLines = 1 })
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(pill(context, sceneLabel(scene)).apply { setOnClickListener { callbacks.onEditAssociation(pkg) } })
            addView(TextView(context).apply {
                text = "×"
                textSize = 24f
                setTextColor(muted)
                gravity = Gravity.CENTER
                contentDescription = "删除 ${appLabel(context, pkg)} 的场景关联"
                setOnClickListener { callbacks.onDeleteAssociation(pkg) }
            }, LinearLayout.LayoutParams(dp(context, 38), dp(context, 42)).apply { marginStart = dp(context, 4) })
        }

    private fun emptyCard(context: Context) =
        text(context, "尚未关联应用\n点击下方按钮添加短视频或音乐应用", 13f, secondary, false).apply {
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 20), dp(context, 14), dp(context, 20))
            background = rounded(context, Color.argb(175, 7, 31, 67), 15, border)
        }

    private fun addAssociationCard(context: Context, callbacks: ScenePageCallbacks) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 15), dp(context, 14), dp(context, 14), dp(context, 14))
        background = rounded(context, Color.argb(190, 5, 34, 74), 15, cyan, dashed = true)
        addView(text(context, "＋", 24f, cyan, false).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(context, 42), dp(context, 42)))
        addView(text(context, "关联新的应用", 15f, Color.WHITE, true), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(context, 10) })
        addView(text(context, "›", 26f, Color.WHITE, false))
        setOnClickListener { callbacks.onAddAssociation() }
    }

    /** Scene choice sheet content. The activity wraps it in its own [android.app.Dialog]. */
    fun buildSceneChoice(
        context: Context,
        packageName: String,
        initial: GestureScene,
        firstAssociation: Boolean,
        callbacks: SceneChoiceCallbacks
    ): View {
        var selected = initial
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 20))
            background = rounded(context, Color.rgb(7, 28, 65), 22, Color.rgb(62, 111, 190))
            addView(text(context, if (firstAssociation) "为当前应用选择场景" else "切换当前场景", 21f, Color.WHITE, true))
            addView(text(context, if (firstAssociation) "${appLabel(context, packageName)} 尚未关联，选择后立即生效" else appLabel(context, packageName), 12f, secondary, false).apply {
                setPadding(0, dp(context, 5), 0, dp(context, 10))
            })
            val rows = mutableMapOf<GestureScene, TextView>()
            fun repaint() = rows.forEach { (scene, row) ->
                row.background = rounded(context, if (scene == selected) Color.argb(220, 38, 42, 145) else surface, 14, if (scene == selected) violet else border)
                row.text = "${if (scene == selected) "●" else "○"}  ${sceneLabel(scene)}\n     ${sceneDescription(scene)}"
            }
            GestureScene.entries.forEach { scene ->
                val row = text(context, "", 14f, Color.WHITE, scene == selected).apply {
                    setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
                    setOnClickListener { selected = scene; repaint() }
                }
                rows[scene] = row
                addView(row, blockParams(context, 4, 4))
            }
            repaint()
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(actionButton(context, "仅本次使用", primary = false) {
                    callbacks.onTemporary(selected)
                }, LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginEnd = dp(context, 6) })
                addView(actionButton(context, "设为默认", primary = true) {
                    callbacks.onSetDefault(selected)
                }, LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginStart = dp(context, 6) })
            }, blockParams(context, 10, 0))
            addView(text(context, "暂不设置", 12f, cyan, false).apply {
                gravity = Gravity.CENTER
                setPadding(dp(context, 10), dp(context, 15), dp(context, 10), dp(context, 2))
                setOnClickListener { callbacks.onDismiss() }
            })
            if (!firstAssociation) addView(text(context, "离开当前应用后，临时选择自动失效\n识别锁开启时，切换将在解锁后生效", 10.5f, muted, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 12), 0, 0)
            })
        }
    }

    /**
     * Sheet for editing which gestures a preset scene uses. Members of the factory preset can be
     * turned off, and any other gesture can be added. A gesture added from outside the preset keeps
     * the action the user bound to it globally; preset members keep their scene-local mapping, and
     * the recognition lock is always kept.
     */
    fun buildSceneGestureEditor(
        context: Context,
        scene: GestureScene,
        removedCodes: Set<GestureCode>,
        addedCodes: Set<GestureCode>,
        callbacks: SceneGestureEditorCallbacks
    ): View {
        val removed = removedCodes.toMutableSet()
        val added = addedCodes.toMutableSet()
        val preset = GestureSceneCatalog.definition(scene)
        val unlocked = GestureUnlockStore(context).entitlement().codes
        val inPreset = { code: GestureCode -> code in preset.enabledCodes }
        val isEnabled = { code: GestureCode ->
            if (inPreset(code)) code !in removed else code in added
        }
        fun rowText(code: GestureCode, enabled: Boolean): String {
            val detail = when {
                code in SYSTEM_CONTROL_CODES -> "识别锁，始终保留"
                inPreset(code) -> preset.actionOverrides[code]?.displayLabel()
                    ?.let { "场景内：$it" }.orEmpty()
                code !in unlocked -> "未解锁 · 添加后仍需解锁才生效"
                else -> "沿用你在全局设置的动作"
            }
            return buildString {
                append(if (enabled) "☑  " else "☐  ")
                append(GESTURE_DISPLAY_NAMES[code] ?: code.name)
                append(" · ${code.name}")
                if (detail.isNotBlank()) append("\n     $detail")
            }
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 20))
            background = rounded(context, Color.rgb(7, 28, 65), 22, Color.rgb(62, 111, 190))
            addView(text(context, "编辑${sceneLabel(scene)}的手势", 21f, Color.WHITE, true))
            addView(text(context, "勾选即参与该场景，取消勾选则不参与。识别锁始终保留。", 12f, secondary, false).apply {
                setPadding(0, dp(context, 5), 0, dp(context, 10))
            })
            val rows = mutableMapOf<GestureCode, TextView>()
            val headers = mutableListOf<TextView>()
            fun repaint() {
                rows.forEach { (code, row) ->
                    val enabled = isEnabled(code)
                    row.text = rowText(code, enabled)
                    row.setTextColor(if (enabled) Color.WHITE else muted)
                    row.alpha = if (enabled) 1f else 0.65f
                    row.background = rounded(
                        context,
                        if (enabled) Color.argb(220, 38, 42, 145) else surface,
                        13,
                        if (enabled) cyan else border
                    )
                }
                headers[0].text = sectionTitle("场景预设", rows.count { inPreset(it.key) && isEnabled(it.key) })
                headers[1].text = sectionTitle("可添加的手势", rows.count { !inPreset(it.key) && isEnabled(it.key) })
            }
            val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val presetCodes = preset.enabledCodes.sortedWith(
                compareBy<GestureCode>({ it in SYSTEM_CONTROL_CODES }, { it.name })
            )
            val addableCodes = GestureCode.entries
                .filter { it !in preset.enabledCodes }
                .sortedBy { it.name }
            fun addSection(title: String, codes: List<GestureCode>) {
                val header = text(context, title, 13f, cyan, true).apply {
                    setPadding(dp(context, 2), dp(context, 10), dp(context, 2), dp(context, 4))
                }
                headers.add(header)
                list.addView(header)
                codes.forEach { code ->
                    val locked = code in SYSTEM_CONTROL_CODES
                    val row = text(context, "", 14f, Color.WHITE, !locked).apply {
                        setPadding(dp(context, 14), dp(context, 11), dp(context, 14), dp(context, 11))
                        if (locked) {
                            contentDescription = "识别锁，不可移除"
                        } else {
                            contentDescription = "${GESTURE_DISPLAY_NAMES[code] ?: code.name}，点击切换是否参与该场景"
                            setOnClickListener {
                                val nowEnabled = !isEnabled(code)
                                if (inPreset(code)) {
                                    if (nowEnabled) removed.remove(code) else removed.add(code)
                                } else {
                                    if (nowEnabled) added.add(code) else added.remove(code)
                                }
                                callbacks.onToggle(code, nowEnabled)
                                repaint()
                            }
                        }
                    }
                    rows[code] = row
                    list.addView(row, blockParams(context, 4, 4))
                }
            }
            addSection("", presetCodes)
            addSection("", addableCodes)
            repaint()
            addView(ScrollView(context).apply { addView(list) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 320)
            ).apply { topMargin = dp(context, 4) })
            addView(LinearLayout(context).apply {
                addView(actionButton(context, "恢复默认", primary = false) {
                    removed.clear()
                    added.clear()
                    callbacks.onRestoreDefaults()
                    repaint()
                }, LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginEnd = dp(context, 6) })
                addView(actionButton(context, "完成", primary = true) {
                    callbacks.onDone()
                }, LinearLayout.LayoutParams(0, dp(context, 50), 1f).apply { marginStart = dp(context, 6) })
            }, blockParams(context, 12, 0))
        }
    }

    private fun sectionTitle(label: String, enabledCount: Int) = "$label · 已选 $enabledCount"

    /**
     * Sheet opened by a long press on the floating dot: it switches the scene that is active right
     * now and never touches the saved "app -> scene" association.
     */
    fun buildSceneSwitchPrompt(
        context: Context,
        current: GestureScene,
        canRememberDefault: Boolean,
        callbacks: SceneSwitchCallbacks
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        // Swallows its own touches so a tap inside the panel never reaches the dimmed root.
        isClickable = true
        setPadding(dp(context, 18), dp(context, 18), dp(context, 18), dp(context, 20))
        background = rounded(context, Color.rgb(7, 28, 65), 22, Color.rgb(62, 111, 190))
        addView(text(context, "切换当前场景", 21f, Color.WHITE, true))
        addView(text(context, if (canRememberDefault) "点击场景立即切换，「默认」会把它记住给当前应用。"
            else "手动切换只影响当前应用，切换应用后恢复自动选择。", 12f, secondary, false).apply {
            setPadding(0, dp(context, 5), 0, dp(context, 12))
        })
        GestureScene.entries.forEach { scene ->
            val active = scene == current
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(context, 13), dp(context, 11), dp(context, 13), dp(context, 11))
                background = rounded(
                    context,
                    if (active) Color.argb(220, 38, 42, 145) else surface,
                    13,
                    if (active) cyan else border
                )
                addView(scenePresetIcon(context, scene), LinearLayout.LayoutParams(
                    dp(context, 40), dp(context, 40)
                ).apply { marginEnd = dp(context, 12) })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(context, sceneLabel(scene), 15f, if (active) Color.WHITE else secondary, true))
                    addView(text(context, sceneDescription(scene), 11.5f, muted, false))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                if (active) addView(text(context, "当前", 11f, cyan, true).apply {
                    setPadding(dp(context, 8), dp(context, 4), dp(context, 8), dp(context, 4))
                    background = rounded(context, Color.argb(95, 24, 215, 255), 12, cyan)
                })
                if (canRememberDefault) addView(text(context, "默认", 11f, violet, true).apply {
                    setPadding(dp(context, 9), dp(context, 6), dp(context, 9), dp(context, 6))
                    background = rounded(context, Color.argb(120, 36, 78, 145), 12, violet)
                    contentDescription = "把${sceneLabel(scene)}设为当前应用的默认场景"
                    setOnClickListener { callbacks.onRememberDefault(scene) }
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(context, 8) })
                contentDescription = "${sceneLabel(scene)}，${sceneDescription(scene)}"
                setOnClickListener { callbacks.onPick(scene) }
            }
            addView(row, blockParams(context, 6, 4))
        }
        addView(actionButton(context, "关闭", primary = false) { callbacks.onDismiss() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 50)
            ).apply { topMargin = dp(context, 12) })
    }

    fun appLabel(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun sceneLabel(scene: GestureScene) = when (scene) {
        GestureScene.GENERAL -> "通用场景"
        GestureScene.SHORT_VIDEO -> "短视频场景"
        GestureScene.MUSIC -> "音乐场景"
    }

    private fun sceneDescription(scene: GestureScene) = when (scene) {
        GestureScene.GENERAL -> "保持你的全局手势设置"
        GestureScene.SHORT_VIDEO -> "滑动、点赞、收藏、播放控制"
        GestureScene.MUSIC -> "播放、切歌、音量控制"
    }

    private fun appIcon(context: Context, pkg: String?): ImageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.CENTER_INSIDE
        setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7))
        background = rounded(context, Color.argb(190, 22, 66, 133), 12, border)
        val icon = pkg?.let { runCatching { context.packageManager.getApplicationIcon(it) }.getOrNull() }
        if (icon != null) setImageDrawable(icon) else setImageResource(R.drawable.ic_scene_layers)
        contentDescription = pkg?.let { appLabel(context, it) } ?: "通用场景"
    }

    private fun sceneIcon(context: Context) = ImageView(context).apply {
        setImageResource(R.drawable.ic_scene_layers)
        setPadding(dp(context, 7), dp(context, 7), dp(context, 7), dp(context, 7))
        background = rounded(context, Color.argb(190, 20, 73, 155), 12, cyan)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    private fun pill(context: Context, value: String) = text(context, value, 11f, Color.WHITE, true).apply {
        gravity = Gravity.CENTER
        setPadding(dp(context, 11), dp(context, 6), dp(context, 11), dp(context, 6))
        background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(30, 116, 255), violet)).apply {
            cornerRadius = dp(context, 18).toFloat()
        }
    }

    private fun actionButton(context: Context, value: String, primary: Boolean, click: () -> Unit) = Button(context).apply {
        text = value
        textSize = 14f
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = if (primary) {
            GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(14, 190, 255), violet)).apply {
                cornerRadius = dp(context, 14).toFloat()
            }
        } else {
            rounded(context, Color.argb(160, 6, 31, 70), 14, Color.rgb(76, 148, 255))
        }
        setOnClickListener { click() }
    }

    private fun text(context: Context, value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(context: Context, fill: Int, radius: Int, stroke: Int? = null, dashed: Boolean = false) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = dp(context, radius).toFloat()
            if (stroke != null) setStroke(dp(context, 1), stroke, if (dashed) dp(context, 6).toFloat() else 0f, if (dashed) dp(context, 4).toFloat() else 0f)
        }

    private fun blockParams(context: Context, top: Int, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top)
            bottomMargin = dp(context, bottom)
        }

    private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
