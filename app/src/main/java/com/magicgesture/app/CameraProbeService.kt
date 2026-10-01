package com.magicgesture.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.ContentValues
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Bitmap
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.media.AudioManager
import android.provider.MediaStore
import android.os.*
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.KeyEvent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Keeps the front camera and gesture recognition running while another app is visible. */
class CameraProbeService : Service() {
    companion object {
        @Volatile
        var isControlRunning = false
            private set
    }

    private val channel = "camera_probe"
    private val worker = HandlerThread("camera-probe")
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var cameraOpening = false
    private var cameraGeneration = 0
    private var stopped = false
    private var firstFrameLogged = false
    private var pipeline: HandPipeline? = null
    private var sensorRotation = 0
    private var lastFrameRotation = Int.MIN_VALUE
    private var controlMode = false
    @Volatile private var selfieInProgress = false
    private var featureConfig = GestureFeatureConfig()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var overlayIndicator: OverlayIndicator
    private val globalCooldown = GlobalCooldownManager()
    private var volumeHoldActive = false
    private var volumeHoldRaise = false
    private var volumeHoldChanged = false
    private var mappingManager = GestureMappingManager()
    private val featureGate = GestureFeatureGate()
    private val selfieWriter = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var toneGenerator: ToneGenerator? = null
    @Volatile private var mediaActionSound: MediaActionSound? = null
    private val actionExecutor = GestureActionExecutor(
        accessibilityService = { ControlAccessibilityService.active },
        selfieCapture = ::captureSelfie,
        mediaKey = ::dispatchMediaKey,
        volumeAdjust = ::adjustVolume,
        // Rolling screenshots report per-screen progress; surface it on the overlay feedback.
        actionProgress = { message -> overlayIndicator.showFeedback(message) },
        launchApp = ::launchAppForSlot
    )

    /** Launches the app bound to an open-app slot; the package is read live from preferences. */
    private fun launchAppForSlot(slot: Int, callback: (Boolean) -> Unit) {
        val packageName = GesturePreferences.openAppPackage(this, slot)
        if (packageName == null) {
            callback(false)
            return
        }
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            callback(false)
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
            callback(true)
        } catch (e: Exception) {
            Log.e("CameraProbe", "open app $packageName failed", e)
            callback(false)
        }
    }
    private val reopenCamera = Runnable {
        if (!stopped && camera == null && !cameraOpening) openCamera()
    }
    private val resumeAfterCooldown = object : Runnable {
        override fun run() {
            if (stopped) return
            val remaining = globalCooldown.remainingMs()
            if (remaining > 0L) handler.postDelayed(this, remaining)
            else pipeline?.resume()
        }
    }
    override fun onCreate() {
        super.onCreate()
        worker.start(); handler = Handler(worker.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        featureConfig = GesturePreferences.features(this)
        mappingManager = GestureMappingManager(GesturePreferences.actionOverrides(this))
        globalCooldown.updateDuration(GesturePreferences.cooldownMs(this))
        overlayIndicator = OverlayIndicator(this)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when {
                key?.startsWith("feature_") == true -> {
                    featureConfig = GesturePreferences.features(this)
                    pipeline?.updateFeatures(featureConfig)
                    if (!featureConfig.cursor) ControlAccessibilityService.active?.hideCursor()
                    overlayIndicator.showFeedback("手势开关已即时更新")
                }
                key?.startsWith("mapping_") == true -> {
                    mappingManager = GestureMappingManager(GesturePreferences.actionOverrides(this))
                    overlayIndicator.showFeedback("手势映射已即时更新")
                }
                key == GesturePreferences.COOLDOWN_MS -> {
                    globalCooldown.updateDuration(GesturePreferences.cooldownMs(this))
                    overlayIndicator.showFeedback("冷却时长已更新")
                }
            }
        }.also {
            getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(it)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel, "魔法手势运行状态", NotificationManager.IMPORTANCE_LOW))
        // Pre-warm audio on the idle worker thread so the countdown ticks stay exactly 1s apart.
        handler.post {
            try { if (toneGenerator == null) toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80) } catch (e: Exception) { Log.w("CameraProbe", "tone generator init failed", e) }
            try { if (mediaActionSound == null) mediaActionSound = MediaActionSound() } catch (e: Exception) { Log.w("CameraProbe", "media action sound init failed", e) }
        }
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        isControlRunning = false
        controlMode = intent?.getBooleanExtra("control", false) == true
        val notification = notification("启动摄像头中")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) startForeground(7, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        else startForeground(7, notification)
        overlayIndicator.show()
        if (camera == null && reader == null) {
            val startedAt = SystemClock.uptimeMillis()
            if (controlMode && pipeline == null) {
                // Model initialization is CPU-heavy (several seconds on mid-range devices).
                // Run it on its own thread so camera opening and auto-exposure warm-up overlap
                // with it instead of running back to back on the camera-probe thread.
                Thread({
                    val t0 = SystemClock.uptimeMillis()
                    try {
                        pipeline = HandPipeline(this) { event ->
                            val service = ControlAccessibilityService.active
                            if (selfieInProgress && event !is GestureEvent.Feedback) return@HandPipeline
                            if (event is GestureEvent.TwoFingerVolumeHold) {
                                handleContinuousVolume(event, event.raise, event.phase)
                                return@HandPipeline
                            }
                            if (!globalCooldown.allows(event)) return@HandPipeline
                            val mapped = mappingManager.resolve(event)
                            if (mapped != null) {
                                executeMapped(mapped)
                                return@HandPipeline
                            }
                            when (event) {
                                is GestureEvent.Cursor, is GestureEvent.Click,
                                is GestureEvent.Swipe, is GestureEvent.HorizontalSwipe,
                                GestureEvent.Selfie, GestureEvent.Like, GestureEvent.Screenshot,
                                GestureEvent.ThumbsUp, GestureEvent.Ok, GestureEvent.PlayPause,
                                GestureEvent.LotusRecents, GestureEvent.OrchidBack,
                                GestureEvent.LeftLBack, GestureEvent.LShape, GestureEvent.CShape,
                                GestureEvent.LoveLock, is GestureEvent.TwoFingerSwipe,
                                GestureEvent.TwoFingerDoubleTap, is GestureEvent.TwoFingerVolumeHold,
                                is GestureEvent.OpenApp -> Unit // Migrated gestures use the mapping pipeline above.
                                is GestureEvent.ClawDrag -> overlayIndicator.showFeedback("爪形手势未绑定动作，可在校准页映射中指定") // Unbound by default.
                                is GestureEvent.Feedback -> overlayIndicator.showFeedback(event.message, event.progress)
                                GestureEvent.Back -> service?.inject(event) { finishAction(it, "返回") }
                                    ?: finishAction(false, "返回", "无障碍服务未连接")
                                GestureEvent.Home -> service?.inject(event) { finishAction(it, "返回桌面") }
                                    ?: finishAction(false, "返回桌面", "无障碍服务未连接")
                                GestureEvent.Recents -> service?.inject(event) { finishAction(it, "打开最近任务") }
                                    ?: finishAction(false, "最近任务", "无障碍服务未连接")
                            }
                        }
                        Log.d("CameraProbe", "startup: model ready ${SystemClock.uptimeMillis() - t0} ms after thread start")
                    } catch (e: Exception) {
                        Log.e("CameraProbe", "model init failed", e)
                        controlMode = false
                        val reason = if (e is java.io.FileNotFoundException) "模型文件缺失：hand_landmarker.task" else "模型初始化失败：${e.javaClass.simpleName}"
                        mainHandler.post { fail(reason) }
                    }
                }, "model-init").start()
            }
            handler.post {
                Log.d("CameraProbe", "startup: opening camera ${SystemClock.uptimeMillis() - startedAt} ms after start request")
                openCamera()
            }
        }
        return START_NOT_STICKY
    }
    private fun openCamera() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { fail("相机权限失效"); return }
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: run { fail("未找到前置摄像头"); return }
            sensorRotation = cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            cameraOpening = true
            val generation = ++cameraGeneration
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { image ->
                    if (!firstFrameLogged) {
                        firstFrameLogged = true
                        Log.d("CameraProbe", "startup: first camera frame received")
                    }
                    if (controlMode && !globalCooldown.isActive()) try {
                        val frameRotation = CameraFrameOrientation.relativeRotationDegrees(
                            sensorOrientationDegrees = sensorRotation,
                            displayRotationDegrees = currentDisplayRotationDegrees(),
                            frontFacing = true
                        )
                        if (frameRotation != lastFrameRotation) {
                            lastFrameRotation = frameRotation
                            pipeline?.resetTracking()
                        }
                        pipeline?.submit(image, frameRotation)
                    } catch (e: Exception) { fail("推理帧失败：${e.javaClass.simpleName}") }
                } }, handler)
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraOpening = false
                    Log.d("CameraProbe", "startup: camera opened")
                    if (stopped || generation != cameraGeneration) { device.close(); return }
                    camera = device
                    configure(device)
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (generation == cameraGeneration) recoverCamera("摄像头被其他应用占用，等待恢复")
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (generation == cameraGeneration) recoverCamera("摄像头暂时不可用，等待恢复")
                }
            }, handler)
        } catch (e: CameraAccessException) {
            cameraOpening = false
            recoverCamera("摄像头暂时不可用，等待恢复")
        } catch (e: Exception) {
            cameraOpening = false
            fail("相机启动失败：${e.javaClass.simpleName}")
        }
    }
    private fun executeMapped(mapped: MappedGesture) {
        if (!featureGate.allows(mapped.mapping, featureConfig)) return
        Log.d("CameraProbe", "gesture ${mapped.mapping.code} -> ${mapped.mapping.action}")
        val submitted = actionExecutor.execute(mapped) { success ->
            if (mapped.mapping.cooldownPolicy == CooldownPolicy.GLOBAL_AFTER_SUCCESS) {
                finishAction(success, mapped.mapping.action.successMessage(), mapped.mapping.action.failureMessage())
            }
        }
        if (!submitted && mapped.mapping.action != GestureAction.MOVE_CURSOR) {
            finishAction(false, mapped.mapping.action.successMessage(), "无障碍服务未连接")
        }
    }
    private fun handleContinuousVolume(event: GestureEvent, raise: Boolean, phase: GestureEvent.VolumeHoldPhase) {
        when (phase) {
            GestureEvent.VolumeHoldPhase.START -> {
                if (globalCooldown.isActive()) return
                val mapped = mappingManager.resolve(event) ?: run {
                    pipeline?.finishVolumeSession(waitForRelease = true)
                    return
                }
                if (!featureGate.allows(mapped.mapping, featureConfig)) {
                    pipeline?.finishVolumeSession(waitForRelease = true)
                    return
                }
                val continuousAction = if (raise) GestureAction.VOLUME_UP else GestureAction.VOLUME_DOWN
                if (mapped.mapping.action != continuousAction) {
                    pipeline?.finishVolumeSession(waitForRelease = true)
                    executeMapped(mapped)
                    return
                }
                volumeHoldActive = true
                volumeHoldRaise = raise
                volumeHoldChanged = false
                adjustHeldVolume(raise)
            }
            GestureEvent.VolumeHoldPhase.TICK -> if (volumeHoldActive && volumeHoldRaise == raise) {
                adjustHeldVolume(raise)
            }
            GestureEvent.VolumeHoldPhase.END -> if (volumeHoldActive && volumeHoldRaise == raise) {
                finishVolumeHold(atBoundary = false)
            }
        }
    }
    private fun currentDisplayRotationDegrees(): Int {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return when (displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation ?: Surface.ROTATION_0) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }
    private fun configure(device: CameraDevice) {
        try {
            val surface: Surface = reader?.surface ?: run { fail("图像读取器已关闭"); return }
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (stopped) { s.close(); return }
                    session = s
                    try {
                        val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(surface) }.build()
                        s.setRepeatingRequest(request, null, handler)
                        isControlRunning = controlMode
                        overlayIndicator.setState(OverlayIndicator.State.RUNNING)
                        updateNotification(if (controlMode) "手势识别与悬浮控制正在运行" else "摄像头后台运行中")
                    } catch (e: Exception) { fail("开始取帧失败：${e.javaClass.simpleName}") }
                }
                override fun onConfigureFailed(s: CameraCaptureSession) { s.close(); recoverCamera("相机会话中断，等待恢复") }
            }, handler)
        } catch (e: CameraAccessException) { recoverCamera("相机会话中断，等待恢复") }
        catch (e: Exception) { fail("配置失败：${e.javaClass.simpleName}") }
    }
    private fun recoverCamera(message: String) {
        if (stopped) return
        isControlRunning = false
        cameraOpening = false
        Log.w("CameraProbe", message)
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        overlayIndicator.setState(OverlayIndicator.State.ERROR)
        updateNotification(message)
        handler.removeCallbacks(reopenCamera)
        handler.postDelayed(reopenCamera, 1500)
    }
    private fun captureSelfie(callback: (Boolean) -> Unit) {
        if (selfieInProgress) { callback(false); return }
        selfieInProgress = true
        // Countdown freezes every gesture (including the cursor), so the hand can be lowered right away.
        ControlAccessibilityService.active?.hideCursor()
        // Schedule on the main looper: the camera worker thread is busy with YUV conversion
        // and inference, which would otherwise delay the 1s ticks.
        mainHandler.post(CountdownTick(3))
        mainHandler.postDelayed(CountdownTick(2), 1_000L)
        mainHandler.postDelayed(CountdownTick(1), 2_000L)
        mainHandler.postDelayed({
            val activePipeline = pipeline
            if (stopped || activePipeline == null) {
                selfieInProgress = false
                callback(false)
                return@postDelayed
            }
            activePipeline.captureNextFrame { bitmap ->
                selfieWriter.execute {
                    playShutter()
                    val preview = previewThumbnail(bitmap)
                    val saved = saveSelfie(bitmap)
                    bitmap.recycle()
                    selfieInProgress = false
                    if (saved && preview != null) overlayIndicator.showSelfiePreview(preview) else preview?.recycle()
                    callback(saved)
                }
            }
        }, 3_000L)
    }
    private inner class CountdownTick(private val seconds: Int) : Runnable {
        override fun run() {
            overlayIndicator.showCountdown(seconds)
            playCountdownTick()
        }
    }
    /** Short beep for each countdown second; falls back silently when audio is unavailable. */
    private fun playCountdownTick() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 160)
        } catch (e: Exception) {
            Log.w("CameraProbe", "countdown tick failed", e)
        }
    }
    /** System camera shutter sound at the actual capture moment. */
    private fun playShutter() {
        try {
            if (mediaActionSound == null) mediaActionSound = MediaActionSound()
            mediaActionSound?.play(MediaActionSound.SHUTTER_CLICK)
        } catch (e: Exception) {
            Log.w("CameraProbe", "shutter sound failed", e)
        }
    }
    /** Builds an independent small thumbnail for the overlay preview; the original selfie bitmap keeps its own lifecycle. */
    private fun previewThumbnail(source: Bitmap): Bitmap? = try {
        if (source.width <= 480) source.copy(Bitmap.Config.ARGB_8888, false)
        else Bitmap.createScaledBitmap(
            source,
            480,
            (source.height * 480f / source.width).toInt().coerceAtLeast(1),
            true
        )
    } catch (e: Exception) {
        Log.w("CameraProbe", "selfie preview thumbnail failed", e)
        null
    }

    /** Dispatches a media/volume key through AudioManager; works without the accessibility service. */
    private fun dispatchMediaKey(keyCode: Int, callback: (Boolean) -> Unit) {
        return try {
            val audio = getSystemService(AudioManager::class.java)
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
            callback(true)
        } catch (e: Exception) {
            Log.e("CameraProbe", "media key $keyCode failed", e)
            callback(false)
        }
    }

    /**
     * Raises/lowers the media stream volume. dispatchMediaKeyEvent silently drops
     * VOLUME_UP/DOWN on modern Android, so adjust the stream directly and only fall
     * back to a simulated key event if that path fails. One native step is barely
     * audible on devices with fine-grained volume (Huawei exposes dozens of steps),
     * so each gesture moves ~10% of the full range instead.
     */
    private fun adjustVolume(raise: Boolean, callback: (Boolean) -> Unit) {
        try {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val step = maxOf(1, max / 10)
            val target = (current + if (raise) step else -step).coerceIn(0, max)
            Log.d("CameraProbe", "volume $current -> $target (max $max)")
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            callback(true)
        } catch (e: Exception) {
            Log.e("CameraProbe", "volume adjust failed", e)
            dispatchMediaKey(
                if (raise) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN,
                callback
            )
        }
    }
    /** One tick of the exclusive two-finger volume session (about 5% of the media range). */
    private fun adjustHeldVolume(raise: Boolean) {
        if (!volumeHoldActive) return
        try {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val atBoundary = if (raise) current >= max else current <= 0
            if (atBoundary) {
                finishVolumeHold(
                    atBoundary = true,
                    message = if (raise) "媒体音量已最大" else "媒体音量已静音"
                )
                return
            }
            val step = maxOf(1, (max + 19) / 20)
            val target = (current + if (raise) step else -step).coerceIn(0, max)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            volumeHoldChanged = true
            val percent = ((target * 100f) / max).toInt().coerceIn(0, 100)
            overlayIndicator.showFeedback("正在${if (raise) "增加" else "降低"}媒体音量：$percent%")
            if (target == 0 || target == max) {
                finishVolumeHold(
                    atBoundary = true,
                    message = if (target == max) "媒体音量已最大" else "媒体音量已静音"
                )
            }
        } catch (e: Exception) {
            Log.e("CameraProbe", "continuous volume adjust failed", e)
            volumeHoldActive = false
            pipeline?.finishVolumeSession(waitForRelease = true)
            finishAction(false, "", "持续调节音量失败")
        }
    }
    private fun finishVolumeHold(atBoundary: Boolean, message: String? = null) {
        if (!volumeHoldActive) return
        volumeHoldActive = false
        pipeline?.finishVolumeSession(waitForRelease = atBoundary)
        val finalMessage = message ?: run {
            val audio = getSystemService(AudioManager::class.java)
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val percent = ((current * 100f) / max).toInt().coerceIn(0, 100)
            "音量调节结束：$percent%"
        }
        finishAction(volumeHoldChanged || atBoundary, finalMessage, "音量未发生变化")
        volumeHoldChanged = false
    }
    private fun saveSelfie(bitmap: Bitmap): Boolean {
        return try {
            val name = "MagicGesture_selfie_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.jpg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/MagicGesture")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return false
            val saved = contentResolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 94, it) } == true
            if (!saved) {
                contentResolver.delete(uri, null, null)
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
            }
            true
        } catch (e: Exception) {
            Log.e("CameraProbe", "selfie save failed", e)
            false
        }
    }
    private fun finishAction(success: Boolean, successMessage: String, failureMessage: String = "动作执行失败") {
        if (success) {
            globalCooldown.actionSucceeded()
            // Freeze recognition itself, not only emitted actions. Clearing partial state here
            // prevents a hold or swipe accumulated during cooldown from firing immediately after it.
            pipeline?.pause()
            handler.removeCallbacks(resumeAfterCooldown)
            handler.postDelayed(resumeAfterCooldown, globalCooldown.remainingMs())
            overlayIndicator.showProtection(globalCooldown.currentDurationMs())
        }
        overlayIndicator.showFeedback(if (success) successMessage else failureMessage)
    }
    private fun fail(message: String) { isControlRunning = false; Log.e("CameraProbe", message); overlayIndicator.setState(OverlayIndicator.State.ERROR); updateNotification(message); handler.postDelayed({ stopSelf() }, 3000) }
    private fun notification(message: String): Notification {
        val stopIntent = Intent(this, CameraProbeService::class.java).setAction("STOP")
        val pending = PendingIntent.getService(this, 8, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("魔法手势")
            .setContentText(message).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", pending).build())
            .build()
    }
    private fun updateNotification(message: String) { getSystemService(NotificationManager::class.java).notify(7, notification(message)) }
    override fun onDestroy() {
        isControlRunning = false
        stopped = true
        globalCooldown.reset()
        cameraGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacksAndMessages(null)
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        pipeline?.close(); pipeline = null
        selfieWriter.shutdownNow()
        try { toneGenerator?.release() } catch (_: Exception) { }
        toneGenerator = null
        try { mediaActionSound?.release() } catch (_: Exception) { }
        mediaActionSound = null
        preferenceListener?.let {
            getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(it)
        }
        preferenceListener = null
        overlayIndicator.remove()
        ControlAccessibilityService.active?.hideCursor()
        worker.quitSafely()
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
