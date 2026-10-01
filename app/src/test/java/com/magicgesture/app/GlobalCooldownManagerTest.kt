package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalCooldownManagerTest {
    @Test fun allGestureEventsAreBlockedForDefaultDuration() {
        var time = 1_000L
        val cooldown = GlobalCooldownManager(now = { time })

        cooldown.actionSucceeded()
        assertFalse(cooldown.allows(GestureEvent.Back))
        assertFalse(cooldown.allows(GestureEvent.Cursor(.5f, .5f)))
        assertFalse(cooldown.allows(GestureEvent.Feedback("识别中")))
        assertTrue(cooldown.isActive())
        assertEquals(GlobalCooldownManager.DEFAULT_DURATION_MS, cooldown.remainingMs())
        assertEquals(GlobalCooldownManager.DEFAULT_DURATION_MS, cooldown.currentDurationMs())

        time = 2_499L
        assertFalse(cooldown.allows(GestureEvent.Home))
        time = 2_500L
        assertTrue(cooldown.allows(GestureEvent.Home))
        assertFalse(cooldown.isActive())
    }

    /** The calibration slider tunes 0.6s..4s; a new duration applies from the next action on. */
    @Test fun durationUpdatesApplyWithoutTouchingAnActiveLock() {
        var time = 0L
        val cooldown = GlobalCooldownManager(now = { time })
        cooldown.updateDuration(4_000L)
        cooldown.actionSucceeded()

        // Changing the duration while locked must not extend or shorten the running lock.
        cooldown.updateDuration(600L)
        assertEquals(4_000L, cooldown.remainingMs())
        time = 3_999L
        assertFalse(cooldown.allows(GestureEvent.Back))

        time = 4_000L
        assertTrue(cooldown.allows(GestureEvent.Back))
        cooldown.updateDuration(600L)
        cooldown.actionSucceeded()
        assertEquals(600L, cooldown.remainingMs())
    }

    @Test fun resetClearsAnActiveCooldown() {
        val cooldown = GlobalCooldownManager(now = { 500L })
        cooldown.actionSucceeded()
        cooldown.reset()
        assertTrue(cooldown.allows(GestureEvent.Screenshot))
        assertEquals(0L, cooldown.remainingMs())
    }
}
