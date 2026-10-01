package com.magicgesture.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Coordinates the first-use favorite-button calibration and later coordinate clicks. */
class FavoriteButtonController(
    private val context: Context,
    private val accessibilityService: () -> ControlAccessibilityService?,
    private val onFlowStateChanged: (Boolean) -> Unit
) {
    private val main = Handler(Looper.getMainLooper())
    private val window = context.getSystemService(WindowManager::class.java)
    private var overlay: View? = null
    private var completion: ((Boolean) -> Unit)? = null
    private var targetPackage: String? = null
    private var targetLabel: String = ""
    private var targetPortrait = true
    private var selectedX = .82f
    private var selectedY = .52f
    private val timeout = Runnable { finish(false) }

    fun execute(callback: (Boolean) -> Unit) = main.post {
        if (completion != null) { callback(false); return@post }
        val service = accessibilityService() ?: run { callback(false); return@post }
        val packageName = service.foregroundPackage()?.takeIf(::isAllowedTarget) ?: run {
            callback(false)
            return@post
        }
        val portrait = context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE
        completion = callback
        targetPackage = packageName
        targetLabel = appLabel(packageName)
        targetPortrait = portrait
        onFlowStateChanged(true)
        main.postDelayed(timeout, 30_000L)

        val profile = GesturePreferences.favoriteProfile(context, packageName, portrait)
        if (profile != null) {
            service.tapNormalized(profile.normalizedX, profile.normalizedY) { finish(it) }
        } else {
            showFirstUsePrompt()
        }
    }

    fun dismiss() = main.post { finish(false) }

    private fun showFirstUsePrompt() {
        if (!Settings.canDrawOverlays(context)) { finish(false); return }
        replaceOverlay(fullScreenRoot().apply {
            addView(card().apply {
                addView(heading("当前应用尚未定义收藏位置"))
                addView(body("“$targetLabel”需要先标记收藏按钮。定义过程会停留在当前页面。"))
                addView(buttonRow(
                    button("暂不定义") { finish(false) },
                    button("现在定义", primary = true) { showCalibration() }
                ))
            }, centeredCardParams())
        })
    }

    private fun showCalibration() {
        val root = fullScreenRoot(Color.argb(88, 0, 0, 0))
        val marker = CrosshairView(context).apply {
            normalizedX = selectedX
            normalizedY = selectedY
        }
        root.addView(marker, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        root.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                selectedX = (event.x / root.width.coerceAtLeast(1)).coerceIn(.03f, .97f)
                selectedY = (event.y / root.height.coerceAtLeast(1)).coerceIn(.03f, .97f)
                marker.normalizedX = selectedX
                marker.normalizedY = selectedY
                marker.invalidate()
                true
            } else true
        }
        root.addView(card().apply {
            addView(heading("定义 $targetLabel 收藏按钮"))
            addView(body("点击或拖动十字准星到收藏按钮中央。标定时不会操作底层页面。"))
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP).apply {
            setMargins(dp(14), dp(24), dp(14), 0)
        })
        root.addView(card().apply {
            orientation = LinearLayout.HORIZONTAL
            addView(button("取消") { finish(false) }, LinearLayout.LayoutParams(0, dp(48), 1f))
            addView(button("重置") {
                selectedX = .82f; selectedY = .52f
                marker.normalizedX = selectedX; marker.normalizedY = selectedY; marker.invalidate()
            }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(8) })
            addView(button("测试位置", primary = true) { showTestWarning() }, LinearLayout.LayoutParams(0, dp(48), 1.25f).apply { marginStart = dp(8) })
        }, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM).apply {
            setMargins(dp(14), 0, dp(14), dp(24))
        })
        replaceOverlay(root)
    }

    private fun showTestWarning() {
        replaceOverlay(fullScreenRoot().apply {
            addView(card().apply {
                addView(heading("确认测试点击"))
                addView(body("测试会真实点击一次收藏按钮，可能会收藏或取消收藏当前内容。"))
                addView(buttonRow(
                    button("返回调整") { showCalibration() },
                    button("继续测试", primary = true) { performTestClick() }
                ))
            }, centeredCardParams())
        })
    }

    private fun performTestClick() {
        removeOverlay()
        main.postDelayed({
            if (!sameTargetStillForeground()) { finish(false); return@postDelayed }
            accessibilityService()?.tapNormalized(selectedX, selectedY) { success ->
                if (success) main.postDelayed({ showTestResult() }, 350L) else finish(false)
            } ?: finish(false)
        }, 180L)
    }

    private fun showTestResult() {
        replaceOverlay(fullScreenRoot().apply {
            addView(card().apply {
                addView(heading("是否成功点击收藏按钮？"))
                addView(body("只有确认位置正确后才会保存。以后在 $targetLabel 中触发收藏手势会直接点击此位置。"))
                addView(button("点击成功，保存", primary = true) { saveAndFinish() })
                addView(button("位置不准，重新调整") { showCalibration() }.apply {
                    (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
                })
                addView(button("取消，不保存") { finish(false) })
            }, centeredCardParams())
        })
    }

    private fun saveAndFinish() {
        val pkg = targetPackage ?: run { finish(false); return }
        if (!sameTargetStillForeground()) { finish(false); return }
        GesturePreferences.saveFavoriteProfile(context, FavoriteButtonProfile(
            packageName = pkg,
            appLabel = targetLabel,
            portrait = targetPortrait,
            normalizedX = selectedX,
            normalizedY = selectedY,
            appVersion = appVersion(pkg),
            updatedAt = System.currentTimeMillis()
        ))
        finish(true)
    }

    private fun sameTargetStillForeground(): Boolean =
        accessibilityService()?.foregroundPackage() == targetPackage &&
            (context.resources.configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) == targetPortrait

    private fun finish(success: Boolean) {
        main.removeCallbacks(timeout)
        removeOverlay()
        val callback = completion
        completion = null
        targetPackage = null
        onFlowStateChanged(false)
        callback?.invoke(success)
    }

    private fun isAllowedTarget(pkg: String): Boolean {
        if (pkg == context.packageName || pkg == "com.android.systemui" || pkg.startsWith("com.android.settings")) return false
        val home = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
        return pkg != home
    }

    private fun appLabel(pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    @Suppress("DEPRECATION")
    private fun appVersion(pkg: String): String = runCatching {
        context.packageManager.getPackageInfo(pkg, 0).versionName ?: ""
    }.getOrDefault("")

    private fun replaceOverlay(view: View) {
        removeOverlay()
        overlay = view
        try { window.addView(view, overlayParams()) } catch (_: Exception) { overlay = null; finish(false) }
    }

    private fun removeOverlay() {
        overlay?.let { try { window.removeView(it) } catch (_: Exception) { } }
        overlay = null
    }

    private fun overlayParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    private fun fullScreenRoot(color: Int = Color.argb(105, 15, 23, 42)) = FrameLayout(context).apply {
        setBackgroundColor(color)
        isClickable = true
    }

    private fun card() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(18), dp(17), dp(18), dp(17))
        elevation = dp(10).toFloat()
        background = GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = dp(18).toFloat() }
    }

    private fun heading(textValue: String) = TextView(context).apply {
        text = textValue; textSize = 18f; setTextColor(Color.rgb(30, 41, 59)); setTypeface(typeface, Typeface.BOLD)
    }

    private fun body(textValue: String) = TextView(context).apply {
        text = textValue; textSize = 14f; setTextColor(Color.rgb(71, 85, 105)); setPadding(0, dp(9), 0, dp(14))
    }

    private fun button(textValue: String, primary: Boolean = false, action: () -> Unit) = Button(context).apply {
        text = textValue; isAllCaps = false; textSize = 14f
        setTextColor(if (primary) Color.WHITE else Color.rgb(51, 65, 85))
        background = GradientDrawable().apply {
            setColor(if (primary) Color.rgb(79, 70, 229) else Color.rgb(241, 245, 249))
            cornerRadius = dp(12).toFloat()
        }
        setOnClickListener { action() }
    }

    private fun buttonRow(left: View, right: View) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(left, LinearLayout.LayoutParams(0, dp(48), 1f))
        addView(right, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginStart = dp(9) })
    }

    private fun centeredCardParams() = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER
    ).apply { setMargins(dp(22), 0, dp(22), 0) }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()

    private class CrosshairView(context: Context) : View(context) {
        var normalizedX = .82f
        var normalizedY = .52f
        private val white = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 5f; style = Paint.Style.STROKE }
        private val purple = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(99, 82, 230); strokeWidth = 3f; style = Paint.Style.STROKE }
        override fun onDraw(canvas: Canvas) {
            val x = width * normalizedX
            val y = height * normalizedY
            canvas.drawCircle(x, y, 28f, white)
            canvas.drawCircle(x, y, 25f, purple)
            canvas.drawLine(x - 42f, y, x + 42f, y, white)
            canvas.drawLine(x, y - 42f, x, y + 42f, white)
            canvas.drawLine(x - 39f, y, x + 39f, y, purple)
            canvas.drawLine(x, y - 39f, x, y + 39f, purple)
        }
    }
}
