package com.magicgesture.app

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot

/** Injection and cursor boundary. Called only while user-started control is active. */
class ControlAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "MagicGestureA11y"
        @Volatile var active: ControlAccessibilityService? = null; private set
        /** How long Recents may keep the foreground unknown before the wait is given up. */
        const val RECENTS_TARGET_TIMEOUT_MS = 5_000L
        /**
         * Rolling screenshot limits. Frames stop at the screen count, and the stitched image stops at
         * the height multiple — whichever comes first. Four screens, the old limit, produced images
         * about two screens tall, which is why a long page looked like it stopped almost immediately.
         */
        const val MAX_ROLLING_FRAMES = 24
        const val MAX_ROLLING_SCREENS = 8f
        /**
         * How each screen is advanced: one slow drag whose travel is known. A quick swipe leaves the
         * distance to the fling, which on device moved the page by only a fifth of a screen and
         * cannot be predicted; a drag is followed one-to-one, so the overlap between two frames is
         * known instead of guessed.
         */
        const val ROLLING_DRAG_MS = 1_400L
        const val ROLLING_DRAG_TRAVEL = .35f
        /** Wait after the drag so any leftover inertia settles before the next frame is captured. */
        const val ROLLING_DRAG_SETTLE_MS = 400L
        /** How many times a drag that moves nothing is retried before the page counts as finished. */
        const val ROLLING_MAX_STALLS = 1
        /** Row fingerprints used to line two frames up: colour per row, per column bucket. */
        const val ROLLING_FINGERPRINT_COLS = 64
        /**
         * Upper bound of the seam search, as a share of the frame. A single drag scrolled 71% of the
         * frame on the home page and 13px on the very next one, so the range has to cover both.
         */
        const val ROLLING_MAX_SHIFT = .72f
        /** With less than half the frame left overlapping a match wins by chance far too easily. */
        const val ROLLING_THIN_OVERLAP_RATIO = .60f
        /** Stride of the coarse pass; the fine pass then checks every pixel around it. */
        const val ROLLING_COARSE_STEP = 8
        /**
         * Weight that pulls the seam towards the expected distance when rows look alike. Kept small
         * on purpose: the measured travel was three times the hint, so a strong pull moves the seam
         * off the true position and towards the hint.
         */
        const val ROLLING_OVERLAP_BIAS = .015
        /**
         * Above this the two frames are treated as not lining up. The score is the mean difference
         * of the red+green+blue sum (0-765), not of a single channel, so a real seam on a text-heavy
         * page measures around 60-70 and a single-channel threshold of 25 rejected every good seam.
         */
        const val ROLLING_SEAM_LIMIT = 110.0
        /** A best score this close to "nothing moved" means the page did not actually scroll. */
        const val ROLLING_STILL_RATIO = .90f
        /** How long after Recents a launcher event is treated as the switcher closing, not a target. */
        const val RECENTS_LAUNCHER_GRACE_MS = 3_000L
    }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    override fun onRebind(intent: Intent?) { super.onRebind(intent); active = this }
    override fun onUnbind(intent: Intent?): Boolean {
        active = null
        hideCursor()
        busy = false
        return true
    }
    private var cursor: View? = null
    private var cursorX = .5f
    private var cursorY = .5f
    private var busy = false
    private val main = Handler(Looper.getMainLooper())
    private val imageWorker = Executors.newSingleThreadExecutor()
    /**
     * Notes from the last rolling capture. They are written to a file as well as the log: the cable
     * drops often enough that a capture cannot be diagnosed from logcat alone.
     */
    private val rollingTrace = StringBuilder()
    private val window by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    @Volatile private var foregroundPackageName: String? = null
    @Volatile private var awaitingRecentsTarget = false
    /** When Recents was last opened; the selected app may never announce itself at all. */
    @Volatile private var recentsOpenedAt = 0L
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString()?.takeIf { it.isNotBlank() && it != packageName } ?: return
        foregroundPackageName = when {
            // Status bar, notification shade, IME and permission dialogs are transient overlays:
            // recording them as "the current app" made the OK favorite gesture keep failing after
            // a single notification shade pull, or click into the wrong package.
            isTransientOverlay(pkg) -> return
            // The launcher and Settings have nothing to favorite: clear the target so the gesture
            // cannot click the previously used app's coordinates while it is not even visible.
            isHomeOrSettings(pkg) -> {
                // Honor reports the task switcher as the launcher, and it does so twice: once
                // while Recents is handing control over, and again right after the restored app
                // has already been announced. The second one wiped the app the user had just
                // restored, leaving every later OK gesture with no target at all. Ignore the
                // launcher briefly around Recents; the timeout above is what ends the wait.
                if (awaitingRecentsTarget || withinRecentsLauncherGrace()) return
                awaitingRecentsTarget = false
                null
            }
            else -> {
                // TYPE_WINDOWS_CHANGED is required here: restoring an existing task from Recents
                // does not reliably produce TYPE_WINDOW_STATE_CHANGED on every Android build.
                awaitingRecentsTarget = false
                pkg
            }
        }
    }

    /** System surfaces that sit on top of an app without replacing it. */
    private fun isTransientOverlay(pkg: String): Boolean {
        if (pkg == "android" || pkg == "com.android.systemui") return true
        if (pkg.startsWith("com.android.permissioncontroller") || pkg.startsWith("com.google.android.permissioncontroller")) return true
        if (pkg.startsWith("com.android.packageinstaller") || pkg.startsWith("com.google.android.packageinstaller")) return true
        return pkg == currentInputMethodPackage()
    }

    private fun isHomeOrSettings(pkg: String): Boolean =
        pkg == "com.android.settings" || pkg == homePackage()

    /** Right after Recents a launcher event is the task switcher closing, not the destination. */
    private fun withinRecentsLauncherGrace(): Boolean =
        SystemClock.uptimeMillis() - recentsOpenedAt < RECENTS_LAUNCHER_GRACE_MS

    private fun homePackage(): String? = runCatching {
        packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName
    }.getOrNull()

    private fun currentInputMethodPackage(): String? = runCatching {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        if (flat.isNullOrBlank()) null else ComponentName.unflattenFromString(flat)?.packageName
    }.getOrNull()
    override fun onInterrupt() { hideCursor(); busy = false }
    override fun onDestroy() { active = null; hideCursor(); imageWorker.shutdownNow(); super.onDestroy() }

    fun foregroundPackage(): String? = if (isAwaitingRecentsTarget()) null else foregroundPackageName

    /**
     * True only between opening Recents and receiving the selected app's real window event.
     * That event is not guaranteed to arrive at all, so the wait expires instead of sticking
     * forever and failing every later favorite gesture.
     */
    fun isAwaitingRecentsTarget(): Boolean {
        if (!awaitingRecentsTarget) return false
        if (SystemClock.uptimeMillis() - recentsOpenedAt >= RECENTS_TARGET_TIMEOUT_MS) {
            awaitingRecentsTarget = false
            return false
        }
        return true
    }

    fun tapNormalized(x: Float, y: Float, callback: (Boolean) -> Unit) = main.post {
        if (busy) { callback(false); return@post }
        val m = resources.displayMetrics
        val path = Path().apply {
            moveTo(x.coerceIn(0f, 1f) * m.widthPixels, y.coerceIn(0f, 1f) * m.heightPixels)
        }
        dispatch(path, 70, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
    }

    /**
     * Taps raw display pixels in the full-display coordinate space (system bars included).
     * The favorite calibration overlay covers the whole display, so its positions must never be
     * scaled by [android.content.res.Resources.getSystem] style app-usable metrics: those drop
     * the status and navigation bars and shifted every saved favorite position upwards.
     */
    fun tapPixels(xPx: Float, yPx: Float, callback: (Boolean) -> Unit) = main.post {
        if (busy) { callback(false); return@post }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val (width, height) = realDisplaySize(wm)
        val path = Path().apply {
            moveTo(xPx.coerceIn(0f, width.toFloat()), yPx.coerceIn(0f, height.toFloat()))
        }
        dispatch(path, 70, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
    }

    /** Largest bounds an app window can occupy — the full display including system bar areas. */
    private fun realDisplaySize(wm: WindowManager): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = wm.maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            @Suppress("DEPRECATION")
            val metrics = DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
            metrics.widthPixels to metrics.heightPixels
        }

    fun render(x: Float, y: Float) = main.post {
        cursorX = x.coerceIn(0f, 1f)
        cursorY = y.coerceIn(0f, 1f)
        val metrics = resources.displayMetrics
        val view = cursor ?: View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.CYAN); setStroke(3, Color.BLACK) }
            window.addView(this, WindowManager.LayoutParams(28, 28,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT).apply { gravity = Gravity.TOP or Gravity.LEFT })
            cursor = this
        }
        val params = view.layoutParams as WindowManager.LayoutParams
        params.x = (x.coerceIn(0f, 1f) * metrics.widthPixels).toInt().coerceIn(0, metrics.widthPixels - 28)
        params.y = (y.coerceIn(0f, 1f) * metrics.heightPixels).toInt().coerceIn(0, metrics.heightPixels - 28)
        window.updateViewLayout(view, params)
    }
    fun hideCursor() = main.post { cursor?.let { window.removeView(it) }; cursor = null }
    fun confirmAtCursor(callback: (Boolean) -> Unit) = main.post {
        if (busy || cursor == null) { callback(false); return@post }
        val metrics = resources.displayMetrics
        val path = Path().apply { moveTo(cursorX * metrics.widthPixels, cursorY * metrics.heightPixels) }
        dispatch(path, 70, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
    }

    // ---- Action-centric entry points: used by GestureActionExecutor so a remapped gesture
    // ---- never depends on its original event type. ----

    /** Performs a system global action (back / home / recents / ...). */
    fun globalAction(actionCode: Int, callback: (Boolean) -> Unit = {}) = main.post {
        if (busy) { callback(false); return@post }
        val success = performGlobalAction(actionCode)
        if (success && actionCode == GLOBAL_ACTION_RECENTS) {
            // Never let an OK gesture reuse the previous app's saved coordinates while the
            // task switcher is still handing control to the newly selected application.
            foregroundPackageName = null
            awaitingRecentsTarget = true
            recentsOpenedAt = SystemClock.uptimeMillis()
        }
        callback(success)
    }

    /** Scrolls the current page in the given direction, mirroring the swipe injection timing. */
    fun scrollDirectional(up: Boolean, callback: (Boolean) -> Unit = {}) = main.post {
        if (busy) { callback(false); return@post }
        val m = resources.displayMetrics
        val path = Path().apply {
            moveTo(m.widthPixels * .5f, m.heightPixels * (if (up) .78f else .18f))
            lineTo(m.widthPixels * .5f, m.heightPixels * (if (up) .22f else .82f))
        }
        dispatch(path, if (up) 300 else 420, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
    }

    /** Horizontal page scroll; the stroke direction matches the hand wave direction. */
    fun scrollHorizontal(left: Boolean, callback: (Boolean) -> Unit = {}) = main.post {
        if (busy) { callback(false); return@post }
        val m = resources.displayMetrics
        val path = Path().apply {
            moveTo(m.widthPixels * (if (left) .78f else .22f), m.heightPixels * .5f)
            lineTo(m.widthPixels * (if (left) .22f else .78f), m.heightPixels * .5f)
        }
        dispatch(path, 420, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
    }

    /**
     * Continuous drag (G26 claw): START only anchors the finger position, every MOVE injects one
     * short completed swipe from the previous point to the new one, and END closes the drag.
     *
     * The finger is deliberately not kept pressed between segments. A touch that rests still for a
     * few hundred milliseconds becomes a long-press, and on a web page that selects text instead of
     * scrolling it. Injecting one short swipe per palm movement scrolls the page the same way while
     * the touch is never still long enough to turn into a selection. Each swipe keeps the real palm
     * travel over a fixed short duration, so the page follows the hand: quick palms fling, slow
     * palms creep, and the drag may last as long as the pose is held.
     */
    private var dragActive = false
    private var dragLastX = 0f
    private var dragLastY = 0f
    /** Short enough that the touch never rests long enough to be read as a long-press. */
    private val dragSwipeMs = 120L
    /** Below this a swipe does nothing on screen; skip it and let the travel accumulate. */
    private val dragMinTravelPx = 10f

    fun beginDrag(x: Float, y: Float, callback: (Boolean) -> Unit = {}) = main.post {
        val m = resources.displayMetrics
        dragLastX = x * m.widthPixels
        dragLastY = y * m.heightPixels
        dragActive = true
        callback(true)
    }

    fun moveDrag(x: Float, y: Float, callback: (Boolean) -> Unit = {}) = main.post {
        if (!dragActive) { callback(false); return@post }
        // Skip while the previous swipe is still running; the travel accumulates into the next one.
        if (busy) { callback(false); return@post }
        val m = resources.displayMetrics
        val nextX = x * m.widthPixels
        val nextY = y * m.heightPixels
        if (hypot(nextX - dragLastX, nextY - dragLastY) < dragMinTravelPx) { callback(true); return@post }
        busy = true
        val path = Path().apply { moveTo(dragLastX, dragLastY); lineTo(nextX, nextY) }
        val stroke = GestureDescription.StrokeDescription(path, 0, dragSwipeMs, false)
        val accepted = dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    busy = false; dragLastX = nextX; dragLastY = nextY; callback(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d("CameraProbe", "drag swipe cancelled by the system")
                    busy = false; callback(false)
                }
            },
            main
        )
        if (!accepted) {
            Log.d("CameraProbe", "drag swipe refused")
            busy = false; callback(false)
        }
    }

    fun endDrag(x: Float, y: Float, callback: (Boolean) -> Unit = {}) = main.post {
        // The last swipe already put the finger down and up, so ending only closes the drag.
        dragActive = false
        callback(true)
    }

    /** System screenshot; mirrors the API-28 guard inside inject(). */
    fun screenshotAction(callback: (Boolean) -> Unit = {}) = main.post {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) callback(performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT))
        else callback(false)
    }

    /** Locks the screen; GLOBAL_ACTION_LOCK_SCREEN requires API 28+. */
    fun lockScreenAction(callback: (Boolean) -> Unit = {}) = main.post {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) callback(performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN))
        else callback(false)
    }

    /** Opens the system voice assistant; no window content is read or passed along. */
    fun voiceAssistantAction(callback: (Boolean) -> Unit = {}) = main.post {
        try {
            startActivity(Intent(Intent.ACTION_VOICE_COMMAND).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            callback(true)
        } catch (e: Exception) {
            callback(false)
        }
    }

    fun inject(event: GestureEvent, callback: (Boolean) -> Unit = {}) = main.post {
        if (busy) { callback(false); return@post }
        val m = resources.displayMetrics
        val path = Path()
        when (event) {
            is GestureEvent.Click -> {
                path.moveTo(event.x * m.widthPixels, event.y * m.heightPixels)
                dispatch(path, 70, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
            }
            is GestureEvent.Swipe -> {
                val x = m.widthPixels * .5f
                val start = m.heightPixels * (if (event.up) .78f else .18f)
                val end = m.heightPixels * (if (event.up) .22f else .82f)
                path.moveTo(x, start); path.lineTo(x, end)
                dispatch(path, if (event.up) 300 else 420, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
            }
            is GestureEvent.HorizontalSwipe -> {
                val y = m.heightPixels * .52f
                val start = m.widthPixels * (if (event.left) .82f else .18f)
                val end = m.widthPixels * (if (event.left) .18f else .82f)
                path.moveTo(start, y); path.lineTo(end, y)
                dispatch(path, 360, onDone = { busy = false; callback(true) }, onCancelled = { busy = false; callback(false) })
            }
            GestureEvent.Screenshot -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    callback(performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT))
                } else callback(false)
            }
            GestureEvent.Like -> Unit
            GestureEvent.Back -> callback(performGlobalAction(GLOBAL_ACTION_BACK))
            GestureEvent.Home -> callback(performGlobalAction(GLOBAL_ACTION_HOME))
            GestureEvent.Recents -> callback(performGlobalAction(GLOBAL_ACTION_RECENTS))
            GestureEvent.LotusRecents -> callback(performGlobalAction(GLOBAL_ACTION_RECENTS))
            GestureEvent.OrchidBack -> callback(performGlobalAction(GLOBAL_ACTION_BACK))
            else -> callback(false)
        }
    }

    fun likeVideo(callback: (Boolean) -> Unit) = main.post {
        if (busy) { callback(false); return@post }
        val metrics = resources.displayMetrics
        val x = metrics.widthPixels * .50f
        val y = metrics.heightPixels * .52f
        val tap = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(tap, 0, 55))
            .addStroke(GestureDescription.StrokeDescription(tap, 135, 55))
            .build()
        busy = true
        val accepted = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { busy = false; callback(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { busy = false; callback(false) }
        }, main)
        if (!accepted) { busy = false; callback(false) }
    }

    @SuppressLint("NewApi") // Every API 28-30 call below is guarded by the Android 11 check.
    fun captureRollingScreenshot(
        onProgress: (String) -> Unit,
        onComplete: (Boolean, String) -> Unit,
        onOverlayVisible: (Boolean) -> Unit = {},
        onPreview: (Bitmap) -> Unit = { it.recycle() }
    ) = main.post {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            onComplete(false, "滚动截图需要 Android 11 或更高版本")
            return@post
        }
        if (busy) {
            onComplete(false, "当前有操作正在执行")
            return@post
        }
        busy = true
        hideCursor()
        val metrics = resources.displayMetrics
        val stitch = RollingStitch()
        var screens = 0
        var stalled = 0
        var finished = false
        rollingTrace.setLength(0)
        trace("capture started")

        fun finishCapture(success: Boolean, failureMessage: String = "滚动截图未能完成，请保持页面静止后重试") {
            if (finished) return
            finished = true
            trace("finished success=$success after $screens screens")
            // Nothing is captured from here on, so the overlay can come back before the stitch runs.
            onOverlayVisible(true)
            if (!success) {
                stitch.release()
                imageWorker.execute { saveRollingTrace() }
                busy = false
                onComplete(false, failureMessage)
                return
            }
            onProgress("正在拼接长图…")
            imageWorker.execute {
                try {
                    val stitched = stitch.result()
                    trace("image ${stitched.width}x${stitched.height}")
                    val uri = saveLongScreenshot(stitched)
                    trace("saved=${uri != null}")
                    val preview = if (uri != null) rollingPreview(stitched) else null
                    stitched.recycle()
                    stitch.release()
                    saveRollingTrace()
                    main.post {
                        busy = false
                        preview?.let(onPreview)
                        onComplete(uri != null, if (uri != null) "滚动截图已保存到图片/MagicGesture" else "滚动截图保存失败")
                    }
                } catch (e: Throwable) {
                    // OutOfMemoryError is an Error, not an Exception: catching only Exception let a
                    // failed stitch kill the process instead of reporting an ordinary failure.
                    Log.w(TAG, "rolling screenshot stitch failed", e)
                    stitch.release()
                    saveRollingTrace()
                    main.post { busy = false; onComplete(false, "滚动截图拼接失败，内容可能过长") }
                }
            }
        }

        // Held as a value so the drag helper and the capture step can call each other.
        var captureNext: () -> Unit = {}

        /** One scroll plus the next capture. */
        fun scrollAndCapture() {
            // Keep canRetrieveWindowContent=false: scrolling is a deterministic injected drag and
            // never searches or acts on another app's accessibility node tree.
            val centreX = metrics.widthPixels * .5f
            val travel = metrics.heightPixels * ROLLING_DRAG_TRAVEL
            val lower = metrics.heightPixels * .80f
            val path = Path().apply {
                moveTo(centreX, lower)
                lineTo(centreX, lower - travel)
            }
            // A drag the system refuses is not the end of the page: one more try keeps an otherwise
            // good capture from ending after two screens.
            val onCancelled: () -> Unit = {
                if (stalled++ < ROLLING_MAX_STALLS) {
                    trace("drag cancelled, retrying")
                    main.postDelayed({ scrollAndCapture() }, ROLLING_DRAG_SETTLE_MS)
                } else {
                    trace("drag refused twice, stopping")
                    finishCapture(false, "页面没有响应滚动手势，长截图未完成")
                }
            }
            dispatch(
                path, ROLLING_DRAG_MS,
                onDone = { main.postDelayed({ captureNext() }, ROLLING_DRAG_SETTLE_MS) },
                onCancelled = onCancelled
            )
        }

        captureNext = {
            takeScreenBitmap { bitmap ->
                if (bitmap == null) {
                    stitch.release()
                    busy = false
                    onOverlayVisible(true)
                    onComplete(false, "无法读取屏幕画面")
                    return@takeScreenBitmap
                }
                val previous = stitch.lastFrame
                val difference = previous?.let { frameDifference(it, bitmap) } ?: Double.MAX_VALUE
                if (difference < 5.0) {
                    trace("page did not move (diff=${"%.1f".format(difference)})")
                    // The page may still have room left and the drag simply did not land, so give it
                    // one more chance before calling this the bottom.
                    if (stalled++ < ROLLING_MAX_STALLS) {
                        val dropped = bitmap
                        dropped.recycle()
                        scrollAndCapture()
                        return@takeScreenBitmap
                    }
                    // Same view as the frame already stitched, so its tail still belongs at the end.
                    stitch.appendTail(bitmap)
                    bitmap.recycle()
                    finishCapture(true)
                    return@takeScreenBitmap
                }
                when (stitch.append(bitmap)) {
                    AppendResult.FULL -> { trace("reached the height ceiling"); bitmap.recycle(); finishCapture(true); return@takeScreenBitmap }
                    AppendResult.MISALIGNED -> {
                        bitmap.recycle()
                        finishCapture(false, "页面内容无法可靠对齐，未保存错误长图")
                        return@takeScreenBitmap
                    }
                    AppendResult.BOTTOM -> { bitmap.recycle(); finishCapture(true); return@takeScreenBitmap }
                    AppendResult.OK -> { stalled = 0 }
                }
                screens++
                if (screens >= MAX_ROLLING_FRAMES) {
                    trace("reached the frame limit")
                    finishCapture(true)
                    return@takeScreenBitmap
                }
                scrollAndCapture()
            }
        }

        // The pill is shown first so the user sees the gesture was accepted, then removed with a
        // short gap before the first frame: anything still floating would be stitched into the image.
        onProgress("正在滚动截图，请勿触碰屏幕…")
        main.postDelayed({
            onOverlayVisible(false)
            main.postDelayed({ captureNext() }, 300L)
        }, 700L)
    }

    /** Keeps a very tall long screenshot cheap enough for the overlay while preserving its shape. */
    private fun rollingPreview(source: Bitmap): Bitmap? = try {
        val scale = minOf(480f / source.width, 720f / source.height, 1f)
        Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true
        )
    } catch (e: Throwable) {
        Log.w(TAG, "rolling screenshot preview failed", e)
        null
    }

    @SuppressLint("NewApi") // Called only after captureRollingScreenshot verifies API 30+.
    private fun takeScreenBitmap(callback: (Bitmap?) -> Unit) {
        // Android 14 refuses the call outright when the service does not declare canTakeScreenshot,
        // and some ROMs throw instead of reporting onFailure. An unguarded throw here escapes on the
        // binder thread and takes the whole process down, so every step is fenced and reported as a
        // normal failure instead.
        try {
            takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    try {
                        val buffer = screenshot.hardwareBuffer
                        val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                        buffer.close()
                        callback(bitmap)
                    } catch (e: Throwable) {
                        Log.w(TAG, "screenshot decode failed", e)
                        callback(null)
                    }
                }
                override fun onFailure(errorCode: Int) {
                    Log.w(TAG, "takeScreenshot failed: $errorCode")
                    callback(null)
                }
            })
        } catch (e: Throwable) {
            Log.w(TAG, "takeScreenshot unavailable", e)
            callback(null)
        }
    }

    private fun trace(line: String) {
        Log.d(TAG, "rolling $line")
        rollingTrace.append(line).append('\n')
    }

    /**
     * Writes the capture notes to Downloads so they can be read back after the fact — a dropped
     * adb connection otherwise leaves nothing to go on.
     */
    private fun saveRollingTrace() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val text = rollingTrace.toString()
        if (text.isBlank()) return
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, "rolling_trace.txt")
            put(MediaStore.Downloads.MIME_TYPE, "text/plain")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        try {
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            contentResolver.update(uri, values, null, null)
        } catch (e: Throwable) {
            Log.w(TAG, "rolling trace write failed", e)
        }
    }

    private fun frameDifference(a: Bitmap, b: Bitmap): Double {
        val width = minOf(a.width, b.width)
        val height = minOf(a.height, b.height)
        var total = 0L
        var count = 0L
        val top = (height * .10f).toInt()
        val bottom = (height * .90f).toInt()
        for (y in top until bottom step 36) for (x in width / 10 until width * 9 / 10 step 36) {
            val ca = a.getPixel(x, y); val cb = b.getPixel(x, y)
            total += abs(Color.red(ca) - Color.red(cb)) + abs(Color.green(ca) - Color.green(cb)) + abs(Color.blue(ca) - Color.blue(cb))
            count += 3
        }
        return if (count == 0L) 255.0 else total.toDouble() / count
    }

    /**
     * Appends captured screens to one tall bitmap and releases the frames it no longer needs.
     * Keeping every screen alive as a separate full-resolution bitmap is what made long pages run
     * out of memory, so only the previous screen — needed to measure the overlap — and the result
     * are held. The result uses RGB_565: a long screenshot is saved as JPEG, and halving the bytes
     * per pixel is what leaves room for a page several screens tall.
     */
    private inner class RollingStitch {
        // The captured frames are not the size DisplayMetrics reports: sizing the crop from the
        // metrics cut the first screen short and put every seam in the wrong place. Everything is
        // measured off the first frame instead, which is also what keeps the buffer from being
        // allocated for a screen taller than the frames that go into it.
        private var width = 0
        private var topCrop = 0
        private var cropHeight = 0
        private var maxHeight = 0
        private lateinit var bitmap: Bitmap
        private lateinit var canvas: Canvas
        private var used = 0
        private var previous: Bitmap? = null
        private var previousRows: IntArray? = null
        val lastFrame: Bitmap? get() = previous

        private fun sizeTo(frame: Bitmap): Boolean {
            if (width != 0) return true
            width = frame.width
            topCrop = (frame.height * .07f).toInt()
            cropHeight = (frame.height * .86f).toInt().coerceAtLeast(1)
            maxHeight = (frame.height * MAX_ROLLING_SCREENS).toInt()
            trace("frame ${frame.width}x${frame.height} ceiling=$maxHeight")
            // Bumped against the memory ceiling on tall pages: a failure here ends the capture with
            // what fits instead of killing the service.
            bitmap = try {
                Bitmap.createBitmap(width, maxHeight, Bitmap.Config.RGB_565)
            } catch (e: OutOfMemoryError) {
                Log.w(TAG, "rolling buffer too large, stopping")
                width = 0
                return false
            }
            canvas = Canvas(bitmap)
            return true
        }

        /**
         * FULL once the ceiling is reached, MISALIGNED when the new frame cannot be lined up with
         * the one before it — stitching a frame that does not line up is what produced visibly
         * broken seams, so the capture stops and keeps what already lines up.
         */
        fun append(frame: Bitmap): AppendResult {
            if (!sizeTo(frame)) return AppendResult.FULL
            val rows = rowFingerprints(frame, topCrop, cropHeight)
            // How far the page actually scrolled. Measuring the shift itself — instead of the
            // overlap left over after cropping — keeps the search and the cut in the same units.
            val expected = (frame.height * ROLLING_DRAG_TRAVEL).toInt()
            val travel = previousRows?.let { findShift(it, rows, expected) }
            if (previousRows != null && travel == null) {
                trace("lost the seam, stopping at $used px")
                return AppendResult.MISALIGNED
            }
            // A shift of zero is the end of the page. Nothing more lines up to be appended, so this
            // last frame contributes only the tail below the crop.
            if (travel?.shift == 0) {
                appendTail(frame)
                return AppendResult.BOTTOM
            }
            // The first screen has nothing above it, so it contributes its whole usable band;
            // applying the predicted shift there cut the top of the image off.
            val seam = travel?.let { (cropHeight - it.shift).coerceIn(0, cropHeight - 1) } ?: 0
            val srcTop = (topCrop + seam).coerceAtMost(frame.height - 1)
            val srcHeight = (cropHeight - seam).coerceAtLeast(1).coerceAtMost(frame.height - srcTop)
            if (used + srcHeight > maxHeight) return AppendResult.FULL
            canvas.drawBitmap(frame, Rect(0, srcTop, width, srcTop + srcHeight), Rect(0, used, width, used + srcHeight), null)
            used += srcHeight
            previous?.recycle()
            previous = frame
            previousRows = rows
            return AppendResult.OK
        }

        /**
         * Adds the strip below the crop: the bottom of the screen, minus the navigation bar. Every
         * other frame only contributes above that line, so without this the last screenful loses
         * its tail and the page ends early. The bar itself is excluded — it belongs to the system,
         * not to the content, and its height comes from the platform so no device guess is needed.
         */
        fun appendTail(frame: Bitmap) {
            if (width == 0) return
            val top = topCrop + cropHeight
            val navBar = resources.getIdentifier("navigation_bar_height", "dimen", "android")
                .takeIf { it > 0 }?.let { resources.getDimensionPixelSize(it) } ?: 0
            val bottom = (frame.height - navBar).coerceIn(top, frame.height)
            val take = minOf(bottom - top, maxHeight - used)
            if (take <= 0) return
            trace("tail +$take px (nav bar $navBar px)")
            canvas.drawBitmap(frame, Rect(0, top, width, top + take), Rect(0, used, width, used + take), null)
            used += take
        }

        fun result(): Bitmap = if (width == 0) Bitmap.createBitmap(1, 1, Bitmap.Config.RGB_565)
        else Bitmap.createBitmap(bitmap, 0, 0, width, used.coerceAtLeast(1))

        fun release() {
            previous?.let { if (!it.isRecycled) it.recycle() }
            previous = null
            previousRows = null
            if (width != 0 && !bitmap.isRecycled) bitmap.recycle()
        }
    }

    enum class AppendResult { OK, FULL, MISALIGNED, BOTTOM }

    /**
     * One colour sample per row and column bucket. Matching whole rows instead of a sparse grid
     * is what keeps the seam accurate: a grid that sampled every 42nd pixel picked neighbouring
     * look-alike rows on pages built from repeating cards and drifted the seam on every screen.
     */
    private fun rowFingerprints(frame: Bitmap, top: Int, height: Int): IntArray {
        val cols = ROLLING_FINGERPRINT_COLS
        val safeTop = top.coerceIn(0, frame.height - 1)
        val safeHeight = height.coerceAtLeast(1).coerceAtMost(frame.height - safeTop)
        val out = IntArray(safeHeight * cols)
        var row = 0
        val left = frame.width * .10f
        val sampleWidth = frame.width * .80f
        for (y in safeTop until safeTop + safeHeight) {
            for (col in 0 until cols) {
                val x = (left + sampleWidth * (col + .5f) / cols).toInt().coerceIn(0, frame.width - 1)
                val pixel = frame.getPixel(x, y)
                out[row * cols + col] = pixel and 0x00ffffff
            }
            row++
        }
        return out
    }

    private data class Seam(val shift: Int, val score: Double)

    /**
     * How far the page scrolled between two frames, in pixels. Null when no shift lines the frames
     * up well enough to be trusted — a wrong shift is what puts a visible step in the stitched image.
     */
    private fun findShift(previousRows: IntArray, nextRows: IntArray, expected: Int): Seam? {
        val cols = ROLLING_FINGERPRINT_COLS
        val rowCount = minOf(previousRows.size, nextRows.size) / cols
        val minShift = 1
        val maxShift = (rowCount * ROLLING_MAX_SHIFT).toInt().coerceAtMost(rowCount - 1)
        if (maxShift <= minShift) return null

        fun scoreAt(shift: Int): Double {
            val rows = rowCount - shift
            var total = 0L
            for (r in 0 until rows) {
                val previousBase = (shift + r) * cols
                val nextBase = r * cols
                for (col in 0 until cols) {
                    val a = previousRows[previousBase + col]
                    val b = nextRows[nextBase + col]
                    total += abs(Color.red(a) - Color.red(b))
                    total += abs(Color.green(a) - Color.green(b))
                    total += abs(Color.blue(a) - Color.blue(b))
                }
            }
            return total.toDouble() / (rows * cols) + abs(shift - expected) * ROLLING_OVERLAP_BIAS
        }

        // Coarse pass for the neighbourhood, fine pass for the exact pixel: a seam that is off by a
        // single pixel reads as a clear step in the finished image.
        var coarse = minShift
        var coarseScore = Double.MAX_VALUE
        for (shift in minShift..maxShift step ROLLING_COARSE_STEP) {
            val score = scoreAt(shift)
            if (score < coarseScore) { coarseScore = score; coarse = shift }
        }
        var bestShift = coarse
        var bestScore = Double.MAX_VALUE
        val fineFrom = (coarse - ROLLING_COARSE_STEP).coerceAtLeast(minShift)
        val fineTo = (coarse + ROLLING_COARSE_STEP).coerceAtMost(maxShift)
        for (shift in fineFrom..fineTo) {
            val score = scoreAt(shift)
            if (score < bestScore) { bestScore = score; bestShift = shift }
        }
        val still = scoreAt(0)
        trace("shift expected=$expected got=$bestShift score=${"%.1f".format(bestScore)} still=${"%.1f".format(still)}")
        // A page that never moved still produces a low score somewhere in the range; without this
        // check the search happily reports a shift that is just the closest look-alike pair of rows.
        // A zero shift means "the bottom is here", not "the seam is broken", so it is reported as
        // such and the caller ends the capture where things already line up.
        // Measured: the 13px shift of a page already at its bottom had look-alike rows nearby that
        // scored almost as well at 1484px, so a thin overlap has to beat "nothing moved" by a far
        // wider margin before such a big shift is believed.
        val ratio = if (bestShift > rowCount / 2) ROLLING_THIN_OVERLAP_RATIO else ROLLING_STILL_RATIO
        if (bestScore > still * ratio) return Seam(0, bestScore)
        if (bestScore > ROLLING_SEAM_LIMIT) return null
        return Seam(bestShift, bestScore)
    }

    private fun saveLongScreenshot(bitmap: Bitmap): android.net.Uri? {
        val name = "MagicGesture_long_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/MagicGesture")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val saved = contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) } == true
        if (!saved) { contentResolver.delete(uri, null, null); return null }
        values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        return uri
    }

    private fun dispatch(path: Path, duration: Long, onDone: (() -> Unit)? = null, onCancelled: (() -> Unit)? = null) {
        dispatch(
            GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build(),
            onDone,
            onCancelled
        )
    }

    private fun dispatch(gesture: GestureDescription, onDone: (() -> Unit)? = null, onCancelled: (() -> Unit)? = null) {
        busy = true
        val accepted = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (onDone == null) busy = false; onDone?.invoke() }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (onCancelled == null) busy = false; onCancelled?.invoke() }
        }, main)
        if (!accepted) { if (onCancelled == null) busy = false; onCancelled?.invoke() }
    }
}
