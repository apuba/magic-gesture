package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalCooldownManagerTest {
    @Test fun discreteActionsAreBlockedForTwoSecondsButCursorRemainsAllowed() {
        var time = 1_000L
        val cooldown = GlobalCooldownManager(now = { time })

        cooldown.actionSucceeded()
        assertFalse(cooldown.allows(GestureEvent.Back))
        assertTrue(cooldown.allows(GestureEvent.Cursor(.5f, .5f)))
        assertTrue(cooldown.allows(GestureEvent.Feedback("识别中")))
        assertEquals(2_000L, cooldown.remainingMs())

        time = 2_999L
        assertFalse(cooldown.allows(GestureEvent.Home))
        time = 3_000L
        assertTrue(cooldown.allows(GestureEvent.Home))
    }

    @Test fun resetClearsAnActiveCooldown() {
        val cooldown = GlobalCooldownManager(now = { 500L })
        cooldown.actionSucceeded()
        cooldown.reset()
        assertTrue(cooldown.allows(GestureEvent.Screenshot))
        assertEquals(0L, cooldown.remainingMs())
    }
}
