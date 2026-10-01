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
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.media.AudioManager
import android.provider.MediaStore
import android.os.*
import android.util.Log
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
    private var pipeline: HandPipeline? = null
    private var sensorRotation = 0
    private var controlMode = false
    @Volatile private var selfieInProgress = false
    private var featureConfig = GestureFeatureConfig()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var overlayIndicator: OverlayIndicator
    private val globalCooldown = GlobalCooldownManager()
    private var mappingManager = GestureMappingManager()
    private val featureGate = GestureFeatureGate()
    private val selfieWriter = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var toneGenerator: ToneGenerator? = null
    @Volatile private var mediaActionSound: MediaActionSound? = null
    private val actionExecutor = GestureActionExecutor(
        accessibilityService = { ControlAccessibilityService.active },
        selfieCapture = ::captureSelfie,
        mediaKey = ::dispatchMediaKey
    )
    private val reopenCamera = Runnable {
        if (!stopped && camera == null && !cameraOpening) openCamera()
    }
    override fun onCreate() {
        super.onCreate()
        worker.start(); handler = Handler(worker.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        featureConfig = GesturePreferences.features(this)
        mappingManager = GestureMappingManager(GesturePreferences.actionOverrides(this))
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
        if (camera == null && reader == null) handler.post {
            if (controlMode && pipeline == null) {
                try {
                    pipeline = HandPipeline(this) { event ->
                        val service = ControlAccessibilityService.active
                        if (selfieInProgress && event !is GestureEvent.Feedback) return@HandPipeline
                        if (!globalCooldown.allows(event)) return@HandPipeline
                        val mapped = mappingManager.resolve(event)
                        if (mapped != null) {
                            if (!featureGate.allows(mapped.mapping, featureConfig)) return@HandPipeline
                            val submitted = actionExecutor.execute(mapped) { success ->
                                if (mapped.mapping.cooldownPolicy == CooldownPolicy.GLOBAL_AFTER_SUCCESS) {
                                    finishAction(
                                        success,
                                        mapped.mapping.action.successMessage(),
                                        mapped.mapping.action.failureMessage()
                                    )
                                }
                            }
                            if (!submitted && mapped.mapping.action != GestureAction.MOVE_CURSOR) {
                                finishAction(false, mapped.mapping.action.successMessage(), "无障碍服务未连接")
                            }
                            return@HandPipeline
                        }
                        when (event) {
                            is GestureEvent.Cursor, is GestureEvent.Click,
                            is GestureEvent.Swipe, is GestureEvent.HorizontalSwipe,
                            GestureEvent.Selfie, GestureEvent.Like, GestureEvent.Screenshot,
                            GestureEvent.ThumbsUp, GestureEvent.Ok, GestureEvent.PlayPause,
                            GestureEvent.LotusRecents, GestureEvent.OrchidBack,
                            GestureEvent.LeftLBack, GestureEvent.LShape, GestureEvent.CShape,
                            GestureEvent.LoveLock, is GestureEvent.ClawDrag -> Unit // Migrated gestures use the mapping pipeline above.
                            is GestureEvent.Feedback -> overlayIndicator.showFeedback(event.message, event.progress)
                            GestureEvent.Back -> service?.inject(event) { finishAction(it, "返回") }
                                ?: finishAction(false, "返回", "无障碍服务未连接")
                            GestureEvent.Home -> service?.inject(event) { finishAction(it, "返回桌面") }
                                ?: finishAction(false, "返回桌面", "无障碍服务未连接")
                            GestureEvent.Recents -> service?.inject(event) { finishAction(it, "打开最近任务") }
                                ?: finishAction(false, "最近任务", "无障碍服务未连接")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("CameraProbe", "model init failed", e)
                    controlMode = false
                    val reason = if (e is java.io.FileNotFoundException) "模型文件缺失：hand_landmarker.task" else "模型初始化失败：${e.javaClass.simpleName}"
                    fail(reason)
                    return@post
                }
            }
            openCamera()
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
                    if (controlMode) try { pipeline?.submit(image, sensorRotation) }
                    catch (e: Exception) { fail("推理帧失败：${e.javaClass.simpleName}") }
                } }, handler)
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    cameraOpening = false
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
            overlayIndicator.showProtection(GlobalCooldownManager.DEFAULT_DURATION_MS)
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
