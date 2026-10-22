package com.magicgesture.app

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

/** A small service-owned status dot that remains visible above other apps. */
class OverlayIndicator(
    private val context: Context
) {
    /**
     * LOCKED：识别已锁定，仅 Love 手势可以解锁。相机与手部检测仍在运行，
     * 但除解锁姿势外不输出任何手势、光标或动作。
     */
    enum class State { STARTING, RUNNING, LOCKED, ERROR }

    /**
     * Two full-strength pulses with a gap between them. A single short tick was too weak to notice
     * on Honor's motor; this reads as a deliberate confirmation without becoming a notification
     * buzz. Timings are delay/pause pairs: 0 delay, 90ms on, 60ms off, 90ms on, no repeat.
     */
    private val tickTimings = longArrayOf(0, 90, 60, 90)
    private val tickAmplitudes = intArrayOf(0, 255, 0, 255)

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var feedbackEnabled = GesturePreferences.feedbackEnabled(context)
    private val visualSize = dp(32)
    private val touchSize = dp(44)
    private val margin = dp(8)
    /** Keeps the default corner above the system gesture bar at the bottom of the screen. */
    private val bottomInset = dp(28)
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
        x = margin
        // Left-bottom corner, lifted clear of the system gesture bar so dragging the dot is not
        // stolen by the back gesture.
        y = context.resources.displayMetrics.heightPixels - touchSize - bottomInset
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
    /**
     * Fired when the dot is held without being dragged. The service uses it to open the manual
     * scene switcher, so a press that already triggered this must not also open the app.
     */
    var onLongPress: (() -> Unit)? = null
    // Long enough that an ordinary tap on the dot (well under 400ms) still opens the app, short
    // enough that it does not feel like a wait. Matches Android's "medium" hold delay.
    private val longPressMs = 1000L
    private var dragListener: DragTouchListener? = null
    private val hideFeedback = Runnable {
        feedbackView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        feedbackView = null
    }
    /**
     * True while a rolling screenshot is being captured. The dot and the feedback pill both float
     * above the screen, so an unhidden overlay ends up stitched into every saved long image.
     */
    @Volatile private var hiddenForCapture = false
    private var selfiePreviewView: ImageView? = null
    private var selfiePreviewBitmap: Bitmap? = null
    private var countdownView: TextView? = null
    private val hideCountdown = Runnable {
        countdownView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        countdownView = null
    }
    private val hideSelfiePreview = Runnable {
        val preview = selfiePreviewView ?: return@Runnable
        preview.animate().alpha(0f).setDuration(350L).withEndAction {
            try { windowManager.removeView(preview) } catch (_: Exception) { }
            if (selfiePreviewView === preview) {
                selfiePreviewView = null
                selfiePreviewBitmap?.recycle()
                selfiePreviewBitmap = null
            }
        }.start()
    }

    fun show() {
        if (view != null || !Settings.canDrawOverlays(context)) return
        restorePosition()
        val listener = DragTouchListener()
        val dot = FloatingDotView(context).apply {
            contentDescription = "隔空手势运行中，轻点返回应用，拖动可移动，长按切换场景"
            setOnTouchListener(listener)
        }
        dragListener = listener
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

    fun showProtection(durationMs: Long = GlobalCooldownManager.DEFAULT_DURATION_MS) {
        view?.post { view?.startProtection(durationMs) }
    }

    /** Applies the preference live; disabling also removes feedback already visible on screen. */
    fun updateFeedbackEnabled(enabled: Boolean) {
        feedbackEnabled = enabled
        if (!enabled) main.post {
            main.removeCallbacks(hideFeedback)
            hideFeedback.run()
        }
    }

    /**
     * Takes the whole overlay down while a rolling screenshot is captured and puts it back
     * afterwards. Called from the accessibility service, which owns the capture.
     */
    fun setHiddenForCapture(hidden: Boolean) {
        hiddenForCapture = hidden
        main.post {
            if (hidden) {
                main.removeCallbacks(hideFeedback)
                hideFeedback.run()
                view?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
                view = null
            } else {
                show()
            }
        }
    }

    fun showFeedback(message: String, progress: Int? = null) {
        if (!Settings.canDrawOverlays(context) || hiddenForCapture) return
        main.post {
            if (!feedbackEnabled || hiddenForCapture) return@post
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

    /** Shows one large countdown digit for the selfie timer; call again each second. */
    fun showCountdown(seconds: Int) {
        if (!Settings.canDrawOverlays(context)) return
        main.post {
            main.removeCallbacks(hideCountdown)
            countdownView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
            countdownView = null
            val digit = TextView(context).apply {
                text = seconds.toString()
                textSize = 84f
                setTextColor(Color.WHITE)
                typeface = Typeface.DEFAULT_BOLD
                gravity = Gravity.CENTER
                elevation = dp(16).toFloat()
                contentDescription = "自拍倒计时 $seconds"
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.argb(190, 30, 27, 92))
                    setStroke(dp(2), Color.argb(200, 255, 255, 255))
                }
                alpha = 0f
                scaleX = 1.6f
                scaleY = 1.6f
            }
            val size = dp(132)
            val countdownParams = WindowManager.LayoutParams(
                size,
                size,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.CENTER
            }
            try {
                windowManager.addView(digit, countdownParams)
            } catch (_: Exception) { return@post }
            countdownView = digit
            digit.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180L).start()
            main.postDelayed(hideCountdown, 850L)
        }
    }

    /** Shows a short-lived thumbnail of the just-saved selfie, similar to the system screenshot preview. Takes bitmap ownership. */
    fun showSelfiePreview(source: Bitmap) = showSavedImagePreview(
        source = source,
        description = "自拍已保存，这是照片缩略图预览",
        maxPreviewHeight = Int.MAX_VALUE
    )

    /** Shows the complete long screenshot as a bounded, short-lived preview. Takes bitmap ownership. */
    fun showRollingScreenshotPreview(source: Bitmap) = showSavedImagePreview(
        source = source,
        description = "滚动长截图已保存，这是截图缩略图预览",
        maxPreviewHeight = dp(220)
    )

    private fun showSavedImagePreview(source: Bitmap, description: String, maxPreviewHeight: Int) {
        if (!Settings.canDrawOverlays(context)) { source.recycle(); return }
        main.post {
            main.removeCallbacks(hideSelfiePreview)
            selfiePreviewView?.let { existing ->
                existing.animate().cancel()
                try { windowManager.removeView(existing) } catch (_: Exception) { }
                selfiePreviewBitmap?.recycle()
            }
            selfiePreviewView = null
            selfiePreviewBitmap = null

            val previewWidth = dp(128)
            val ratio = source.height.toFloat() / source.width
            val thumbnail: Bitmap = try {
                val scaled = if (source.width <= 480) source
                else Bitmap.createScaledBitmap(
                    source,
                    480,
                    (source.height * (480f / source.width)).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled !== source) source.recycle()
                scaled
            } catch (_: Exception) { return@post }
            val viewWidth = previewWidth + dp(8)
            val viewHeight = (previewWidth * ratio).toInt()
                .coerceAtLeast(dp(48))
                .coerceAtMost(maxPreviewHeight) + dp(8)
            val previewParams = WindowManager.LayoutParams(
                viewWidth,
                viewHeight,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = margin
                y = dp(84)
            }
            val imageView = ImageView(context).apply {
                setImageBitmap(thumbnail)
                scaleType = ImageView.ScaleType.FIT_CENTER
                setPadding(dp(4), dp(4), dp(4), dp(4))
                elevation = dp(12).toFloat()
                clipToOutline = true
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.WHITE)
                    setStroke(dp(1), Color.argb(170, 255, 255, 255))
                }
                contentDescription = description
                alpha = 0f
            }
            try {
                windowManager.addView(imageView, previewParams)
            } catch (_: Exception) { thumbnail.recycle(); return@post }
            selfiePreviewView = imageView
            selfiePreviewBitmap = thumbnail
            imageView.animate().alpha(1f).setDuration(220L).start()
            main.postDelayed(hideSelfiePreview, 3200L)
        }
    }

    fun remove() {
        dragListener?.cancelLongPress()
        dragListener = null
        main.removeCallbacks(hideFeedback)
        feedbackView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        feedbackView = null
        main.removeCallbacks(hideCountdown)
        countdownView?.let { try { windowManager.removeView(it) } catch (_: Exception) { } }
        countdownView = null
        main.removeCallbacks(hideSelfiePreview)
        selfiePreviewView?.let {
            it.animate().cancel()
            try { windowManager.removeView(it) } catch (_: Exception) { }
        }
        selfiePreviewView = null
        selfiePreviewBitmap?.recycle()
        selfiePreviewBitmap = null
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

    /**
     * Short tick when the long press fires. Uses the Vibrator directly instead of
     * performHapticFeedback: that one is muted by the system "touch feedback" setting, and on a
     * device where the user turned it off the long press had no feedback at all.
     */
    private fun vibrateTick() {
        val vibrator = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        }.getOrNull()
        if (vibrator?.hasVibrator() != true) {
            Log.d("CameraProbe", "long press tick skipped: no vibrator")
            return
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Explicit amplitudes: the waveform overload defaults to the platform strength,
                // which was too weak to feel here. MAX_AMPLITUDE is not public, hence 255.
                vibrator.vibrate(VibrationEffect.createWaveform(tickTimings, tickAmplitudes, -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(tickTimings, -1)
            }
            Log.d("CameraProbe", "long press tick waveform ${tickTimings.joinToString(",")}")
        }.onFailure { Log.d("CameraProbe", "long press tick failed: ${it.javaClass.simpleName}") }
    }

    private inner class DragTouchListener : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var longPressRunnable: Runnable? = null
        private var longPressFired = false

        fun cancelLongPress() {
            longPressRunnable?.let { main.removeCallbacks(it) }
            longPressRunnable = null
        }

        private fun scheduleLongPress(view: View) {
            longPressFired = false
            val runnable = Runnable {
                longPressRunnable = null
                longPressFired = true
                // A tick at the moment the hold is enough, so the sheet never appears unannounced.
                vibrateTick()
                onLongPress?.invoke()
            }
            longPressRunnable = runnable
            main.postDelayed(runnable, longPressMs)
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x
                    startY = params.y
                    if (onLongPress != null) scheduleLongPress(v)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    // A drag is not a long press: cancel as soon as the finger really moves.
                    if (abs(event.rawX - downX) + abs(event.rawY - downY) > dp(8)) cancelLongPress()
                    params.x = startX + (event.rawX - downX).toInt()
                    params.y = startY + (event.rawY - downY).toInt()
                    try { windowManager.updateViewLayout(v, params) } catch (_: Exception) { }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cancelLongPress()
                    // The press already opened the scene switcher; releasing must not open the app.
                    if (longPressFired) {
                        longPressFired = false
                        return true
                    }
                    val moved = abs(event.rawX - downX) + abs(event.rawY - downY)
                    if (moved < dp(8)) openApp() else snapToEdge(v)
                    return true
                }
            }
            return false
        }
    }

    /**
     * Where the dot appears when gesture control starts: the place the user last dragged it to, or
     * the home corner (bottom-left) when it has never been moved. Saved coordinates are clamped,
     * because the screen can be a different size after a rotation or a display change.
     */
    private fun restorePosition() {
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - touchSize - margin).coerceAtLeast(margin)
        val maxY = (metrics.heightPixels - touchSize - margin).coerceAtLeast(margin)
        val saved = GesturePreferences.savedDotPosition(context)
        if (saved != null) {
            params.x = saved.first.coerceIn(margin, maxX)
            params.y = saved.second.coerceIn(margin, maxY)
            return
        }
        params.x = margin
        params.y = (metrics.heightPixels - touchSize - bottomInset).coerceIn(margin, maxY)
    }

    private fun snapToEdge(dot: View) {
        val screenWidth = context.resources.displayMetrics.widthPixels
        params.x = if (params.x + touchSize / 2 < screenWidth / 2) margin else screenWidth - touchSize - margin
        params.y = params.y.coerceIn(margin, context.resources.displayMetrics.heightPixels - touchSize - margin)
        try { windowManager.updateViewLayout(dot, params) } catch (_: Exception) { }
        // Remember where it was left, so closing the app or rebooting does not send it home.
        GesturePreferences.saveDotPosition(context, params.x, params.y)
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
                State.LOCKED -> Color.rgb(250, 173, 40)
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
