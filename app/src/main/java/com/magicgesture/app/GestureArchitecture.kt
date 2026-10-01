package com.magicgesture.app

import android.accessibilityservice.AccessibilityService
import android.view.KeyEvent

enum class GestureCode {
    G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11, G12,
    G13, G14, G15, G16, G17, G18, G19, G20, G21, G22, G23
}

enum class GestureType { CONTINUOUS, DISCRETE, DYNAMIC, HOLD, SEQUENCE }

enum class GestureAction {
    MOVE_CURSOR, CLICK, SCROLL_UP, SCROLL_DOWN, BACK, HOME, SELFIE, LIKE, SCREENSHOT,
    THUMBS_UP_LIKE, CONFIRM, PLAY_PAUSE, RECENTS,
    NOTIFICATIONS, VOLUME_UP, VOLUME_DOWN, MEDIA_NEXT, MEDIA_PREVIOUS, LOCK_SCREEN, VOICE_ASSISTANT;

    fun successMessage(): String = when (this) {
        MOVE_CURSOR -> ""
        CLICK -> "点击"
        SCROLL_UP -> "向上滑动"
        SCROLL_DOWN -> "向下滑动"
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
        MEDIA_NEXT -> "已切换下一曲"
        MEDIA_PREVIOUS -> "已切换上一曲"
        LOCK_SCREEN -> "已锁屏"
        VOICE_ASSISTANT -> "已唤起语音助手"
    }

    fun failureMessage(): String = when (this) {
        SELFIE -> "自拍保存失败"
        LIKE, THUMBS_UP_LIKE -> "未找到可用的点赞按钮"
        CONFIRM -> "请先启用并移动光标"
        LOCK_SCREEN -> "锁屏需要 Android 9 或更高版本"
        VOICE_ASSISTANT -> "未找到可用的语音助手"
        else -> "动作执行失败"
    }

    /** Short label shown in the mapping configuration UI. */
    fun displayLabel(): String = when (this) {
        MOVE_CURSOR -> "移动光标"
        CLICK -> "点击"
        SCROLL_UP -> "向上滚动"
        SCROLL_DOWN -> "向下滚动"
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
        MEDIA_NEXT -> "下一曲"
        MEDIA_PREVIOUS -> "上一曲"
        LOCK_SCREEN -> "锁屏"
        VOICE_ASSISTANT -> "语音助手"
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
            else -> return null
        }
        val base = defaultMappings[code] ?: return null
        val overridden = overrides[code]
        val mapping = if (overridden != null && overridden != base.action) base.copy(action = overridden) else base
        return MappedGesture(mapping, event)
    }

    /** The action a gesture currently performs: user override, or the factory default. */
    fun actionFor(code: GestureCode): GestureAction = overrides[code] ?: requireNotNull(defaultActionOf(code))

    /** Only gestures with a real detection pipeline may be remapped; the cursor stays fixed. */
    fun isRemappable(code: GestureCode): Boolean = code != GestureCode.G01 && defaultMappings.containsKey(code)

    companion object {
        fun defaultActionOf(code: GestureCode): GestureAction? = defaultMappings[code]?.action

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
        GestureCode.G03 to dynamicMapping(GestureCode.G03, GestureAction.SCROLL_UP),
        GestureCode.G04 to dynamicMapping(GestureCode.G04, GestureAction.SCROLL_DOWN),
        GestureCode.G05 to dynamicMapping(GestureCode.G05, GestureAction.SCROLL_UP),
        GestureCode.G06 to dynamicMapping(GestureCode.G06, GestureAction.SCROLL_DOWN),
        GestureCode.G07 to dynamicMapping(GestureCode.G07, GestureAction.BACK),
        GestureCode.G08 to dynamicMapping(GestureCode.G08, GestureAction.HOME),
        GestureCode.G09 to dynamicMapping(GestureCode.G09, GestureAction.BACK),
        GestureCode.G10 to dynamicMapping(GestureCode.G10, GestureAction.HOME),
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
            GestureAction.RECENTS,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G15 to GestureMapping(
            GestureCode.G15,
            GestureType.HOLD,
            GestureAction.BACK,
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
            GestureAction.CONFIRM,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        ),
        GestureCode.G22 to GestureMapping(
            GestureCode.G22,
            GestureType.HOLD,
            GestureAction.PLAY_PAUSE,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        )
    )
    }
}

class GestureFeatureGate {
    fun allows(mapping: GestureMapping, features: GestureFeatureConfig): Boolean = when (mapping.code) {
        GestureCode.G01 -> features.cursor
        GestureCode.G02 -> features.click
        GestureCode.G03, GestureCode.G04, GestureCode.G05, GestureCode.G06 -> features.scroll
        GestureCode.G07, GestureCode.G09 -> features.back
        GestureCode.G08, GestureCode.G10 -> features.home
        GestureCode.G11 -> features.selfie
        GestureCode.G12 -> features.like
        GestureCode.G13 -> features.screenshot
        GestureCode.G14 -> features.lotusRecents
        GestureCode.G15 -> features.orchidBack
        GestureCode.G20 -> features.thumbsUp
        GestureCode.G21 -> features.ok
        GestureCode.G22 -> features.playPause
        else -> false
    }
}

class GestureActionExecutor(
    private val accessibilityService: () -> ControlAccessibilityService?,
    private val selfieCapture: ((Boolean) -> Unit) -> Unit,
    private val mediaKey: (Int, (Boolean) -> Unit) -> Unit
) {
    /**
     * Action-centric dispatch: execution depends only on the mapped action, never on the
     * gesture event that produced it, so user-remapped gestures work for every action.
     * Returns false when the action cannot even be submitted.
     */
    fun execute(mapped: MappedGesture, callback: (Boolean) -> Unit): Boolean {
        return when (mapped.mapping.action) {
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
            GestureAction.BACK -> globalAction(AccessibilityService.GLOBAL_ACTION_BACK, callback)
            GestureAction.HOME -> globalAction(AccessibilityService.GLOBAL_ACTION_HOME, callback)
            GestureAction.RECENTS -> globalAction(AccessibilityService.GLOBAL_ACTION_RECENTS, callback)
            GestureAction.NOTIFICATIONS -> globalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS, callback)
            GestureAction.SCREENSHOT -> {
                val service = accessibilityService() ?: return false
                service.screenshotAction(callback)
                true
            }
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
            // Media and volume keys go through AudioManager and keep working even when the
            // accessibility service is reconnecting.
            GestureAction.PLAY_PAUSE -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, callback); true }
            GestureAction.MEDIA_NEXT -> { mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT, callback); true }
            GestureAction.MEDIA_PREVIOUS -> { mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS, callback); true }
            GestureAction.VOLUME_UP -> { mediaKey(KeyEvent.KEYCODE_VOLUME_UP, callback); true }
            GestureAction.VOLUME_DOWN -> { mediaKey(KeyEvent.KEYCODE_VOLUME_DOWN, callback); true }
        }
    }

    private fun globalAction(actionCode: Int, callback: (Boolean) -> Unit): Boolean {
        val service = accessibilityService() ?: return false
        service.globalAction(actionCode, callback)
        return true
    }
}
