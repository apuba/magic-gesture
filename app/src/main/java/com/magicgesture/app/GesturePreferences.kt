package com.magicgesture.app

import android.content.Context

/**
 * The claw ships off (2026-10-04): it reads any half-curled hand — a vertical or horizontal index
 * with the other fingers folded, a heart, the start of a swipe — and it sits early enough in the
 * pipeline to swallow those frames. On device it fired 16 drags during one round while the index
 * scrolls and the single-finger waves never got a turn, matching the product intent of leaving G26
 * unbound until something needs it. Users can still switch it on from the calibration page.
 */
private const val CLAW_DRAG_DEFAULT = false

/**
 * G03/G04/G09/G10 ship off (2026-10-04). A single index finger is reserved for cursor and click;
 * four-finger waves G05-G08 cover directional scrolling. The four codes remain available for
 * explicit remapping and are reserved for phase-two trajectory gestures.
 */
private const val INDEX_DIRECTIONAL_DEFAULT = false

data class GestureFeatureConfig(
    val cursor: Boolean = true,
    val click: Boolean = true,
    /** Legacy aggregate value; retained so existing callers/settings migrate safely. */
    val scroll: Boolean = true,
    val indexVerticalScroll: Boolean = INDEX_DIRECTIONAL_DEFAULT,
    val palmVerticalScroll: Boolean = scroll,
    val palmLeftScroll: Boolean = scroll,
    val indexLeftScroll: Boolean = INDEX_DIRECTIONAL_DEFAULT,
    val palmRightScroll: Boolean = scroll,
    val indexRightScroll: Boolean = INDEX_DIRECTIONAL_DEFAULT,
    val screenshot: Boolean = true,
    val selfie: Boolean = true,
    val like: Boolean = true,
    val thumbsUp: Boolean = true,
    val ok: Boolean = true,
    val playPause: Boolean = true,
    val pinkyMute: Boolean = true,
    val lotusRecents: Boolean = true,
    val orchidBack: Boolean = true,
    val leftL: Boolean = true,
    val lShape: Boolean = true,
    val clawDrag: Boolean = CLAW_DRAG_DEFAULT,
    val cShape: Boolean = true,
    val loveLock: Boolean = true,
    // G34 "666": thumb and pinky out, other fingers curled. Unbound by default.
    val six666: Boolean = true,
    /**
     * G29/G30 双指左右挥切歌与 G33 双指双点播放/暂停（第 3 次签到解锁）。
     * 原先与音量共用一个 `two_finger_media` 开关，导致第 2 次签到后切歌未解锁却已经可用，
     * 页面又显示禁用；拆分后与页面卡片和签到批次一一对应。
     */
    val twoFingerTrack: Boolean = true,
    /** G31/G32 双指上下拉持续增减音量（第 2 次签到解锁），独立于切歌开关。 */
    val twoFingerVolume: Boolean = true,
    // G35: two fingers together pointing up with the thumb sideways, held 1s to scroll feeds.
    val twoFingerUp: Boolean = true,
    // G16-G19 open-app sequences: open palm, then fold to 1-4 fingers.
    val openApp1: Boolean = true,
    val openApp2: Boolean = true,
    val openApp3: Boolean = true,
    val openApp4: Boolean = true
)

data class FavoriteButtonProfile(
    val packageName: String,
    val appLabel: String,
    val portrait: Boolean,
    val normalizedX: Float,
    val normalizedY: Float,
    val appVersion: String,
    val updatedAt: Long
)

object GesturePreferences {
    const val FILE = "gesture_settings"
    const val SENSITIVITY = "sensitivity"
    private const val REVERSE_HORIZONTAL = "reverse_horizontal"
    const val FEEDBACK = "feedback_enabled"
    const val COOLDOWN_MS = "cooldown_ms"
    private const val INDEX_DIRECTIONAL_RETIREMENT_MIGRATED = "migration_index_directional_retirement_v1"

    /** Post-action lock duration, user-tunable 0.6s..4s via the calibration page slider. */
    const val MIN_COOLDOWN_MS = 600L
    const val MAX_COOLDOWN_MS = 4_000L

    fun sensitivity(context: Context): String = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getString(SENSITIVITY, "normal") ?: "normal"

    fun movementScale(context: Context): Float = movementScaleFor(sensitivity(context))

    /** Pure mapping kept explicit so all three UI options have a stable, testable meaning. */
    internal fun movementScaleFor(sensitivity: String): Float = when (sensitivity) {
        "high" -> 0.78f
        "stable" -> 1.28f
        else -> 1f
    }

    /** Saved on tap from the calibration page so a running control session retunes live. */
    fun saveSensitivity(context: Context, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(SENSITIVITY, value)
            .apply()
    }

    fun reverseHorizontal(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getBoolean(REVERSE_HORIZONTAL, false)

    fun feedbackEnabled(context: Context): Boolean = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getBoolean(FEEDBACK, true)

    /** Saved on toggle so a running overlay can apply the feedback preference immediately. */
    fun saveFeedbackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(FEEDBACK, enabled)
            .apply()
    }

    fun cooldownMs(context: Context): Long = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getLong(COOLDOWN_MS, GlobalCooldownManager.DEFAULT_DURATION_MS)
        .coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS)

    /** Saved immediately on slider release so a running control session picks it up live. */
    fun saveCooldownMs(context: Context, value: Long) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putLong(COOLDOWN_MS, value.coerceIn(MIN_COOLDOWN_MS, MAX_COOLDOWN_MS))
            .apply()
    }

    /** Package name of the app bound to an open-app slot (1..4); null when unbound. */
    fun openAppPackage(context: Context, slot: Int): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getString("open_app_package_$slot", null)

    fun saveOpenAppPackage(context: Context, slot: Int, packageName: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (packageName == null) remove("open_app_package_$slot") else putString("open_app_package_$slot", packageName)
        }.apply()
    }

    /** Package bound directly to a gesture whose selected action is OPEN_APP. */
    fun openAppPackage(context: Context, code: GestureCode): String? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val direct = prefs.getString(openAppGestureKey(code), null)
        if (direct != null) return direct
        // Preserve the four original bindings after upgrading from the slot-based UI.
        val legacySlot = when (code) {
            GestureCode.G16 -> 1
            GestureCode.G17 -> 2
            GestureCode.G18 -> 3
            GestureCode.G19 -> 4
            else -> null
        }
        return legacySlot?.let { prefs.getString("open_app_package_$it", null) }
    }

    fun saveOpenAppPackage(context: Context, code: GestureCode, packageName: String?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().apply {
            if (packageName == null) remove(openAppGestureKey(code))
            else putString(openAppGestureKey(code), packageName)
        }.apply()
    }

    private fun openAppGestureKey(code: GestureCode) = "open_app_package_${code.name}"

    private const val FAVORITE_PACKAGES = "favorite_position_packages"
    private fun favoritePrefix(packageName: String, portrait: Boolean) =
        "favorite_position_${packageName}_${if (portrait) "portrait" else "landscape"}"

    fun favoriteProfile(context: Context, packageName: String, portrait: Boolean): FavoriteButtonProfile? {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val prefix = favoritePrefix(packageName, portrait)
        if (!prefs.contains("${prefix}_x") || !prefs.contains("${prefix}_y")) return null
        return FavoriteButtonProfile(
            packageName = packageName,
            appLabel = prefs.getString("${prefix}_label", packageName) ?: packageName,
            portrait = portrait,
            normalizedX = prefs.getFloat("${prefix}_x", .5f).coerceIn(0f, 1f),
            normalizedY = prefs.getFloat("${prefix}_y", .5f).coerceIn(0f, 1f),
            appVersion = prefs.getString("${prefix}_version", "") ?: "",
            updatedAt = prefs.getLong("${prefix}_updated", 0L)
        )
    }

    fun favoriteProfiles(context: Context): List<FavoriteButtonProfile> {
        val packages = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .getStringSet(FAVORITE_PACKAGES, emptySet()).orEmpty()
        return packages.flatMap { pkg ->
            listOfNotNull(favoriteProfile(context, pkg, true), favoriteProfile(context, pkg, false))
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { !it.portrait }))
    }

    fun saveFavoriteProfile(context: Context, profile: FavoriteButtonProfile) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val prefix = favoritePrefix(profile.packageName, profile.portrait)
        val packages = prefs.getStringSet(FAVORITE_PACKAGES, emptySet()).orEmpty().toMutableSet().apply {
            add(profile.packageName)
        }
        prefs.edit()
            .putStringSet(FAVORITE_PACKAGES, packages)
            .putString("${prefix}_label", profile.appLabel)
            .putFloat("${prefix}_x", profile.normalizedX.coerceIn(0f, 1f))
            .putFloat("${prefix}_y", profile.normalizedY.coerceIn(0f, 1f))
            .putString("${prefix}_version", profile.appVersion)
            .putLong("${prefix}_updated", profile.updatedAt)
            .apply()
    }

    fun deleteFavoriteProfile(context: Context, packageName: String, portrait: Boolean) {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val prefix = favoritePrefix(packageName, portrait)
        val editor = prefs.edit()
        listOf("label", "x", "y", "version", "updated").forEach { editor.remove("${prefix}_$it") }
        val otherExists = favoriteProfile(context, packageName, !portrait) != null
        if (!otherExists) {
            val packages = prefs.getStringSet(FAVORITE_PACKAGES, emptySet()).orEmpty().toMutableSet().apply {
                remove(packageName)
            }
            editor.putStringSet(FAVORITE_PACKAGES, packages)
        }
        editor.apply()
    }

    /**
     * 用户开关与解锁权益合并后的生效配置：未解锁的手势强制不参与识别。
     * 用户自己的开关值不会被写入，因此“关闭功能”永远不会等价于“未解锁”。
     */
    fun effectiveFeatures(context: Context): GestureFeatureConfig =
        features(context).restrictedTo(GestureUnlockStore(context).entitlement().codes)

    fun unlockedCodes(context: Context): Set<GestureCode> = GestureUnlockStore(context).unlockedCodes()

    fun features(context: Context): GestureFeatureConfig {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(INDEX_DIRECTIONAL_RETIREMENT_MIGRATED, false)) {
            prefs.edit()
                .putBoolean("feature_index_vertical_scroll", false)
                .putBoolean("feature_index_left_scroll", false)
                .putBoolean("feature_index_right_scroll", false)
                .putBoolean(INDEX_DIRECTIONAL_RETIREMENT_MIGRATED, true)
                .apply()
        }
        val legacyScroll = prefs.getBoolean("feature_scroll", true)
        // 旧版只有 feature_two_finger_media 一个开关；拆成两个后用它作为两个新键的初始值，
        // 用户此前关闭过的话两个新开关都继承关闭，不再被默认值强制打开。
        val legacyTwoFingerMedia = prefs.getBoolean("feature_two_finger_media", true)
        return GestureFeatureConfig(
            cursor = prefs.getBoolean("feature_cursor", true),
            click = prefs.getBoolean("feature_click", true),
            scroll = legacyScroll,
            indexVerticalScroll = prefs.getBoolean("feature_index_vertical_scroll", INDEX_DIRECTIONAL_DEFAULT),
            palmVerticalScroll = prefs.getBoolean("feature_palm_vertical_scroll", legacyScroll),
            palmLeftScroll = prefs.getBoolean("feature_palm_left_scroll", legacyScroll),
            indexLeftScroll = prefs.getBoolean("feature_index_left_scroll", INDEX_DIRECTIONAL_DEFAULT),
            palmRightScroll = prefs.getBoolean("feature_palm_right_scroll", legacyScroll),
            indexRightScroll = prefs.getBoolean("feature_index_right_scroll", INDEX_DIRECTIONAL_DEFAULT),
            screenshot = prefs.getBoolean("feature_screenshot", true),
            selfie = prefs.getBoolean("feature_selfie", prefs.getBoolean("feature_recents", true)),
            like = prefs.getBoolean("feature_like", true),
            thumbsUp = prefs.getBoolean("feature_thumbs_up", true),
            ok = prefs.getBoolean("feature_ok", true),
            playPause = prefs.getBoolean("feature_play_pause", true),
            pinkyMute = prefs.getBoolean("feature_pinky_mute", true),
            lotusRecents = prefs.getBoolean("feature_lotus_recents", true),
            orchidBack = prefs.getBoolean("feature_orchid_back", true),
            leftL = prefs.getBoolean("feature_left_l", true),
            lShape = prefs.getBoolean("feature_l_shape", true),
            clawDrag = prefs.getBoolean("feature_claw_drag", CLAW_DRAG_DEFAULT),
            cShape = prefs.getBoolean("feature_c_shape", true),
            loveLock = prefs.getBoolean("feature_love_lock", true),
            six666 = prefs.getBoolean("feature_six666", true),
            twoFingerTrack = prefs.getBoolean("feature_two_finger_track", legacyTwoFingerMedia),
            twoFingerVolume = prefs.getBoolean("feature_two_finger_volume", legacyTwoFingerMedia),
            twoFingerUp = prefs.getBoolean("feature_two_finger_up", true),
            openApp1 = prefs.getBoolean("feature_open_app_1", true),
            openApp2 = prefs.getBoolean("feature_open_app_2", true),
            openApp3 = prefs.getBoolean("feature_open_app_3", true),
            openApp4 = prefs.getBoolean("feature_open_app_4", true)
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
            var action = runCatching { GestureAction.valueOf(stored) }.getOrNull() ?: continue
            val legacySlot = when (action) {
                GestureAction.OPEN_APP_1 -> 1
                GestureAction.OPEN_APP_2 -> 2
                GestureAction.OPEN_APP_3 -> 3
                GestureAction.OPEN_APP_4 -> 4
                else -> null
            }
            if (legacySlot != null) {
                prefs.getString("open_app_package_$legacySlot", null)?.let { legacyPackage ->
                    if (prefs.getString(openAppGestureKey(code), null) == null) {
                        prefs.edit().putString(openAppGestureKey(code), legacyPackage).apply()
                    }
                }
                action = GestureAction.OPEN_APP
            }
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
            .putBoolean("feature_index_vertical_scroll", features.indexVerticalScroll)
            .putBoolean("feature_palm_vertical_scroll", features.palmVerticalScroll)
            .putBoolean("feature_palm_left_scroll", features.palmLeftScroll)
            .putBoolean("feature_index_left_scroll", features.indexLeftScroll)
            .putBoolean("feature_palm_right_scroll", features.palmRightScroll)
            .putBoolean("feature_index_right_scroll", features.indexRightScroll)
            .putBoolean("feature_screenshot", features.screenshot)
            .putBoolean("feature_selfie", features.selfie)
            .putBoolean("feature_like", features.like)
            .putBoolean("feature_thumbs_up", features.thumbsUp)
            .putBoolean("feature_ok", features.ok)
            .putBoolean("feature_play_pause", features.playPause)
            .putBoolean("feature_pinky_mute", features.pinkyMute)
            .putBoolean("feature_lotus_recents", features.lotusRecents)
            .putBoolean("feature_orchid_back", features.orchidBack)
            .putBoolean("feature_left_l", features.leftL)
            .putBoolean("feature_l_shape", features.lShape)
            .putBoolean("feature_claw_drag", features.clawDrag)
            .putBoolean("feature_c_shape", features.cShape)
            .putBoolean("feature_love_lock", features.loveLock)
            .putBoolean("feature_two_finger_track", features.twoFingerTrack)
            .putBoolean("feature_two_finger_volume", features.twoFingerVolume)
            .putBoolean("feature_two_finger_up", features.twoFingerUp)
            .putBoolean("feature_open_app_1", features.openApp1)
            .putBoolean("feature_open_app_2", features.openApp2)
            .putBoolean("feature_open_app_3", features.openApp3)
            .putBoolean("feature_open_app_4", features.openApp4)
            .apply()
    }
}
