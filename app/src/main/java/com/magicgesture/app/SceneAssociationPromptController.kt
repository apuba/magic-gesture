package com.magicgesture.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Guidance shown when a stable foreground app has no saved scene association.
 *
 * It starts as a small banner at the top of the screen that disappears on its own and is opened
 * into the full chooser only when the user taps it. The banner is deliberately not focusable and
 * not touch modal, and it never pauses recognition: seeing the guidance must not break gestures.
 *
 * Every package is offered the choice once. Leaving the banner up, letting it time out, dismissing
 * it, or answering it all record the package so it never comes back for that app.
 */
class SceneAssociationPromptController(
    private val context: Context,
    /**
     * Reports the package that has just been offered the choice. The service keeps the in-memory
     * "already asked" set, and preferences keep the persisted one: without the in-memory update the
     * next foreground evaluation would still consider the app unasked and prompt again.
     */
    private val onNotified: (String) -> Unit,
    private val onTemporary: (String, GestureScene) -> Unit,
    private val onDefault: (String, GestureScene) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(WindowManager::class.java)
    private var banner: View? = null
    private var sheet: View? = null
    private var autoHide: Runnable? = null
    @Volatile private var targetPackage: String? = null

    private val bannerVisibleMs = 5_000L

    fun show(packageName: String) = main.post {
        if (!GestureSceneAssignmentController.isValidPackageName(packageName)) return@post
        if (targetPackage == packageName && (banner != null || sheet != null)) return@post
        if (!Settings.canDrawOverlays(context)) {
            onMessage("需要允许显示悬浮窗，才能选择应用场景")
            return@post
        }
        closeViews()
        targetPackage = packageName
        showBanner(packageName)
    }

    /** Tapping the banner promotes it to the full chooser. */
    private fun showBanner(packageName: String) {
        val pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(9), dp(14), dp(9))
            elevation = dp(8).toFloat()
            background = rounded(Color.argb(240, 8, 30, 62), 18, CYAN)
            addView(text("◎", 15f, CYAN, true))
            addView(text("${appLabel(packageName)} 未关联场景 · 点此选择 ›", 13f, Color.WHITE, true).apply {
                setPadding(dp(8), 0, 0, 0)
            })
            setOnClickListener { expand(packageName) }
            contentDescription = "${appLabel(packageName)}未关联场景，点击选择场景"
        }
        val root = FrameLayout(context).apply {
            addView(pill, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(58) })
        }
        banner = root
        if (!attach(root, bannerParams())) return
        val runnable = Runnable { dismiss() }
        autoHide = runnable
        main.postDelayed(runnable, bannerVisibleMs)
    }

    private fun expand(packageName: String) {
        cancelAutoHide()
        closeViews()
        targetPackage = packageName
        var selected = GestureScene.GENERAL
        val rows = mutableMapOf<GestureScene, TextView>()
        fun repaint() = rows.forEach { (scene, row) ->
            val active = scene == selected
            row.text = "${if (active) "●" else "○"}  ${label(scene)}\n     ${description(scene)}"
            row.background = rounded(
                if (active) Color.argb(235, 30, 53, 133) else Color.argb(235, 8, 35, 74),
                14,
                if (active) CYAN else BORDER
            )
            row.setTextColor(if (active) Color.WHITE else SECONDARY)
        }
        val panel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(17), dp(18), dp(17))
            elevation = dp(10).toFloat()
            background = rounded(Color.rgb(8, 28, 58), 20, BORDER)
            // Swallows its own touches so a tap inside the panel never reaches the dimmed root.
            isClickable = true
            addView(text("为当前应用选择场景", 18f, Color.WHITE, true))
            addView(text("${appLabel(packageName)} 尚未关联，选择后立即生效", 12.5f, SECONDARY, false).apply {
                setPadding(0, dp(5), 0, dp(10))
            })
            GestureScene.entries.forEach { scene ->
                val row = text("", 14f, Color.WHITE, scene == selected).apply {
                    setPadding(dp(13), dp(10), dp(13), dp(10))
                    setOnClickListener { selected = scene; repaint() }
                }
                rows[scene] = row
                addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = dp(7)
                })
            }
            repaint()
            addView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                addView(button("仅本次使用") {
                    val pkg = targetPackage ?: return@button
                    dismiss()
                    onTemporary(pkg, selected)
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(6) })
                addView(button("设为默认", primary = true) {
                    val pkg = targetPackage ?: return@button
                    dismiss()
                    onDefault(pkg, selected)
                }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(6) })
            })
            addView(text("暂不设置", 12.5f, CYAN, true).apply {
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(14), dp(8), dp(2))
                setOnClickListener { dismiss() }
            })
            addView(text("仅识别前台应用包名，不读取页面内容", 10.5f, MUTED, false).apply {
                gravity = Gravity.CENTER
                setPadding(0, dp(10), 0, 0)
            })
        }
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 0, 4, 22))
            isClickable = true
            setOnClickListener { dismiss() }
            addView(panel, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER).apply {
                setMargins(dp(16), 0, dp(16), 0)
            })
        }
        sheet = root
        attach(root, sheetParams())
    }

    fun isShowing(): Boolean = banner != null || sheet != null

    fun dismissIfTargetChanged(packageName: String?) {
        if (targetPackage == null) return
        if (packageName == targetPackage) return
        main.post {
            if (banner != null || sheet != null) {
                if (packageName != targetPackage) dismissInternal()
            }
        }
    }

    fun dismiss() {
        if (targetPackage == null) return
        main.post { dismissInternal() }
    }

    private fun attach(view: View, params: WindowManager.LayoutParams): Boolean {
        return try {
            window.addView(view, params)
            true
        } catch (_: Exception) {
            if (banner === view) banner = null
            if (sheet === view) sheet = null
            val pkg = targetPackage
            targetPackage = null
            pkg?.let { markNotified(it) }
            onMessage("无法显示场景选择，请检查悬浮窗权限")
            false
        }
    }

    private fun dismissInternal() {
        val pkg = targetPackage
        closeViews()
        targetPackage = null
        // Whatever the user did, this app has now been offered the choice once.
        pkg?.let { markNotified(it) }
    }

    private fun markNotified(packageName: String) {
        GesturePreferences.markSceneNotified(context, packageName)
        onNotified(packageName)
    }

    private fun closeViews() {
        cancelAutoHide()
        banner?.let { runCatching { window.removeView(it) } }
        sheet?.let { runCatching { window.removeView(it) } }
        banner = null
        sheet = null
    }

    private fun cancelAutoHide() {
        autoHide?.let { main.removeCallbacks(it) }
        autoHide = null
    }

    private fun baseParams(flags: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        flags,
        PixelFormat.TRANSLUCENT
    )

    /**
     * A top strip that must not steal anything from the app below: without NOT_FOCUSABLE and
     * NOT_TOUCH_MODAL it would swallow every touch and pause the app while it is on screen.
     */
    private fun bannerParams() = baseParams(
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        height = WindowManager.LayoutParams.WRAP_CONTENT
    }

    private fun sheetParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun button(value: String, primary: Boolean = false, click: () -> Unit) = Button(context).apply {
        text = value
        textSize = 13.5f
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = if (primary) GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.rgb(14, 190, 255), Color.rgb(152, 55, 255))
        ).apply { cornerRadius = dp(14).toFloat() } else rounded(Color.argb(210, 10, 40, 82), 14, Color.rgb(76, 148, 255))
        setOnClickListener { click() }
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(context).apply {
        text = value
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun appLabel(packageName: String) = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun label(scene: GestureScene) = when (scene) {
        GestureScene.GENERAL -> "通用场景"
        GestureScene.SHORT_VIDEO -> "短视频场景"
        GestureScene.MUSIC -> "音乐场景"
    }

    private fun description(scene: GestureScene) = when (scene) {
        GestureScene.GENERAL -> "保持你的全局手势设置"
        GestureScene.SHORT_VIDEO -> "滑动、点赞、收藏、播放控制"
        GestureScene.MUSIC -> "播放、切歌、音量控制"
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private val BORDER = Color.rgb(37, 88, 151)
        private val CYAN = Color.rgb(24, 215, 255)
        private val SECONDARY = Color.rgb(205, 220, 244)
        private val MUTED = Color.rgb(145, 169, 205)
    }
}
