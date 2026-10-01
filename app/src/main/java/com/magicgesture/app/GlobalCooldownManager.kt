package com.magicgesture.app

import android.os.SystemClock

/**
 * Controls the post-action lock. No gesture recognition or event may pass while active.
 * The duration is user-configurable in CalibrationActivity (0.6s..4s) and may be updated
 * at runtime; an update never extends or shortens a lock already in progress.
 */
class GlobalCooldownManager(
    private var durationMs: Long = DEFAULT_DURATION_MS,
    private val now: () -> Long = SystemClock::uptimeMillis
) {
    companion object { const val DEFAULT_DURATION_MS = 1_500L }

    private var lockedUntil = 0L

    @Synchronized
    fun updateDuration(value: Long) { durationMs = value }

    @Synchronized
    fun currentDurationMs(): Long = durationMs

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
