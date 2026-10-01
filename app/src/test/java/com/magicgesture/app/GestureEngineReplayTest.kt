package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replays synthetic 21-keypoint hand frames through the recognition state machine,
 * covering poses and timing the mapping-layer tests cannot reach.
 *
 * The hand model mirrors MediaPipe landmark indices:
 * 0 wrist, 1-4 thumb, 5-8 index, 9-12 middle, 13-16 ring, 17-20 pinky.
 * Frames advance at 50ms, matching the ~20fps cap of HandPipeline.
 */
class GestureEngineReplayTest {

    private val frameMs = 50L

    // ---------------------------------------------------------------- hand model

    private enum class FingerPose { EXTENDED, FOLDED }

    private data class ThumbSpec(val p1: Point, val p2: Point, val p3: Point, val p4: Point)

    private val thumbOut = ThumbSpec(Point(.44f, .74f), Point(.35f, .70f), Point(.31f, .65f), Point(.29f, .59f))
    private val thumbUp = ThumbSpec(Point(.46f, .72f), Point(.46f, .66f), Point(.46f, .60f), Point(.46f, .52f))
    private val thumbSide = ThumbSpec(Point(.44f, .72f), Point(.41f, .68f), Point(.40f, .64f), Point(.41f, .70f))

    /** Hand pointing up, wrist at (0.5, 0.8); fingers fan outward when spread. */
    private fun baseHand(
        index: FingerPose = FingerPose.EXTENDED,
        middle: FingerPose = FingerPose.EXTENDED,
        ring: FingerPose = FingerPose.EXTENDED,
        pinky: FingerPose = FingerPose.EXTENDED,
        thumb: ThumbSpec = thumbOut,
        fan: Boolean = false,
        offsetX: Float = 0f,
        offsetY: Float = 0f
    ): List<Point> {
        fun finger(mcpX: Float, mcpY: Float, pose: FingerPose, tilt: Float): List<Point> =
            if (pose == FingerPose.EXTENDED) listOf(
                Point(mcpX, mcpY),
                Point(mcpX, mcpY - .09f),
                Point(mcpX + tilt * .5f, mcpY - .17f),
                Point(mcpX + tilt, mcpY - .25f)
            ) else listOf(
                Point(mcpX, mcpY),
                Point(mcpX, mcpY - .05f),
                Point(mcpX, mcpY - .01f),
                Point(mcpX, mcpY + .05f)
            )
        val fingers = listOf(
            finger(.40f, .60f, index, if (fan) -.04f else 0f),
            finger(.47f, .58f, middle, if (fan) -.01f else 0f),
            finger(.54f, .58f, ring, if (fan) .02f else 0f),
            finger(.61f, .60f, pinky, if (fan) .06f else 0f)
        )
        return (listOf(Point(.5f, .8f), thumb.p1, thumb.p2, thumb.p3, thumb.p4) + fingers.flatten())
            .map { Point(it.x + offsetX, it.y + offsetY) }
    }

    /** Replaces a landmark by index, returning a new frame. */
    private fun List<Point>.withLandmark(index: Int, point: Point): List<Point> = toMutableList().also { it[index] = point }

    // ---------------------------------------------------------------- poses

    /** Realistic V: middle finger splayed outward so the index/middle angle is well above 15°. */
    private fun vSign() = baseHand(index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED)
        .withLandmark(11, Point(.52f, .41f))
        .withLandmark(12, Point(.57f, .33f))

    /**
     * Neutral release pose: index and middle extended but touching, ring and pinky folded.
     * It matches no firing state machine (fingers parallel so it is not a V, not index-only,
     * not a fist, not an open palm), so every detector observes the release.
     */
    private fun restPose(): List<Point> = baseHand(index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED)
        .withLandmark(12, Point(.44f, .35f))

    private fun fistPose() = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, thumbSide
    )

    private fun thumbsUpPose() = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, thumbUp
    )

    private fun okPose(): List<Point> = baseHand(middle = FingerPose.EXTENDED, ring = FingerPose.EXTENDED, pinky = FingerPose.EXTENDED)
        .withLandmark(6, Point(.40f, .51f))
        .withLandmark(7, Point(.38f, .45f))
        .withLandmark(8, Point(.43f, .42f))
        .withLandmark(1, Point(.44f, .70f))
        .withLandmark(2, Point(.40f, .60f))
        .withLandmark(3, Point(.40f, .60f))
        .withLandmark(4, Point(.42f, .47f))

    private fun lotusPose(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.FOLDED, pinky = FingerPose.EXTENDED
    ).withLandmark(1, Point(.44f, .74f))
        .withLandmark(2, Point(.48f, .70f))
        .withLandmark(3, Point(.52f, .67f))
        .withLandmark(4, Point(.54f, .625f)) // touches folded ring tip (0.54, 0.63)

    private fun orchidPose(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.EXTENDED, pinky = FingerPose.EXTENDED
    ).withLandmark(5, Point(.40f, .60f))
        .withLandmark(6, Point(.40f, .51f))
        .withLandmark(7, Point(.38f, .45f))
        .withLandmark(8, Point(.36f, .35f))
        .withLandmark(1, Point(.44f, .70f))
        .withLandmark(2, Point(.42f, .58f))
        .withLandmark(3, Point(.44f, .47f))
        .withLandmark(4, Point(.46f, .35f)) // touches middle tip (0.47, 0.33)

    private fun fingerHeartPose(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED
    ).withLandmark(6, Point(.40f, .51f))
        .withLandmark(7, Point(.39f, .45f))
        .withLandmark(8, Point(.41f, .40f))
        .withLandmark(2, Point(.42f, .66f))
        .withLandmark(3, Point(.42f, .58f))
        .withLandmark(4, Point(.41f, .42f)) // thumb tip beside index tip

    /** Four fingers open and parallel: the closed palm used for directional waves. */
    private fun closedPalm(offsetY: Float = 0f) = baseHand(fan = false, offsetY = offsetY)

    /** Five fingers spread: the open palm that arms the screenshot sequence. */
    private fun spreadPalm() = baseHand(fan = true)

    private fun pointingIndex(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED
    )

    private fun bentIndex(): List<Point> = pointingIndex()
        .withLandmark(6, Point(.40f, .51f))
        .withLandmark(7, Point(.41f, .56f))
        .withLandmark(8, Point(.40f, .63f))

    /** Index extended almost horizontally (angle within 35 degrees), other fingers folded. */
    private fun horizontalIndex(): List<Point> = pointingIndex()
        .withLandmark(5, Point(.40f, .60f))
        .withLandmark(6, Point(.48f, .605f))
        .withLandmark(7, Point(.55f, .595f))
        .withLandmark(8, Point(.63f, .58f))

    /** Same hand after an upward flick of the fingertip. */
    private fun horizontalIndexFlicked(): List<Point> = horizontalIndex()
        .withLandmark(8, Point(.65f, .45f))

    /** Deeply curled fingers with one tip slightly farther out, so this is a claw rather than a fist. */
    private fun clawPose(): List<Point> = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED
    ).withLandmark(8, Point(.40f, .45f))
        .withLandmark(12, Point(.47f, .50f))
        .withLandmark(16, Point(.54f, .50f))
        .withLandmark(20, Point(.59f, .52f))

    /** Same claw curl viewed too far from the side: projected palm width collapses. */
    private fun sideOnClawPose(): List<Point> = clawPose().map { point ->
        Point(.5f + (point.x - .5f) * .35f, point.y)
    }

    /** Moderately curled fingers forming a broad C; wrist-distance alone can look almost extended. */
    private fun cShapePose(): List<Point> = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED
    ).withLandmark(8, Point(.43f, .46f))
        .withLandmark(12, Point(.47f, .46f))
        .withLandmark(16, Point(.51f, .46f))
        .withLandmark(20, Point(.55f, .48f))

    /** A wider, less-curled C that must remain valid without becoming an open-palm match. */
    private fun wideCShapePose(): List<Point> = cShapePose()
        .withLandmark(8, Point(.40f, .41f))
        .withLandmark(12, Point(.45f, .42f))
        .withLandmark(16, Point(.50f, .44f))
        .withLandmark(20, Point(.55f, .47f))

    /** One edge fingertip drifts sideways, as commonly happens when fingers overlap on camera. */
    private fun noisyCShapePose(): List<Point> = cShapePose()
        .withLandmark(20, Point(.63f, .49f))
        .withLandmark(4, Point(.35f, .55f))

    private fun indexCirclePose(step: Int, clockwise: Boolean): List<Point> {
        val direction = if (clockwise) 1f else -1f
        val angle = direction * (Math.PI * 2.0 * step / 24.0)
        return pointingIndex().withLandmark(
            8,
            Point(.40f + .065f * kotlin.math.cos(angle).toFloat(), .35f + .065f * kotlin.math.sin(angle).toFloat())
        )
    }

    // ---------------------------------------------------------------- replay driver

    private class Replay(features: GestureFeatureConfig = GestureFeatureConfig()) {
        private val engine = GestureEngine(movementScale = 1f, features = features)
        private var t = 0L
        val events = mutableListOf<GestureEvent>()

        fun feed(frames: Int, pose: () -> List<Point>) {
            repeat(frames) { events += engine.consume(pose(), ++t * 50L) }
        }

        fun feedPoses(poses: Iterable<List<Point>>) {
            poses.forEach { events += engine.consume(it, ++t * 50L) }
        }
    }

    private inline fun <reified T : GestureEvent> List<GestureEvent>.countOf(): Int = count { it is T }

    // ---------------------------------------------------------------- tests

    @Test fun vSignHoldsForTwoSecondsThenFiresSelfieOnceUntilReleased() {
        val r = Replay()
        r.feed(45, ::vSign)                       // 2.25s: Selfie fires at 2000ms
        assertEquals(1, r.events.countOf<GestureEvent.Selfie>())
        r.feed(45, ::vSign)                       // still holding: latched, no retrigger
        assertEquals(1, r.events.countOf<GestureEvent.Selfie>())
        r.feed(8, ::restPose)                     // release
        r.feed(45, ::vSign)                       // re-enter: fires again
        assertEquals(2, r.events.countOf<GestureEvent.Selfie>())
        // Progress feedback accompanies the hold.
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("V 字保持") })
    }

    @Test fun selfieFeatureDisabledSuppressesTheVSignPipeline() {
        val r = Replay(GestureFeatureConfig(selfie = false))
        r.feed(45, ::vSign)
        assertEquals(0, r.events.countOf<GestureEvent.Selfie>())
    }

    @Test fun indexBendClickFiresOnlyAfterStabilizeBentAndReExtend() {
        val r = Replay()
        r.feed(5, ::pointingIndex)                // 250ms stabilized and armed
        r.feed(3, ::bentIndex)                    // bend acknowledged
        r.feed(1, ::pointingIndex)                // re-extend -> Click
        val clicks = r.events.filterIsInstance<GestureEvent.Click>()
        assertEquals(1, clicks.size)
        r.feed(20, ::pointingIndex)               // holding the pose does not re-click
        assertEquals(1, r.events.filterIsInstance<GestureEvent.Click>().size)
    }

    @Test fun thumbsUpHoldFiresOnceAndRequiresRelease() {
        val r = Replay()
        r.feed(14, ::thumbsUpPose)                // 700ms hold -> ThumbsUp at 600ms
        assertEquals(1, r.events.countOf<GestureEvent.ThumbsUp>())
        assertEquals(0, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(20, ::thumbsUpPose)                // still holding: no repeat
        assertEquals(1, r.events.countOf<GestureEvent.ThumbsUp>())
        r.feed(8, ::restPose)                     // release
        r.feed(14, ::thumbsUpPose)                // re-enter: fires again
        assertEquals(2, r.events.countOf<GestureEvent.ThumbsUp>())
    }

    @Test fun fistHoldFiresPlayPauseOnceAndRequiresRelease() {
        val r = Replay()
        r.feed(20, ::fistPose)                   // 1s: below the 1.5s hold, must not fire yet
        assertEquals(0, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(15, ::fistPose)                   // 1.75s total -> PlayPause at 1.5s
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(20, ::fistPose)                   // still holding: no repeat
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(8, ::restPose)
        r.feed(35, ::fistPose)                   // re-enter: fires again
        assertEquals(2, r.events.countOf<GestureEvent.PlayPause>())
    }

    @Test fun okHoldFiresConfirmOnce() {
        val r = Replay()
        r.feed(14, ::okPose)
        assertEquals(1, r.events.countOf<GestureEvent.Ok>())
        r.feed(20, ::okPose)
        assertEquals(1, r.events.countOf<GestureEvent.Ok>())
    }

    @Test fun lotusAndOrchidHoldFireTheirOwnActionWithoutCrossTriggering() {
        val r = Replay()
        r.feed(14, ::lotusPose)
        assertEquals(1, r.events.countOf<GestureEvent.LotusRecents>())
        assertEquals(0, r.events.countOf<GestureEvent.OrchidBack>())
        r.feed(8, ::restPose)
        r.feed(14, ::orchidPose)
        assertEquals(1, r.events.countOf<GestureEvent.OrchidBack>())
        assertEquals(1, r.events.countOf<GestureEvent.LotusRecents>())
    }

    @Test fun fingerHeartHoldFiresLikeOnce() {
        // Directional features stay off so the vertical index of the heart pose
        // is not swallowed by the horizontal-index swipe state machine first.
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::fingerHeartPose)
        assertEquals(1, r.events.countOf<GestureEvent.Like>())
        r.feed(20, ::fingerHeartPose)
        assertEquals(1, r.events.countOf<GestureEvent.Like>())
    }

    @Test fun screenshotSequenceOpenFistOpenFiresScreenshotOnce() {
        val r = Replay()
        r.feed(9, ::spreadPalm)                   // 450ms: open palm confirmed
        r.feed(5, ::fistPose)                     // 250ms: fist acknowledged
        r.feed(7, ::spreadPalm)                   // 350ms: final open triggers Screenshot
        assertEquals(1, r.events.countOf<GestureEvent.Screenshot>())
    }

    @Test fun closedPalmWaveDownFiresOneSwipeAndReturnPathStaysSilent() {
        val r = Replay()
        r.feed(4) { closedPalm() }                // settle past 160ms
        var dy = 0f
        r.feed(6) { dy += .03f; closedPalm(offsetY = dy) }
        val swipes = r.events.filterIsInstance<GestureEvent.Swipe>()
        assertEquals(1, swipes.size)
        assertEquals(false, swipes[0].up)
        assertEquals(GestureEvent.MotionSource.PALM, swipes[0].source)
        // Moving back up (reverse path) must not fire the opposite swipe.
        var dyBack = dy
        r.feed(6) { dyBack -= .03f; closedPalm(offsetY = dyBack) }
        assertEquals(1, r.events.filterIsInstance<GestureEvent.Swipe>().size)
    }

    @Test fun horizontalIndexFlickUpFiresSingleIndexFingerSwipe() {
        val r = Replay()
        r.feed(4, ::horizontalIndex)              // recognized as horizontal index
        r.feed(2, ::horizontalIndexFlicked)        // upward flick
        val swipes = r.events.filterIsInstance<GestureEvent.Swipe>()
        assertEquals(1, swipes.size)
        assertEquals(true, swipes[0].up)
        assertEquals(GestureEvent.MotionSource.INDEX_FINGER, swipes[0].source)
    }

    @Test fun everyFrameEmitsExactlyOneCursorEvent() {
        val r = Replay()
        r.feed(10, ::pointingIndex)
        r.feed(10, ::vSign)
        assertEquals(20, r.events.countOf<GestureEvent.Cursor>())
    }

    /** Index and middle extended and touching, ring and pinky folded; whole hand translated. */
    private fun twoFingerHand(offsetX: Float = 0f, offsetY: Float = 0f): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED,
        ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED, offsetX = offsetX, offsetY = offsetY
    ).withLandmark(12, Point(.44f + offsetX, .35f + offsetY))

    /** Pressed fingers with a small real-device splay that exceeded the former 15-degree limit. */
    private fun slightlySplayedTwoFingerHand(offsetX: Float = 0f): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED,
        ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED, offsetX = offsetX
    ).withLandmark(11, Point(.49f + offsetX, .41f))
        .withLandmark(12, Point(.47f + offsetX, .35f))

    /** Middle fingertip is partly hidden beside the index and fails the old wrist-open test. */
    private fun occludedMiddleTwoFingerHand(offsetX: Float = 0f): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED,
        ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED, offsetX = offsetX
    ).withLandmark(10, Point(.47f + offsetX, .42f))
        .withLandmark(11, Point(.46f + offsetX, .405f))
        .withLandmark(12, Point(.45f + offsetX, .39f))

    @Test fun twoFingerSwipeLeftFiresPreviousAndRightFiresNext() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }                // settle into the pose
        var dx = 0f
        r.feed(6) { dx -= .03f; twoFingerHand(dx) }  // wave left -> previous track
        val first = r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>()
        assertEquals(1, first.size)
        assertEquals(GestureEvent.TwoFingerDirection.LEFT, first[0].direction)

        r.feed(4, ::fistPose)                        // release: re-arm needs a pose break
        r.feed(4) { twoFingerHand() }
        var dx2 = 0f
        r.feed(6) { dx2 += .03f; twoFingerHand(dx2) }  // wave right -> next track
        val both = r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>()
        assertEquals(2, both.size)
        assertEquals(GestureEvent.TwoFingerDirection.RIGHT, both[1].direction)
    }

    @Test fun twoFingerVerticalHoldStartsTicksExclusivelyAndEndsOnPoseChange() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        var dy = 0f
        r.feed(6) { dy -= .025f; twoFingerHand(0f, dy) }
        val cursorCountAtStart = r.events.countOf<GestureEvent.Cursor>()
        r.feed(10) { twoFingerHand(0f, dy) }
        assertEquals(cursorCountAtStart, r.events.countOf<GestureEvent.Cursor>())
        r.feed(1, ::fistPose)

        val holds = r.events.filterIsInstance<GestureEvent.TwoFingerVolumeHold>()
        assertEquals(GestureEvent.VolumeHoldPhase.START, holds.first().phase)
        assertTrue(holds.first().raise)
        assertTrue(holds.any { it.phase == GestureEvent.VolumeHoldPhase.TICK })
        assertEquals(GestureEvent.VolumeHoldPhase.END, holds.last().phase)
    }

    @Test fun twoFingerDoubleTapTogglesPlayPause() {
        val r = Replay()
        r.feed(6) { twoFingerHand() }   // hold the pose (300ms)
        r.feed(2, ::fistPose)           // bend both fingers
        r.feed(6) { twoFingerHand() }   // re-extend and hold -> tap 1
        r.feed(2, ::fistPose)           // bend again
        r.feed(6) { twoFingerHand() }   // re-extend -> tap 2 fires play/pause
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
    }

    @Test fun aQuickRealWorldDoubleTapRhythmAlsoFires() {
        val r = Replay()
        r.feed(3) { twoFingerHand() }   // ~150ms per phase: real users tap fast
        r.feed(1, ::fistPose)
        r.feed(3) { twoFingerHand() }
        r.feed(1, ::fistPose)
        r.feed(3) { twoFingerHand() }
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
    }

    @Test fun aSingleTwoFingerBendNeverTogglesPlayPause() {
        val r = Replay()
        r.feed(6) { twoFingerHand() }
        r.feed(2, ::fistPose)           // one bend only
        r.feed(6) { twoFingerHand() }
        r.feed(20, ::fistPose)          // then the hand leaves for good
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
    }

    @Test fun holdingTheTwoFingerPoseAloneNeverFires() {
        val r = Replay()
        r.feed(50, ::restPose)                       // restPose IS the two-finger pose; 2.5s > selfie hold
        assertEquals(0, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
        // Pressed-together fingers (gap slightly over the old 0.28 threshold, parallel) must
        // never be mistaken for a spread V and start the selfie countdown.
        assertEquals(0, r.events.countOf<GestureEvent.Selfie>())
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("两指并拢已识别") })
    }

    @Test fun slightlySplayedTwoFingerPoseDoesNotArmTheSingleIndexPipeline() {
        val r = Replay()
        r.feed(5, ::slightlySplayedTwoFingerHand)
        var dx = 0f
        r.feed(6) { dx += .03f; slightlySplayedTwoFingerHand(dx) }
        assertEquals(1, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
        assertEquals(0, r.events.countOf<GestureEvent.Click>())
        assertEquals(0, r.events.filterIsInstance<GestureEvent.HorizontalSwipe>().size)
    }

    @Test fun occludedMiddleFingerStillTracksAsTwoFingersInsteadOfOneIndex() {
        val r = Replay()
        r.feed(5, ::occludedMiddleTwoFingerHand)
        var dx = 0f
        r.feed(6) { dx -= .03f; occludedMiddleTwoFingerHand(dx) }
        val swipes = r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>()
        assertEquals(1, swipes.size)
        assertEquals(GestureEvent.TwoFingerDirection.LEFT, swipes.single().direction)
        assertEquals(0, r.events.countOf<GestureEvent.Click>())
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("两指并拢已识别") })
    }

    @Test fun clawAndCShapeFireTheirOwnEventsOnTolerantThreeFingerGeometry() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::clawPose)
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("拖动已开始") })
        assertEquals(0, r.events.countOf<GestureEvent.CShape>())

        r.feed(8, ::spreadPalm)
        r.feed(14, ::cShapePose)
        assertEquals(1, r.events.countOf<GestureEvent.CShape>())
    }

    @Test fun clawRequiresFrontFacingPalmAndSeparatedFingers() {
        val side = Replay(GestureFeatureConfig(scroll = false))
        side.feed(14, ::sideOnClawPose)
        assertEquals(0, side.events.countOf<GestureEvent.ClawDrag>())
        assertEquals(0, side.events.countOf<GestureEvent.CShape>())

        val grouped = Replay(GestureFeatureConfig(scroll = false))
        grouped.feed(14, ::cShapePose)
        assertEquals(0, grouped.events.countOf<GestureEvent.ClawDrag>())
        assertEquals(1, grouped.events.countOf<GestureEvent.CShape>())
    }

    @Test fun widerCShapeStillFiresButSpreadPalmDoesNot() {
        val wide = Replay(GestureFeatureConfig(scroll = false))
        wide.feed(14, ::wideCShapePose)
        assertEquals(1, wide.events.countOf<GestureEvent.CShape>())

        val open = Replay(GestureFeatureConfig(scroll = false))
        open.feed(14, ::spreadPalm)
        assertEquals(0, open.events.countOf<GestureEvent.CShape>())
    }

    @Test fun cShapeToleratesOneNoisyFingertipAndACloserThumbOpening() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::noisyCShapePose)
        assertEquals(1, r.events.countOf<GestureEvent.CShape>())
        assertEquals(0, r.events.countOf<GestureEvent.ClawDrag>())
    }

    @Test fun clockwiseAndCounterClockwiseIndexCirclesStartContinuousVolumeSessions() {
        val clockwise = Replay(GestureFeatureConfig(scroll = false))
        clockwise.feedPoses((0..36).map { indexCirclePose(it % 24, true) })
        clockwise.feed(1, ::fistPose)
        val up = clockwise.events.filterIsInstance<GestureEvent.CircleVolume>()
        assertTrue(up.any { it.raise && it.phase == GestureEvent.VolumeHoldPhase.START })
        assertTrue(up.any { it.raise && it.phase == GestureEvent.VolumeHoldPhase.TICK })
        assertEquals(GestureEvent.VolumeHoldPhase.END, up.last().phase)

        val counterClockwise = Replay(GestureFeatureConfig(scroll = false))
        counterClockwise.feedPoses((0..36).map { indexCirclePose(it % 24, false) })
        counterClockwise.feed(1, ::fistPose)
        val down = counterClockwise.events.filterIsInstance<GestureEvent.CircleVolume>()
        assertTrue(down.any { !it.raise && it.phase == GestureEvent.VolumeHoldPhase.START })
        assertTrue(down.any { !it.raise && it.phase == GestureEvent.VolumeHoldPhase.TICK })
        assertEquals(GestureEvent.VolumeHoldPhase.END, down.last().phase)
    }
}
