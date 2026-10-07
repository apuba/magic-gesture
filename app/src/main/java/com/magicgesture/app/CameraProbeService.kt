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
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import android.hardware.camera2.*
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.MediaActionSound
import android.media.ToneGenerator
import android.media.AudioManager
import android.provider.MediaStore
import android.os.*
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
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

        /** 识别锁（Love 手势）当前是否锁定；首页据此显示手动解除入口。 */
        @Volatile
        var recognitionLocked = false
            private set

        /** 手动解除识别锁：常驻通知与首页按钮的唯一出口，不属于任何手势解锁。 */
        const val ACTION_UNLOCK_RECOGNITION = "UNLOCK_RECOGNITION"

        /** 识别锁状态变化。首页停留在前台时不会触发 onResume，必须靠这条广播刷新解除入口。 */
        const val ACTION_RECOGNITION_LOCK_CHANGED = "RECOGNITION_LOCK_CHANGED"
        const val EXTRA_RECOGNITION_LOCKED = "recognition_locked"

        // Delays for the play fallback: verify audio, confirm the silence, let the music app
        // publish its MediaSession, then confirm the repeated play key really produced audio.
        private const val PLAYBACK_VERIFY_DELAY_MS = 800L
        private const val PLAYBACK_RECHECK_DELAY_MS = 700L
        private const val MUSIC_APP_SESSION_DELAY_MS = 1_200L
        private const val PLAYBACK_FINAL_CHECK_DELAY_MS = 2_000L
        private const val SELFIE_JPEG_OUTPUT_QUALITY = 95
    }

    private val channel = "camera_probe"
    private val worker = HandlerThread("camera-probe")
    private lateinit var handler: Handler
    private lateinit var cameraManager: CameraManager
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var selfieReader: ImageReader? = null
    private var selfieJpegSizes: List<Size> = emptyList()
    private var selfieJpegSizeIndex = -1
    private var cameraOpening = false
    private var cameraGeneration = 0
    @Volatile private var stopped = false
    private var firstFrameLogged = false
    @Volatile private var pipeline: HandPipeline? = null
    private val modelInitLock = Any()
    private var modelInitGeneration = 0
    private var modelInitInProgress = false
    private var sensorRotation = 0
    private var lastFrameRotation = Int.MIN_VALUE
    private var controlMode = false
    @Volatile private var selfieInProgress = false
    private var pendingHighQualitySelfie: ((ByteArray?) -> Unit)? = null
    private val highQualitySelfieTimeout = Runnable { completeHighQualitySelfie(null) }
    private var featureConfig = GestureFeatureConfig()
    private var entitlement = GestureEntitlement(GestureUnlockPlan.BASE_CODES.toSet())
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var overlayIndicator: OverlayIndicator
    private val globalCooldown = GlobalCooldownManager()
    private var volumeHoldActive = false
    private var volumeHoldRaise = false
    private var volumeHoldChanged = false
    @Volatile private var favoriteFlowActive = false
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
        muteToggle = ::toggleMute,
        // Rolling screenshots report per-screen progress; surface it on the overlay feedback.
        actionProgress = { message -> overlayIndicator.showFeedback(message) },
        // A rolling screenshot captures the real screen, so its own overlay has to leave the screen.
        // The capture outlives "stop all control" when it is already running, and the dot must not
        // come back for a session that has ended.
        overlayVisible = { visible ->
            if (visible) { if (!stopped) overlayIndicator.setHiddenForCapture(false) }
            else overlayIndicator.setHiddenForCapture(true)
        },
        launchApp = ::launchAppForGesture,
        favoriteCurrent = ::favoriteCurrentContent
    )
    /**
     * Wording of the last mute toggle. The generic success text reads the same whether the toggle
     * muted or restored, which tells the user nothing about the state they are now in, so the
     * toggle reports its own direction and that wording is what stays on screen.
     */
    private var lastMuteMessage: String? = null
    private val favoriteController by lazy {
        FavoriteButtonController(
            context = this,
            accessibilityService = { ControlAccessibilityService.active },
            onFlowStateChanged = { favoriteFlowActive = it },
            onMessage = { message -> overlayIndicator.showFeedback(message) }
        )
    }

    private fun favoriteCurrentContent(callback: (Boolean) -> Unit) {
        favoriteFlowActive = true
        favoriteController.execute { success ->
            favoriteFlowActive = false
            callback(success)
        }
    }

    /** Launches the app bound directly to the gesture; the package is read live from preferences. */
    private fun launchAppForGesture(code: GestureCode, callback: (Boolean) -> Unit) {
        val packageName = GesturePreferences.openAppPackage(this, code)
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
        featureConfig = GesturePreferences.effectiveFeatures(this)
        entitlement = GestureUnlockStore(this).entitlement()
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
                key?.startsWith(GestureUnlockStore.KEY_PREFIX) == true -> {
                    // 签到解锁后无需重启控制：权益与生效开关立即下发给识别引擎。
                    entitlement = GestureUnlockStore(this).entitlement()
                    featureConfig = GesturePreferences.effectiveFeatures(this)
                    pipeline?.updateFeatures(featureConfig)
                    overlayIndicator.showFeedback("解锁状态已即时更新")
                }
                key?.startsWith("mapping_") == true -> {
                    mappingManager = GestureMappingManager(GesturePreferences.actionOverrides(this))
                    overlayIndicator.showFeedback("手势映射已即时更新")
                }
                key == GesturePreferences.COOLDOWN_MS -> {
                    globalCooldown.updateDuration(GesturePreferences.cooldownMs(this))
                    overlayIndicator.showFeedback("冷却时长已更新")
                }
                key == GesturePreferences.SENSITIVITY -> {
                    // 灵敏度决定所有位移/角度阈值，运行中直接重算，无需重启手势控制。
                    pipeline?.updateSensitivity(GesturePreferences.movementScale(this))
                    overlayIndicator.showFeedback("识别灵敏度已即时更新")
                }
                key == GesturePreferences.FEEDBACK -> {
                    val enabled = GesturePreferences.feedbackEnabled(this)
                    overlayIndicator.updateFeedbackEnabled(enabled)
                    if (enabled) overlayIndicator.showFeedback("识别反馈已开启")
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
        // 手动解除识别锁：只改锁状态，不能重置 controlMode，否则会把手势控制一起关掉。
        if (intent?.action == ACTION_UNLOCK_RECOGNITION) { unlockRecognition(); return START_NOT_STICKY }
        // 兜底：用户尚未同意隐私政策时不开启摄像头，也不进入前台服务状态。
        if (!PrivacyConsent.isAccepted(this)) {
            Log.d("CameraProbe", "startup: blocked, privacy policy not accepted yet")
            stopSelf()
            return START_NOT_STICKY
        }
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
                startModelInitialization()
            }
            handler.post {
                Log.d("CameraProbe", "startup: opening camera ${SystemClock.uptimeMillis() - startedAt} ms after start request")
                openCamera()
            }
        }
        return START_NOT_STICKY
    }
    private fun startModelInitialization() {
        val generation = synchronized(modelInitLock) {
            if (stopped || pipeline != null || modelInitInProgress) return
            modelInitInProgress = true
            ++modelInitGeneration
        }
        Thread({
            var createdPipeline: HandPipeline? = null
            val t0 = SystemClock.uptimeMillis()
            try {
                val newPipeline = HandPipeline(this) { event ->
                            val service = ControlAccessibilityService.active
                            if (favoriteFlowActive) return@HandPipeline
                            // 自拍倒计时期间冻结全部手势：连识别提示也不显示，避免与倒计时抢占浮层。
                            if (selfieInProgress) return@HandPipeline
                            // 识别锁是控制状态而不是动作：它不经过映射，不执行动作，也不启动冷却。
                            // 冷却期间整条管线是冻结的，所以这里不会在冷却中提前判断 Love 手势。
                            if (event is GestureEvent.RecognitionLock) {
                                applyRecognitionLock(event.locked)
                                return@HandPipeline
                            }
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
                                GestureEvent.PinkyMute,
                                GestureEvent.LotusRecents, GestureEvent.OrchidBack,
                                GestureEvent.LeftLBack, GestureEvent.LShape, GestureEvent.CShape,
                                GestureEvent.LoveLock, is GestureEvent.TwoFingerSwipe,
                                GestureEvent.TwoFingerDoubleTap, is GestureEvent.TwoFingerVolumeHold,
                                GestureEvent.TwoFingerUp, is GestureEvent.OpenApp -> Unit // Migrated gestures use the mapping pipeline above.
                                is GestureEvent.RecognitionLock -> Unit // Handled above: a control state, never an action.
                                is GestureEvent.ClawDrag -> overlayIndicator.showFeedback("抓取手势未绑定动作，可在校准页映射中指定") // Unbound by default.
                                GestureEvent.Six666 -> overlayIndicator.showFeedback("六六顺手势未绑定动作，可在校准页映射中指定") // Unbound by default.
                                is GestureEvent.Feedback -> overlayIndicator.showFeedback(event.message, event.progress)
                                GestureEvent.Back -> service?.inject(event) { finishAction(it, "返回") }
                                    ?: finishAction(false, "返回", "无障碍服务未连接")
                                GestureEvent.Home -> service?.inject(event) { finishAction(it, "返回桌面") }
                                    ?: finishAction(false, "返回桌面", "无障碍服务未连接")
                                GestureEvent.Recents -> service?.inject(event) { finishAction(it, "打开最近任务") }
                                    ?: finishAction(false, "最近任务", "无障碍服务未连接")
                            }
                }
                createdPipeline = newPipeline
                newPipeline.setDiag { Log.d("CameraProbe", "diag: $it") }
                val accepted = synchronized(modelInitLock) {
                    if (!stopped && generation == modelInitGeneration && pipeline == null) {
                        pipeline = newPipeline
                        modelInitInProgress = false
                        true
                    } else {
                        if (generation == modelInitGeneration) modelInitInProgress = false
                        false
                    }
                }
                if (accepted) {
                    Log.d("CameraProbe", "startup: model ready ${SystemClock.uptimeMillis() - t0} ms after thread start")
                } else {
                    // The service stopped or a newer initialization won while this native
                    // model was being created. It was never published, so close it here.
                    newPipeline.close()
                }
            } catch (e: Exception) {
                Log.e("CameraProbe", "model init failed", e)
                createdPipeline?.close()
                val shouldReport = synchronized(modelInitLock) {
                    val current = !stopped && generation == modelInitGeneration
                    if (generation == modelInitGeneration) modelInitInProgress = false
                    current
                }
                if (shouldReport) {
                    controlMode = false
                    val reason = if (e is java.io.FileNotFoundException) "模型文件缺失：hand_landmarker.task" else "模型初始化失败：${e.javaClass.simpleName}"
                    mainHandler.post { if (!stopped) fail(reason) }
                }
            }
        }, "model-init").start()
    }
    private fun openCamera() {
        try {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { fail("相机权限失效"); return }
            val id = cameraManager.cameraIdList.firstOrNull {
                cameraManager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
            } ?: run { fail("未找到前置摄像头"); return }
            val characteristics = cameraManager.getCameraCharacteristics(id)
            sensorRotation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
            cameraOpening = true
            val generation = ++cameraGeneration
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { image ->
                    if (!firstFrameLogged) {
                        firstFrameLogged = true
                        Log.d("CameraProbe", "startup: first camera frame received")
                    }
                    if (controlMode && !globalCooldown.isActive() && !favoriteFlowActive) try {
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
            selfieJpegSizes = characteristics
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
                ?.sortedByDescending { it.width.toLong() * it.height.toLong() }
                .orEmpty()
            selfieJpegSizeIndex = -1
            advanceSelfieReader()
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
        // 未解锁的手势不得执行；功能开关是独立的一层，不能代替解锁权益判断。
        if (!entitlement.owns(mapped.mapping.code)) return
        if (!featureGate.allows(mapped.mapping, featureConfig)) return
        val dragPhase = (mapped.event as? GestureEvent.ClawDrag)?.phase
        val isDrag = mapped.mapping.action == GestureAction.DRAG
        val isRollingScreenshot = mapped.mapping.action == GestureAction.ROLLING_SCREENSHOT
        // A rolling screenshot is a multi-second exclusive action. Freeze before its first frame so
        // cursor, holds, paths and other actions cannot alter the page while it is being stitched.
        if (isRollingScreenshot) pipeline?.pause()
        // A drag is one action delivered as START/MOVE/END. Cooling down on any MOVE would freeze
        // the pipeline mid-drag, so the cooldown starts only when the finger is lifted; MOVE is
        // also kept out of the log so one long drag does not flood it.
        if (!(isDrag && dragPhase == GestureEvent.DragPhase.MOVE)) {
            Log.d("CameraProbe", "gesture ${mapped.mapping.code} -> ${mapped.mapping.action}")
        }
        val submitted = actionExecutor.execute(mapped) { success ->
            if (isRollingScreenshot && !success) pipeline?.resume()
            if (mapped.mapping.cooldownPolicy == CooldownPolicy.GLOBAL_AFTER_SUCCESS ||
                (isDrag && dragPhase == GestureEvent.DragPhase.END)
            ) {
                // The mute toggle knows which way it flipped, so its wording wins over the generic
                // success text, which reads the same for muting and for restoring the sound.
                val message = if (mapped.mapping.action == GestureAction.TOGGLE_MUTE) {
                    lastMuteMessage ?: mapped.mapping.action.successMessage()
                } else mapped.mapping.action.successMessage()
                finishAction(success, message, mapped.mapping.action.failureMessage())
            }
        }
        if (!submitted && mapped.mapping.action != GestureAction.MOVE_CURSOR) {
            if (isRollingScreenshot) pipeline?.resume()
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
                if (!entitlement.owns(mapped.mapping.code) || !featureGate.allows(mapped.mapping, featureConfig)) {
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
    /**
     * 切换识别锁：锁定期间相机与手部检测继续运行，但只有 Love 手势被识别，
     * 其余手势、光标与动作一律不输出。锁定不等于停止服务，也不会释放摄像头。
     */
    private fun applyRecognitionLock(locked: Boolean) {
        recognitionLocked = locked
        if (locked) {
            // 持续音量是独占动作，锁定前必须先安全结束；引擎也会发送自己的 END，这里只作兜底。
            if (volumeHoldActive) finishVolumeHold(atBoundary = true)
            ControlAccessibilityService.active?.hideCursor()
            overlayIndicator.setState(OverlayIndicator.State.LOCKED)
            overlayIndicator.showFeedback("识别已锁定，仅 Love 手势可以解锁")
            updateNotification("识别已锁定，仅 Love 手势可以解锁")
            Log.d("CameraProbe", "recognition locked by love gesture")
        } else {
            overlayIndicator.setState(OverlayIndicator.State.RUNNING)
            overlayIndicator.showFeedback("识别已解锁，请放下手后继续操作")
            updateNotification(if (controlMode) "手势识别与悬浮控制正在运行" else "摄像头后台运行中")
            Log.d("CameraProbe", "recognition unlocked by love gesture")
        }
        // 用户可能正停在首页，此时不会有 onResume；不广播的话手动解除入口就不会出现。
        sendBroadcast(
            Intent(ACTION_RECOGNITION_LOCK_CHANGED)
                .putExtra(EXTRA_RECOGNITION_LOCKED, locked)
                .setPackage(packageName)
        )
    }

    /** 常驻通知与首页的手动解除入口：不依赖手势识别成功。 */
    private fun unlockRecognition() {
        if (!recognitionLocked) return
        pipeline?.unlockRecognition()
        applyRecognitionLock(false)
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

    /** Physical display pixels at the moment the selfie starts; system-bar insets are irrelevant. */
    private fun currentDisplayPixelSize(): SelfieFraming.Size {
        val displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.getRealMetrics(metrics)
        return SelfieFraming.Size(metrics.widthPixels, metrics.heightPixels)
    }
    private fun configure(device: CameraDevice, includeHighQualitySelfie: Boolean = true) {
        try {
            val surface: Surface = reader?.surface ?: run { fail("图像读取器已关闭"); return }
            val selfieSurface = selfieReader?.surface?.takeIf { includeHighQualitySelfie }
            val surfaces = listOfNotNull(surface, selfieSurface)
            device.createCaptureSession(surfaces, object : CameraCaptureSession.StateCallback() {
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
                override fun onConfigureFailed(s: CameraCaptureSession) {
                    s.close()
                    if (includeHighQualitySelfie && selfieReader != null) {
                        // Some devices cannot combine their largest JPEG with the 640x480 YUV
                        // recognition stream. Try every smaller JPEG before accepting preview-only
                        // quality; the first configured candidate is the best stable one.
                        if (advanceSelfieReader()) {
                            Log.w("CameraProbe", "selfie stream combination unsupported; trying a smaller JPEG")
                            configure(device, includeHighQualitySelfie = true)
                        } else {
                            Log.w("CameraProbe", "all high quality selfie streams unsupported; using preview fallback")
                            configure(device, includeHighQualitySelfie = false)
                        }
                    } else recoverCamera("相机会话中断，等待恢复")
                }
            }, handler)
        } catch (e: CameraAccessException) { recoverCamera("相机会话中断，等待恢复") }
        catch (e: Exception) { fail("配置失败：${e.javaClass.simpleName}") }
    }

    private fun advanceSelfieReader(): Boolean {
        selfieReader?.close()
        selfieReader = null
        selfieJpegSizeIndex++
        val size = selfieJpegSizes.getOrNull(selfieJpegSizeIndex) ?: return false
        Log.d("CameraProbe", "high quality selfie candidate ${size.width}x${size.height}")
        selfieReader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 2).apply {
            setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { image ->
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    completeHighQualitySelfie(bytes)
                }
            }, handler)
        }
        return true
    }
    private fun recoverCamera(message: String) {
        if (stopped) return
        isControlRunning = false
        cameraOpening = false
        Log.w("CameraProbe", message)
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        selfieReader?.close(); selfieReader = null
        completeHighQualitySelfie(null)
        overlayIndicator.setState(OverlayIndicator.State.ERROR)
        updateNotification(message)
        handler.removeCallbacks(reopenCamera)
        handler.postDelayed(reopenCamera, 1500)
    }
    /**
     * 结束自拍冻结：恢复识别并清空引擎状态，倒计时期间的任何手部动作都不会在恢复后被补触发。
     */
    private fun endSelfieFreeze() {
        selfieInProgress = false
        pipeline?.resetTracking()
    }

    private fun captureSelfie(callback: (Boolean) -> Unit) {
        if (selfieInProgress) { callback(false); return }
        selfieInProgress = true
        val displaySize = currentDisplayPixelSize()
        // Countdown freezes every gesture (including the cursor), so the hand can be lowered right away.
        // Pausing the pipeline also clears holds and trajectories: nothing may fire when it resumes.
        pipeline?.pause()
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
            captureHighQualitySelfie { jpeg ->
                if (jpeg != null) finishHighQualitySelfie(jpeg, displaySize, callback)
                else capturePreviewSelfie(activePipeline, displaySize, callback)
            }
        }, 3_000L)
    }
    private fun captureHighQualitySelfie(callback: (ByteArray?) -> Unit) {
        handler.post {
            val device = camera
            val activeSession = session
            val target = selfieReader?.surface
            if (stopped || device == null || activeSession == null || target == null || pendingHighQualitySelfie != null) {
                callback(null)
                return@post
            }
            try {
                pendingHighQualitySelfie = callback
                handler.removeCallbacks(highQualitySelfieTimeout)
                handler.postDelayed(highQualitySelfieTimeout, 4_000L)
                val jpegRotation = CameraFrameOrientation.relativeRotationDegrees(
                    sensorOrientationDegrees = sensorRotation,
                    displayRotationDegrees = currentDisplayRotationDegrees(),
                    frontFacing = true
                )
                val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                    addTarget(target)
                    set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                    set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                    set(CaptureRequest.JPEG_QUALITY, 100.toByte())
                    set(CaptureRequest.JPEG_ORIENTATION, jpegRotation)
                }.build()
                activeSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                    override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                        Log.w("CameraProbe", "high quality selfie capture failed: ${failure.reason}")
                        completeHighQualitySelfie(null)
                    }
                }, handler)
            } catch (e: Exception) {
                Log.w("CameraProbe", "high quality selfie unavailable; using preview fallback", e)
                completeHighQualitySelfie(null)
            }
        }
    }
    private fun completeHighQualitySelfie(jpeg: ByteArray?) {
        handler.removeCallbacks(highQualitySelfieTimeout)
        val callback = pendingHighQualitySelfie ?: return
        pendingHighQualitySelfie = null
        callback(jpeg)
    }
    private fun finishHighQualitySelfie(
        jpeg: ByteArray,
        displaySize: SelfieFraming.Size,
        callback: (Boolean) -> Unit
    ) {
        selfieWriter.execute {
            playShutter()
            val framed = framedSelfieBitmap(jpeg, displaySize)
            val preview = framed?.let(::previewThumbnail) ?: previewThumbnail(jpeg)
            val saved = if (framed != null) {
                try {
                    saveSelfie(framed)
                } finally {
                    framed.recycle()
                }
            } else {
                // Framing is best effort: a decode/OOM failure must not throw away a valid
                // high-quality capture. Preserve the upright original as the safe fallback.
                saveSelfie(jpeg)
            }
            endSelfieFreeze()
            if (saved && preview != null) overlayIndicator.showSelfiePreview(preview) else preview?.recycle()
            callback(saved)
        }
    }
    private fun capturePreviewSelfie(
        activePipeline: HandPipeline,
        displaySize: SelfieFraming.Size,
        callback: (Boolean) -> Unit
    ) {
        activePipeline.captureNextFrame { bitmap ->
            selfieWriter.execute {
                playShutter()
                val framed = frameSelfieBitmap(bitmap, displaySize)
                val preview = previewThumbnail(framed)
                val saved = saveSelfie(framed)
                framed.recycle()
                endSelfieFreeze()
                if (saved && preview != null) overlayIndicator.showSelfiePreview(preview) else preview?.recycle()
                callback(saved)
            }
        }
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
    private fun previewThumbnail(jpeg: ByteArray): Bitmap? = try {
        val rotation = selfieRotationDegrees(jpeg)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > 960 || bounds.outHeight / sample > 960) sample *= 2
        val decoded = BitmapFactory.decodeByteArray(
            jpeg,
            0,
            jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        ) ?: return null
        val upright = rotateBitmap(decoded, rotation)
        val thumbnail = previewThumbnail(upright)
        if (thumbnail !== upright) upright.recycle()
        thumbnail
    } catch (e: Exception) {
        Log.w("CameraProbe", "high quality selfie preview failed", e)
        null
    }

    /**
     * Dispatches a media/volume key through AudioManager; works without the accessibility
     * service. A dispatched key only reaches an app that owns an active MediaSession, so when
     * nothing is playing and no session exists the system drops it silently — that is why the
     * same gesture sometimes starts the music app and sometimes does nothing. For a play
     * request we therefore verify that audio really started and, if it did not, open the
     * default music app and repeat the key once its session is up.
     */
    private fun dispatchMediaKey(keyCode: Int, callback: (Boolean) -> Unit) {
        val audio = runCatching { getSystemService(AudioManager::class.java) }.getOrNull()
        if (audio == null) {
            callback(false)
            return
        }
        // Remember whether audio was already playing: only a "play" request may wake a music
        // app, a "pause" request must never open one.
        val musicActiveBefore = isMusicActive(audio)
        if (!sendMediaKey(audio, keyCode)) {
            callback(false)
            return
        }
        callback(true)
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE && !musicActiveBefore) {
            mainHandler.postDelayed({
                verifyPlaybackStarted(audio, secondCheck = false)
            }, PLAYBACK_VERIFY_DELAY_MS)
        }
    }

    private fun sendMediaKey(audio: AudioManager, keyCode: Int): Boolean = try {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        true
    } catch (e: Exception) {
        Log.e("CameraProbe", "media key $keyCode failed", e)
        false
    }

    private fun isMusicActive(audio: AudioManager): Boolean =
        runCatching { audio.isMusicActive() }.getOrDefault(false)

    /**
     * Confirms a play request actually produced audio. A cold player can take almost a second
     * before it is audible, so the silent case is confirmed twice before the music app is
     * launched, and once more after the repeated play key so the feedback never claims success
     * the user cannot hear.
     */
    private fun verifyPlaybackStarted(audio: AudioManager, secondCheck: Boolean) {
        if (isMusicActive(audio)) return
        if (!secondCheck) {
            mainHandler.postDelayed(
                { verifyPlaybackStarted(audio, secondCheck = true) },
                PLAYBACK_RECHECK_DELAY_MS
            )
            return
        }
        if (!launchDefaultMusicApp()) {
            overlayIndicator.showFeedback("没有找到音乐应用，请先打开音乐 App 再试")
            return
        }
        overlayIndicator.showFeedback("正在唤起音乐应用")
        mainHandler.postDelayed({
            val current = runCatching { getSystemService(AudioManager::class.java) }.getOrNull()
                ?: return@postDelayed
            sendMediaKey(current, KeyEvent.KEYCODE_MEDIA_PLAY)
            mainHandler.postDelayed({
                if (!isMusicActive(current)) {
                    overlayIndicator.showFeedback("未能唤起音乐应用，请先打开音乐 App 再试")
                }
            }, PLAYBACK_FINAL_CHECK_DELAY_MS)
        }, MUSIC_APP_SESSION_DELAY_MS)
    }

    /** Opens the system's default music app; false when no music app can be resolved. */
    private fun launchDefaultMusicApp(): Boolean {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_APP_MUSIC)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            if (intent.resolveActivity(packageManager) == null) return false
            startActivity(intent)
            true
        } catch (e: Exception) {
            Log.w("CameraProbe", "default music app launch failed", e)
            false
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

    private fun toggleMute(callback: (Boolean) -> Unit) {
        try {
            val audio = getSystemService(AudioManager::class.java)
            val wasMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)
            audio.adjustStreamVolume(
                AudioManager.STREAM_MUSIC,
                if (wasMuted) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE,
                AudioManager.FLAG_SHOW_UI
            )
            val message = if (wasMuted) "已恢复媒体声音" else "媒体已静音"
            lastMuteMessage = message
            overlayIndicator.showFeedback(message)
            // Some vendor audio services publish the mute bit a few frames after accepting
            // ADJUST_MUTE/UNMUTE. Verify shortly afterward instead of reporting a false failure.
            mainHandler.postDelayed({
                callback(runCatching { audio.isStreamMute(AudioManager.STREAM_MUSIC) != wasMuted }.getOrDefault(false))
            }, 120L)
        } catch (e: Exception) {
            Log.e("CameraProbe", "toggle mute failed", e)
            callback(false)
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
            val saved = contentResolver.openOutputStream(uri)?.use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, SELFIE_JPEG_OUTPUT_QUALITY, it)
            } == true
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
    /**
     * Devices answer JPEG_ORIENTATION differently: asked to rotate, some rotate the pixels while
     * others only write an orientation tag. Galleries read the tag, BitmapFactory and many viewers
     * do not, so the selfie looked sideways in the overlay. Baking the rotation into the pixels once
     * here leaves every consumer looking at the same upright picture. Original bytes are kept
     * whenever there is no rotation or the bitmap could not be built, so picture quality is intact.
     */
    private fun saveSelfie(jpeg: ByteArray): Boolean {
        val rotation = selfieRotationDegrees(jpeg)
        if (rotation == 0) return saveSelfieBytes { output -> output.write(jpeg) }
        val upright = uprightBitmap(jpeg, rotation) ?: return saveSelfieBytes { output -> output.write(jpeg) }
        return try {
            saveSelfieBytes { output ->
                upright.compress(Bitmap.CompressFormat.JPEG, SELFIE_JPEG_OUTPUT_QUALITY, output)
            }
        } finally {
            upright.recycle()
        }
    }

    /**
     * Decodes the highest-quality JPEG, makes its pixels upright, then crops only the excess edges
     * needed to match the physical display aspect ratio. The crop keeps the largest possible pixel
     * dimensions; it never downsizes to the display resolution and never stretches the subject.
     */
    private fun framedSelfieBitmap(jpeg: ByteArray, displaySize: SelfieFraming.Size): Bitmap? {
        val rotation = selfieRotationDegrees(jpeg)
        val upright = uprightBitmap(jpeg, rotation) ?: return null
        return frameSelfieBitmap(upright, displaySize)
    }

    private fun frameSelfieBitmap(source: Bitmap, displaySize: SelfieFraming.Size): Bitmap {
        return try {
            val crop = SelfieFraming.centerCrop(
                SelfieFraming.Size(source.width, source.height),
                displaySize
            )
            if (crop.left == 0 && crop.top == 0 && crop.width == source.width && crop.height == source.height) {
                source
            } else {
                Bitmap.createBitmap(source, crop.left, crop.top, crop.width, crop.height).also { framed ->
                    Log.d(
                        "CameraProbe",
                        "selfie framed ${source.width}x${source.height} -> ${framed.width}x${framed.height} " +
                            "for display ${displaySize.width}x${displaySize.height}"
                    )
                    if (framed !== source) source.recycle()
                }
            }
        } catch (e: OutOfMemoryError) {
            Log.e("CameraProbe", "selfie framing ran out of memory; preserving uncropped image", e)
            source
        } catch (e: Exception) {
            Log.e("CameraProbe", "selfie framing failed; preserving uncropped image", e)
            source
        }
    }

    /** Clockwise rotation the captured JPEG declares; 0 when its pixels are already upright. */
    private fun selfieRotationDegrees(jpeg: ByteArray): Int = try {
        val constant = ExifInterface(ByteArrayInputStream(jpeg))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        CameraFrameOrientation.exifRotationDegrees(constant).also {
            Log.d("CameraProbe", "selfie orientation tag=$constant -> rotating $it deg")
        }
    } catch (e: Exception) {
        Log.w("CameraProbe", "selfie orientation read failed", e)
        0
    }
    private fun uprightBitmap(jpeg: ByteArray, rotation: Int): Bitmap? = try {
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size)?.let { rotateBitmap(it, rotation) }
    } catch (e: OutOfMemoryError) {
        Log.e("CameraProbe", "selfie decode ran out of memory", e)
        null
    } catch (e: Exception) {
        Log.w("CameraProbe", "selfie rotation failed", e)
        null
    }
    private fun rotateBitmap(source: Bitmap, rotation: Int): Bitmap {
        if (rotation == 0) return source
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(rotation.toFloat()) },
            true
        )
        if (rotated !== source) source.recycle()
        return rotated
    }
    private fun saveSelfieBytes(write: (java.io.OutputStream) -> Unit): Boolean {
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
            val saved = try {
                contentResolver.openOutputStream(uri)?.use { write(it) } != null
            } catch (e: Exception) {
                Log.e("CameraProbe", "selfie output failed", e)
                false
            }
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
        val builder = Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.ic_menu_camera).setContentTitle("魔法手势")
            .setContentText(message).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", pending).build())
        // 锁定时必须保留手动解除入口，防止弱光或识别失败时只能强制停止服务。
        if (recognitionLocked) {
            val unlockIntent = Intent(this, CameraProbeService::class.java).setAction(ACTION_UNLOCK_RECOGNITION)
            val unlockPending = PendingIntent.getService(this, 9, unlockIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(null, "解除锁定", unlockPending).build())
        }
        return builder.build()
    }
    private fun updateNotification(message: String) { getSystemService(NotificationManager::class.java).notify(7, notification(message)) }
    override fun onDestroy() {
        isControlRunning = false
        // 停止全部控制后清除锁状态：下次主动启动控制默认从正常识别开始。
        recognitionLocked = false
        stopped = true
        val pipelineToClose = synchronized(modelInitLock) {
            modelInitGeneration++
            modelInitInProgress = false
            pipeline.also { pipeline = null }
        }
        if (::overlayIndicator.isInitialized) favoriteController.dismiss()
        globalCooldown.reset()
        cameraGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        handler.removeCallbacksAndMessages(null)
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        selfieReader?.close(); selfieReader = null
        pipelineToClose?.close()
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
