package com.magicgesture.app

import android.content.Context

data class GestureFeatureConfig(
    val cursor: Boolean = true,
    val click: Boolean = true,
    val scroll: Boolean = true,
    val back: Boolean = true,
    val home: Boolean = true,
    val screenshot: Boolean = true,
    val recents: Boolean = true,
    val like: Boolean = true
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
            back = prefs.getBoolean("feature_back", true),
            home = prefs.getBoolean("feature_home", true),
            screenshot = prefs.getBoolean("feature_screenshot", true),
            recents = prefs.getBoolean("feature_recents", true),
            like = prefs.getBoolean("feature_like", true)
        )
    }

    fun setFeature(context: Context, feature: String, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean("feature_$feature", enabled)
            .apply()
    }

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
            .putBoolean("feature_back", features.back)
            .putBoolean("feature_home", features.home)
            .putBoolean("feature_screenshot", features.screenshot)
            .putBoolean("feature_recents", features.recents)
            .putBoolean("feature_like", features.like)
            .apply()
    }
}
