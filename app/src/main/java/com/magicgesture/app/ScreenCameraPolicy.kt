package com.magicgesture.app

/**
 * Pure state for deciding whether gesture recognition still needs the camera while the display is
 * unavailable. Navigation guidance is filtered before it reaches this class; [mediaMusicActive]
 * means confirmed USAGE_MEDIA playback only.
 */
class ScreenCameraPolicy(
    screenUsable: Boolean,
    mediaMusicActive: Boolean = false
) {
    var screenUsable: Boolean = screenUsable
        private set
    var mediaMusicActive: Boolean = mediaMusicActive
        private set

    val shouldRunCamera: Boolean
        get() = screenUsable || mediaMusicActive

    val shouldForceMusicScene: Boolean
        get() = !screenUsable && mediaMusicActive

    fun updateScreenUsable(value: Boolean) {
        screenUsable = value
    }

    fun updateMediaMusicActive(value: Boolean) {
        mediaMusicActive = value
    }

    companion object {
        /** Android AudioAttributes.USAGE_MEDIA; kept pure so the policy has ordinary JVM coverage. */
        private const val USAGE_MEDIA = 1

        fun isMediaMusicUsage(usage: Int): Boolean = usage == USAGE_MEDIA
    }
}
