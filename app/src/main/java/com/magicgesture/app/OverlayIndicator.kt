package com.magicgesture.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/** A small service-owned status dot that remains visible above other apps. */
class OverlayIndicator(
    private val context: Context
) {
    enum class State { STARTING, RUNNING, ERROR }

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val feedbackEnabled = GesturePreferences.feedbackEnabled(context)
    private val visualSize = dp(32)
    private val touchSize = dp(44)
    private val margin = dp(8)
    private var view: FloatingDotView? = null
    private var feedbackView: TextView? = null
    private var state = State.STARTING
    private val params = WindowManager.LayoutParams(
        touchSize,
        touchSize,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = context.resources.displayMetrics.widthPixels - touchSize - margin
        y = context.resources.displayMetrics.heightPixels / 3
    }
    private val feedbackParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        dp(44),
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        y = dp(72)
    }
    private val hideFeedback = Runnable {
        feedbackView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        feedbackView = null
    }

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        val dot = FloatingDotView(context).apply {
            contentDescription = "隔空手势运行中，轻点返回应用，拖动可移动"
            setOnTouchListener(DragTouchListener())
        }
        view = dot
        paint()
        try {
            windowManager.addView(dot, params)
        } catch (_: Exception) {
            view = null
        }
    }

    fun setState(value: State) {
        state = value
        view?.post { view?.setIndicatorState(value) }
    }

    fun showProtection(durationMs: Long = 2000L) {
        view?.post { view?.startProtection(durationMs) }
    }

    fun showFeedback(message: String, progress: Int? = null) {
        if (!feedbackEnabled || !Settings.canDrawOverlays(context)) return
        main.post {
            main.removeCallbacks(hideFeedback)
            val pill = feedbackView ?: TextView(context).apply {
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(dp(18), 0, dp(18), 0)
                elevation = dp(8).toFloat()
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(22).toFloat()
                    setColor(Color.argb(238, 63, 60, 154))
                    setStroke(dp(1), Color.argb(150, 255, 255, 255))
                }
                contentDescription = "手势识别反馈"
                try { windowManager.addView(this, feedbackParams) } catch (_: Exception) { return@post }
                feedbackView = this
            }
            pill.text = if (progress == null) message else "$message  $progress%"
            main.postDelayed(hideFeedback, if (progress == null) 1300 else 700)
        }
    }

    fun remove() {
        main.removeCallbacks(hideFeedback)
        feedbackView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        feedbackView = null
        val dot = view ?: return
        view = null
        try { windowManager.removeView(dot) } catch (_: Exception) { }
    }

    private fun paint() {
        view?.setIndicatorState(state)
    }

    private fun openApp() {
        context.startActivity(Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        })
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    try { windowManager.updateViewLayout(v, params) } catch (_: Exception) { }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val moved = abs(event.rawX - downX) + abs(event.rawY - downY)
                    if (moved < dp(8)) openApp() else snapToEdge(v)
                    return true
                }
            }
            return false
        }
    }

    private fun snapToEdge(dot: View) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        params.x = if (params.x + touchSize / 2 < screenWidth / 2) margin else screenWidth - touchSize - margin
        params.y = params.y.coerceIn(margin, context.resources.displayMetrics.heightPixels - touchSize - margin)
        try { windowManager.updateViewLayout(dot, params) } catch (_: Exception) { }
    }

    private inner class FloatingDotView(context: Context) : View(context) {
        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(100, 19, 28, 72)
            setShadowLayer(dp(5).toFloat(), 0f, dp(2).toFloat(), Color.argb(110, 15, 23, 60))
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.argb(185, 160, 220, 255)
        }
        private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(1.7f)
        }
        private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val statusBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(1).toFloat()
            color = Color.WHITE
        }
        private val protectionTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(2).toFloat()
            color = Color.argb(65, 255, 255, 255)
        }
        private val protectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeWidth = dp(2).toFloat()
            color = Color.argb(235, 143, 233, 255)
        }
        private var indicatorState = State.STARTING
        private var protectionStartedAt = 0L
        private var protectionUntil = 0L

        init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

        fun setIndicatorState(value: State) {
            indicatorState = value
            invalidate()
        }

        fun startProtection(durationMs: Long) {
            protectionStartedAt = SystemClock.uptimeMillis()
            protectionUntil = protectionStartedAt + durationMs
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val now = SystemClock.uptimeMillis()
            val protecting = now < protectionUntil
            val cx = width / 2f
            val cy = height / 2f
            val radius = visualSize / 2f
            val left = cx - radius
            val top = cy - radius
            val right = cx + radius
            val bottom = cy + radius

            canvas.drawCircle(cx, cy + dp(1), radius, shadowPaint)
            fillPaint.shader = LinearGradient(
                left, top, right, bottom,
                Color.rgb(33, 92, 238), Color.rgb(106, 45, 214), Shader.TileMode.CLAMP
            )
            fillPaint.alpha = if (protecting) 165 else 245
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius - dp(.5f), borderPaint)

            // A compact magic-gesture mark that stays legible at 32dp.
            symbolPaint.alpha = if (protecting) 175 else 245
            val arm = dp(5).toFloat()
            canvas.drawLine(cx, cy - arm, cx, cy + arm, symbolPaint)
            canvas.drawLine(cx - arm, cy, cx + arm, cy, symbolPaint)
            canvas.drawLine(cx - dp(3), cy - dp(3), cx + dp(3), cy + dp(3), symbolPaint)
            canvas.drawLine(cx + dp(3), cy - dp(3), cx - dp(3), cy + dp(3), symbolPaint)

            statusPaint.color = when (indicatorState) {
                State.STARTING -> Color.rgb(80, 205, 255)
                State.RUNNING -> Color.rgb(52, 211, 153)
                State.ERROR -> Color.rgb(255, 82, 100)
            }
            val statusX = right - dp(3)
            val statusY = bottom - dp(3)
            canvas.drawCircle(statusX, statusY, dp(4).toFloat(), statusPaint)
            canvas.drawCircle(statusX, statusY, dp(4).toFloat(), statusBorderPaint)

            if (protecting) {
                val ringRadius = radius + dp(3)
                val ring = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
                val duration = (protectionUntil - protectionStartedAt).coerceAtLeast(1L)
                val remaining = ((protectionUntil - now).toFloat() / duration).coerceIn(0f, 1f)
                canvas.drawArc(ring, -90f, 360f, false, protectionTrackPaint)
                canvas.drawArc(ring, -90f, 360f * remaining, false, protectionPaint)
                postInvalidateDelayed(33L)
            }
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * context.resources.displayMetrics.density
}
