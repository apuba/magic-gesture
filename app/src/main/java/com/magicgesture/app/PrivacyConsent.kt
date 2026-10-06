package com.magicgesture.app

import android.content.Context

/**
 * Locally stored record of the user's agreement to the in-app privacy policy.
 *
 * Consent only has to be given once: after agreeing, the flag is kept in app-private storage and
 * later launches go straight to the home page. Bump [CURRENT_VERSION] when the policy text changes
 * in a way that needs a fresh explicit agreement, so users who accepted an older version are asked
 * again on their next launch instead of silently continuing under the old consent.
 */
object PrivacyConsent {
    /**
     * App-internal version of the consent record. Bump it whenever the policy needs a fresh,
     * explicit agreement; it is independent of the "政策版本" printed inside the policy text.
     */
    const val CURRENT_VERSION = 1

    private const val FILE = "privacy_consent"
    private const val KEY_ACCEPTED = "accepted"
    private const val KEY_VERSION = "policy_version"

    /** True when the user has accepted the version of the policy shipped with this build. */
    fun isAccepted(context: Context): Boolean {
        val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ACCEPTED, false) && prefs.getInt(KEY_VERSION, 0) == CURRENT_VERSION
    }

    fun accept(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACCEPTED, true)
            .putInt(KEY_VERSION, CURRENT_VERSION)
            .apply()
    }
}
