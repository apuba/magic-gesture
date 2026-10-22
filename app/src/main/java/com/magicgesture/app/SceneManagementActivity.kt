package com.magicgesture.app

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Typeface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors

/**
 * Phase-two local scene management. It never reads third-party page text or view trees.
 * The whole page is drawn by [ScenePageUi]; this activity only resolves state, persists changes
 * and owns dialogs, toasts and the camera service hand-off.
 */
class SceneManagementActivity : Activity() {
    private val pageBackground = Color.rgb(0, 6, 32)
    private var associationPromptShown = false
    private val iconCache = mutableMapOf<String, Drawable?>()
    private val iconLoader = Executors.newSingleThreadExecutor()
    /** Separate from [iconLoader] so building the app list never delays the icons. */
    private val appLoader = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = pageBackground
        window.navigationBarColor = pageBackground
        renderPage()
    }

    override fun onResume() {
        super.onResume()
        renderPage()
        val current = currentExternalPackage()
        if (!associationPromptShown && GesturePreferences.sceneAutoSwitchEnabled(this) &&
            current != null && GesturePreferences.sceneAssociation(this, current) == null) {
            associationPromptShown = true
            window.decorView.post { showSceneChoice(current, firstAssociation = true) }
        }
    }

    override fun onDestroy() {
        iconLoader.shutdownNow()
        appLoader.shutdownNow()
        super.onDestroy()
    }

    private fun renderPage() {
        setContentView(ScenePageUi.build(this, currentState(), pageCallbacks()))
    }

    private fun currentState() = ScenePageState(
        autoSwitchEnabled = GesturePreferences.sceneAutoSwitchEnabled(this),
        currentPackage = currentExternalPackage(),
        associations = GesturePreferences.sceneAssociations(this).toList(),
        removedCodes = GestureScene.entries
            .filter(GestureSceneCatalog::isEditable)
            .associateWith { GesturePreferences.sceneRemovedCodes(this, it) },
        addedCodes = GestureScene.entries
            .filter(GestureSceneCatalog::isEditable)
            .associateWith { GesturePreferences.sceneAddedCodes(this, it) }
    )

    private fun pageCallbacks() = object : ScenePageCallbacks {
        override fun onBack() = finish()

        override fun onAutoSwitchChanged(enabled: Boolean) {
            GesturePreferences.saveSceneAutoSwitchEnabled(this@SceneManagementActivity, enabled)
            Toast.makeText(
                this@SceneManagementActivity,
                if (enabled) "已开启自动切换" else "已关闭自动切换，当前使用通用场景",
                Toast.LENGTH_SHORT
            ).show()
        }

        override fun onSelectScene(scene: GestureScene) {
            val current = currentExternalPackage()
            if (current != null) {
                showSceneChoice(current, firstAssociation = GesturePreferences.sceneAssociation(this@SceneManagementActivity, current) == null, initialScene = scene)
            } else {
                showAppPicker(scene)
            }
        }

        override fun onEditSceneGestures(scene: GestureScene) = showSceneGestureEditor(scene)

        override fun onEditAssociation(packageName: String) = showSceneChoice(packageName, firstAssociation = false)

        override fun onAddAssociation() = showAppPicker()

        override fun onDeleteAssociation(packageName: String) = confirmDelete(packageName)
    }

    /** A launcher app whose label has already been resolved; see [loadLauncherApps]. */
    private data class AppEntry(val packageName: String, val label: String)

    private fun showAppPicker(initialScene: GestureScene? = null) {
        val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(this).apply { addView(rows) }
        // One read instead of a SharedPreferences lookup per row.
        val associations = GesturePreferences.sceneAssociations(this)
        /** Null until the app list arrives from the background thread. */
        var entries: List<AppEntry>? = null
        lateinit var dialog: AlertDialog

        fun message(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(TEXT_MUTED)
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, dp(20))
        }

        fun render(query: String) {
            rows.removeAllViews()
            val list = entries
            if (list == null) {
                rows.addView(message("正在加载应用…"))
                return
            }
            val normalized = query.trim().lowercase()
            val shown = if (normalized.isEmpty()) list else list.filter {
                it.label.lowercase().contains(normalized) || it.packageName.contains(normalized)
            }
            if (shown.isEmpty()) {
                rows.addView(message(if (list.isEmpty()) "没有可关联的应用" else "没有匹配的应用"))
                return
            }
            shown.forEach { entry ->
                val bound = associations.containsKey(entry.packageName)
                rows.addView(
                    appPickerRow(
                        packageName = entry.packageName,
                        label = entry.label,
                        selected = bound
                    ) {
                        dialog.dismiss()
                        showSceneChoice(entry.packageName, firstAssociation = !bound, initialScene = initialScene)
                    },
                    LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = dp(6)
                    }
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
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = render(s?.toString().orEmpty())
                override fun afterTextChanged(s: Editable?) = Unit
            })
        }
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(12), 0, 0)
            addView(search)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(320)).apply {
                topMargin = dp(10)
            })
        }
        dialog = neonDialog("选择要关联的应用", body)
        // Open straight away and let the list fill in: resolving a few hundred labels takes long
        // enough that doing it first made the button feel dead.
        render("")
        dialog.show()
        appLoader.execute {
            val loaded = runCatching { loadLauncherApps() }.getOrDefault(emptyList())
            mainHandler.post {
                if (isFinishing || isDestroyed) return@post
                entries = loaded
                render(search.text?.toString().orEmpty())
            }
        }
    }

    /**
     * Resolves the launcher apps and their labels. Both the query and every [loadLabel] are binder
     * calls, hence off the main thread; the label is read exactly once and reused for sorting and
     * for filtering.
     */
    private fun loadLauncherApps(): List<AppEntry> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .map { info ->
                val pkg = info.activityInfo.packageName
                AppEntry(pkg, runCatching { info.loadLabel(packageManager).toString() }.getOrDefault(pkg))
            }
            .sortedBy { it.label.lowercase() }
    }

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
        panel.addView(cancel, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        val dialog = AlertDialog.Builder(this).setView(panel).create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        cancel.setOnClickListener { dialog.dismiss() }
        dialog.setOnShowListener {
            dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.92f).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        return dialog
    }

    private fun appPickerRow(packageName: String, label: String, selected: Boolean, onClick: () -> Unit) =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(9), dp(13), dp(9))
            background = pressable(
                rounded(if (selected) Color.argb(110, 24, 116, 220) else Color.argb(120, 10, 28, 58), 12, if (selected) COLOR_CYAN else COLOR_BORDER),
                rounded(Color.argb(190, 24, 62, 110), 12, COLOR_BORDER)
            )
            val icon = ImageView(this@SceneManagementActivity).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = rounded(Color.argb(120, 13, 42, 86), 10)
                clipToOutline = true
                tag = packageName
            }
            addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(12) })
            loadAppIcon(packageName, icon)
            addView(TextView(this@SceneManagementActivity).apply {
                text = label
                textSize = 14.5f
                setTextColor(if (selected) COLOR_CYAN else TEXT_PRIMARY)
                if (selected) typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (selected) addView(TextView(this@SceneManagementActivity).apply {
                text = "✓"
                textSize = 13f
                setTextColor(COLOR_CYAN)
                typeface = Typeface.DEFAULT_BOLD
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(8)
            })
            setOnClickListener { onClick() }
        }

    private fun loadAppIcon(packageName: String, target: ImageView) {
        if (iconCache.containsKey(packageName)) {
            target.setImageDrawable(iconCache[packageName])
            return
        }
        iconLoader.execute {
            val drawable = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
            iconCache[packageName] = drawable
            mainHandler.post { if (target.tag == packageName) target.setImageDrawable(drawable) }
        }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun pressable(normal: Drawable, pressed: Drawable) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), pressed)
        addState(intArrayOf(android.R.attr.state_focused), pressed)
        addState(intArrayOf(), normal)
    }

    private fun pressable(fill: Int, pressedFill: Int, radius: Int, stroke: Int? = null) =
        pressable(rounded(fill, radius, stroke), rounded(pressedFill, radius, stroke))

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun showSceneChoice(pkg: String, firstAssociation: Boolean, initialScene: GestureScene? = null) {
        var dialog: Dialog? = null
        val initial = initialScene ?: GesturePreferences.sceneAssociation(this, pkg) ?: GestureScene.GENERAL
        val callbacks = object : SceneChoiceCallbacks {
            override fun onTemporary(scene: GestureScene) {
                if (!CameraProbeService.isControlRunning) {
                    Toast.makeText(this@SceneManagementActivity, "请先启动手势控制，再使用临时场景", Toast.LENGTH_LONG).show()
                    return
                }
                startService(Intent(this@SceneManagementActivity, CameraProbeService::class.java).apply {
                    action = CameraProbeService.ACTION_SET_TEMPORARY_SCENE
                    putExtra(CameraProbeService.EXTRA_SCENE_PACKAGE, pkg)
                    putExtra(CameraProbeService.EXTRA_SCENE_NAME, scene.name)
                })
                Toast.makeText(this@SceneManagementActivity, "已临时切换为${ScenePageUi.sceneLabel(scene)}", Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
            }

            override fun onSetDefault(scene: GestureScene) {
                GesturePreferences.saveSceneAssociation(this@SceneManagementActivity, pkg, scene)
                Toast.makeText(this@SceneManagementActivity, "已设为${ScenePageUi.sceneLabel(scene)}", Toast.LENGTH_SHORT).show()
                dialog?.dismiss()
                renderPage()
            }

            override fun onDismiss() {
                dialog?.dismiss()
            }
        }
        dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(ScenePageUi.buildSceneChoice(this@SceneManagementActivity, pkg, initial, firstAssociation, callbacks))
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
            }
            show()
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * Edits which gestures a preset scene uses: preset members can be turned off, other gestures
     * can be added. Every change is written immediately so the running control service picks it up
     * through its preference listener.
     */
    private fun showSceneGestureEditor(scene: GestureScene) {
        var dialog: Dialog? = null
        val presetCodes = GestureSceneCatalog.definition(scene).enabledCodes
        val callbacks = object : SceneGestureEditorCallbacks {
            override fun onToggle(code: GestureCode, enabled: Boolean) {
                if (code in presetCodes) {
                    val current = GesturePreferences
                        .sceneRemovedCodes(this@SceneManagementActivity, scene).toMutableSet()
                    if (enabled) current.remove(code) else current.add(code)
                    GesturePreferences.saveSceneRemovedCodes(this@SceneManagementActivity, scene, current)
                } else {
                    val current = GesturePreferences
                        .sceneAddedCodes(this@SceneManagementActivity, scene).toMutableSet()
                    if (enabled) current.add(code) else current.remove(code)
                    GesturePreferences.saveSceneAddedCodes(this@SceneManagementActivity, scene, current)
                }
            }

            override fun onRestoreDefaults() {
                GesturePreferences.clearSceneRemovedCodes(this@SceneManagementActivity, scene)
                GesturePreferences.clearSceneAddedCodes(this@SceneManagementActivity, scene)
                Toast.makeText(
                    this@SceneManagementActivity,
                    "已恢复${ScenePageUi.sceneLabel(scene)}的默认手势",
                    Toast.LENGTH_SHORT
                ).show()
            }

            override fun onDone() {
                dialog?.dismiss()
                renderPage()
            }
        }
        dialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(
                ScenePageUi.buildSceneGestureEditor(
                    context = this@SceneManagementActivity,
                    scene = scene,
                    removedCodes = GesturePreferences.sceneRemovedCodes(this@SceneManagementActivity, scene),
                    addedCodes = GesturePreferences.sceneAddedCodes(this@SceneManagementActivity, scene),
                    callbacks = callbacks
                )
            )
            setCanceledOnTouchOutside(true)
            window?.apply {
                setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setGravity(Gravity.BOTTOM)
            }
            show()
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun confirmDelete(pkg: String) {
        AlertDialog.Builder(this)
            .setTitle("删除场景关联")
            .setMessage("确定删除 ${ScenePageUi.appLabel(this, pkg)} 的场景关联吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                GesturePreferences.deleteSceneAssociation(this, pkg)
                renderPage()
            }.show()
    }

    private fun currentExternalPackage(): String? = ControlAccessibilityService.active?.foregroundPackage()
        ?.takeIf(GestureSceneAssignmentController::isValidPackageName)

    companion object {
        private val COLOR_BORDER = Color.rgb(37, 88, 151)
        private val COLOR_CYAN = Color.rgb(24, 215, 255)
        private val TEXT_PRIMARY = Color.rgb(235, 242, 252)
        private val TEXT_MUTED = Color.rgb(158, 180, 211)
    }
}
