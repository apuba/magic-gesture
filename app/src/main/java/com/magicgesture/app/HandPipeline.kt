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
    private val activeHandSelector = ActiveHandSelector()
    private val reverseHorizontal: Boolean
    private val landmarker: HandLandmarker
    private var lastSentAt = 0L
    private var lastResultAt = 0L
    private val closed = AtomicBoolean(false)
    /**
     * A paused pipeline may still be asked for one preview frame by the selfie fallback, but it
     * must not submit frames to MediaPipe or deliver a result that was already in flight. This is
     * also the hard recognition lock used while a rolling screenshot mutates the page.
     */
    private val recognitionPaused = AtomicBoolean(false)
    @Volatile private var pendingFrameCapture: ((Bitmap) -> Unit)? = null
    @Volatile private var firstDetectionLogged = false
    init {
        engine = GestureEngine(GesturePreferences.movementScale(context), GesturePreferences.effectiveFeatures(context))
        reverseHorizontal = GesturePreferences.reverseHorizontal(context)
        context.assets.open("hand_landmarker.task").close() // fail clearly if the model isn't installed
        val options = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("hand_landmarker.task").build())
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                if (closed.get() || recognitionPaused.get()) return@setResultListener
                val now = SystemClock.uptimeMillis()
                if (now - lastResultAt > 300) engine.lost(now).forEach(onEvent)
                lastResultAt = now
                val candidates = result.landmarks().mapIndexed { index, hand ->
                    val detectedHandedness = result.handedness().getOrNull(index)?.firstOrNull()?.categoryName()
                    HandCandidate(
                        points = hand.map { Point(if (reverseHorizontal) 1f - it.x() else it.x(), it.y()) },
                        // Keep MediaPipe's handedness label consistent with the coordinates after
                        // the optional horizontal flip. This lets anatomical left/right checks use
                        // one rule regardless of the user's cursor-direction preference.
                        handedness = if (reverseHorizontal) oppositeHandedness(detectedHandedness) else detectedHandedness
                    )
                }
                val selected = activeHandSelector.select(candidates, now)
                if (selected == null) engine.lost(now).forEach(onEvent)
                else {
                    if (!firstDetectionLogged) {
                        firstDetectionLogged = true
                        Log.d("HandPipeline", "startup: first hand detected")
                    }
                    if (selected.ownershipChanged) {
                        // A new person/hand must never inherit holds, paths or sequences.
                        engine.stop()
                        engine.resume()
                    }
                    engine.consume(selected.points, now, selected.handedness).forEach(onEvent)
                }
            }
            .setErrorListener { error -> Log.e("HandPipeline", "inference failed", error) }
            .build()
        landmarker = HandLandmarker.createFromOptions(context, options)
    }

    private fun oppositeHandedness(handedness: String?): String? = when (handedness?.lowercase()) {
        "left" -> "Right"
        "right" -> "Left"
        else -> handedness
    }
    /**
     * Resuming after a freeze must not look like the hand disappeared. No frame reaches the
     * landmarker while frozen, so the gap since the last result is longer than the 300ms the
     * listener reads as "the hand is gone" — reporting that would clear the lock of a hold that
     * already fired, and a pose the user is still holding would act again. The clock restarts with
     * the recognition that is now running again.
     */
    @Synchronized fun resume() {
        // The selector deliberately keeps the hand it had: freezing for a cooldown does not move
        // the hand, and resetting here makes the next few frames report no hand at all, which reads
        // to the engine as the hand being lowered and frees the lock of a hold that already fired.
        lastResultAt = SystemClock.uptimeMillis()
        engine.resume()
        recognitionPaused.set(false)
    }
    @Synchronized fun pause() {
        // Set the cross-thread gate first: an already-running MediaPipe callback must not publish
        // another gesture after the caller has declared the operation exclusive.
        recognitionPaused.set(true)
        engine.stop()
    }
    /** Routes per-frame engine diagnostics to a sink (logcat) so thresholds can be tuned on device. */
    fun setDiag(sink: ((String) -> Unit)?) { engine.diag = sink }
    @Synchronized fun resetTracking() { activeHandSelector.reset(); engine.stop(); engine.resume() }
    @Synchronized fun finishVolumeSession(waitForRelease: Boolean) = engine.finishVolumeSession(waitForRelease)
    /** 手动解除识别锁（App 页面与常驻通知入口）；不参与手势识别本身。 */
    @Synchronized fun unlockRecognition() = engine.setRecognitionLocked(false)
    /** 识别锁当前是否锁定；服务据此同步悬浮点与常驻通知。 */
    @Synchronized fun isRecognitionLocked(): Boolean = engine.isRecognitionLocked()
    @Synchronized fun updateFeatures(features: GestureFeatureConfig) = engine.updateFeatures(features)
    /** Retunes every movement threshold for a new sensitivity without restarting the pipeline. */
    @Synchronized fun updateSensitivity(movementScale: Float) = engine.updateMovementScale(movementScale)
    fun captureNextFrame(callback: (Bitmap) -> Unit) { pendingFrameCapture = callback }
    fun submit(image: Image, frameRotation: Int) {
        if (closed.get()) return
        val now = SystemClock.uptimeMillis()
        if (now - lastSentAt < 50) return // cap expensive conversion at ~20 fps
        lastSentAt = now
        val bitmap = yuvToBitmap(image)
        val matrix = Matrix().apply { postRotate(frameRotation.toFloat()); postScale(-1f, 1f) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        pendingFrameCapture?.let { capture ->
            pendingFrameCapture = null
            capture(rotated.copy(Bitmap.Config.ARGB_8888, false))
        }
        if (recognitionPaused.get()) {
            rotated.recycle()
            return
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
    override fun close() { if (closed.compareAndSet(false, true)) { activeHandSelector.reset(); engine.stop(); landmarker.close() } }
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
