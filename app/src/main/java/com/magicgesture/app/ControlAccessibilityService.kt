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
        const val MAX_ROLLING_SCREENS = 6f
        /**
         * How each screen is advanced: one slow drag whose travel is known. A quick swipe leaves the
         * distance to the fling, which on device moved the page by only a fifth of a screen and
         * cannot be predicted; a drag is followed one-to-one, so the overlap between two frames is
         * known instead of guessed.
         */
        const val ROLLING_DRAG_MS = 800L
        const val ROLLING_DRAG_TRAVEL = .60f
        /** Wait after the drag so any leftover inertia settles before the next frame is captured. */
        const val ROLLING_DRAG_SETTLE_MS = 400L
        /**
         * How much of the drag the page actually follows. Measured at about three quarters on the
         * test device; the search window below is what absorbs the difference.
         */
        const val ROLLING_DRAG_FOLLOW = .85f
        /** How far the overlap search may stray from the distance the drag is expected to move. */
        const val ROLLING_OVERLAP_SLACK = 400
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
        onOverlayVisible: (Boolean) -> Unit = {}
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
        val stitch = RollingStitch(metrics.widthPixels, metrics.heightPixels)
        var screens = 0

        fun finishCapture() {
            // Nothing is captured from here on, so the overlay can come back before the stitch runs.
            onOverlayVisible(true)
            onProgress("正在拼接长图…")
            imageWorker.execute {
                try {
                    val stitched = stitch.result()
                    val uri = saveLongScreenshot(stitched)
                    stitched.recycle()
                    stitch.release()
                    main.post {
                        busy = false
                        onComplete(uri != null, if (uri != null) "滚动截图已保存到图片/MagicGesture" else "滚动截图保存失败")
                    }
                } catch (e: Throwable) {
                    // OutOfMemoryError is an Error, not an Exception: catching only Exception let a
                    // failed stitch kill the process instead of reporting an ordinary failure.
                    Log.w(TAG, "rolling screenshot stitch failed", e)
                    stitch.release()
                    main.post { busy = false; onComplete(false, "滚动截图拼接失败，内容可能过长") }
                }
            }
        }

        fun captureNext() {
            takeScreenBitmap { bitmap ->
                if (bitmap == null) {
                    stitch.release()
                    busy = false
                    onOverlayVisible(true)
                    onComplete(false, "无法读取屏幕画面")
                    return@takeScreenBitmap
                }
                val previous = stitch.lastFrame
                if (previous != null && frameDifference(previous, bitmap) < 5.0) {
                    bitmap.recycle()
                    finishCapture()
                    return@takeScreenBitmap
                }
                // A false return means the image has reached its height ceiling: keep what was
                // captured instead of failing the whole gesture.
                val followed = (metrics.heightPixels * ROLLING_DRAG_TRAVEL * ROLLING_DRAG_FOLLOW).toInt()
                if (!stitch.append(bitmap, followed)) {
                    bitmap.recycle()
                    finishCapture()
                    return@takeScreenBitmap
                }
                screens++
                if (screens >= MAX_ROLLING_FRAMES) {
                    finishCapture()
                    return@takeScreenBitmap
                }
                val centreX = metrics.widthPixels * .5f
                val travel = metrics.heightPixels * ROLLING_DRAG_TRAVEL
                val lower = metrics.heightPixels * .80f
                val path = Path().apply {
                    moveTo(centreX, lower)
                    lineTo(centreX, lower - travel)
                }
                dispatch(path, ROLLING_DRAG_MS, onDone = { main.postDelayed({ captureNext() }, ROLLING_DRAG_SETTLE_MS) }, onCancelled = { finishCapture() })
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
    private inner class RollingStitch(private val width: Int, screenHeight: Int) {
        private val topCrop = (screenHeight * .07f).toInt()
        private val cropHeight = (screenHeight * .86f).toInt().coerceAtLeast(1)
        private val maxHeight = (screenHeight * MAX_ROLLING_SCREENS).toInt()
        private val bitmap = Bitmap.createBitmap(width, maxHeight, Bitmap.Config.RGB_565)
        private val canvas = Canvas(bitmap)
        private var used = 0
        private var previous: Bitmap? = null
        val lastFrame: Bitmap? get() = previous

        /** False once the ceiling is reached; the caller keeps whatever was already stitched. */
        fun append(frame: Bitmap, expectedTravel: Int): Boolean {
            // Searching the whole screen for the best match picked up unrelated look-alike rows —
            // cards on the home page are near-identical — and silently cut real content out of the
            // image. The drag fixes the distance, so the search only has to refine it.
            val expected = (cropHeight - expectedTravel).coerceIn(0, cropHeight - 1)
            val overlap = previous?.let { findOverlap(it, frame, expected) } ?: 0
            val srcTop = (topCrop + overlap).coerceAtMost(frame.height - 1)
            val srcHeight = (cropHeight - overlap).coerceAtLeast(1).coerceAtMost(frame.height - srcTop)
            if (used + srcHeight > maxHeight) return false
            canvas.drawBitmap(frame, Rect(0, srcTop, width, srcTop + srcHeight), Rect(0, used, width, used + srcHeight), null)
            used += srcHeight
            previous?.recycle()
            previous = frame
            return true
        }

        fun result(): Bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, used.coerceAtLeast(1))

        fun release() {
            previous?.let { if (!it.isRecycled) it.recycle() }
            previous = null
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun findOverlap(previous: Bitmap, next: Bitmap, expected: Int): Int {
        val height = minOf(previous.height, next.height)
        val width = minOf(previous.width, next.width)
        var bestOverlap = expected
        var bestScore = Double.MAX_VALUE
        val minOverlap = (expected - ROLLING_OVERLAP_SLACK).coerceAtLeast(0)
        val maxOverlap = (expected + ROLLING_OVERLAP_SLACK).coerceAtMost((height * .74f).toInt())
        for (overlap in minOverlap..maxOverlap step 12) {
            var total = 0L
            var count = 0L
            val previousStart = height - overlap
            for (offsetY in 0 until overlap step 42) for (x in width / 10 until width * 9 / 10 step 42) {
                val a = previous.getPixel(x, previousStart + offsetY)
                val b = next.getPixel(x, offsetY)
                total += abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) + abs(Color.blue(a) - Color.blue(b))
                count += 3
            }
            val score = if (count == 0L) Double.MAX_VALUE else total.toDouble() / count
            if (score < bestScore) { bestScore = score; bestOverlap = overlap }
        }
        return bestOverlap
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
