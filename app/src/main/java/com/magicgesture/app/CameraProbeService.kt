package com.magicgesture.app

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import android.view.Surface

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
    private var stopped = false
    private var pipeline: HandPipeline? = null
    private var sensorRotation = 0
    private var controlMode = false
    private var featureConfig = GestureFeatureConfig()
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private lateinit var overlayIndicator: OverlayIndicator
    override fun onCreate() {
        super.onCreate()
        worker.start(); handler = Handler(worker.looper)
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        featureConfig = GesturePreferences.features(this)
        overlayIndicator = OverlayIndicator(this)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key?.startsWith("feature_") == true) {
                featureConfig = GesturePreferences.features(this)
                pipeline?.updateFeatures(featureConfig)
                if (!featureConfig.cursor) ControlAccessibilityService.active?.hideCursor()
                overlayIndicator.showFeedback("手势开关已即时更新")
            }
        }.also {
            getSharedPreferences(GesturePreferences.FILE, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(it)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(channel, "魔法手势运行状态", NotificationManager.IMPORTANCE_LOW))
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
            if (controlMode) {
                try {
                    pipeline = HandPipeline(this) { event ->
                        val service = ControlAccessibilityService.active
                        when (event) {
                            is GestureEvent.Cursor -> { if (featureConfig.cursor) service?.render(event.x, event.y) }
                            is GestureEvent.Feedback -> overlayIndicator.showFeedback(event.message, event.progress)
                            is GestureEvent.Click -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback("点击") }
                            is GestureEvent.Swipe -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback(if (event.up) "向上滑动" else "向下滑动") }
                            is GestureEvent.HorizontalSwipe -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback(if (event.left) "向左滑动" else "向右滑动") }
                            GestureEvent.Screenshot -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback("已触发截图") }
                            GestureEvent.Like -> {
                                overlayIndicator.showProtection()
                                if (service == null) overlayIndicator.showFeedback("无障碍服务未连接")
                                else service.likeVideo { success ->
                                    overlayIndicator.showFeedback(if (success) "已点赞" else "未找到可用的点赞按钮")
                                }
                            }
                            GestureEvent.Back -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback("返回") }
                            GestureEvent.Home -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback("返回桌面") }
                            GestureEvent.Recents -> { service?.inject(event); overlayIndicator.showProtection(); overlayIndicator.showFeedback("打开最近任务") }
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
            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2).apply {
                setOnImageAvailableListener({ r -> r.acquireLatestImage()?.use { image ->
                    if (controlMode) try { pipeline?.submit(image, sensorRotation) }
                    catch (e: Exception) { fail("推理帧失败：${e.javaClass.simpleName}") }
                } }, handler)
            }
            cameraManager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) { camera = device; configure(device) }
                override fun onDisconnected(device: CameraDevice) { device.close(); camera = null; fail("摄像头断开") }
                override fun onError(device: CameraDevice, error: Int) { device.close(); camera = null; fail("摄像头错误 $error") }
            }, handler)
        } catch (e: Exception) { fail("相机启动失败：${e.javaClass.simpleName}") }
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
                override fun onConfigureFailed(s: CameraCaptureSession) { s.close(); fail("相机会话配置失败") }
            }, handler)
        } catch (e: Exception) { fail("配置失败：${e.javaClass.simpleName}") }
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
        handler.removeCallbacksAndMessages(null)
        session?.close(); session = null
        camera?.close(); camera = null
        reader?.close(); reader = null
        pipeline?.close(); pipeline = null
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
