package com.magicgesture.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Manual scene switcher opened by a long press on the floating dot. It changes the scene that is
 * active right now and never writes an "app -> scene" association.
 */
class SceneSwitchPromptController(
    private val context: Context,
    private val onPickScene: (GestureScene) -> Unit,
    private val onRememberDefault: (GestureScene) -> Unit,
    private val onMessage: (String) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(WindowManager::class.java)
    @Volatile private var overlay: View? = null

    private val callbacks = object : SceneSwitchCallbacks {
        override fun onPick(scene: GestureScene) {
            dismissInternal()
            onPickScene(scene)
        }

        override fun onRememberDefault(scene: GestureScene) {
            dismissInternal()
            onRememberDefault(scene)
        }

        override fun onDismiss() = dismissInternal()
    }

    fun isShowing(): Boolean = overlay != null

    /**
     * Opening twice is a no-op: a second long press can never stack a second sheet on top of the
     * one already covering the screen.
     */
    fun show(current: GestureScene, canRememberDefault: Boolean) = main.post {
        if (overlay != null) return@post
        if (!Settings.canDrawOverlays(context)) {
            onMessage("需要允许显示悬浮窗，才能切换场景")
            return@post
        }
        val panel = ScenePageUi.buildSceneSwitchPrompt(context, current, canRememberDefault, callbacks)
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.argb(120, 0, 4, 22))
            isClickable = true
            // Tapping the dimmed area closes the switcher, so it is never a dead end.
            setOnClickListener { dismissInternal() }
            addView(
                panel,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER
                ).apply { setMargins(dp(16), 0, dp(16), 0) }
            )
        }
        replace(root)
    }

    fun dismiss() = main.post { dismissInternal() }

    private fun replace(view: View) {
        removeOverlay()
        overlay = view
        try {
            window.addView(view, overlayParams())
        } catch (_: Exception) {
            overlay = null
            onMessage("无法显示场景切换，请检查悬浮窗权限")
        }
    }

    private fun dismissInternal() {
        removeOverlay()
    }

    private fun removeOverlay() {
        overlay?.let { runCatching { window.removeView(it) } }
        overlay = null
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
