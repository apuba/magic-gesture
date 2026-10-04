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
    // A "666" hand really does stick the thumb out; the generic thumbOut above only clears the
    // folded-thumb test by 7%, and after the pinky pose was allowed a resting thumb the two shapes
    // became the same frame. This one keeps G34 distinguishable from G23 the way a hand is.
    private val thumbStretchedOut = ThumbSpec(Point(.44f, .74f), Point(.36f, .70f), Point(.31f, .64f), Point(.26f, .54f))

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

    /** `steps` frames walking every landmark from one pose to the other, endpoints included. */
    private fun morph(from: List<Point>, to: List<Point>, steps: Int): List<List<Point>> =
        (0 until steps).map { i ->
            val k = i / (steps - 1f)
            from.indices.map { j -> Point(from[j].x + (to[j].x - from[j].x) * k, from[j].y + (to[j].y - from[j].y) * k) }
        }

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

    /**
     * OK with the middle, ring and pinky reading as curled: the wobble frame that drops
     * [okPose] while the thumb/index contact stays well inside the finger-heart distance.
     */
    private fun okPoseWobble(): List<Point> = okPose()
        .withLandmark(20, Point(.61f, .49f))

    /**
     * G12 finger heart after the 2026-10-03 redefinition: the thumb tip presses onto the index
     * finger's first joint (PIP) and the two fingers cross, while the other three curl.
     */
    private fun fingerHeartPose(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED
    ).withLandmark(6, Point(.40f, .51f))
        .withLandmark(7, Point(.39f, .45f))
        .withLandmark(8, Point(.41f, .40f))
        .withLandmark(2, Point(.42f, .66f))
        .withLandmark(3, Point(.42f, .58f))
        .withLandmark(4, Point(.41f, .50f)) // thumb tip pressing the index PIP

    /** The same pose with the thumb crossing nearer the index tip instead of the joint. */
    private fun fingerHeartTipPose(): List<Point> = fingerHeartPose()
        .withLandmark(4, Point(.41f, .42f))

    /** Four fingers open and parallel: the closed palm used for directional waves. */
    private fun closedPalm(offsetY: Float = 0f) = baseHand(fan = false, offsetY = offsetY)

    /** Five fingers spread: the open palm that arms the screenshot sequence. */
    private fun spreadPalm() = baseHand(fan = true)

    /** Thumb resting against the palm ridge (thumbOpen == false), used by the open-app folds. */
    private val thumbFolded = ThumbSpec(Point(.44f, .74f), Point(.42f, .70f), Point(.42f, .66f), Point(.41f, .63f))

    // G16-G19: open palm, then fold to exactly N fingers with the thumb closed.
    private fun foldedIndexOnly() = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED,
        pinky = FingerPose.FOLDED, thumb = thumbFolded
    )

    private fun foldedTwoFingers() = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.FOLDED,
        pinky = FingerPose.FOLDED, thumb = thumbFolded
    )

    private fun foldedThreeFingers() = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED, ring = FingerPose.EXTENDED,
        pinky = FingerPose.FOLDED, thumb = thumbFolded
    )

    private fun foldedFourFingers() = baseHand(fan = true, thumb = thumbFolded)

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

    /** G24 gun: index points left and the raised thumb stays behind the index PIP. */
    private fun gunPose(): List<Point> = pointingIndex()
        .withLandmark(5, Point(.40f, .60f))
        .withLandmark(6, Point(.32f, .60f))
        .withLandmark(7, Point(.25f, .60f))
        .withLandmark(8, Point(.17f, .60f))
        .withLandmark(1, thumbUp.p1)
        .withLandmark(2, thumbUp.p2)
        .withLandmark(3, thumbUp.p3)
        .withLandmark(4, thumbUp.p4)

    /** Thumb is behind PIP toward the wrist, but does not need to pass behind MCP. */
    private fun gunThumbBetweenPipAndMcp(): List<Point> = gunPose()
        .withLandmark(4, Point(.38f, .52f))

    /** Thumb leans too close to the left-pointing index: included angle is below 45 degrees. */
    private fun gunTooAcute(): List<Point> = gunPose()
        .withLandmark(2, Point(.46f, .68f))
        .withLandmark(4, Point(.30f, .59f))

    /** Thumb opens past the approved L range: included angle is above 90 degrees. */
    private fun gunTooObtuse(): List<Point> = gunPose()
        .withLandmark(2, Point(.40f, .68f))
        .withLandmark(4, Point(.56f, .59f))

    /** Heart-like contact: thumb presses the index PIP, so this must never be the gun. */
    private fun horizontalFingerHeartPose(): List<Point> = gunPose()
        .withLandmark(2, Point(.36f, .70f))
        .withLandmark(3, Point(.35f, .65f))
        .withLandmark(4, Point(.33f, .595f))

    /** G34 "666": thumb and pinky out, index/middle/ring curled. */
    private fun six666Pose(): List<Point> = baseHand(
        index = FingerPose.FOLDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED, pinky = FingerPose.EXTENDED,
        thumb = thumbStretchedOut
    )

    /** G23: pinky alone; folded thumb keeps it distinct from G34 "666". */
    private fun pinkyOnlyPose(): List<Point> = baseHand(
        index = FingerPose.FOLDED, middle = FingerPose.FOLDED, ring = FingerPose.FOLDED,
        pinky = FingerPose.EXTENDED, thumb = thumbFolded
    )

    /** Deeply curled fingers with one tip slightly farther out, so this is a claw rather than a fist. */
    private fun clawPose(): List<Point> = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED
    ).withLandmark(8, Point(.40f, .45f))
        .withLandmark(12, Point(.47f, .50f))
        .withLandmark(16, Point(.54f, .50f))
        .withLandmark(20, Point(.59f, .52f))

    /** The same claw translated across the frame: what the camera sees while the palm is moved. */
    private fun clawPoseShiftedBy(dx: Float, dy: Float): List<Point> =
        clawPose().map { Point(it.x + dx, it.y + dy) }

    /**
     * Real-device claw: every fingertip curls back past its own PIP — exactly the frame that
     * satisfied the plain fist test, blocked the claw and fired play/pause instead — while the
     * tips stay splayed and off the palm.
     */
    private fun deepClawPose(): List<Point> = baseHand(
        FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED, FingerPose.FOLDED
    ).withLandmark(8, Point(.40f, .56f))
        .withLandmark(12, Point(.47f, .54f))
        .withLandmark(16, Point(.54f, .54f))
        .withLandmark(20, Point(.59f, .56f))
        .withLandmark(4, Point(.27f, .62f)) // thumb held clear of the index tip

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

    /**
     * A slightly wider C that still stays inside the narrowed curl envelope. The old
     * near-extended wide pose no longer fires since the 2026-10-01 real-device tightening
     * (an open palm must never match the C).
     */
    private fun wideCShapePose(): List<Point> = cShapePose()
        .withLandmark(8, Point(.42f, .46f))
        .withLandmark(12, Point(.465f, .46f))
        .withLandmark(16, Point(.515f, .46f))
        .withLandmark(20, Point(.555f, .48f))

    /** One edge fingertip drifts sideways, as commonly happens when fingers overlap on camera. */
    private fun noisyCShapePose(): List<Point> = cShapePose()
        .withLandmark(20, Point(.63f, .49f))
        .withLandmark(4, Point(.35f, .55f))

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

        /** The hand leaves the frame: what HandPipeline does when no hand is detected anymore. */
        fun loseHand(frames: Int = 8) {
            repeat(frames) { events += engine.lost(++t * 50L) }
        }

        /** Applies a new sensitivity live, exactly what the preference listener does. */
        fun setSensitivity(scale: Float) = engine.updateMovementScale(scale)

        /**
         * The post-action cooldown: the service freezes recognition for its whole duration and then
         * resumes it. Nothing observes the hand while frozen.
         */
        fun cooldown() { engine.stop(); engine.resume() }
    }

    private inline fun <reified T : GestureEvent> List<GestureEvent>.countOf(): Int = count { it is T }

    @Test fun fingerHeartFiresLikeWhileIndexScrollsAreEnabled() {
        val r = Replay()
        r.feed(20, ::fingerHeartPose)
        assertEquals(1, r.events.countOf<GestureEvent.Like>())
        assertEquals(0, r.events.count { it is GestureEvent.Feedback && it.message.startsWith("竖直食指") })
    }

    @Test fun verticalIndexStillStartsHorizontalSwipeWhenThumbIsClear() {
        val r = Replay(GestureFeatureConfig(indexLeftScroll = true, indexRightScroll = true))
        r.feed(4, ::pointingIndex)
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("竖直食指") })
    }

    // G09/G10 share one hand movement with cursor steering (G01), so they ship off; see the notes
    // on INDEX_HORIZONTAL_SCROLL_DEFAULT. A vertical index must stay silent until they are switched on.
    @Test fun verticalIndexStaysSilentWhileIndexWavesAreOff() {
        val r = Replay()
        r.feed(4, ::pointingIndex)
        assertTrue(r.events.none { it is GestureEvent.Feedback && it.message.startsWith("竖直食指") })
    }

    // ---------------------------------------------------------------- tests

    @Test fun vSignHoldsForTwoSecondsThenFiresSelfieOnceUntilReleased() {
        val r = Replay()
        r.feed(45, ::vSign)                       // 2.25s: Selfie fires at 2000ms
        assertEquals(1, r.events.countOf<GestureEvent.Selfie>())
        r.feed(45, ::vSign)                       // still holding: latched, no retrigger
        assertEquals(1, r.events.countOf<GestureEvent.Selfie>())
        r.feed(8, ::restPose)                     // release (the first frame back hands control to the
        r.feed(45, ::vSign)                       // two-finger machine, so V restarts one frame late)
        assertEquals(2, r.events.countOf<GestureEvent.Selfie>())
        // Progress feedback accompanies the hold.
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("V 手势保持") })
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
        // The shape changed once when re-entering, so the hold starts one settle window later.
        r.feed(17, ::thumbsUpPose)                // re-enter: fires again
        assertEquals(2, r.events.countOf<GestureEvent.ThumbsUp>())
    }

    @Test fun fistHoldFiresPlayPauseOnceAndRequiresRelease() {
        val r = Replay()
        r.feed(20, ::fistPose)                   // 950ms elapsed: below the 1s hold
        assertEquals(0, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(2, ::fistPose)                    // 1.05s elapsed -> PlayPause at 1s
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(20, ::fistPose)                   // still holding: no repeat
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        // The fist has to stay gone for the whole 800ms grace before the hold arms again.
        r.feed(17, ::restPose)
        r.feed(25, ::fistPose)                   // re-enter: one settle window, then fires again
        assertEquals(2, r.events.countOf<GestureEvent.PlayPause>())
    }

    /**
     * 2026-10-03 real-device complaint: play/pause fired again every cooldown while the fist was
     * simply held up. The cooldown resets the engine, and that used to clear the fired lock even
     * though the hand had never let go. One deliberate fist must act once however long it is held.
     */
    @Test fun fistKeptThroughCooldownFiresPlayPauseOnce() {
        val r = Replay()
        r.feed(22, ::fistPose)                   // 1.1s -> PlayPause
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.cooldown()
        r.feed(60, ::fistPose)                   // still up after the cooldown: stays silent
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.cooldown()
        r.feed(60, ::fistPose)                   // and through a second cooldown too
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(17, ::restPose)                   // 850ms gone: a real release
        r.feed(25, ::fistPose)
        assertEquals(2, r.events.countOf<GestureEvent.PlayPause>())
    }

    /** Same lock as the fist: a pinky held through the cooldown must toggle mute exactly once. */
    @Test fun pinkyKeptThroughCooldownMutesOnce() {
        val r = Replay()
        r.feed(22, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.cooldown()
        r.feed(60, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.feed(17, ::restPose)
        r.feed(25, ::pinkyOnlyPose)
        assertEquals(2, r.events.countOf<GestureEvent.PinkyMute>())
    }

    /**
     * The hand leaving the frame is the clearest possible release. Lowering the hand out of shot
     * after toggling play/pause must free the hold, or the fist can never act again.
     */
    @Test fun fistRearmsAfterTheHandLeavesTheFrame() {
        val r = Replay()
        r.feed(22, ::fistPose)
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.loseHand(16)                           // hand dropped out of shot: let go
        r.feed(22, ::fistPose)                   // fist again: must act
        assertEquals(2, r.events.countOf<GestureEvent.PlayPause>())
    }

    /**
     * 2026-10-04 real-device finding: tracking blinks out for a couple of frames right after the
     * post-action cooldown, and reading that as "the hand is gone" freed the lock every single
     * time — play/pause then fired again for a fist that was never lowered.
     */
    @Test fun aBriefTrackingGapDoesNotFreeTheFist() {
        val r = Replay()
        r.feed(22, ::fistPose)
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.loseHand(8)                            // 400ms gap: tracking blinked, hand still up
        r.feed(40, ::fistPose)                   // still holding: must stay silent
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
    }

    /**
     * 2026-10-04 real-device finding: the pose reads as gone for a frame or two while the hand is
     * still held up, and the first frame after a cooldown is the noisiest of all. Freeing the hold
     * on that made play/pause and mute retrigger every single cooldown — the release has to be a
     * pose that stays gone, never a single dropped frame.
     */
    @Test fun aDroppedFrameAfterCooldownDoesNotRetriggerTheFist() {
        val r = Replay()
        r.feed(22, ::fistPose)
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.cooldown()
        r.feed(2, ::restPose)                    // 100ms of landmark noise, hand still up
        r.feed(40, ::fistPose)                   // still holding: must stay silent
        assertEquals(1, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(17, ::restPose)                   // 850ms gone: a real release
        r.feed(25, ::fistPose)
        assertEquals(2, r.events.countOf<GestureEvent.PlayPause>())
    }

    /** Same for the pinky: noise right after a cooldown must not toggle mute a second time. */
    @Test fun aDroppedFrameAfterCooldownDoesNotRetriggerThePinky() {
        val r = Replay()
        r.feed(22, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.cooldown()
        r.feed(2, ::restPose)
        r.feed(40, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
    }

    /**
     * Letting go while recognition is frozen cannot be seen, so the hold stays locked and the
     * release clock starts on the first frame back, from when the pose must simply stay gone.
     */
    @Test fun releasingTheHandDuringCooldownRearmsTheHoldAfterTheGrace() {
        val r = Replay()
        r.feed(22, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.cooldown()                             // hand lowered while frozen
        r.feed(17, ::restPose)                   // 850ms gone after resuming: release done
        r.feed(22, ::pinkyOnlyPose)
        assertEquals(2, r.events.countOf<GestureEvent.PinkyMute>())
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
        // Put the hand down between the two poses, as anyone switching gestures really does, so the
        // next pose starts from a clean engine instead of inheriting a sequence still in flight.
        r.feed(8, ::restPose)
        r.loseHand(16)
        r.feed(22, ::orchidPose)
        assertEquals(1, r.events.countOf<GestureEvent.OrchidBack>())
        assertEquals(1, r.events.countOf<GestureEvent.LotusRecents>())
    }

    @Test fun gunRequiresThumbIndexAngleBetweenFortyFiveAndNinetyDegrees() {
        val valid = Replay()
        valid.feed(14, ::gunPose)
        assertEquals(1, valid.events.countOf<GestureEvent.LeftLBack>())
        assertEquals(0, valid.events.countOf<GestureEvent.Like>())

        val tooAcute = Replay()
        tooAcute.feed(20, ::gunTooAcute)
        assertEquals(0, tooAcute.events.countOf<GestureEvent.LeftLBack>())

        val tooObtuse = Replay()
        tooObtuse.feed(20, ::gunTooObtuse)
        assertEquals(0, tooObtuse.events.countOf<GestureEvent.LeftLBack>())
    }

    @Test fun horizontalFingerHeartNeverFiresGunBack() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::horizontalFingerHeartPose)
        assertEquals(0, r.events.countOf<GestureEvent.LeftLBack>())
        assertEquals(1, r.events.countOf<GestureEvent.Like>())
    }

    @Test fun gunAcceptsThumbBehindPipWithoutRequiringItBehindMcp() {
        val r = Replay()
        r.feed(14, ::gunThumbBetweenPipAndMcp)
        assertEquals(1, r.events.countOf<GestureEvent.LeftLBack>())
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
    }

    @Test fun cleanOkHoldFiresOkWithoutLiking() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::okPose)
        assertEquals(1, r.events.countOf<GestureEvent.Ok>())
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
    }

    /**
     * OK is the same thumb/index contact as the finger heart, only with three fingers extended.
     * Frames where those fingers wobble below the extension threshold must never let the heart
     * state machine accumulate a like — that was firing "已点赞" instead of the OK action.
     */
    @Test fun okPoseWobbleDoesNotTurnIntoALike() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        // Two consecutive wobble frames are needed for the heart to get a frame at all: the
        // first one is still swallowed while the OK hold state machine resets itself.
        repeat(12) {
            r.feed(1, ::okPose)
            r.feed(2, ::okPoseWobble)
        }
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
    }

    @Test fun fingerHeartHeldNearTheTipAlsoFiresLike() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(14, ::fingerHeartTipPose)
        assertEquals(1, r.events.countOf<GestureEvent.Like>())
        // Contact at the tip is a heart as well, so the gun must not read it as Back.
        assertEquals(0, r.events.countOf<GestureEvent.LeftLBack>())
    }

    /** A closed fist also parks the thumb on the index joint; it must never like anything. */
    @Test fun fistDoesNotBecomeFingerHeart() {
        val r = Replay(GestureFeatureConfig(scroll = false))
        r.feed(20, ::fistPose)
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
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

    /** The pinky keeps 666 clear of the thumbs-up; the curled index keeps it clear of the love pose. */
    @Test fun six666HoldFiresOnceWithoutTriggeringThumbUpOrLove() {
        val r = Replay()
        r.feed(14, ::six666Pose)
        assertEquals(1, r.events.countOf<GestureEvent.Six666>())
        assertEquals(0, r.events.countOf<GestureEvent.ThumbsUp>())
        assertEquals(0, r.events.countOf<GestureEvent.LoveLock>())
        assertEquals(0, r.events.countOf<GestureEvent.PlayPause>())
        r.feed(20, ::six666Pose)
        assertEquals(1, r.events.countOf<GestureEvent.Six666>())
    }

    /** G35: index and middle together reaching up, thumb stretched sideways, ring and pinky curled. */
    private fun twoFingerUpPose(): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED,
        ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED, thumb = thumbSide
    )

    @Test fun twoFingerUpHoldFiresOnceAfter1000msAndRequiresRelease() {
        val r = Replay()
        r.feed(19, ::twoFingerUpPose)             // 0.95s elapsed: below the 1s hold
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerUp>())
        r.feed(2, ::twoFingerUpPose)              // 1.05s elapsed -> fires once
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerUp>())
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("双指枪·竖向保持") })
        r.feed(20, ::twoFingerUpPose)             // still holding: no repeat
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerUp>())
        r.feed(8, ::restPose)                     // release
        r.feed(31, ::twoFingerUpPose)             // re-enter: fires again
        assertEquals(2, r.events.countOf<GestureEvent.TwoFingerUp>())
        // Held still, it must not be read as the V countdown or the two-finger media chain.
        assertEquals(0, r.events.countOf<GestureEvent.Selfie>())
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerSwipe>())
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerVolumeHold>())
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
    }

    /** The plain two-finger pose (thumb not stretched sideways) belongs to media control only. */
    @Test fun plainTwoFingerPoseDoesNotScrollUp() {
        val r = Replay()
        r.feed(40, ::restPose)
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerUp>())
    }

    @Test fun twoFingerUpFeatureDisabledSuppressesThePipeline() {
        val r = Replay(GestureFeatureConfig(twoFingerUp = false))
        r.feed(40, ::twoFingerUpPose)
        assertEquals(0, r.events.countOf<GestureEvent.TwoFingerUp>())
    }

    @Test fun pinkyOnlyHoldFiresMuteOnceAndRequiresRelease() {
        val r = Replay()
        r.feed(20, ::pinkyOnlyPose)                   // 950ms elapsed: below the 1s hold
        assertEquals(0, r.events.countOf<GestureEvent.PinkyMute>())
        r.feed(2, ::pinkyOnlyPose)                    // 1.05s elapsed: fires once
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        assertEquals(0, r.events.countOf<GestureEvent.Six666>())
        assertEquals(0, r.events.countOf<GestureEvent.LoveLock>())
        assertEquals(0, r.events.countOf<GestureEvent.Like>())
        r.feed(20, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        // The hand has to stay out of the pose for the whole 800ms grace before the hold arms again.
        r.feed(17, ::restPose)
        r.feed(25, ::pinkyOnlyPose)
        assertEquals(2, r.events.countOf<GestureEvent.PinkyMute>())
    }

    /**
     * Lowering the hand makes the pinky pose blink off for a moment before it is really gone. That
     * flicker must not re-arm the hold, otherwise one deliberate pinky toggles mute twice.
     */
    @Test fun pinkyFlickerAfterFiringDoesNotToggleMuteTwice() {
        val r = Replay()
        r.feed(22, ::pinkyOnlyPose)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.feed(10, ::restPose)                        // 500ms gone: inside the 800ms grace
        r.feed(22, ::pinkyOnlyPose)                   // pose returns and is held past 1s again
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
        r.feed(17, ::restPose)                        // 850ms gone: a real release
        r.feed(25, ::pinkyOnlyPose)
        assertEquals(2, r.events.countOf<GestureEvent.PinkyMute>())
    }

    /**
     * Acceptance issue 6 (2026-10-04): a hand that merely strikes another gesture's shape on its way
     * somewhere else used to fire that gesture. Sweeping the hand back and forth keeps it inside the
     * pinky pose for a frame at a time, which must never arm the hold — only a shape that comes to
     * rest is a shape the user is holding.
     */
    @Test fun aHandThatKeepsReshapingNeverFiresThePoseItPassesThrough() {
        val r = Replay()
        val sweep = morph(restPose(), pinkyOnlyPose(), 4)      // ~4 frames per fold: real pace at 20fps
        repeat(6) { r.feedPoses(sweep) }                        // 24 frames of continuous reshaping
        assertEquals(0, r.events.countOf<GestureEvent.PinkyMute>())
        r.feed(25, ::pinkyOnlyPose)                             // once it rests, the same pose fires
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
    }

    /**
     * The gate reads the shape relative to the wrist, so carrying the same pose across the frame —
     * what every real hand does while being tracked — must not cost a settle window.
     */
    @Test fun carryingAPoseAcrossTheFrameStillFiresIt() {
        val r = Replay()
        val drifting = (0 until 25).map { i -> pinkyOnlyPose().map { p -> Point(p.x + i * .012f, p.y) } }
        r.feedPoses(drifting)
        assertEquals(1, r.events.countOf<GestureEvent.PinkyMute>())
    }

    @Test fun screenshotSequenceOpenFistOpenFiresScreenshotOnce() {
        val r = Replay()
        r.feed(9, ::spreadPalm)                   // 450ms: open palm confirmed
        r.feed(5, ::fistPose)                     // 250ms: fist acknowledged
        r.feed(7, ::spreadPalm)                   // 350ms: final open triggers Screenshot
        assertEquals(1, r.events.countOf<GestureEvent.Screenshot>())
    }

    /** G16-G19: open palm, then hold each folded pose ~1s; only the matching slot fires once. */
    @Test fun openPalmThenFoldedFingersFireOpenAppSlots() {
        val cases = listOf(
            1 to ::foldedIndexOnly,
            2 to ::foldedTwoFingers,
            3 to ::foldedThreeFingers,
            4 to ::foldedFourFingers
        )
        for ((slot, pose) in cases) {
            val r = Replay()
            r.feed(10, ::spreadPalm)              // 500ms: arm on the open palm
            r.feed(20, pose)                      // 1s: folded pose confirmed and fired
            val fired = r.events.filterIsInstance<GestureEvent.OpenApp>()
            assertEquals(listOf(slot), fired.map { it.slot })
        }
    }

    /** The open-app pipeline must stay dormant when all four switches are off. */
    @Test fun openAppSequenceStaysDormantWhenSwitchesAreOff() {
        val off = GestureFeatureConfig(
            scroll = false,
            openApp1 = false, openApp2 = false, openApp3 = false, openApp4 = false
        )
        val r = Replay(off)
        r.feed(10, ::spreadPalm)
        r.feed(20, ::foldedIndexOnly)
        assertEquals(0, r.events.countOf<GestureEvent.OpenApp>())
    }

    /**
     * Folding to three fingers naturally crosses another slot first (the thumb folds while
     * the four fingers are still extended -> slot 4). The sequence must follow the latest
     * slot and restart its hold timer instead of cancelling with "pose changed".
     */
    @Test fun foldingThroughAnotherSlotFollowsTheLatestStablePose() {
        val r = Replay()
        r.feed(10, ::spreadPalm)                // 500ms: arm on the open palm
        r.feed(4, ::foldedFourFingers)          // 200ms: slot 4 locks while the thumb folds
        r.feed(15, ::foldedThreeFingers)        // 750ms: pinky joins, timer restarts, fires slot 3
        val fired = r.events.filterIsInstance<GestureEvent.OpenApp>()
        assertEquals(listOf(3), fired.map { it.slot })
    }

    /** After locking a slot, relaxing the hand cancels the sequence only after a grace window. */
    @Test fun holdingThenRelaxingCancelsAfterGraceWindow() {
        val r = Replay()
        r.feed(10, ::spreadPalm)                // 500ms: arm
        r.feed(4, ::foldedIndexOnly)            // 200ms: slot 1 locks (below the 600ms hold)
        r.feed(15, ::fistPose)                  // 750ms: no slot pose anymore -> cancelled
        assertEquals(0, r.events.countOf<GestureEvent.OpenApp>())
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.contains("取消") })
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

    /** 张掌静止 6 秒后再向下挥：同样只重置基准，动作照常触发。 */
    @Test fun palmWaveStillWorksAfterIdleTimeout() {
        val r = Replay()
        r.feed(4) { closedPalm() }                  // settle past 160ms
        r.feed(120) { closedPalm() }                // 6 秒不动
        var dy = 0f
        r.feed(6) { dy += .03f; closedPalm(offsetY = dy) }
        assertEquals(1, r.events.filterIsInstance<GestureEvent.Swipe>().count { !it.up })
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

    /** The same fingertip travel, split into small per-frame steps instead of one flick. */
    private fun horizontalIndexTipStep(step: Int, total: Int): List<Point> = horizontalIndex()
        .withLandmark(8, Point(.63f + .02f * step / total, .58f - .13f * step / total))

    // G03/G04 must read angular *speed*, not total travel time: steering the cursor (G01) drifts the
    // index angle slowly, and that drift must never turn into a scroll. Measured on device (2026-10-04):
    // a real flick jumps 22.9deg..40.9deg inside 100-180ms, cursor steering stays under 4deg.
    @Test fun slowlyDriftingIndexAngleNeverScrolls() {
        val r = Replay()
        r.feed(4, ::horizontalIndex)
        r.feedPoses((1..12).map { horizontalIndexTipStep(it, 12) })
        assertEquals(0, r.events.filterIsInstance<GestureEvent.Swipe>().size)
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

    /** 只有手指在动：手腕留在原地，掌心因手指带动而产生位移。 */
    private fun fingersOnlyTwoFingerHand(offsetX: Float = 0f): List<Point> = baseHand(
        index = FingerPose.EXTENDED, middle = FingerPose.EXTENDED,
        ring = FingerPose.FOLDED, pinky = FingerPose.FOLDED
    ).mapIndexed { i, p -> if (i == 0) p else Point(p.x + offsetX, p.y) }
        .withLandmark(12, Point(.44f + offsetX, .35f))

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

    /** 整只手小幅轻挑也要能切歌：位移低于旧的 0.09 阈值，但手腕与掌心一起移动。 */
    @Test fun smallWholeHandFlickStillSwitchesTracks() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        var dx = 0f
        r.feed(6) { dx -= .013f; twoFingerHand(dx) }  // 0.078 总位移，手腕同步移动
        assertEquals(1, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
        assertEquals(GestureEvent.TwoFingerDirection.LEFT, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().single().direction)
    }

    /** 只有指尖挑动、手腕不动：掌心位移已过阈值，但整只手没有挥动，不得切歌。 */
    @Test fun fingerOnlySidewaysMotionDoesNotSwitchTracks() {
        val r = Replay()
        r.feed(4) { fingersOnlyTwoFingerHand() }
        var dx = 0f
        r.feed(6) { dx += .02f; fingersOnlyTwoFingerHand(dx) }
        assertEquals(0, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
    }

    /** 带上下起伏的斜向轻挑（约 40°）也要能切歌：放宽前水平分量必须压过垂直分量 1.25 倍。 */
    @Test fun slantedWholeHandFlickStillSwitchesTracks() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        var dx = 0f
        var dy = 0f
        r.feed(10) { dx -= .012f; dy += .010f; twoFingerHand(dx, dy) }
        val swipes = r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>()
        assertEquals(1, swipes.size)
        assertEquals(GestureEvent.TwoFingerDirection.LEFT, swipes.single().direction)
        assertEquals(0, r.events.filterIsInstance<GestureEvent.TwoFingerVolumeHold>().size)
    }

    /** 运行中把灵敏度调到“灵敏”：同样的位移在标准档不够触发，换档后立刻能触发。 */
    @Test fun sensitivityChangeRetunesThresholdsWithoutRestart() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        var dx = 0f
        r.feed(5) { dx -= .012f; twoFingerHand(dx) }  // 总位移 0.06 < 0.07，标准档不触发
        assertEquals(0, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)

        r.setSensitivity(.78f)                        // “灵敏”：阈值降到 0.0546
        r.feed(4, ::fistPose)                         // 换档清空状态，需重新进入姿势
        r.feed(4) { twoFingerHand() }
        var dx2 = 0f
        r.feed(5) { dx2 -= .012f; twoFingerHand(dx2) }
        assertEquals(1, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
    }

    /** 摆好姿势后静止 6 秒再挥：超过动作窗口只重置基准，不再要求松手重来。 */
    @Test fun twoFingerSwipeStillWorksAfterIdleTimeout() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        r.feed(120) { twoFingerHand() }             // 6 秒不动
        var dx = 0f
        r.feed(6) { dx -= .03f; twoFingerHand(dx) }
        assertEquals(1, r.events.filterIsInstance<GestureEvent.TwoFingerSwipe>().size)
    }

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

    /** 快速双击：伸直段只有 100ms 也必须算一次完整的弯下——放宽前要求 120ms，会整段作废。 */
    @Test fun fastTwoFingerDoubleTapStillTogglesPlayPause() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }   // 200ms 摆好姿势
        r.feed(3, ::fistPose)           // 150ms 弯下
        r.feed(2) { twoFingerHand() }   // 100ms 伸直（放宽前这一关就判失败）
        r.feed(2, ::fistPose)           // 第二次弯下
        r.feed(2) { twoFingerHand() }   // 伸直 -> 触发
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
    }

    /** 双击过程中掌心被带出上下位移：只能算双击，不能被判成上下拉音量。 */
    @Test fun doubleTapWithPalmDriftNeverStartsVolume() {
        val r = Replay()
        r.feed(4) { twoFingerHand() }
        r.feed(3, ::fistPose)                            // 第一次弯下
        var dy = 0f
        r.feed(4) { dy += .02f; twoFingerHand(0f, dy) }  // 伸直并继续向下带出位移
        r.feed(3, ::fistPose)                            // 第二次弯下
        r.feed(2) { twoFingerHand(0f, dy) }              // 伸直 -> 双击
        assertEquals(1, r.events.countOf<GestureEvent.TwoFingerDoubleTap>())
        assertEquals(0, r.events.filterIsInstance<GestureEvent.TwoFingerVolumeHold>().size)
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
        // The claw is off by default and has to be switched on explicitly.
        val r = Replay(GestureFeatureConfig(scroll = false, clawDrag = true))
        r.feed(14, ::clawPose)
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("拖动已开始") })
        assertEquals(0, r.events.countOf<GestureEvent.CShape>())

        r.feed(8, ::spreadPalm)
        r.feed(17, ::cShapePose)
        assertEquals(1, r.events.countOf<GestureEvent.CShape>())
    }

    /**
     * The 2026-10-03 real-device complaint: a claw reads as a fist because both curl the fingers.
     * Curling must no longer cost the claw, and it must not arm the fist hold either.
     */
    @Test fun deeplyCurledClawStillStartsTheDragAndNeverBecomesAFist() {
        // The claw ships switched off, so this one opts in explicitly.
        val r = Replay(GestureFeatureConfig(scroll = false, clawDrag = true))
        r.feed(25, ::deepClawPose)                // 1.25s: past the 600ms confirm and the 1s fist hold
        assertEquals(0, r.events.countOf<GestureEvent.PlayPause>())
        assertEquals(0, r.events.countOf<GestureEvent.CShape>())
        assertTrue(r.events.any { it is GestureEvent.Feedback && it.message.startsWith("拖动已开始") })
    }

    /**
     * 2026-10-03: the drag is one continuous action. The finger goes down as soon as the claw is
     * confirmed, follows the palm for as long as the pose is held — there is no time limit, so a
     * 20 second drag is legal — and lifts only when the hand opens again.
     */
    @Test fun clawDragStaysPressedWhileHeldAndLiftsOnlyOnRelease() {
        // The claw ships switched off, so this one opts in explicitly.
        val r = Replay(GestureFeatureConfig(scroll = false, clawDrag = true))
        r.feed(14, ::clawPose)                     // 0.7s: past the 600ms confirm
        assertEquals(
            listOf(GestureEvent.DragPhase.START),
            r.events.filterIsInstance<GestureEvent.ClawDrag>().map { it.phase }
        )
        // Holding the pose for 20 s must neither time the drag out nor lift the finger.
        r.feed(400, ::clawPose)
        val held = r.events.filterIsInstance<GestureEvent.ClawDrag>().map { it.phase }
        assertTrue("the drag must stay pressed while the claw is held", !held.contains(GestureEvent.DragPhase.END))
        // Walking the palm across the frame feeds MOVE segments into the same ongoing stroke.
        r.feedPoses((1..12).map { step -> clawPoseShiftedBy(step * .02f, 0f) })
        val moving = r.events.filterIsInstance<GestureEvent.ClawDrag>().map { it.phase }
        assertTrue("palm travel must drive MOVE segments", moving.contains(GestureEvent.DragPhase.MOVE))
        assertTrue("the finger must still be down while moving", !moving.contains(GestureEvent.DragPhase.END))
        r.feed(8, ::spreadPalm)                    // release
        assertEquals(
            GestureEvent.DragPhase.END,
            r.events.filterIsInstance<GestureEvent.ClawDrag>().last().phase
        )
    }

    /**
     * 2026-10-03: while a drag runs, the hand owns every frame. The pinky pose is tested before the
     * drag is advanced, so it used to fire mute mid-drag and return first, leaving the drag running
     * with no way to end it. A running drag must keep the pose slot; a hand that really stopped
     * being a claw still ends it through the pose grace, so nothing can stay armed forever.
     */
    @Test fun runningDragIsNotStolenByAnotherPose() {
        // The claw ships switched off, so this one turns it on before exercising a running drag.
        val r = Replay(GestureFeatureConfig(scroll = false, clawDrag = true))
        r.feed(14, ::clawPose)                      // 0.7s: past the 600ms confirm
        assertEquals(
            1,
            r.events.filterIsInstance<GestureEvent.ClawDrag>()
                .count { it.phase == GestureEvent.DragPhase.START }
        )
        r.feed(30, ::pinkyOnlyPose)                 // 1.5s: long enough to fire mute on its own
        assertEquals("a running drag must not let another pose fire", 0, r.events.countOf<GestureEvent.PinkyMute>())
        assertTrue(
            "the hand is still in frame, so the drag must keep running",
            r.events.filterIsInstance<GestureEvent.ClawDrag>()
                .none { it.phase == GestureEvent.DragPhase.END }
        )
        // The hand leaving the frame is the last exit, and it has to lift the finger.
        r.loseHand()
        assertTrue(
            "a hand that leaves the frame must end the drag",
            r.events.filterIsInstance<GestureEvent.ClawDrag>().any { it.phase == GestureEvent.DragPhase.END }
        )
    }

    @Test fun clawRequiresFrontFacingPalmAndSeparatedFingers() {
        // What follows only makes sense with the claw switched on; it ships off.
        val side = Replay(GestureFeatureConfig(scroll = false, clawDrag = true))
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
}
