package com.magicgesture.app

enum class GestureCode {
    G01, G02, G03, G04, G05, G06, G07, G08, G09, G10, G11, G12,
    G13, G14, G15, G16, G17, G18, G19, G20, G21, G22, G23
}

enum class GestureType { CONTINUOUS, DISCRETE, DYNAMIC, HOLD, SEQUENCE }

enum class GestureAction {
    MOVE_CURSOR, CLICK, SCROLL_UP, SCROLL_DOWN, BACK, HOME, SELFIE, LIKE, SCREENSHOT,
    THUMBS_UP_LIKE, CONFIRM, PLAY_PAUSE, RECENTS;

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
    }

    fun failureMessage(): String = when (this) {
        SELFIE -> "自拍保存失败"
        LIKE, THUMBS_UP_LIKE -> "未找到可用的点赞按钮"
        CONFIRM -> "请先启用并移动光标"
        else -> "动作执行失败"
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

class GestureMappingManager {
    private val mappings = mapOf(
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

    companion object {
        private fun dynamicMapping(code: GestureCode, action: GestureAction) = GestureMapping(
            code,
            GestureType.DYNAMIC,
            action,
            CooldownPolicy.GLOBAL_AFTER_SUCCESS
        )
    }

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
        return MappedGesture(requireNotNull(mappings[code]), event)
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
    private val mediaToggle: ((Boolean) -> Unit) -> Unit
) {
    /** Returns false when the action cannot even be submitted. */
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
                val click = mapped.event as? GestureEvent.Click ?: return false
                service.inject(click, callback)
                true
            }
            GestureAction.SCROLL_UP, GestureAction.SCROLL_DOWN,
            GestureAction.BACK, GestureAction.HOME, GestureAction.SCREENSHOT, GestureAction.RECENTS -> {
                val service = accessibilityService() ?: return false
                service.inject(mapped.event, callback)
                true
            }
            GestureAction.SELFIE -> {
                selfieCapture(callback)
                true
            }
            GestureAction.LIKE -> {
                val service = accessibilityService() ?: return false
                service.likeVideo(callback)
                true
            }
            GestureAction.THUMBS_UP_LIKE -> {
                val service = accessibilityService() ?: return false
                service.likeVideo(callback)
                true
            }
            GestureAction.CONFIRM -> {
                val service = accessibilityService() ?: return false
                service.confirmAtCursor(callback)
                true
            }
            GestureAction.PLAY_PAUSE -> {
                mediaToggle(callback)
                true
            }
        }
    }
}
