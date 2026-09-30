package com.magicgesture.app

import android.os.SystemClock

/** Controls the post-action lock independently from gesture recognition state. */
class GlobalCooldownManager(
    private val durationMs: Long = DEFAULT_DURATION_MS,
    private val now: () -> Long = SystemClock::uptimeMillis
) {
    companion object { const val DEFAULT_DURATION_MS = 2_000L }

    private var lockedUntil = 0L

    @Synchronized
    fun allows(event: GestureEvent): Boolean =
        event is GestureEvent.Cursor || event is GestureEvent.Feedback || now() >= lockedUntil

    @Synchronized
    fun actionSucceeded() {
        lockedUntil = now() + durationMs
    }

    @Synchronized
    fun remainingMs(): Long = (lockedUntil - now()).coerceAtLeast(0L)

    @Synchronized
    fun reset() { lockedUntil = 0L }
}
