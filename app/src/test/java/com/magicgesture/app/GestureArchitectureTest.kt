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
        assertEquals(GestureAction.FAVORITE_CURRENT, ok.action)
        assertEquals(GestureCode.G22, playPause.code)
        assertEquals(GestureAction.PLAY_PAUSE, playPause.action)
        assertFalse(gate.allows(thumbsUp, GestureFeatureConfig(thumbsUp = false)))
        assertFalse(gate.allows(ok, GestureFeatureConfig(ok = false)))
        assertFalse(gate.allows(playPause, GestureFeatureConfig(playPause = false)))
    }

    @Test fun lotusMapsHomeAndOrchidMapsRecents() {
        val lotus = requireNotNull(mappings.resolve(GestureEvent.LotusRecents)).mapping
        val orchid = requireNotNull(mappings.resolve(GestureEvent.OrchidBack)).mapping

        assertEquals(GestureCode.G14, lotus.code)
        assertEquals(GestureAction.HOME, lotus.action)
        assertEquals(GestureCode.G15, orchid.code)
        assertEquals(GestureAction.RECENTS, orchid.action)
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

    @Test fun directionFeatureGatesAreIndependentPerHomeCard() {
        val scroll = requireNotNull(mappings.resolve(GestureEvent.Swipe(true, GestureEvent.MotionSource.PALM))).mapping
        val palmLeftScroll = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.PALM))).mapping
        val indexLeftScroll = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.INDEX_FINGER))).mapping
        val indexRightScroll = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.INDEX_FINGER))).mapping

        assertFalse(gate.allows(scroll, GestureFeatureConfig(palmVerticalScroll = false)))
        assertTrue(gate.allows(scroll, GestureFeatureConfig(palmVerticalScroll = true)))
        assertFalse(gate.allows(palmLeftScroll, GestureFeatureConfig(palmLeftScroll = false)))
        assertTrue(gate.allows(palmLeftScroll, GestureFeatureConfig(palmLeftScroll = true)))
        assertFalse(gate.allows(indexLeftScroll, GestureFeatureConfig(indexLeftScroll = false)))
        assertFalse(gate.allows(indexRightScroll, GestureFeatureConfig(indexRightScroll = false)))
        assertTrue(gate.allows(indexLeftScroll, GestureFeatureConfig(indexLeftScroll = true, indexRightScroll = false)))
    }

    @Test fun allHorizontalWavesScrollLeftAndRight() {
        val left = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.PALM))).mapping
        val right = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.PALM))).mapping
        val indexLeft = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(true, GestureEvent.MotionSource.INDEX_FINGER))).mapping
        val indexRight = requireNotNull(mappings.resolve(GestureEvent.HorizontalSwipe(false, GestureEvent.MotionSource.INDEX_FINGER))).mapping

        assertEquals(GestureCode.G07, left.code)
        assertEquals(GestureAction.SCROLL_LEFT, left.action)
        assertEquals(GestureCode.G08, right.code)
        assertEquals(GestureAction.SCROLL_RIGHT, right.action)
        assertEquals(GestureCode.G09, indexLeft.code)
        assertEquals(GestureAction.SCROLL_LEFT, indexLeft.action)
        assertEquals(GestureCode.G10, indexRight.code)
        assertEquals(GestureAction.SCROLL_RIGHT, indexRight.action)
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
        assertEquals(GestureAction.RECENTS, remapped.actionFor(GestureCode.G15))
        assertEquals(GestureAction.MOVE_CURSOR, remapped.actionFor(GestureCode.G01))
    }

    @Test fun remappingOnlyCoversGesturesWithRealPipelines() {
        val manager = GestureMappingManager()
        assertTrue(manager.isRemappable(GestureCode.G02))
        assertTrue(manager.isRemappable(GestureCode.G22))
        // G26 is unbound by default but its pipeline exists, so it stays bindable.
        assertTrue(manager.isRemappable(GestureCode.G26))
        // G01 is the continuous cursor; G23 has no pipeline. G16-G19 were revived on
        // 2026-10-02 as open-app sequences, so they are remappable again.
        assertFalse(manager.isRemappable(GestureCode.G01))
        assertTrue(manager.isRemappable(GestureCode.G16))
        assertTrue(manager.isRemappable(GestureCode.G17))
        assertTrue(manager.isRemappable(GestureCode.G18))
        assertTrue(manager.isRemappable(GestureCode.G19))
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

    @Test fun g16ThroughG19DefaultToOpenAppSlotsAndResolve() {
        assertEquals(GestureAction.OPEN_APP, GestureMappingManager.defaultActionOf(GestureCode.G16))
        assertEquals(GestureAction.OPEN_APP, GestureMappingManager.defaultActionOf(GestureCode.G17))
        assertEquals(GestureAction.OPEN_APP, GestureMappingManager.defaultActionOf(GestureCode.G18))
        assertEquals(GestureAction.OPEN_APP, GestureMappingManager.defaultActionOf(GestureCode.G19))
        val mappings = GestureMappingManager()
        val events = listOf(
            GestureEvent.OpenApp(1) to GestureCode.G16,
            GestureEvent.OpenApp(2) to GestureCode.G17,
            GestureEvent.OpenApp(3) to GestureCode.G18,
            GestureEvent.OpenApp(4) to GestureCode.G19
        )
        for ((event, code) in events) {
            val mapped = mappings.resolve(event)
            assertEquals(code, mapped?.mapping?.code)
            assertEquals(GestureType.SEQUENCE, mapped?.mapping?.type)
        }
        assertTrue(mappings.isRemappable(GestureCode.G16))
        assertTrue(mappings.isRemappable(GestureCode.G19))
    }

    @Test fun g24ThroughG28DefaultToTheirSpecifiedActions() {
        assertEquals(GestureAction.BACK, GestureMappingManager.defaultActionOf(GestureCode.G24))
        assertEquals(GestureAction.NOTIFICATIONS, GestureMappingManager.defaultActionOf(GestureCode.G25))
        // G26 is deliberately unbound; users can assign any action to it themselves.
        assertNull(GestureMappingManager.defaultActionOf(GestureCode.G26))
        assertEquals(GestureAction.RECENTS, GestureMappingManager.defaultActionOf(GestureCode.G27))
        assertEquals(GestureAction.HOME, GestureMappingManager.defaultActionOf(GestureCode.G28))
    }

    @Test fun g24ThroughG28EventsResolveWithHoldTypeAndOwnFeatureGates() {
        val cases = listOf(
            GestureEvent.LeftLBack to GestureCode.G24,
            GestureEvent.LShape to GestureCode.G25,
            GestureEvent.CShape to GestureCode.G27,
            GestureEvent.LoveLock to GestureCode.G28
        )
        cases.forEach { (event, code) ->
            val mapped = requireNotNull(mappings.resolve(event))
            assertEquals(code, mapped.mapping.code)
            assertEquals(GestureType.HOLD, mapped.mapping.type)
            assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, mapped.mapping.cooldownPolicy)
            assertTrue(gate.allows(mapped.mapping, GestureFeatureConfig()))
        }
        // Each new gesture is gated by its own switch.
        assertFalse(gate.allows(requireNotNull(mappings.resolve(GestureEvent.LeftLBack)).mapping, GestureFeatureConfig(leftL = false)))
        assertFalse(gate.allows(requireNotNull(mappings.resolve(GestureEvent.LShape)).mapping, GestureFeatureConfig(lShape = false)))
        assertFalse(gate.allows(requireNotNull(mappings.resolve(GestureEvent.CShape)).mapping, GestureFeatureConfig(cShape = false)))
        assertFalse(gate.allows(requireNotNull(mappings.resolve(GestureEvent.LoveLock)).mapping, GestureFeatureConfig(loveLock = false)))
        // G26 keeps its own switch even though it has no default action.
        val claw = GestureMapping(GestureCode.G26, GestureType.HOLD, GestureAction.DRAG, CooldownPolicy.GLOBAL_AFTER_SUCCESS)
        assertTrue(gate.allows(claw, GestureFeatureConfig()))
        assertFalse(gate.allows(claw, GestureFeatureConfig(clawDrag = false)))
        // An unbound gesture stays silent without an override...
        assertNull(mappings.resolve(GestureEvent.ClawDrag(0f, 0f, 0f, 0f)))
        // ...and accepts a user override that supplies the missing default.
        val bound = GestureMappingManager(mapOf(GestureCode.G26 to GestureAction.DRAG))
        val mappedClaw = requireNotNull(bound.resolve(GestureEvent.ClawDrag(0f, 0f, 0f, 0f)))
        assertEquals(GestureCode.G26, mappedClaw.mapping.code)
        assertEquals(GestureAction.DRAG, mappedClaw.mapping.action)
        // The new codes are remappable like the rest.
        assertTrue(mappings.isRemappable(GestureCode.G24))
        assertTrue(mappings.isRemappable(GestureCode.G28))
    }

    @Test fun dragEventKeepsItsCoordinatesThroughThePipeline() {
        val bound = GestureMappingManager(mapOf(GestureCode.G26 to GestureAction.DRAG))
        val mapped = requireNotNull(bound.resolve(GestureEvent.ClawDrag(.25f, .30f, .75f, .80f)))
        assertEquals(GestureAction.DRAG, mapped.mapping.action)
        val drag = mapped.event as GestureEvent.ClawDrag
        assertEquals(.25f, drag.startX)
        assertEquals(.30f, drag.startY)
        assertEquals(.75f, drag.endX)
        assertEquals(.80f, drag.endY)
    }

    @Test fun twoFingerWavesDefaultToTrackControlAndVolume() {
        val previous = requireNotNull(mappings.resolve(GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.LEFT)))
        assertEquals(GestureCode.G29, previous.mapping.code)
        assertEquals(GestureAction.MEDIA_PREVIOUS, previous.mapping.action)
        assertEquals(GestureType.DYNAMIC, previous.mapping.type)
        assertEquals(CooldownPolicy.GLOBAL_AFTER_SUCCESS, previous.mapping.cooldownPolicy)

        val next = requireNotNull(mappings.resolve(GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.RIGHT)))
        assertEquals(GestureCode.G30, next.mapping.code)
        assertEquals(GestureAction.MEDIA_NEXT, next.mapping.action)

        val louder = requireNotNull(mappings.resolve(GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.UP)))
        assertEquals(GestureCode.G31, louder.mapping.code)
        assertEquals(GestureAction.VOLUME_UP, louder.mapping.action)

        val quieter = requireNotNull(mappings.resolve(GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.DOWN)))
        assertEquals(GestureCode.G32, quieter.mapping.code)
        assertEquals(GestureAction.VOLUME_DOWN, quieter.mapping.action)

        val heldLouder = requireNotNull(mappings.resolve(
            GestureEvent.TwoFingerVolumeHold(true, GestureEvent.VolumeHoldPhase.START)
        ))
        val heldQuieter = requireNotNull(mappings.resolve(
            GestureEvent.TwoFingerVolumeHold(false, GestureEvent.VolumeHoldPhase.TICK)
        ))
        assertEquals(GestureCode.G31, heldLouder.mapping.code)
        assertEquals(GestureAction.VOLUME_UP, heldLouder.mapping.action)
        assertEquals(GestureCode.G32, heldQuieter.mapping.code)
        assertEquals(GestureAction.VOLUME_DOWN, heldQuieter.mapping.action)

        // All four directions share one feature switch and are remappable.
        assertFalse(gate.allows(previous.mapping, GestureFeatureConfig(twoFingerMedia = false)))
        assertTrue(gate.allows(previous.mapping, GestureFeatureConfig(twoFingerMedia = true)))
        assertTrue(gate.allows(louder.mapping, GestureFeatureConfig(twoFingerMedia = true)))
        assertTrue(gate.allows(quieter.mapping, GestureFeatureConfig(twoFingerMedia = true)))
        assertTrue(mappings.isRemappable(GestureCode.G29))
        assertTrue(mappings.isRemappable(GestureCode.G30))
        assertTrue(mappings.isRemappable(GestureCode.G31))
        assertTrue(mappings.isRemappable(GestureCode.G32))

        // G33 double-tap toggles play/pause and shares the same switch.
        val toggle = requireNotNull(mappings.resolve(GestureEvent.TwoFingerDoubleTap)).mapping
        assertEquals(GestureCode.G33, toggle.code)
        assertEquals(GestureAction.PLAY_PAUSE, toggle.action)
        assertFalse(gate.allows(toggle, GestureFeatureConfig(twoFingerMedia = false)))
        assertTrue(gate.allows(toggle, GestureFeatureConfig(twoFingerMedia = true)))
        assertTrue(mappings.isRemappable(GestureCode.G33))
    }
}
