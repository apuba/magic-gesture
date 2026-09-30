package com.magicgesture.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

/** A simple, deliberately unoptimized YUV-to-RGB bridge for the first device run. */
class HandPipeline(context: Context, private val onEvent: (GestureEvent) -> Unit) : AutoCloseable {
    private val engine: GestureEngine
    private val reverseHorizontal: Boolean
    private val landmarker: HandLandmarker
    private var lastSentAt = 0L
    private var lastResultAt = 0L
    private val closed = AtomicBoolean(false)
    @Volatile private var pendingFrameCapture: ((Bitmap) -> Unit)? = null
    init {
        engine = GestureEngine(GesturePreferences.movementScale(context), GesturePreferences.features(context))
        reverseHorizontal = GesturePreferences.reverseHorizontal(context)
        context.assets.open("hand_landmarker.task").close() // fail clearly if the model isn't installed
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setNumHands(1)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                if (closed.get()) return@setResultListener
                val now = SystemClock.uptimeMillis()
                if (now - lastResultAt > 300) engine.lost(now)
                lastResultAt = now
                val hand = result.landmarks().firstOrNull()
                if (hand == null) engine.lost(now)
                else {
                    val points = hand.map { Point(if (reverseHorizontal) 1f - it.x() else it.x(), it.y()) }
                    engine.consume(points, now).forEach(onEvent)
                }
            }
            .setErrorListener { error -> Log.e("HandPipeline", "inference failed", error) }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }
    @Synchronized fun resume() = engine.resume()
    @Synchronized fun pause() = engine.stop()
    @Synchronized fun updateFeatures(features: GestureFeatureConfig) = engine.updateFeatures(features)
    fun captureNextFrame(callback: (Bitmap) -> Unit) { pendingFrameCapture = callback }
    fun submit(image: Image, sensorRotation: Int) {
        if (closed.get()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastSentAt < 50) return // cap expensive conversion at ~20 fps
        lastSentAt = now
        val bitmap = yuvToBitmap(image)
        val matrix = Matrix().apply { postRotate(sensorRotation.toFloat()); postScale(-1f, 1f) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        pendingFrameCapture?.let { capture ->
            pendingFrameCapture = null
            capture(rotated.copy(Bitmap.Config.ARGB_8888, false))
        }
        val mpImage = BitmapImageBuilder(rotated).build()
        try {
            landmarker.detectAsync(mpImage, now)
        } finally {
            // detectAsync acquires its own reference while the graph uses the frame.
            // Release the caller reference now so completed/dropped frames can be recycled.
            mpImage.close()
        }
    }
    override fun close() { if (closed.compareAndSet(false, true)) { engine.stop(); landmarker.close() } }
    private fun yuvToBitmap(image: Image): Bitmap {
        val w = image.width; val h = image.height
        val planes = image.planes
        val out = IntArray(w * h)
        val y = planes[0].buffer; val u = planes[1].buffer; val v = planes[2].buffer
        for (row in 0 until h) for (col in 0 until w) {
            val yy = y.get(row * planes[0].rowStride + col * planes[0].pixelStride).toInt() and 255
            val uvIndex = (row / 2) * planes[1].rowStride + (col / 2) * planes[1].pixelStride
            val uu = (u.get(uvIndex).toInt() and 255) - 128
            val vv = (v.get((row / 2) * planes[2].rowStride + (col / 2) * planes[2].pixelStride).toInt() and 255) - 128
            val r = (yy + 1.402f * vv).toInt().coerceIn(0, 255)
            val g = (yy - .344f * uu - .714f * vv).toInt().coerceIn(0, 255)
            val b = (yy + 1.772f * uu).toInt().coerceIn(0, 255)
            out[row * w + col] = -0x1000000 or (r shl 16) or (g shl 8) or b
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
