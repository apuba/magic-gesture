package com.magicgesture.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.Image
import android.os.SystemClock
import android.util.Log
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker

/** Camera-frame preprocessing and MediaPipe hand-landmark inference. */
class HandPipeline(
    context: Context,
    private val onEvent: (GestureEvent) -> Unit,
    private val onFirstResult: () -> Unit = {}
) : AutoCloseable {
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
    private val firstResultDelivered = AtomicBoolean(false)
    /**
     * Cap how far conversion may run ahead of the asynchronous graph.
     *
     * Two is deliberate: on a low-end SoC one frame costs ~35 ms of CPU conversion plus ~105 ms
     * of GPU inference, and holding a single frame in flight serialises them into ~145 ms per
     * result (under 7 fps). Keeping one frame being converted while another is on the GPU turns
     * the cycle into roughly the inference time alone. More than two would only queue stale
     * frames the gesture engine has already moved past.
     */
    private val MAX_IN_FLIGHT_FRAMES = 2
    private val inFlightFrames = AtomicInteger(0)
    /** Wall clock of the oldest frame MediaPipe has not answered for; 0 while none is in flight. */
    @Volatile private var oldestInFlightAt = 0L
    /**
     * The counter above is only ever released by a MediaPipe callback. If a callback is lost —
     * a GPU graph that stops answering on a throttled or overheating device — every later frame
     * stays locked out with no error, no log and no recovery, and the app looks like it simply
     * stopped recognising. Anything unanswered for this long is treated as lost and the gate is
     * reopened; a callback that still arrives afterwards cannot push the counter below zero.
     */
    private val STALE_IN_FLIGHT_MS = 3_000L
    init {
        engine = GestureEngine(GesturePreferences.movementScale(context), GesturePreferences.effectiveFeatures(context))
        reverseHorizontal = GesturePreferences.reverseHorizontal(context)
        context.assets.open("hand_landmarker.task").close() // fail clearly if the model isn't installed
        fun options(delegate: Delegate) = HandLandmarker.HandLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(delegate)
                    .build()
            )
            .setNumHands(2)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ ->
                releaseInFlightFrame()
                if (closed.get() || recognitionPaused.get()) return@setResultListener
                if (firstResultDelivered.compareAndSet(false, true)) onFirstResult()
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
            .setErrorListener { error ->
                releaseInFlightFrame()
                Log.e("HandPipeline", "inference failed", error)
            }
            .build()
        landmarker = try {
            HandLandmarker.createFromOptions(context, options(Delegate.GPU)).also {
                Log.d("HandPipeline", "MediaPipe delegate=GPU")
            }
        } catch (gpuError: Throwable) {
            Log.w("HandPipeline", "GPU delegate unavailable; falling back to CPU", gpuError)
            HandLandmarker.createFromOptions(context, options(Delegate.CPU)).also {
                Log.d("HandPipeline", "MediaPipe delegate=CPU")
            }
        }
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
    /**
     * Ends an exclusive freeze started by [pause] and hands recognition back.
     *
     * [resetTracking] deliberately keeps [recognitionPaused] set: it is also called on a display
     * rotation, which may happen while a cooldown or a rolling screenshot owns the freeze, and
     * lifting the gate there would let gestures through in the middle of an exclusive operation.
     * A selfie is different. It owns the freeze from the countdown to the saved file, and a failed
     * capture never reaches the cooldown path that would otherwise resume the pipeline — leaving
     * this gate set would silently kill every gesture for the rest of the service's life.
     *
     * The hand selector is still reset because a selfie lasts seconds and the hand in front of the
     * camera when recognition returns is not the hand that asked for the photo.
     */
    @Synchronized fun resumeExclusive() {
        activeHandSelector.reset()
        engine.stop()
        engine.resume()
        lastResultAt = SystemClock.uptimeMillis()
        recognitionPaused.set(false)
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
        if (closed.get()) { image.close(); return }
        val now = SystemClock.uptimeMillis()
        if (now - lastSentAt < 50) { image.close(); return } // cap inference at ~20 fps
        pendingFrameCapture?.let { capture ->
            pendingFrameCapture = null
            lastSentAt = now
            val bitmap = try { yuvToBitmap(image) } finally { image.close() }
            val matrix = Matrix().apply { postRotate(frameRotation.toFloat()); postScale(-1f, 1f) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            try { capture(rotated.copy(Bitmap.Config.ARGB_8888, false)) } finally { rotated.recycle() }
            return
        }
        if (recognitionPaused.get()) { image.close(); return }
        if (inFlightFrames.get() >= MAX_IN_FLIGHT_FRAMES) {
            if (!reopenStalledGate(now)) { image.close(); return }
        }
        if (inFlightFrames.getAndIncrement() == 0) oldestInFlightAt = now
        try {
            lastSentAt = now
            val bitmap = try { yuvToBitmap(image) } finally { image.close() }
            val matrix = Matrix().apply { postRotate(frameRotation.toFloat()); postScale(-1f, 1f) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            val mpImage = BitmapImageBuilder(rotated).build()
            try {
                landmarker.detectAsync(mpImage, now)
            } finally {
                // Keep the established MediaPipe wrapper lifecycle. The official asynchronous
                // samples do not establish that the caller-owned Bitmap may be recycled here;
                // recycling it before the result callback would risk native use-after-recycle.
                mpImage.close()
            }
        } catch (error: Throwable) {
            releaseInFlightFrame()
            throw error
        }
    }
    private fun releaseInFlightFrame() {
        if (inFlightFrames.updateAndGet { count -> if (count > 0) count - 1 else 0 } == 0) oldestInFlightAt = 0L
    }
    /** Reopens the gate when MediaPipe has stopped answering; see [STALE_IN_FLIGHT_MS]. */
    private fun reopenStalledGate(now: Long): Boolean {
        val oldest = oldestInFlightAt
        if (oldest == 0L || now - oldest < STALE_IN_FLIGHT_MS) return false
        Log.w("HandPipeline", "no inference result for ${now - oldest} ms; reopening the frame gate")
        inFlightFrames.set(0)
        oldestInFlightAt = 0L
        return true
    }
    override fun close() {
        if (closed.compareAndSet(false, true)) {
            pendingFrameCapture = null
            activeHandSelector.reset()
            engine.stop()
            landmarker.close()
        }
    }
    /**
     * Reused scratch buffers. A 640x480 frame is 307200 pixels, and allocating fresh plane and
     * pixel arrays per frame at 20 fps feeds the allocator hundreds of megabytes per second,
     * which on a low-end device shows up as multi-hundred-millisecond GC pauses — long enough to
     * make every hold and sequence look like the hand vanished.
     */
    private var pixelScratch: IntArray? = null
    private val planeScratch = arrayOfNulls<ByteArray>(3)
    private fun planeBytes(slot: Int, buffer: ByteBuffer): ByteArray {
        val size = buffer.remaining()
        var bytes = planeScratch[slot]
        if (bytes == null || bytes.size < size) { bytes = ByteArray(size); planeScratch[slot] = bytes }
        buffer.get(bytes, 0, size)
        return bytes
    }
    /**
     * YUV_420_888 -> ARGB_8888. Same result as the pixel-by-pixel float version, but the planes
     * are copied in bulk once per frame and the colour math is fixed point: a Kotlin loop over
     * 307200 pixels with per-byte buffer reads costs ~125 ms per frame on a Dimensity 700, which
     * alone caps the pipeline at under 4 fps.
     */
    private fun yuvToBitmap(image: Image): Bitmap {
        val w = image.width; val h = image.height
        val planes = image.planes
        val y = planeBytes(0, planes[0].buffer)
        val u = planeBytes(1, planes[1].buffer)
        val v = planeBytes(2, planes[2].buffer)
        val yStride = planes[0].rowStride; val yStep = planes[0].pixelStride
        val uStride = planes[1].rowStride; val uStep = planes[1].pixelStride
        val vStride = planes[2].rowStride; val vStep = planes[2].pixelStride
        var out = pixelScratch
        if (out == null || out.size < w * h) { out = IntArray(w * h); pixelScratch = out }
        for (row in 0 until h) {
            val yRow = row * yStride
            val uRow = (row shr 1) * uStride
            val vRow = (row shr 1) * vStride
            val outRow = row * w
            for (col in 0 until w) {
                val yy = y[yRow + col * yStep].toInt() and 255
                val uvOffset = (col shr 1)
                val uu = (u[uRow + uvOffset * uStep].toInt() and 255) - 128
                val vv = (v[vRow + uvOffset * vStep].toInt() and 255) - 128
                var r = yy + ((91881 * vv) shr 16)
                var g = yy - ((22554 * uu + 46802 * vv) shr 16)
                var b = yy + ((116130 * uu) shr 16)
                if (r < 0) r = 0 else if (r > 255) r = 255
                if (g < 0) g = 0 else if (g > 255) g = 255
                if (b < 0) b = 0 else if (b > 255) b = 255
                out[outRow + col] = -0x1000000 or (r shl 16) or (g shl 8) or b
            }
        }
        return Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888)
    }
}
