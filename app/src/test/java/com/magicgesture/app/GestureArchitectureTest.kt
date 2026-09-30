package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GestureArchitectureTest {
    private val mappings = GestureMappingManager()
    private val gate = GestureFeatureGate()

    @Test fun cursorUsesContinuousMappingWithoutGlobalCooldown() {
        val mapped = requireNotNull(mappings.resolve(GestureEvent.Cursor(.25f, .75f)))
        assertEquals(GestureCode.G01, mapped.mapping.code)
        assertEquals(GestureAction.MOVE_CURSOR, mapped.mapping.action)
        assertEquals(CooldownPolicy.NONE, mapped.mapping.cooldownPolicy)
        assertTrue(gate.allows(mapped.mapping, GestureFeatureConfig(cursor = true)))
        assertFalse(gate.allows(mapped.mapping, GestureFeatureConfig(cursor = false)))
    }

    @Test fun clickUsesDiscreteMappingAndStartsCooldownOnlyAfterSuccess() {
        val mapped = requireNotNull(mappings.resolve(GestureEvent.Click(.4f, .6f)))
        assertEquals(GestureCode.G02, mapped.mapping.code)
        assertEquals(GestureAction.CLICK, mapped.mapping.action)
        assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, mapped.mapping.cooldownPolicy)
        assertTrue(gate.allows(mapped.mapping, GestureFeatureConfig(click = true)))
        assertFalse(gate.allows(mapped.mapping, GestureFeatureConfig(click = false)))
    }

    @Test fun unmigratedEventsRemainOnTheCompatibilityPath() {
        assertNull(mappings.resolve(GestureEvent.Back))
        assertNull(mappings.resolve(GestureEvent.Recents))
    }

    @Test fun likeAndScreenshotUseTheirOwnFeatureGates() {
        val like = requireNotNull(mappings.resolve(GestureEvent.Like)).mapping
        val screenshot = requireNotNull(mappings.resolve(GestureEvent.Screenshot)).mapping

        assertEquals(GestureCode.G12, like.code)
        assertEquals(GestureAction.LIKE, like.action)
        assertEquals(GestureCode.G13, screenshot.code)
        assertEquals(GestureAction.SCREENSHOT, screenshot.action)
        assertFalse(gate.allows(like, GestureFeatureConfig(like = false)))
        assertFalse(gate.allows(screenshot, GestureFeatureConfig(screenshot = false)))
        assertTrue(gate.allows(like, GestureFeatureConfig(like = true)))
        assertTrue(gate.allows(screenshot, GestureFeatureConfig(screenshot = true)))
    }

    @Test fun selfieIsG11AndUsesTheSelfieFeatureGate() {
        val selfie = requireNotNull(mappings.resolve(GestureEvent.Selfie)).mapping

        assertEquals(GestureCode.G11, selfie.code)
        assertEquals(GestureAction.SELFIE, selfie.action)
        assertEquals(GestureType.HOLD, selfie.type)
        assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, selfie.cooldownPolicy)
        assertFalse(gate.allows(selfie, GestureFeatureConfig(selfie = false)))
        assertTrue(gate.allows(selfie, GestureFeatureConfig(selfie = true)))
    }

    @Test fun p2StaticGesturesMapToG20ThroughG22() {
        val thumbsUp = requireNotNull(mappings.resolve(GestureEvent.ThumbsUp)).mapping
        val ok = requireNotNull(mappings.resolve(GestureEvent.Ok)).mapping
        val playPause = requireNotNull(mappings.resolve(GestureEvent.PlayPause)).mapping

        assertEquals(GestureCode.G20, thumbsUp.code)
        assertEquals(GestureAction.THUMBS_UP_LIKE, thumbsUp.action)
        assertEquals(GestureCode.G21, ok.code)
        assertEquals(GestureAction.CONFIRM, ok.action)
        assertEquals(GestureCode.G22, playPause.code)
        assertEquals(GestureAction.PLAY_PAUSE, playPause.action)
        assertFalse(gate.allows(thumbsUp, GestureFeatureConfig(thumbsUp = false)))
        assertFalse(gate.allows(ok, GestureFeatureConfig(ok = false)))
        assertFalse(gate.allows(playPause, GestureFeatureConfig(playPause = false)))
    }

    @Test fun lotusAndOrchidMapToRecentsAndBack() {
        val lotus = requireNotNull(mappings.resolve(GestureEvent.LotusRecents)).mapping
        val orchid = requireNotNull(mappings.resolve(GestureEvent.OrchidBack)).mapping

        assertEquals(GestureCode.G14, lotus.code)
        assertEquals(GestureAction.RECENTS, lotus.action)
        assertEquals(GestureCode.G15, orchid.code)
        assertEquals(GestureAction.BACK, orchid.action)
        assertFalse(gate.allows(lotus, GestureFeatureConfig(lotusRecents = false)))
        assertFalse(gate.allows(orchid, GestureFeatureConfig(orchidBack = false)))
    }

    @Test fun directionGestureSourceAndDirectionResolveToG03ThroughG10() {
        val cases = listOf(
            GestureEvent.Swipe(true, GestureEvent.MotionSource.INDEX_FINGER) to GestureCode.G03,
            GestureEvent.Swipe(false, GestureEvent.MotionSource.INDEX_FINGER) to GestureCode.G04,
            GestureEvent.Swipe(true, GestureEvent.MotionSource.PALM) to GestureCode.G05,
            GestureEvent.Swipe(false, GestureEvent.MotionSource.PALM) to GestureCode.G06,
            GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.PALM) to GestureCode.G07,
            GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.PALM) to GestureCode.G08,
            GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.INDEX_FINGER) to GestureCode.G09,
            GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.INDEX_FINGER) to GestureCode.G10
        )

        cases.forEach { (event, expectedCode) ->
            val mapped = requireNotNull(mappings.resolve(event))
            assertEquals(expectedCode, mapped.mapping.code)
            assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, mapped.mapping.cooldownPolicy)
        }
    }

    @Test fun directionFeatureGatesUseExistingUserSettings() {
        val scroll = requireNotNull(mappings.resolve(GestureEvent.Swipe(true, GestureEvent.MotionSource.PALM))).mapping
        val back = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.PALM))).mapping
        val home = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.INDEX_FINGER))).mapping

        assertFalse(gate.allows(scroll, GestureFeatureConfig(scroll = false)))
        assertFalse(gate.allows(back, GestureFeatureConfig(back = false)))
        assertFalse(gate.allows(home, GestureFeatureConfig(home = false)))
        assertTrue(gate.allows(scroll, GestureFeatureConfig(scroll = true)))
    }
}
