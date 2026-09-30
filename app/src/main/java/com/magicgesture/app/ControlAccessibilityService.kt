package com.magicgesture.app

import android.annotation.SuppressLint
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

/** Injection and cursor boundary. Called only while user-started control is active. */
class ControlAccessibilityService : AccessibilityService() {
    companion object { @Volatile var active: ControlAccessibilityService? = null; private set }
    override fun onServiceConnected() { super.onServiceConnected(); active = this }
    private var cursor: View? = null
    private var cursorX = .5f
    private var cursorY = .5f
    private var busy = false
    private val main = Handler(Looper.getMainLooper())
    private val imageWorker = Executors.newSingleThreadExecutor()
    private val window by lazy { getSystemService(WINDOW_SERVICE) as WindowManager }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() { hideCursor(); busy = false }
    override fun onDestroy() { active = null; hideCursor(); imageWorker.shutdownNow(); super.onDestroy() }

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
    fun captureRollingScreenshot(onProgress: (String) -> Unit, onComplete: (Boolean, String) -> Unit) = main.post {
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
        val frames = mutableListOf<Bitmap>()

        fun finishCapture() {
            onProgress("正在拼接长图…")
            imageWorker.execute {
                try {
                    val stitched = stitchFrames(frames)
                    val uri = saveLongScreenshot(stitched)
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    if (stitched !in frames && !stitched.isRecycled) stitched.recycle()
                    main.post {
                        busy = false
                        onComplete(uri != null, if (uri != null) "滚动截图已保存到图片/MagicGesture" else "滚动截图保存失败")
                    }
                } catch (_: Exception) {
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    main.post { busy = false; onComplete(false, "滚动截图拼接失败") }
                }
            }
        }

        fun captureNext() {
            takeScreenBitmap { bitmap ->
                if (bitmap == null) {
                    frames.forEach { if (!it.isRecycled) it.recycle() }
                    busy = false
                    onComplete(false, "无法读取屏幕画面")
                    return@takeScreenBitmap
                }
                if (frames.isNotEmpty() && frameDifference(frames.last(), bitmap) < 5.0) {
                    bitmap.recycle()
                    finishCapture()
                    return@takeScreenBitmap
                }
                frames += bitmap
                onProgress("正在采集第 ${frames.size} 屏…")
                if (frames.size >= 4) {
                    finishCapture()
                    return@takeScreenBitmap
                }
                val metrics = resources.displayMetrics
                val path = Path().apply {
                    moveTo(metrics.widthPixels * .5f, metrics.heightPixels * .78f)
                    lineTo(metrics.widthPixels * .5f, metrics.heightPixels * .24f)
                }
                dispatch(path, 430, onDone = { main.postDelayed({ captureNext() }, 650L) }, onCancelled = { finishCapture() })
            }
        }

        onProgress("正在准备滚动截图…")
        main.postDelayed({ captureNext() }, 250L)
    }

    @SuppressLint("NewApi") // Called only after captureRollingScreenshot verifies API 30+.
    private fun takeScreenBitmap(callback: (Bitmap?) -> Unit) {
        takeScreenshot(android.view.Display.DEFAULT_DISPLAY, mainExecutor, object : TakeScreenshotCallback {
            override fun onSuccess(screenshot: ScreenshotResult) {
                val buffer = screenshot.hardwareBuffer
                val bitmap = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                buffer.close()
                callback(bitmap)
            }
            override fun onFailure(errorCode: Int) = callback(null)
        })
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

    private fun stitchFrames(source: List<Bitmap>): Bitmap {
        require(source.isNotEmpty())
        val width = source.minOf { it.width }
        val topCrop = (source.first().height * .07f).toInt()
        val bottomCrop = (source.first().height * .93f).toInt()
        val frames = source.map { Bitmap.createBitmap(it, 0, topCrop.coerceAtMost(it.height - 1), width, (bottomCrop - topCrop).coerceAtMost(it.height - topCrop)) }
        val overlaps = mutableListOf<Int>()
        for (i in 1 until frames.size) overlaps += findOverlap(frames[i - 1], frames[i])
        val totalHeight = frames.first().height + (1 until frames.size).sumOf { frames[it].height - overlaps[it - 1] }
        val result = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        var y = 0
        frames.forEachIndexed { index, bitmap ->
            val crop = if (index == 0) 0 else overlaps[index - 1]
            canvas.drawBitmap(bitmap, 0f, (y - crop).toFloat(), null)
            y += bitmap.height - crop
        }
        frames.forEach { it.recycle() }
        return result
    }

    private fun findOverlap(previous: Bitmap, next: Bitmap): Int {
        val height = minOf(previous.height, next.height)
        val width = minOf(previous.width, next.width)
        var bestOverlap = (height * .25f).toInt()
        var bestScore = Double.MAX_VALUE
        val minOverlap = (height * .12f).toInt()
        val maxOverlap = (height * .72f).toInt()
        for (overlap in minOverlap..maxOverlap step 18) {
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
        busy = true
        val gesture = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, duration)).build()
        val accepted = dispatchGesture(gesture, object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (onDone == null) busy = false; onDone?.invoke() }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (onCancelled == null) busy = false; onCancelled?.invoke() }
        }, main)
        if (!accepted) { if (onCancelled == null) busy = false; onCancelled?.invoke() }
    }
}
