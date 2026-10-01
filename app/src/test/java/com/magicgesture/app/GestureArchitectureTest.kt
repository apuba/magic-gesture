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

    @Test fun userOverridesReplaceTheActionButKeepGestureTypeAndCooldown() {
        val remapped = GestureMappingManager(mapOf(GestureCode.G15 to GestureAction.HOME))

        val mapped = requireNotNull(remapped.resolve(GestureEvent.OrchidBack))
        assertEquals(GestureCode.G15, mapped.mapping.code)
        assertEquals(GestureAction.HOME, mapped.mapping.action)
        assertEquals(GestureType.HOLD, mapped.mapping.type)
        assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, mapped.mapping.cooldownPolicy)
        // The feature gate still follows the gesture (input side), not the remapped action.
        assertFalse(gate.allows(mapped.mapping, GestureFeatureConfig(orchidBack = false)))
        assertTrue(gate.allows(mapped.mapping, GestureFeatureConfig(orchidBack = true)))
    }

    @Test fun actionForReportsOverrideOrFactoryDefault() {
        val remapped = GestureMappingManager(mapOf(GestureCode.G07 to GestureAction.RECENTS))
        assertEquals(GestureAction.RECENTS, remapped.actionFor(GestureCode.G07))
        assertEquals(GestureAction.BACK, remapped.actionFor(GestureCode.G15))
        assertEquals(GestureAction.MOVE_CURSOR, remapped.actionFor(GestureCode.G01))
    }

    @Test fun remappingOnlyCoversGesturesWithRealPipelines() {
        val manager = GestureMappingManager()
        assertTrue(manager.isRemappable(GestureCode.G02))
        assertTrue(manager.isRemappable(GestureCode.G22))
        // G01 is the continuous cursor; G16-G19/G23 have no pipeline yet.
        assertFalse(manager.isRemappable(GestureCode.G01))
        assertFalse(manager.isRemappable(GestureCode.G16))
        assertFalse(manager.isRemappable(GestureCode.G17))
        assertFalse(manager.isRemappable(GestureCode.G18))
        assertFalse(manager.isRemappable(GestureCode.G19))
        assertFalse(manager.isRemappable(GestureCode.G23))
    }

    @Test fun overrideEqualToTheDefaultBehavesLikeNoOverride() {
        val remapped = GestureMappingManager(mapOf(GestureCode.G15 to GestureAction.BACK))
        val mapped = requireNotNull(remapped.resolve(GestureEvent.OrchidBack))
        assertEquals(GestureAction.BACK, mapped.mapping.action)
        assertEquals(GestureAction.BACK, remapped.actionFor(GestureCode.G15))
    }

    @Test fun newSystemActionsAreBindableThroughOverrides() {
        val remapped = GestureMappingManager(mapOf(
            GestureCode.G07 to GestureAction.NOTIFICATIONS,
            GestureCode.G22 to GestureAction.VOLUME_UP,
            GestureCode.G21 to GestureAction.MEDIA_NEXT
        ))

        val notifications = requireNotNull(remapped.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.PALM)))
        assertEquals(GestureAction.NOTIFICATIONS, notifications.mapping.action)
        assertEquals(GestureType.DYNAMIC, notifications.mapping.type)
        assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, notifications.mapping.cooldownPolicy)

        val volume = requireNotNull(remapped.resolve(GestureEvent.PlayPause))
        assertEquals(GestureAction.VOLUME_UP, volume.mapping.action)
        // The gesture gate still follows G22's own playPause switch.
        assertFalse(gate.allows(volume.mapping, GestureFeatureConfig(playPause = false)))

        val nextTrack = requireNotNull(remapped.resolve(GestureEvent.Ok))
        assertEquals(GestureAction.MEDIA_NEXT, nextTrack.mapping.action)
    }

    @Test fun everyActionHasUILabelsAndOutcomeMessages() {
        for (action in GestureAction.entries) {
            assertTrue(action.displayLabel().isNotBlank())
            assertTrue(action.failureMessage().isNotBlank())
            if (action != GestureAction.MOVE_CURSOR) assertTrue(action.successMessage().isNotBlank())
        }
    }
}
