package com.magicgesture.app

import android.os.SystemClock

/** Controls the post-action lock. No gesture recognition or event may pass while active. */
class GlobalCooldownManager(
    private val durationMs: Long = DEFAULT_DURATION_MS,
    private val now: () -> Long = SystemClock::uptimeMillis
) {
    companion object { const val DEFAULT_DURATION_MS = 2_000L }

    private var lockedUntil = 0L

    @Synchronized
    fun allows(@Suppress("UNUSED_PARAMETER") event: GestureEvent): Boolean = now() >= lockedUntil

    @Synchronized
    fun isActive(): Boolean = now() < lockedUntil

    @Synchronized
    fun actionSucceeded() {
        lockedUntil = now() + durationMs
    }

    @Synchronized
    fun remainingMs(): Long = (lockedUntil - now()).coerceAtLeast(0L)

    @Synchronized
    fun reset() { lockedUntil = 0L }
}
