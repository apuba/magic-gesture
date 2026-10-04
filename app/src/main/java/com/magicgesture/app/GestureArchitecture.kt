package com.magicgesture.app

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent

enum class GestureCode {
    G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11, G12,
    G13, G14, G15, G16, G17, G18, G19, G20, G21, G22, G23,
    G24, G25, G26, G27, G28, G29, G30, G31, G32, G33, G34, G35
}

enum class GestureType { CONTINUOUS, DISCRETE, DYNAMIC, HOLD, SEQUENCE }

/** 手势中文名：首页签到提示与校准页配置区共用，避免两处文案不一致。 */
val GESTURE_DISPLAY_NAMES: Map<GestureCode, String> = mapOf(
    GestureCode.G01 to "指尖移动",
    GestureCode.G02 to "指尖轻点",
    GestureCode.G03 to "轨迹预留一",
    GestureCode.G04 to "轨迹预留二",
    GestureCode.G05 to "并掌上挥",
    GestureCode.G06 to "并掌下挥",
    GestureCode.G07 to "并掌左挥",
    GestureCode.G08 to "并掌右挥",
    GestureCode.G09 to "轨迹预留三",
    GestureCode.G10 to "轨迹预留四",
    GestureCode.G11 to "V 手势",
    GestureCode.G12 to "指尖比心",
    GestureCode.G13 to "开合掌",
    GestureCode.G14 to "莲花指",
    GestureCode.G15 to "兰花指",
    GestureCode.G16 to "张掌变一指",
    GestureCode.G17 to "张掌变二指",
    GestureCode.G18 to "张掌变三指",
    GestureCode.G19 to "张掌变四指",
    GestureCode.G20 to "拇指赞",
    GestureCode.G21 to "OK 手势",
    GestureCode.G22 to "握拳",
    GestureCode.G23 to "小指手势",
    GestureCode.G24 to "单指枪·横向",
    GestureCode.G25 to "单指枪·竖向",
    GestureCode.G26 to "抓取手势",
    GestureCode.G27 to "C 手势",
    GestureCode.G28 to "Love 手势",
    GestureCode.G34 to "六六顺手势",
    GestureCode.G29 to "双指左挥",
    GestureCode.G30 to "双指右挥",
    GestureCode.G31 to "双指上拉",
    GestureCode.G32 to "双指下拉",
    GestureCode.G33 to "双指双点",
    GestureCode.G35 to "双指枪·竖向"
)

enum class GestureAction {
    NONE, MOVE_CURSOR, CLICK, SCROLL_UP, SCROLL_DOWN, SCROLL_LEFT, SCROLL_RIGHT, BACK, HOME, SELFIE, LIKE, SCREENSHOT,
    THUMBS_UP_LIKE, CONFIRM, PLAY_PAUSE, RECENTS,
    NOTIFICATIONS, VOLUME_UP, VOLUME_DOWN, TOGGLE_MUTE, MEDIA_NEXT, MEDIA_PREVIOUS, LOCK_SCREEN, VOICE_ASSISTANT,
    DRAG, ROLLING_SCREENSHOT, OPEN_APP, FAVORITE_CURRENT,
    /** Legacy values retained only so existing saved mappings continue to load after upgrade. */
    OPEN_APP_1, OPEN_APP_2, OPEN_APP_3, OPEN_APP_4;

    fun successMessage(): String = when (this) {
        NONE -> "未绑定动作"
        MOVE_CURSOR -> ""
        CLICK -> "点击"
        SCROLL_UP -> "向上滑动"
        SCROLL_DOWN -> "向下滑动"
        SCROLL_LEFT -> "向左滑动"
        SCROLL_RIGHT -> "向右滑动"
        BACK -> "返回"
        HOME -> "返回桌面"
        SELFIE -> "自拍已保存"
        LIKE -> "已点赞"
        SCREENSHOT -> "已触发截图"
        THUMBS_UP_LIKE -> "已点赞"
        CONFIRM -> "确认"
        PLAY_PAUSE -> "播放/暂停"
        RECENTS -> "打开最近任务"
        NOTIFICATIONS -> "已打开通知栏"
        VOLUME_UP -> "音量已增加"
        VOLUME_DOWN -> "音量已降低"
        TOGGLE_MUTE -> "静音状态已切换"
        MEDIA_NEXT -> "已切换下一曲"
        MEDIA_PREVIOUS -> "已切换上一曲"
        LOCK_SCREEN -> "已锁屏"
        VOICE_ASSISTANT -> "已唤起语音助手"
        DRAG -> "拖动完成"
        ROLLING_SCREENSHOT -> "长截图已保存"
        OPEN_APP, OPEN_APP_1, OPEN_APP_2, OPEN_APP_3, OPEN_APP_4 -> "已打开应用"
        FAVORITE_CURRENT -> "已点击收藏位置"
    }

    fun failureMessage(): String = when (this) {
        NONE -> "未绑定动作"
        SELFIE -> "自拍保存失败"
        LIKE, THUMBS_UP_LIKE -> "未找到可用的点赞按钮"
        CONFIRM -> "请先启用并移动光标"
        LOCK_SCREEN -> "锁屏需要 Android 9 或更高版本"
        VOICE_ASSISTANT -> "未找到可用的语音助手"
        DRAG -> "拖动距离太短或执行失败"
        ROLLING_SCREENSHOT -> "滚动截图需要 Android 11 或更高版本"
        OPEN_APP, OPEN_APP_1, OPEN_APP_2, OPEN_APP_3, OPEN_APP_4 -> "未选择应用，请重新设置该手势"
        FAVORITE_CURRENT -> "当前应用未定义收藏位置或点击失败"
        else -> "动作执行失败"
    }

    /** Short label shown in the mapping configuration UI. */
    fun displayLabel(): String = when (this) {
        NONE -> "暂不绑定动作"
        MOVE_CURSOR -> "移动光标"
        CLICK -> "点击"
        SCROLL_UP -> "向上滚动"
        SCROLL_DOWN -> "向下滚动"
        SCROLL_LEFT -> "向左滚动"
        SCROLL_RIGHT -> "向右滚动"
        BACK -> "返回"
        HOME -> "返回桌面"
        SELFIE -> "自拍"
        LIKE, THUMBS_UP_LIKE -> "双击点赞"
        SCREENSHOT -> "截图"
        CONFIRM -> "点击光标位置"
        PLAY_PAUSE -> "播放/暂停"
        RECENTS -> "最近任务"
        NOTIFICATIONS -> "下拉通知栏"
        VOLUME_UP -> "音量 +"
        VOLUME_DOWN -> "音量 −"
        TOGGLE_MUTE -> "静音开关"
        MEDIA_NEXT -> "下一曲"
        MEDIA_PREVIOUS -> "上一曲"
        LOCK_SCREEN -> "锁屏"
        VOICE_ASSISTANT -> "语音助手"
        DRAG -> "拖动"
        ROLLING_SCREENSHOT -> "滚动长截图"
        OPEN_APP, OPEN_APP_1, OPEN_APP_2, OPEN_APP_3, OPEN_APP_4 -> "打开应用"
        FAVORITE_CURRENT -> "收藏当前内容"
    }
}

enum class CooldownPolicy { NONE, GLOBAL_AFTER_SUCCESS }

data class GestureMapping(
    val code: GestureCode,
    val type: GestureType,
    val action: GestureAction,
    val cooldownPolicy: CooldownPolicy
)

data class MappedGesture(val mapping: GestureMapping, val event: GestureEvent)

/**
 * Resolves gesture events to mappings. User overrides (gesture -> action) are applied on top
 * of the factory defaults; the gesture type and cooldown policy always stay with the gesture.
 */
class GestureMappingManager(private val overrides: Map<GestureCode, GestureAction> = emptyMap()) {

    fun resolve(event: GestureEvent): MappedGesture? {
        val code = when (event) {
            is GestureEvent.Cursor -> GestureCode.G01
            is GestureEvent.Click -> GestureCode.G02
            is GestureEvent.Swipe -> when (event.source) {
                GestureEvent.MotionSource.INDEX_FINGER -> if (event.up) GestureCode.G03 else GestureCode.G04
                GestureEvent.MotionSource.PALM -> if (event.up) GestureCode.G05 else GestureCode.G06
            }
            is GestureEvent.HorizontalSwipe -> when (event.source) {
                GestureEvent.MotionSource.PALM -> if (event.left) GestureCode.G07 else GestureCode.G08
                GestureEvent.MotionSource.INDEX_FINGER -> if (event.left) GestureCode.G09 else GestureCode.G10
            }
            GestureEvent.Selfie -> GestureCode.G11
            GestureEvent.Like -> GestureCode.G12
            GestureEvent.Screenshot -> GestureCode.G13
            GestureEvent.LotusRecents -> GestureCode.G14
            GestureEvent.OrchidBack -> GestureCode.G15
            GestureEvent.ThumbsUp -> GestureCode.G20
            GestureEvent.Ok -> GestureCode.G21
            GestureEvent.PlayPause -> GestureCode.G22
            GestureEvent.PinkyMute -> GestureCode.G23
            GestureEvent.LeftLBack -> GestureCode.G24
            GestureEvent.LShape -> GestureCode.G25
            is GestureEvent.ClawDrag -> GestureCode.G26
            GestureEvent.CShape -> GestureCode.G27
            GestureEvent.LoveLock -> GestureCode.G28
            GestureEvent.Six666 -> GestureCode.G34
            GestureEvent.TwoFingerDoubleTap -> GestureCode.G33
            GestureEvent.TwoFingerUp -> GestureCode.G35
            is GestureEvent.TwoFingerSwipe -> when (event.direction) {
                GestureEvent.TwoFingerDirection.LEFT -> GestureCode.G29
                GestureEvent.TwoFingerDirection.RIGHT -> GestureCode.G30
                GestureEvent.TwoFingerDirection.UP -> GestureCode.G31
                GestureEvent.TwoFingerDirection.DOWN -> GestureCode.G32
            }
            is GestureEvent.TwoFingerVolumeHold -> if (event.raise) GestureCode.G31 else GestureCode.G32
            is GestureEvent.OpenApp -> when (event.slot) {
                1 -> GestureCode.G16
                2 -> GestureCode.G17
                3 -> GestureCode.G18
                else -> GestureCode.G19
            }
            else -> return null
        }
        val base = defaultMappings[code]
        val overridden = overrides[code]
        val mapping = when {
            overridden == GestureAction.NONE -> return null
            base != null && overridden != null && overridden != base.action -> base.copy(action = overridden)
            base?.action == GestureAction.NONE -> return null
            base != null -> base
            // A deliberately unbound gesture (e.g. G26) can still carry a user override; all
            // unbound pipelines are hold/trajectory gestures, so HOLD fits them.
            overridden != null -> GestureMapping(code, GestureType.HOLD, overridden, CooldownPolicy.GLOBAL_AFTER_SUCCESS)
            else -> return null
        }
        return MappedGesture(mapping, event)
    }

    /** The action a gesture currently performs; null for an unbound gesture without override. */
    fun actionFor(code: GestureCode): GestureAction? = when (val override = overrides[code]) {
        GestureAction.NONE -> null
        null -> defaultActionOf(code)
        else -> override
    }

    /** Only gestures with a real detection pipeline may be remapped; the cursor stays fixed. */
    fun isRemappable(code: GestureCode): Boolean =
        code != GestureCode.G01 && code !in NO_PIPELINE_CODES

    companion object {
        /** Codes that only exist in the enum as placeholders; no detector, no event. */
        private val NO_PIPELINE_CODES = emptySet<GestureCode>()

        fun defaultActionOf(code: GestureCode): GestureAction? =
            defaultMappings[code]?.action?.takeUnless { it == GestureAction.NONE }

        private fun dynamicMapping(code: GestureCode, action: GestureAction) = GestureMapping(
            code,
            GestureType.DYNAMIC,
            action,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        )

        private val defaultMappings = mapOf(
        GestureCode.G01 to GestureMapping(
            GestureCode.G01,
            GestureType.CONTINUOUS,
            GestureAction.MOVE_CURSOR,
            CooldownPolicy.NONE
        ),
        GestureCode.G02 to GestureMapping(
            GestureCode.G02,
            GestureType.DISCRETE,
            GestureAction.CLICK,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        // G03/G04/G09/G10 keep their existing detector metadata for compatibility and optional
        // remapping, but ship disabled and unbound. Their codes are reserved for phase-two
        // trajectory gestures; this release does not reinterpret the old swipes as trajectories.
        GestureCode.G03 to dynamicMapping(GestureCode.G03, GestureAction.NONE),
        GestureCode.G04 to dynamicMapping(GestureCode.G04, GestureAction.NONE),
        GestureCode.G05 to dynamicMapping(GestureCode.G05, GestureAction.SCROLL_UP),
        GestureCode.G06 to dynamicMapping(GestureCode.G06, GestureAction.SCROLL_DOWN),
        // The four-finger wave family G05-G08 owns all four scroll directions in this release;
        // back/home stay with G24/G14.
        GestureCode.G07 to dynamicMapping(GestureCode.G07, GestureAction.SCROLL_LEFT),
        GestureCode.G08 to dynamicMapping(GestureCode.G08, GestureAction.SCROLL_RIGHT),
        GestureCode.G09 to dynamicMapping(GestureCode.G09, GestureAction.NONE),
        GestureCode.G10 to dynamicMapping(GestureCode.G10, GestureAction.NONE),
        GestureCode.G11 to GestureMapping(
            GestureCode.G11,
            GestureType.HOLD,
            GestureAction.SELFIE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G12 to GestureMapping(
            GestureCode.G12,
            GestureType.HOLD,
            GestureAction.LIKE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G13 to GestureMapping(
            GestureCode.G13,
            GestureType.SEQUENCE,
            GestureAction.SCREENSHOT,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G14 to GestureMapping(
            GestureCode.G14,
            GestureType.HOLD,
            GestureAction.HOME,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G15 to GestureMapping(
            GestureCode.G15,
            GestureType.HOLD,
            GestureAction.RECENTS,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        // G16-G19 use the same action type; each gesture stores its own target package.
        GestureCode.G16 to GestureMapping(
            GestureCode.G16,
            GestureType.SEQUENCE,
            GestureAction.OPEN_APP,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G17 to GestureMapping(
            GestureCode.G17,
            GestureType.SEQUENCE,
            GestureAction.OPEN_APP,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G18 to GestureMapping(
            GestureCode.G18,
            GestureType.SEQUENCE,
            GestureAction.OPEN_APP,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G19 to GestureMapping(
            GestureCode.G19,
            GestureType.SEQUENCE,
            GestureAction.OPEN_APP,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G20 to GestureMapping(
            GestureCode.G20,
            GestureType.HOLD,
            GestureAction.THUMBS_UP_LIKE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G21 to GestureMapping(
            GestureCode.G21,
            GestureType.HOLD,
            GestureAction.FAVORITE_CURRENT,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G22 to GestureMapping(
            GestureCode.G22,
            GestureType.HOLD,
            GestureAction.PLAY_PAUSE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G23 to GestureMapping(
            GestureCode.G23,
            GestureType.HOLD,
            GestureAction.TOGGLE_MUTE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G24 to GestureMapping(
            GestureCode.G24,
            GestureType.HOLD,
            GestureAction.BACK,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        // G25/G35 swapped their default actions on 2026-10-04 at the product owner's request.
        // Scrolling now belongs to the single-finger gun, which is the quicker shape to strike while
        // watching short video; opening the notification panel belongs to the two-finger gun, which
        // has to be held still for a full second and is therefore easier to stop in time if it was
        // accidental. Both stay ordinary bindings, so users can still swap them from the calibration page.
        GestureCode.G25 to GestureMapping(
            GestureCode.G25,
            GestureType.HOLD,
            GestureAction.SCROLL_UP,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        // G26 (claw) now performs a continuous drag: it presses on START, follows the palm with
        // MOVE and lifts on END. Cooldown is NONE because one drag may legitimately run for many
        // seconds — the cooldown starts only when END arrives, see executeMapped in the service.
        GestureCode.G26 to GestureMapping(
            GestureCode.G26,
            GestureType.HOLD,
            GestureAction.DRAG,
            CooldownPolicy.NONE
        ),
        GestureCode.G27 to GestureMapping(
            GestureCode.G27,
            GestureType.HOLD,
            GestureAction.RECENTS,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G28 to GestureMapping(
            GestureCode.G28,
            GestureType.HOLD,
            GestureAction.HOME,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G29 to dynamicMapping(GestureCode.G29, GestureAction.MEDIA_PREVIOUS),
        GestureCode.G30 to dynamicMapping(GestureCode.G30, GestureAction.MEDIA_NEXT),
        GestureCode.G31 to dynamicMapping(GestureCode.G31, GestureAction.VOLUME_UP),
        GestureCode.G32 to dynamicMapping(GestureCode.G32, GestureAction.VOLUME_DOWN),
        GestureCode.G33 to dynamicMapping(GestureCode.G33, GestureAction.PLAY_PAUSE),
        // G35 holds the two-finger pose still for 1s to open the notification panel. See the note on
        // G25 above for why the panel moved here and scrolling moved to the single-finger gun.
        GestureCode.G35 to GestureMapping(
            GestureCode.G35,
            GestureType.HOLD,
            GestureAction.NOTIFICATIONS,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        )
    )
    }
}

class GestureFeatureGate {
    fun allows(mapping: GestureMapping, features: GestureFeatureConfig): Boolean = when (mapping.code) {
        GestureCode.G01 -> features.cursor
        GestureCode.G02 -> features.click
        GestureCode.G03, GestureCode.G04 -> features.indexVerticalScroll
        GestureCode.G05, GestureCode.G06 -> features.palmVerticalScroll
        GestureCode.G07 -> features.palmLeftScroll
        GestureCode.G08 -> features.palmRightScroll
        GestureCode.G09 -> features.indexLeftScroll
        GestureCode.G10 -> features.indexRightScroll
        GestureCode.G11 -> features.selfie
        GestureCode.G12 -> features.like
        GestureCode.G13 -> features.screenshot
        GestureCode.G14 -> features.lotusRecents
        GestureCode.G15 -> features.orchidBack
        GestureCode.G16 -> features.openApp1
        GestureCode.G17 -> features.openApp2
        GestureCode.G18 -> features.openApp3
        GestureCode.G19 -> features.openApp4
        GestureCode.G20 -> features.thumbsUp
        GestureCode.G21 -> features.ok
        GestureCode.G22 -> features.playPause
        GestureCode.G23 -> features.pinkyMute
        GestureCode.G24 -> features.leftL
        GestureCode.G25 -> features.lShape
        GestureCode.G26 -> features.clawDrag
        GestureCode.G27 -> features.cShape
        GestureCode.G28 -> features.loveLock
        GestureCode.G34 -> features.six666
        GestureCode.G29, GestureCode.G30, GestureCode.G31, GestureCode.G32, GestureCode.G33 -> features.twoFingerMedia
        GestureCode.G35 -> features.twoFingerUp
        else -> false
    }
}

class GestureActionExecutor(
    private val accessibilityService: () -> ControlAccessibilityService?,
    private val selfieCapture: ((Boolean) -> Unit) -> Unit,
    private val mediaKey: (Int, (Boolean) -> Unit) -> Unit,
    private val volumeAdjust: (Boolean, (Boolean) -> Unit) -> Unit,
    private val muteToggle: ((Boolean) -> Unit) -> Unit = { callback -> callback(false) },
    private val actionProgress: ((String) -> Unit)? = null,
    /** Launches the app bound to the given open-app slot (1..4); false when unbound or missing. */
    private val launchApp: (GestureCode, (Boolean) -> Unit) -> Unit = { _, callback -> callback(false) },
    private val favoriteCurrent: ((Boolean) -> Unit) -> Unit = { callback -> callback(false) }
) {
    /**
     * Action-centric dispatch: execution depends only on the mapped action, never on the
     * gesture event that produced it, so user-remapped gestures work for every action.
     * Returns false when the action cannot even be submitted.
     */
    fun execute(mapped: MappedGesture, callback: (Boolean) -> Unit): Boolean {
        return when (mapped.mapping.action) {
            GestureAction.NONE -> false
            GestureAction.MOVE_CURSOR -> {
                val service = accessibilityService() ?: return false
                val cursor = mapped.event as? GestureEvent.Cursor ?: return false
                service.render(cursor.x, cursor.y)
                callback(true)
                true
            }
            GestureAction.CLICK -> {
                val service = accessibilityService() ?: return false
                val click = mapped.event as? GestureEvent.Click
                // Gestures remapped to CLICK have no coordinates of their own; fall back
                // to tapping the current cursor position, exactly like CONFIRM.
                if (click != null) service.inject(click, callback) else service.confirmAtCursor(callback)
                true
            }
            GestureAction.SCROLL_UP, GestureAction.SCROLL_DOWN -> {
                val service = accessibilityService() ?: return false
                service.scrollDirectional(mapped.mapping.action == GestureAction.SCROLL_UP, callback)
                true
            }
            GestureAction.SCROLL_LEFT, GestureAction.SCROLL_RIGHT -> {
                val service = accessibilityService() ?: return false
                service.scrollHorizontal(mapped.mapping.action == GestureAction.SCROLL_LEFT, callback)
                true
            }
            GestureAction.BACK -> globalAction(AccessibilityService.GLOBAL_ACTION_BACK, callback)
            GestureAction.HOME -> globalAction(AccessibilityService.GLOBAL_ACTION_HOME, callback)
            GestureAction.RECENTS -> globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS, callback)
            GestureAction.NOTIFICATIONS -> globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, callback)
            GestureAction.SCREENSHOT -> {
                val service = accessibilityService() ?: return false
                service.screenshotAction(callback)
                true
            }
            GestureAction.ROLLING_SCREENSHOT -> {
                val service = accessibilityService() ?: return false
                service.captureRollingScreenshot(
                    onProgress = { actionProgress?.invoke(it) },
                    onComplete = { ok, _ -> callback(ok) }
                )
                true
            }
            GestureAction.OPEN_APP -> { launchApp(mapped.mapping.code, callback); true }
            GestureAction.OPEN_APP_1 -> { launchApp(GestureCode.G16, callback); true }
            GestureAction.OPEN_APP_2 -> { launchApp(GestureCode.G17, callback); true }
            GestureAction.OPEN_APP_3 -> { launchApp(GestureCode.G18, callback); true }
            GestureAction.OPEN_APP_4 -> { launchApp(GestureCode.G19, callback); true }
            GestureAction.FAVORITE_CURRENT -> { favoriteCurrent(callback); true }
            GestureAction.LOCK_SCREEN -> {
                val service = accessibilityService() ?: return false
                service.lockScreenAction(callback)
                true
            }
            GestureAction.VOICE_ASSISTANT -> {
                val service = accessibilityService() ?: return false
                service.voiceAssistantAction(callback)
                true
            }
            GestureAction.SELFIE -> {
                selfieCapture(callback)
                true
            }
            GestureAction.DRAG -> {
                val service = accessibilityService() ?: return false
                // Only the claw gesture produces the coordinates a drag needs, and it delivers
                // them as a START/MOVE/END stream that keeps the finger down in between.
                val drag = mapped.event as? GestureEvent.ClawDrag ?: return false
                when (drag.phase) {
                    GestureEvent.DragPhase.START -> service.beginDrag(drag.startX, drag.startY, callback)
                    GestureEvent.DragPhase.MOVE -> service.moveDrag(drag.endX, drag.endY, callback)
                    GestureEvent.DragPhase.END -> service.endDrag(drag.endX, drag.endY, callback)
                }
                true
            }
            GestureAction.LIKE, GestureAction.THUMBS_UP_LIKE -> {
                val service = accessibilityService() ?: return false
                service.likeVideo(callback)
                true
            }
            GestureAction.CONFIRM -> {
                val service = accessibilityService() ?: return false
                service.confirmAtCursor(callback)
                true
            }
            // Media keys go through AudioManager and keep working even when the
            // accessibility service is reconnecting. Volume must NOT use dispatchMediaKeyEvent:
            // modern Android only routes media-session keycodes through it and silently
            // drops VOLUME_UP/DOWN, so volume adjusts the stream directly instead.
            GestureAction.PLAY_PAUSE -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, callback); true }
            GestureAction.MEDIA_NEXT -> { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, callback); true }
            GestureAction.MEDIA_PREVIOUS -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, callback); true }
            GestureAction.VOLUME_UP -> { volumeAdjust(true, callback); true }
            GestureAction.VOLUME_DOWN -> { volumeAdjust(false, callback); true }
            GestureAction.TOGGLE_MUTE -> { muteToggle(callback); true }
        }
    }

    private fun globalAction(actionCode: Int, callback: (Boolean) -> Unit): Boolean {
        val service = accessibilityService() ?: return false
        service.globalAction(actionCode, callback)
        return true
    }
}
