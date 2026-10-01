package com.magicgesture.app

import android.content.Context

data class GestureFeatureConfig(
    val cursor: Boolean = true,
    val click: Boolean = true,
    val scroll: Boolean = true,
    val screenshot: Boolean = true,
    val selfie: Boolean = true,
    val like: Boolean = true,
    val thumbsUp: Boolean = true,
    val ok: Boolean = true,
    val playPause: Boolean = true,
    val lotusRecents: Boolean = true,
    val orchidBack: Boolean = true,
    val leftL: Boolean = true,
    val lShape: Boolean = true,
    val clawDrag: Boolean = true,
    val cShape: Boolean = true,
    val loveLock: Boolean = true,
    val twoFingerMedia: Boolean = true
)

object GesturePreferences {
    const val FILE = "gesture_settings"
    private const val SENSITIVITY = "sensitivity"
    private const val REVERSE_HORIZONTAL = "reverse_horizontal"
    private const val FEEDBACK = "feedback_enabled"

    fun sensitivity(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getString(SENSITIVITY, "normal") ?: "normal"

    fun movementScale(context: Context): Float = when (sensitivity(context)) {
        "high" -> 0.78f
        "stable" -> 1.28f
        else -> 1f
    }

    fun reverseHorizontal(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getBoolean(REVERSE_HORIZONTAL, false)

    fun feedbackEnabled(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getBoolean(FEEDBACK, true)

    fun features(context: Context): GestureFeatureConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return GestureFeatureConfig(
            cursor = prefs.getBoolean("feature_cursor", true),
            click = prefs.getBoolean("feature_click", true),
            scroll = prefs.getBoolean("feature_scroll", true),
            screenshot = prefs.getBoolean("feature_screenshot", true),
            selfie = prefs.getBoolean("feature_selfie", prefs.getBoolean("feature_recents", true)),
            like = prefs.getBoolean("feature_like", true),
            thumbsUp = prefs.getBoolean("feature_thumbs_up", true),
            ok = prefs.getBoolean("feature_ok", true),
            playPause = prefs.getBoolean("feature_play_pause", true),
            lotusRecents = prefs.getBoolean("feature_lotus_recents", true),
            orchidBack = prefs.getBoolean("feature_orchid_back", true),
            leftL = prefs.getBoolean("feature_left_l", true),
            lShape = prefs.getBoolean("feature_l_shape", true),
            clawDrag = prefs.getBoolean("feature_claw_drag", true),
            cShape = prefs.getBoolean("feature_c_shape", true),
            loveLock = prefs.getBoolean("feature_love_lock", true),
            twoFingerMedia = prefs.getBoolean("feature_two_finger_media", true)
        )
    }

    fun setFeature(context: Context, feature: String, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("feature_$feature", enabled)
            .apply()
    }

    // ---- Gesture -> action mapping overrides ("mapping_G07" = "HOME"). Missing key = default. ----

    fun actionOverrides(context: Context): Map<GestureCode, GestureAction> {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val overrides = mutableMapOf<GestureCode, GestureAction>()
        for (code in GestureCode.entries) {
            val stored = prefs.getString(mappingKey(code), null) ?: continue
            val action = runCatching { GestureAction.valueOf(stored) }.getOrNull() ?: continue
            overrides[code] = action
        }
        return overrides
    }

    fun setActionOverride(context: Context, code: GestureCode, action: GestureAction?) {
        val editor = context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
        if (action == null) editor.remove(mappingKey(code)) else editor.putString(mappingKey(code), action.name)
        editor.apply()
    }

    private fun mappingKey(code: GestureCode) = "mapping_${code.name}"

    fun save(
        context: Context,
        sensitivity: String,
        reverseHorizontal: Boolean,
        feedbackEnabled: Boolean,
        features: GestureFeatureConfig
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(SENSITIVITY, sensitivity)
            .putBoolean(REVERSE_HORIZONTAL, reverseHorizontal)
            .putBoolean(FEEDBACK, feedbackEnabled)
            .putBoolean("feature_cursor", features.cursor)
            .putBoolean("feature_click", features.click)
            .putBoolean("feature_scroll", features.scroll)
            .putBoolean("feature_screenshot", features.screenshot)
            .putBoolean("feature_selfie", features.selfie)
            .putBoolean("feature_like", features.like)
            .putBoolean("feature_thumbs_up", features.thumbsUp)
            .putBoolean("feature_ok", features.ok)
            .putBoolean("feature_play_pause", features.playPause)
            .putBoolean("feature_lotus_recents", features.lotusRecents)
            .putBoolean("feature_orchid_back", features.orchidBack)
            .putBoolean("feature_left_l", features.leftL)
            .putBoolean("feature_l_shape", features.lShape)
            .putBoolean("feature_claw_drag", features.clawDrag)
            .putBoolean("feature_c_shape", features.cShape)
            .putBoolean("feature_love_lock", features.loveLock)
            .putBoolean("feature_two_finger_media", features.twoFingerMedia)
            .apply()
    }
}
