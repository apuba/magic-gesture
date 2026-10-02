package com.magicgesture.app

import kotlin.math.hypot
import kotlin.math.atan2
import kotlin.math.acos

/** Pure logic. Coordinates are normalized to [0,1], timestamps are monotonic milliseconds. */
data class Point(val x: Float, val y: Float)
sealed interface GestureEvent {
    enum class MotionSource { INDEX_FINGER, PALM }
    data class Cursor(val x: Float, val y: Float) : GestureEvent
    data class Click(val x: Float, val y: Float) : GestureEvent
    data class Swipe(val up: Boolean, val source: MotionSource) : GestureEvent
    data class HorizontalSwipe(val left: Boolean, val source: MotionSource) : GestureEvent
    data object Screenshot : GestureEvent
    data object Like : GestureEvent
    data object Back : GestureEvent
    data object Home : GestureEvent
    data object Selfie : GestureEvent
    data object Recents : GestureEvent
    data object ThumbsUp : GestureEvent
    data object Ok : GestureEvent
    data object PlayPause : GestureEvent
    data object PinkyMute : GestureEvent
    data object LotusRecents : GestureEvent
    data object OrchidBack : GestureEvent
    data object LeftLBack : GestureEvent
    data object LShape : GestureEvent
    data object CShape : GestureEvent
    data object LoveLock : GestureEvent
    data class ClawDrag(val startX: Float, val startY: Float, val endX: Float, val endY: Float) : GestureEvent
    /** Four-direction wave of the two-finger (index+middle together) pose. */
    enum class TwoFingerDirection { LEFT, RIGHT, UP, DOWN }
    data class TwoFingerSwipe(val direction: TwoFingerDirection) : GestureEvent
    enum class VolumeHoldPhase { START, TICK, END }
    data class TwoFingerVolumeHold(val raise: Boolean, val phase: VolumeHoldPhase) : GestureEvent
    /** G34 "666": thumb and pinky extended, index/middle/ring curled. Unbound by default. */
    data object Six666 : GestureEvent
    /** Both fingers bend and re-extend twice in a row while in the two-finger pose. */
    data object TwoFingerDoubleTap : GestureEvent
    /** Open-palm-then-fold sequence fired slot N (1..4): 1=index, 2=index+middle, 3=+ring, 4=+pinky. */
    data class OpenApp(val slot: Int) : GestureEvent
    data class Feedback(val message: String, val progress: Int? = null) : GestureEvent
}
class GestureEngine(
    private val movementScale: Float = 1f,
    private var features: GestureFeatureConfig = GestureFeatureConfig()
) {
    private enum class Pinch { READY, CANDIDATE, FIRED }
    private enum class IndexClick { READY, STABILIZING, ARMED, BENT }
    private enum class PalmAxis { NONE, HORIZONTAL, VERTICAL }
    private enum class StaticHold { READY, CANDIDATE, FIRED }
    private enum class ClawDragState { READY, CONFIRMING, DRAGGING }
    private enum class TwoFingerSwipeState { IDLE, TRACKING, WAIT_RELEASE, VOLUME_UP, VOLUME_DOWN }
    private enum class TwoFingerTapState { IDLE, HELD, BENT_ONCE, POSED_SECOND, BENT_TWICE, FIRED_WAIT }
    private enum class ScreenshotSequence {
        IDLE, OPEN_CANDIDATE, WAIT_FIST, FIST_HOLD, INDEX_FINGER_SCROLL, INDEX_HORIZONTAL_SWIPE,
        WAIT_FINAL_OPEN, WAIT_RELEASE, WAIT_SCREENSHOT_RELEASE
    }
    private var pinch = Pinch.READY
    private var indexClick = IndexClick.READY
    private var indexClickAt = 0L
    private var indexClickPoint: Point? = null
    private var screenshotSequence = ScreenshotSequence.IDLE
    private var candidateAt = 0L
    private var releaseAt = 0L
    private var screenshotStageAt = 0L
    private var screenshotArmedAt = 0L
    private var screenshotCooldownUntil = 0L
    private var backCooldownUntil = 0L
    private var openPalmStart: Point? = null
    private var openPalmAxis = PalmAxis.NONE
    private var lastOpenPalmAt = 0L
    private var indexScrollStartAngle = 0f
    private var vHoldAt = 0L
    private var vLatched = false
    private var thumbsUpHold = StaticHold.READY
    private var thumbsUpHoldAt = 0L
    private var okHold = StaticHold.READY
    private var okHoldAt = 0L
    private var six666Hold = StaticHold.READY
    private var six666HoldAt = 0L
    /** Last frame the OK shape was seen; keeps the finger heart from claiming wobbling frames. */
    private var okPoseAt = 0L
    private var fistHold = StaticHold.READY
    private var fistHoldAt = 0L
    private var pinkyHold = StaticHold.READY
    private var pinkyHoldAt = 0L
    private var lotusHold = StaticHold.READY
    private var lotusHoldAt = 0L
    private var orchidHold = StaticHold.READY
    private var orchidHoldAt = 0L
    private var leftLHold = StaticHold.READY
    private var leftLHoldAt = 0L
    private var lShapeHold = StaticHold.READY
    private var lShapeHoldAt = 0L
    private var cShapeHold = StaticHold.READY
    private var cShapeHoldAt = 0L
    private var loveHold = StaticHold.READY
    private var loveHoldAt = 0L
    private var clawDragState = ClawDragState.READY
    private var clawConfirmAt = 0L
    private var clawAnchor: Point? = null
    private var clawPalmAt: Point? = null
    private var twoFingerState = TwoFingerSwipeState.IDLE
    private var twoFingerStart: Point? = null
    private var twoFingerStageAt = 0L
    private var lastVolumeTickAt = 0L
    private var twoFingerReleaseRequired = false
    private var twoFingerTapState = TwoFingerTapState.IDLE
    private var twoFingerTapPoseAt = 0L
    private var twoFingerTapBentAt = 0L
    private var twoFingerTapFirstCycleAt = 0L
    private var lastFeedbackAt = 0L
    private val localRepeatGuardMs = 2000L
    private val openPalmSettleMs = 160L
    // G16-G19: open-palm then fold to N fingers opens the app bound to that slot.
    private enum class AppSequence { IDLE, ARMED, HOLDING, WAIT_RELEASE }
    private var appSequence = AppSequence.IDLE
    private var appStageAt = 0L
    private var appOpenPalmAt = 0L
    private var appHoldSlot = 0
    private var appHoldAt = 0L
    private var appCooldownUntil = 0L
    private var lastSeenAt = 0L
    private var smoothed: Point? = null
    private var paused = false

    @Synchronized fun resume() { paused = false; resetTransient() }
    @Synchronized fun stop() { paused = true; resetTransient(); smoothed = null }
    @Synchronized fun updateFeatures(value: GestureFeatureConfig) { features = value; resetTransient() }
    @Synchronized fun lost(now: Long): List<GestureEvent> {
        if (now - lastSeenAt < 300) return emptyList()
        val ending = when (twoFingerState) {
            TwoFingerSwipeState.VOLUME_UP -> GestureEvent.TwoFingerVolumeHold(true, GestureEvent.VolumeHoldPhase.END)
            TwoFingerSwipeState.VOLUME_DOWN -> GestureEvent.TwoFingerVolumeHold(false, GestureEvent.VolumeHoldPhase.END)
            else -> null
        }
        resetTransient()
        smoothed = null
        return listOfNotNull(ending)
    }
    @Synchronized fun finishVolumeSession(waitForRelease: Boolean) {
        twoFingerState = if (waitForRelease) TwoFingerSwipeState.WAIT_RELEASE else TwoFingerSwipeState.IDLE
        twoFingerStart = null
        lastVolumeTickAt = 0L
        twoFingerReleaseRequired = waitForRelease
    }
    @Synchronized fun consume(points: List<Point>, now: Long): List<GestureEvent> {
        if (paused || points.size != 21 || (lastSeenAt != 0L && now <= lastSeenAt)) return emptyList()
        if (lastSeenAt != 0L && now - lastSeenAt > 300) resetTransient()
        lastSeenAt = now
        val output = mutableListOf<GestureEvent>()
        val tip = points[8]
        val target = Point(((tip.x - .15f) / .70f).coerceIn(0f, 1f), ((tip.y - .15f) / .70f).coerceIn(0f, 1f))
        val old = smoothed
        val cursor = if (old == null) target else Point(old.x + .35f * (target.x - old.x), old.y + .35f * (target.y - old.y))
        smoothed = cursor
        val handScale = dist(points[5], points[17]).coerceAtLeast(.001f)
        val ratio = dist(points[4], points[8]) / handScale
        val fourFingersOpen = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).all { (tipIndex, pipIndex) ->
            dist(points[tipIndex], points[0]) > dist(points[pipIndex], points[0]) * 1.10f
        }
        val thumbOpen = dist(points[4], points[5]) > dist(points[3], points[5]) * 1.05f
        val fourFingerGapAngles = listOf(
            vectorAngleDegrees(points[5], points[8], points[9], points[12]),
            vectorAngleDegrees(points[9], points[12], points[13], points[16]),
            vectorAngleDegrees(points[13], points[16], points[17], points[20])
        )
        val fiveFingerGapAngles = listOf(
            vectorAngleDegrees(points[2], points[4], points[5], points[8])
        ) + fourFingerGapAngles
        // Compare each finger's own MCP-to-tip direction instead of rays from the wrist.
        // Fingers that physically touch still originate at different places on the palm,
        // so wrist-based angles incorrectly make a closed hand look spread.
        // Direction pose: index, middle, ring and little fingers are extended and touching.
        // The thumb is intentionally not part of this requirement.
        val directionPalm = fourFingersOpen && fourFingerGapAngles.all { it <= 5f }
        val screenshotPalmOpen = fourFingersOpen && thumbOpen && fiveFingerGapAngles.all { it > 5f }
        val fist = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).all { (tipIndex, pipIndex) ->
            dist(points[tipIndex], points[0]) < dist(points[pipIndex], points[0]) * 1.08f
        }
        val palm = Point(
            listOf(0, 5, 9, 13, 17).sumOf { points[it].x.toDouble() }.toFloat() / 5,
            listOf(0, 5, 9, 13, 17).sumOf { points[it].y.toDouble() }.toFloat() / 5
        )
        val indexOpen = dist(points[8], points[0]) > dist(points[6], points[0]) * 1.12f
        val middleOpen = dist(points[12], points[0]) > dist(points[10], points[0]) * 1.12f
        val ringOpen = dist(points[16], points[0]) > dist(points[14], points[0]) * 1.12f
        val pinkyOpen = dist(points[20], points[0]) > dist(points[18], points[0]) * 1.12f
        val indexFolded = dist(points[8], points[0]) < dist(points[6], points[0]) * 1.08f
        val middleFolded = dist(points[12], points[0]) < dist(points[10], points[0]) * 1.08f
        val ringFolded = dist(points[16], points[0]) < dist(points[14], points[0]) * 1.08f
        val pinkyFolded = dist(points[20], points[0]) < dist(points[18], points[0]) * 1.08f
        val thumbUpPose = thumbOpen && middleFolded && ringFolded && pinkyFolded &&
            dist(points[8], points[0]) < dist(points[6], points[0]) * 1.08f &&
            points[4].y < points[3].y - handScale * .18f
        val okPose = dist(points[4], points[8]) / handScale < .30f && middleOpen && ringOpen && pinkyOpen
        // Provisional one-hand definitions; thresholds must be calibrated on real devices.
        val lotusPose = dist(points[4], points[16]) / handScale < .30f &&
            indexOpen && middleOpen && pinkyOpen && dist(points[4], points[12]) / handScale > .38f
        val orchidPose = dist(points[4], points[12]) / handScale < .30f &&
            indexOpen && ringOpen && pinkyOpen && dist(points[4], points[8]) / handScale > .38f
        val indexBentPose = middleFolded && ringFolded && pinkyFolded && (
            dist(points[8], points[0]) < dist(points[6], points[0]) * 1.05f ||
                dist(points[8], points[5]) / handScale < .75f
            )
        val indexAngleDegrees = Math.toDegrees(
            atan2(
                (points[8].y - points[5].y).toDouble(),
                kotlin.math.abs(points[8].x - points[5].x).coerceAtLeast(.001f).toDouble()
            )
        ).toFloat()
        // Provisional G24-G28 poses; thresholds must be calibrated on real devices.
        // The two L-shapes are 90-degree rotations of each other: the thumb direction tells them apart.
        val thumbUpStrong = thumbOpen && points[4].y < points[2].y - handScale * .15f
        val thumbSideways = thumbOpen && kotlin.math.abs(points[4].y - points[2].y) < handScale * .35f &&
            dist(points[4], points[5]) / handScale > .45f
        // G24: index horizontal pointing to the user's left (mirrored view), thumb up.
        val leftLPose = indexOpen && middleFolded && ringFolded && pinkyFolded &&
            kotlin.math.abs(indexAngleDegrees) <= 35f && thumbUpStrong && points[8].x < points[5].x
        // G25: index vertical, thumb stretched sideways.
        val lShapePose = indexOpen && middleFolded && ringFolded && pinkyFolded &&
            kotlin.math.abs(indexAngleDegrees) >= 60f && thumbSideways
        // G28: thumb, index and pinky extended; middle and ring folded ("I love you" sign).
        val lovePose = thumbOpen && indexOpen && pinkyOpen && middleFolded && ringFolded
        // G23: only the little finger is extended; thumb and the other three fingers stay folded.
        val pinkyOnlyPose = pinkyOpen && indexFolded && middleFolded && ringFolded && !thumbOpen
        // G34 "666": thumb and pinky out, index/middle/ring curled. The pinky is what separates
        // it from the thumbs-up (which needs the pinky folded) and the curled index separates
        // it from the love pose (which needs the index extended).
        val six666Pose = thumbOpen && pinkyOpen && indexFolded && middleFolded && ringFolded
        // Claw vs C live on a curl continuum. Use each finger's own reach from wrist and
        // tolerate one noisy/occluded finger; requiring all four tips inside a narrow band
        // made both poses practically unreachable with real MediaPipe frames.
        val tipPalmRatios = listOf(8, 12, 16, 20).map { dist(points[it], palm) / handScale }
        val fingerReachRatios = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).map { (tipIndex, pipIndex) ->
            dist(points[tipIndex], points[0]) / dist(points[pipIndex], points[0]).coerceAtLeast(.001f)
        }
        val cFingerTipGaps = listOf(8 to 12, 12 to 16, 16 to 20).map { (a, b) ->
            dist(points[a], points[b]) / handScale
        }
        // A front-facing palm stays broad relative to its wrist-to-middle-MCP length. A
        // side-on hand collapses this width and must never enter the claw pipeline.
        val palmFrontality = handScale / dist(points[0], points[9]).coerceAtLeast(.001f)
        val palmFacingCamera = palmFrontality >= .65f
        // G26: all five fingers are independently visible and the four curled fingertips
        // stay separated. The deliberate dead band between this and C avoids cross-firing.
        val clawFourFingersSeparated = cFingerTipGaps.count { it >= .30f } >= 2 &&
            cFingerTipGaps.all { it >= .22f }
        val clawThumbSeparated = dist(points[4], points[8]) / handScale >= .55f
        val clawPose = !fist && palmFacingCamera && clawFourFingersSeparated && clawThumbSeparated &&
            fingerReachRatios.count { it in .55f..1.12f } >= 3 &&
            tipPalmRatios.count { it in .45f..1.25f } >= 3 &&
            tipPalmRatios.average() < 1.10f
        // G27: fingers half-bent forming a C, more open than the claw.
        // Real C poses often occlude one fingertip, causing one adjacent gap to jump for a
        // few frames. Require the group as a whole to stay together while tolerating that
        // single noisy landmark; this is much more stable than requiring all three gaps.
        val cFourFingersTogether = cFingerTipGaps.count { it <= .44f } >= 2 &&
            cFingerTipGaps.all { it <= .68f }
        val cThumbOpen = thumbOpen || (
            dist(points[4], palm) / handScale > .58f &&
                dist(points[4], points[8]) / handScale > .45f
            )
        // All geometry is normalized and rotation-invariant, so the hand may be tilted in
        // front of the camera. The four curved fingers must still remain visibly grouped.
        // 2026-10-01 real-device feedback: a plain open palm was firing the C, so the curl
        // envelope is narrowed on every axis (tip gaps, reach ratios, tip-to-palm ratios).
        val cShapePose = !fist && !clawPose && cThumbOpen && cFourFingersTogether &&
            fingerReachRatios.count { it in .58f..1.62f } >= 3 &&
            fingerReachRatios.count { it < 1.30f } >= 2 &&
            tipPalmRatios.count { it in .48f..1.72f } >= 3 &&
            tipPalmRatios.average() < 1.55f
        // G29/G30: index and middle extended and roughly parallel (not a spread V), ring and pinky folded.
        // On a real hand, pressed-together fingertips still sit ~0.3 palm-widths apart, so distance
        // alone cannot separate this pose from the V — the splay angle is the discriminator.
        val twoFingerIndexMiddleAngle = vectorAngleDegrees(points[5], points[8], points[9], points[12])
        val twoFingerTipGap = dist(points[8], points[12]) / handScale
        val middleAlongsideIndex = middleOpen || (
            dist(points[12], points[9]) > dist(points[10], points[9]).coerceAtLeast(.001f) * 1.10f &&
                dist(points[12], points[0]) > dist(points[9], points[0]) * .95f
            )
        val twoFingerTogetherPose = indexOpen && middleAlongsideIndex && ringFolded && pinkyFolded &&
            twoFingerTipGap <= .60f && twoFingerIndexMiddleAngle <= 25f
        val vPose = indexOpen && middleOpen && ringFolded && pinkyFolded &&
            twoFingerTipGap > .32f && (twoFingerTipGap > .60f || twoFingerIndexMiddleAngle > 25f)
        // A close, parallel middle finger may look folded relative to the wrist when hidden
        // behind the index finger. Never let that valid two-finger candidate arm index gestures.
        val indexOnlyPose = indexOpen && middleFolded && ringFolded && pinkyFolded && !twoFingerTogetherPose
        if (advanceActiveVolumeHold(twoFingerTogetherPose, now, output)) return output
        // G16-G19 run before cursor emission so the folded index pose cannot move the cursor;
        // while the palm is still open the sequence stays transparent for the G13 pipeline.
        // Slot poses use the same 1.08 boundary as the folded checks (complementary split),
        // so every finger is classified unambiguously and a slightly curled finger that the
        // strict 1.12 "open" detectors would reject still counts as extended.
        fun slotExt(tipIdx: Int, pipIdx: Int) =
            dist(points[tipIdx], points[0]) > dist(points[pipIdx], points[0]) * 1.08f
        val fingersSeen = listOf(
            "拇指" to thumbOpen, "食指" to indexOpen, "中指" to middleOpen,
            "无名指" to ringOpen, "小指" to pinkyOpen
        ).filter { it.second }.joinToString("、") { it.first }.ifEmpty { "收拢的手" }
        if (advanceAppSequence(
                screenshotPalmOpen,
                slotExt(8, 6) && middleFolded && ringFolded && pinkyFolded && !thumbOpen,
                slotExt(8, 6) && slotExt(12, 10) && ringFolded && pinkyFolded && !thumbOpen,
                slotExt(8, 6) && slotExt(12, 10) && slotExt(16, 14) && pinkyFolded && !thumbOpen,
                slotExt(8, 6) && slotExt(12, 10) && slotExt(16, 14) && slotExt(20, 18) && !thumbOpen,
                fingersSeen,
                now,
                output
            )
        ) return output
        output += GestureEvent.Cursor(cursor.x, cursor.y)
        if (features.selfie && vPose && twoFingerState == TwoFingerSwipeState.IDLE) {
            pinch = Pinch.READY
            candidateAt = 0L
            releaseAt = 0L
            if (!vLatched) {
                if (vHoldAt == 0L) vHoldAt = now
                val held = now - vHoldAt
                if (now - lastFeedbackAt >= 250) {
                    val progress = ((held * 100L) / 2000L).toInt().coerceIn(0, 100)
                    val remaining = ((2000L - held).coerceAtLeast(0L) + 999L) / 1000L
                    output += GestureEvent.Feedback("V 字保持：还需 ${remaining} 秒", progress)
                    lastFeedbackAt = now
                }
                if (now - vHoldAt >= 2000) {
                    output += GestureEvent.Selfie
                    vLatched = true
                }
            }
            return output
        } else {
            vHoldAt = 0L
            vLatched = false
        }
        // G24-G28 checked before the click/swipe chains: their index-based poses would
        // otherwise arm click or horizontal-swipe detection while the L shapes are held.
        if (features.leftL && advanceStaticHold(leftLPose, now, GestureEvent.LeftLBack, { leftLHold }, { leftLHold = it }, { leftLHoldAt }, { leftLHoldAt = it }, output)) return output
        if (features.lShape && advanceStaticHold(lShapePose, now, GestureEvent.LShape, { lShapeHold }, { lShapeHold = it }, { lShapeHoldAt }, { lShapeHoldAt = it }, output, holdMs = 2000L, label = "L 手形保持")) return output
        if (features.loveLock && advanceStaticHold(lovePose, now, GestureEvent.LoveLock, { loveHold }, { loveHold = it }, { loveHoldAt }, { loveHoldAt = it }, output)) return output
        if (pinkyOnlyPose) {
            // Folded thumb/index can resemble a heart pinch. Pinky-only owns this pose and
            // clears partial heart state so landmark wobble cannot fire Like afterward.
            pinch = Pinch.READY
            candidateAt = 0L
            releaseAt = 0L
        }
        if (features.pinkyMute && advanceStaticHold(pinkyOnlyPose, now, GestureEvent.PinkyMute, { pinkyHold }, { pinkyHold = it }, { pinkyHoldAt }, { pinkyHoldAt = it }, output, label = "小指手势保持")) return output
        if (features.six666 && advanceStaticHold(six666Pose, now, GestureEvent.Six666, { six666Hold }, { six666Hold = it }, { six666HoldAt }, { six666HoldAt = it }, output)) return output
        if (features.cShape && advanceStaticHold(cShapePose, now, GestureEvent.CShape, { cShapeHold }, { cShapeHold = it }, { cShapeHoldAt }, { cShapeHoldAt = it }, output)) return output
        if (advanceClawDrag(clawPose, palm, cursor, now, output)) return output
        advanceTwoFingerTap(twoFingerTogetherPose, now, output)
        advanceTwoFingerSwipe(twoFingerTogetherPose, palm, now, output)
        if (twoFingerState == TwoFingerSwipeState.VOLUME_UP || twoFingerState == TwoFingerSwipeState.VOLUME_DOWN) return output
        if (features.click) {
            when (indexClick) {
                IndexClick.READY -> if (indexOnlyPose) {
                    indexClick = IndexClick.STABILIZING
                    indexClickAt = now
                    indexClickPoint = cursor
                }
                IndexClick.STABILIZING -> when {
                    !indexOnlyPose -> {
                        indexClick = IndexClick.READY
                        indexClickPoint = null
                    }
                    now - indexClickAt >= 200 -> {
                        indexClick = IndexClick.ARMED
                        indexClickAt = now
                        indexClickPoint = cursor
                        output += GestureEvent.Feedback("食指点击已准备：请弯曲食指")
                    }
                }
                IndexClick.ARMED -> when {
                    indexBentPose -> {
                        indexClick = IndexClick.BENT
                        indexClickAt = now
                        output += GestureEvent.Feedback("食指已弯曲：请重新伸直")
                    }
                    indexOnlyPose -> {
                        indexClickPoint = cursor
                        if (now - indexClickAt > 5000) {
                            indexClick = IndexClick.STABILIZING
                            indexClickAt = now
                        }
                    }
                    else -> {
                        indexClick = IndexClick.READY
                        indexClickPoint = null
                    }
                }
                IndexClick.BENT -> when {
                    indexOnlyPose && now - indexClickAt <= 1000 -> {
                        val point = indexClickPoint ?: cursor
                        output += GestureEvent.Click(point.x, point.y)
                        indexClick = IndexClick.READY
                        indexClickPoint = null
                        screenshotSequence = ScreenshotSequence.IDLE
                        return output
                    }
                    now - indexClickAt > 1000 -> {
                        indexClick = IndexClick.READY
                        indexClickPoint = null
                    }
                }
            }
        } else {
            indexClick = IndexClick.READY
            indexClickPoint = null
        }
        if (advanceOpenPalmSequence(directionPalm, screenshotPalmOpen, indexOnlyPose, indexAngleDegrees, fist, palm, now, output)) {
            if (output.any { it is GestureEvent.Swipe || it is GestureEvent.HorizontalSwipe }) {
                indexClick = IndexClick.READY
                indexClickPoint = null
            }
            pinch = Pinch.READY
            candidateAt = 0L
            releaseAt = 0L
            return output
        }
        if (features.thumbsUp && advanceStaticHold(thumbUpPose, now, GestureEvent.ThumbsUp, { thumbsUpHold }, { thumbsUpHold = it }, { thumbsUpHoldAt }, { thumbsUpHoldAt = it }, output)) return output
        if (features.ok && okPose) okPoseAt = now
        if (features.ok && advanceStaticHold(okPose, now, GestureEvent.Ok, { okHold }, { okHold = it }, { okHoldAt }, { okHoldAt = it }, output)) return output
        if (features.playPause && advanceStaticHold(fist, now, GestureEvent.PlayPause, { fistHold }, { fistHold = it }, { fistHoldAt }, { fistHoldAt = it }, output, holdMs = 1500L, label = "握拳保持")) return output
        if (features.lotusRecents && advanceStaticHold(lotusPose, now, GestureEvent.LotusRecents, { lotusHold }, { lotusHold = it }, { lotusHoldAt }, { lotusHoldAt = it }, output)) return output
        if (features.orchidBack && advanceStaticHold(orchidPose, now, GestureEvent.OrchidBack, { orchidHold }, { orchidHold = it }, { orchidHoldAt }, { orchidHoldAt = it }, output)) return output
        // Finger-heart uses thumb/index proximity independently from index-bend clicking, but
        // OK is the very same thumb/index contact *with* the other three fingers extended, so:
        //  - the heart requires those fingers to be curled (matches the "其余三指收拢" wording);
        //  - OK keeps ownership for a short grace window, because the frames where OK drops out
        //    are exactly the frames where the three fingers read as curled — without this the
        //    heart arms on those wobble frames and fires a like instead of the OK action.
        val threeFingersExtended = middleOpen && ringOpen && pinkyOpen
        val okOwnsHand = features.ok && okPoseAt > 0L && now - okPoseAt < 500L
        val fingerHeartPose = ratio < .40f && middleFolded && ringFolded && pinkyFolded &&
            !threeFingersExtended && !okOwnsHand
        val fingersClearlyReleased = ratio > .58f
        if (features.like) when (pinch) {
            Pinch.READY -> if (fingerHeartPose) {
                pinch = Pinch.CANDIDATE
                candidateAt = now
                releaseAt = 0L
            }
            Pinch.CANDIDATE -> when {
                okOwnsHand || threeFingersExtended -> {
                    pinch = Pinch.READY
                    candidateAt = 0L
                    releaseAt = 0L
                }
                fingersClearlyReleased -> {
                    if (releaseAt == 0L) releaseAt = now
                    if (now - releaseAt >= 220) {
                        pinch = Pinch.READY
                        candidateAt = 0L
                        releaseAt = 0L
                    }
                }
                features.like && now - candidateAt >= 600 -> {
                    pinch = Pinch.FIRED
                    output += GestureEvent.Like
                }
                features.like && now - candidateAt >= 180 && now - lastFeedbackAt >= 180 -> {
                    val held = now - candidateAt
                    releaseAt = 0L
                    output += GestureEvent.Feedback("保持手指比心：双击点赞", ((held * 100L) / 600L).toInt().coerceIn(0, 100))
                    lastFeedbackAt = now
                }
                else -> releaseAt = 0L
            }
            Pinch.FIRED -> if (fingersClearlyReleased) {
                if (releaseAt == 0L) releaseAt = now
                if (now - releaseAt >= 180) { pinch = Pinch.READY; releaseAt = 0L }
            } else releaseAt = 0L
        } else pinch = Pinch.READY
        return output
    }
    private fun advanceStaticHold(
        pose: Boolean,
        now: Long,
        event: GestureEvent,
        state: () -> StaticHold,
        setState: (StaticHold) -> Unit,
        startedAt: () -> Long,
        setStartedAt: (Long) -> Unit,
        output: MutableList<GestureEvent>,
        holdMs: Long = 600L,
        label: String? = null
    ): Boolean {
        when (state()) {
            StaticHold.READY -> if (pose) {
                setState(StaticHold.CANDIDATE)
                setStartedAt(now)
                return true
            }
            StaticHold.CANDIDATE -> {
                if (!pose) {
                    setState(StaticHold.READY)
                    setStartedAt(0L)
                } else {
                    // Long holds show a countdown so the user knows to keep the pose.
                    if (label != null && holdMs >= 1000L && now - lastFeedbackAt >= 250) {
                        val held = now - startedAt()
                        val progress = ((held * 100L) / holdMs).toInt().coerceIn(0, 100)
                        val remaining = ((holdMs - held).coerceAtLeast(0L) + 999L) / 1000L
                        output += GestureEvent.Feedback("$label：还需 $remaining 秒", progress)
                        lastFeedbackAt = now
                    }
                    if (now - startedAt() >= holdMs) {
                        setState(StaticHold.FIRED)
                        output += event
                    }
                }
                return true
            }
            StaticHold.FIRED -> {
                if (!pose) {
                    setState(StaticHold.READY)
                    setStartedAt(0L)
                }
                return true
            }
        }
        return false
    }
    /**
     * G26 claw drag: hold the claw ~600ms to anchor at the current cursor, move the palm,
     * then open the hand to dispatch one press-move-release stroke from anchor to end.
     */
    private fun advanceClawDrag(
        pose: Boolean,
        palm: Point,
        cursor: Point,
        now: Long,
        output: MutableList<GestureEvent>
    ): Boolean {
        if (!features.clawDrag) {
            clawDragState = ClawDragState.READY
            clawAnchor = null
            clawPalmAt = null
            return false
        }
        when (clawDragState) {
            ClawDragState.READY -> if (pose) {
                clawDragState = ClawDragState.CONFIRMING
                clawConfirmAt = now
                return true
            }
            ClawDragState.CONFIRMING -> {
                if (!pose) {
                    clawDragState = ClawDragState.READY
                    return true
                }
                if (now - clawConfirmAt >= 600L) {
                    clawDragState = ClawDragState.DRAGGING
                    clawAnchor = cursor
                    clawPalmAt = palm
                    output += GestureEvent.Feedback("拖动已开始：移动手掌，张开手指完成拖动", 100)
                }
                return true
            }
            ClawDragState.DRAGGING -> {
                if (!pose) {
                    val anchor = clawAnchor
                    val palmAt = clawPalmAt
                    if (anchor != null && palmAt != null) {
                        // The palm travels less than a fingertip; amplify to keep drags reachable.
                        val dx = (palm.x - palmAt.x) * 1.5f
                        val dy = (palm.y - palmAt.y) * 1.5f
                        if (hypot(dx, dy) >= .03f) {
                            output += GestureEvent.ClawDrag(
                                anchor.x, anchor.y,
                                (anchor.x + dx).coerceIn(0f, 1f),
                                (anchor.y + dy).coerceIn(0f, 1f)
                            )
                        } else output += GestureEvent.Feedback("拖动距离太短，已取消")
                    }
                    clawDragState = ClawDragState.READY
                    clawAnchor = null
                    clawPalmAt = null
                } else if (now - lastFeedbackAt >= 600) {
                    output += GestureEvent.Feedback("拖动中：张开手指结束拖动")
                    lastFeedbackAt = now
                }
                return true
            }
        }
        return false
    }
    /**
     * G29-G32 two-finger media control: hold the index+middle-together pose, then wave the
     * whole hand left/right for track control or pull up/down and hold for continuous volume. The
     * pose is also a common "neutral" hand shape, so this machine never consumes frames —
     * every other detector keeps observing them; it only emits events on its own transitions.
     */
    private fun advanceTwoFingerSwipe(
        pose: Boolean,
        palm: Point,
        now: Long,
        output: MutableList<GestureEvent>
    ) {
        if (!features.twoFingerMedia) {
            twoFingerState = TwoFingerSwipeState.IDLE
            twoFingerStart = null
            return
        }
        when (twoFingerState) {
            TwoFingerSwipeState.IDLE -> if (pose) {
                twoFingerState = TwoFingerSwipeState.TRACKING
                twoFingerStart = palm
                twoFingerStageAt = now
                // Stay quiet while the tap machine is mid double-tap or has just fired.
                if (twoFingerTapState == TwoFingerTapState.IDLE ||
                    twoFingerTapState == TwoFingerTapState.HELD
                ) {
                    output += GestureEvent.Feedback("两指并拢已识别：左右挥切歌，上下拉住持续调音量")
                }
            }
            TwoFingerSwipeState.TRACKING -> {
                if (!pose) {
                    twoFingerState = TwoFingerSwipeState.IDLE
                    twoFingerStart = null
                    return
                }
                val start = twoFingerStart
                if (start != null) {
                    val dx = palm.x - start.x
                    val dy = palm.y - start.y
                    val elapsed = now - twoFingerStageAt
                    val horizontal = kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f
                    val vertical = kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.25f
                    val fired = when {
                        // Landmark y grows downward, so a wave up produces negative dy.
                        dx <= -.09f * movementScale && horizontal && elapsed <= 5000 ->
                            GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.LEFT)
                        dx >= .09f * movementScale && horizontal && elapsed <= 5000 ->
                            GestureEvent.TwoFingerSwipe(GestureEvent.TwoFingerDirection.RIGHT)
                        dy <= -.065f * movementScale && vertical && elapsed <= 5000 ->
                            GestureEvent.TwoFingerVolumeHold(true, GestureEvent.VolumeHoldPhase.START)
                        dy >= .055f * movementScale && vertical && elapsed <= 5000 ->
                            GestureEvent.TwoFingerVolumeHold(false, GestureEvent.VolumeHoldPhase.START)
                        elapsed > 5000 -> null
                        else -> null
                    }
                    if (fired != null || elapsed > 5000) {
                        if (fired != null) output += fired
                        twoFingerState = when (fired) {
                            is GestureEvent.TwoFingerVolumeHold -> if (fired.raise) TwoFingerSwipeState.VOLUME_UP else TwoFingerSwipeState.VOLUME_DOWN
                            null -> TwoFingerSwipeState.WAIT_RELEASE
                            else -> TwoFingerSwipeState.WAIT_RELEASE
                        }
                        if (fired is GestureEvent.TwoFingerVolumeHold) lastVolumeTickAt = now
                        twoFingerStart = null
                    }
                }
            }
            TwoFingerSwipeState.WAIT_RELEASE -> if (!pose) twoFingerState = TwoFingerSwipeState.IDLE
            TwoFingerSwipeState.VOLUME_UP, TwoFingerSwipeState.VOLUME_DOWN -> Unit
        }
    }
    private fun advanceActiveVolumeHold(
        pose: Boolean,
        now: Long,
        output: MutableList<GestureEvent>
    ): Boolean {
        if (twoFingerReleaseRequired) {
            if (!pose) {
                twoFingerReleaseRequired = false
                twoFingerState = TwoFingerSwipeState.IDLE
            } else return true
        }
        val raise = when (twoFingerState) {
            TwoFingerSwipeState.VOLUME_UP -> true
            TwoFingerSwipeState.VOLUME_DOWN -> false
            else -> return false
        }
        if (!pose) {
            output += GestureEvent.TwoFingerVolumeHold(raise, GestureEvent.VolumeHoldPhase.END)
            twoFingerState = TwoFingerSwipeState.IDLE
            lastVolumeTickAt = 0L
            return true
        }
        if (now - lastVolumeTickAt >= 400L) {
            output += GestureEvent.TwoFingerVolumeHold(raise, GestureEvent.VolumeHoldPhase.TICK)
            lastVolumeTickAt = now
        }
        return true
    }
    /**
     * G33 play/pause toggle: with the index+middle-together pose held, bend both fingers
     * and re-extend twice in a row ("tap twice"). Like the sibling swipe machine it never
     * consumes frames. Real double-taps are quick (~150ms per phase), so each pose segment
     * only needs >= 120ms (still long enough to reject single-frame pose flickers) and
     * each bend must resolve within 500ms.
     */
    private fun advanceTwoFingerTap(
        pose: Boolean,
        now: Long,
        output: MutableList<GestureEvent>
    ) {
        if (!features.twoFingerMedia) {
            twoFingerTapState = TwoFingerTapState.IDLE
            return
        }
        when (twoFingerTapState) {
            TwoFingerTapState.IDLE -> if (pose) {
                twoFingerTapState = TwoFingerTapState.HELD
                twoFingerTapPoseAt = now
            }
            TwoFingerTapState.HELD -> if (!pose) {
                if (now - twoFingerTapPoseAt >= 120) {
                    twoFingerTapState = TwoFingerTapState.BENT_ONCE
                    twoFingerTapBentAt = now
                } else {
                    twoFingerTapState = TwoFingerTapState.IDLE
                }
            }
            TwoFingerTapState.BENT_ONCE -> when {
                pose && now - twoFingerTapBentAt <= 500 -> {
                    twoFingerTapState = TwoFingerTapState.POSED_SECOND
                    twoFingerTapPoseAt = now
                    twoFingerTapFirstCycleAt = now
                }
                now - twoFingerTapBentAt > 500 -> twoFingerTapState = TwoFingerTapState.IDLE
            }
            TwoFingerTapState.POSED_SECOND -> when {
                !pose && now - twoFingerTapPoseAt >= 120 && now - twoFingerTapFirstCycleAt <= 1200 -> {
                    twoFingerTapState = TwoFingerTapState.BENT_TWICE
                    twoFingerTapBentAt = now
                }
                !pose -> twoFingerTapState = TwoFingerTapState.IDLE
                now - twoFingerTapFirstCycleAt > 1500 -> twoFingerTapState = TwoFingerTapState.IDLE
            }
            TwoFingerTapState.BENT_TWICE -> when {
                pose && now - twoFingerTapBentAt <= 500 -> {
                    output += GestureEvent.TwoFingerDoubleTap
                    twoFingerTapState = TwoFingerTapState.FIRED_WAIT
                }
                now - twoFingerTapBentAt > 500 -> twoFingerTapState = TwoFingerTapState.IDLE
            }
            TwoFingerTapState.FIRED_WAIT -> if (!pose) twoFingerTapState = TwoFingerTapState.IDLE
        }
    }
    /**
     * G16-G19: open palm, then fold to exactly N fingers (thumb closed). Holding the folded
     * pose for ~600ms fires [GestureEvent.OpenApp] with the matching slot. The open-palm stage
     * never swallows frames, so the G13 screenshot sequence can arm in parallel; once a target
     * pose is detected the sequence owns the hand until the pose is released.
     */
    private fun advanceAppSequence(
        palmOpen: Boolean,
        oneFinger: Boolean,
        twoFinger: Boolean,
        threeFinger: Boolean,
        fourFinger: Boolean,
        fingersSeen: String,
        now: Long,
        output: MutableList<GestureEvent>
    ): Boolean {
        val anyOpen = features.openApp1 || features.openApp2 || features.openApp3 || features.openApp4
        if (!anyOpen) {
            appSequence = AppSequence.IDLE
            return false
        }
        val slotPose = when {
            features.openApp1 && oneFinger -> 1
            features.openApp2 && twoFinger -> 2
            features.openApp3 && threeFinger -> 3
            features.openApp4 && fourFinger -> 4
            else -> 0
        }
        when (appSequence) {
            AppSequence.IDLE -> {
                if (palmOpen && now >= appCooldownUntil) {
                    appSequence = AppSequence.ARMED
                    appStageAt = now
                    appOpenPalmAt = now
                    // The G13 screenshot arm hint wins when both families share the open palm.
                    if (!features.screenshot) {
                        output += GestureEvent.Feedback("五指张开已识别：收起手指保留 1–4 指打开应用")
                    }
                }
                return false
            }
            AppSequence.ARMED -> {
                if (palmOpen) {
                    appOpenPalmAt = now
                    if (now - appStageAt > 5000) appSequence = AppSequence.WAIT_RELEASE
                    return false
                }
                if (now - appOpenPalmAt > 800) {
                    // The open palm was lost for too long: this frame belongs to other gestures.
                    appSequence = AppSequence.IDLE
                    return false
                }
                if (slotPose != 0) {
                    appSequence = AppSequence.HOLDING
                    appHoldSlot = slotPose
                    appHoldAt = now
                    val names = arrayOf("", "食指", "两指", "三指", "四指")
                    output += GestureEvent.Feedback("已保留${names[slotPose]}：请保持")
                    return true
                }
                // Transitional pose while folding (including the fist of the G13 sequence).
                return false
            }
            AppSequence.HOLDING -> {
                // Folding naturally crosses other slots on the way (e.g. the thumb folds
                // first -> slot 4 shows up, then the pinky joins -> slot 3). Follow the
                // latest slot and restart the hold timer instead of cancelling outright.
                if (slotPose != 0 && slotPose != appHoldSlot) {
                    appHoldSlot = slotPose
                    appHoldAt = now
                    val names = arrayOf("", "食指", "两指", "三指", "四指")
                    if (now - lastFeedbackAt > 400) {
                        lastFeedbackAt = now
                        output += GestureEvent.Feedback("已保留${names[slotPose]}：请保持")
                    }
                }
                if (slotPose == appHoldSlot) {
                    if (now - appHoldAt >= 600) {
                        output += GestureEvent.OpenApp(appHoldSlot)
                        appSequence = AppSequence.WAIT_RELEASE
                        appHoldAt = now
                        appCooldownUntil = now + localRepeatGuardMs
                    }
                    return true
                }
                // No slot pose anymore (fist, half-folded hand, palm...): cancel after grace.
                // Diagnose which fingers were still seen so the user knows what to adjust.
                if (now - appHoldAt > 500) {
                    appSequence = AppSequence.WAIT_RELEASE
                    appHoldAt = now
                    output += GestureEvent.Feedback("姿势已改变，已取消（识别到：$fingersSeen）")
                }
                return true
            }
            AppSequence.WAIT_RELEASE -> {
                // Wait for the hand to relax (no folded slot pose, no open palm) before re-arming.
                if (now - appHoldAt > 600 && !palmOpen && slotPose == 0) {
                    appSequence = AppSequence.IDLE
                }
                return true
            }
        }
    }

    private fun advanceOpenPalmSequence(
        directionOpen: Boolean,
        screenshotOpen: Boolean,
        indexOnly: Boolean,
        indexAngleDegrees: Float,
        fist: Boolean,
        palm: Point,
        now: Long,
        output: MutableList<GestureEvent>
    ): Boolean {
        val anyDirectional = features.indexVerticalScroll || features.palmVerticalScroll ||
            features.palmLeftScroll || features.indexLeftScroll ||
            features.palmRightScroll || features.indexRightScroll
        if (!anyDirectional && !features.screenshot) return false
        when (screenshotSequence) {
            ScreenshotSequence.IDLE -> {
                if (features.indexVerticalScroll && indexOnly && kotlin.math.abs(indexAngleDegrees) <= 35f) {
                    screenshotSequence = ScreenshotSequence.INDEX_FINGER_SCROLL
                    screenshotStageAt = now
                    indexScrollStartAngle = indexAngleDegrees
                    output += GestureEvent.Feedback("水平食指已识别：请上挑或下挑")
                    return true
                }
                if (indexOnly && kotlin.math.abs(indexAngleDegrees) >= 65f &&
                    (features.indexLeftScroll || features.indexRightScroll)) {
                    screenshotSequence = ScreenshotSequence.INDEX_HORIZONTAL_SWIPE
                    screenshotStageAt = now
                    openPalmStart = palm
                    output += GestureEvent.Feedback("竖直食指已识别：向左或向右轻挑滚动页面")
                    return true
                }
                if (features.screenshot && screenshotOpen && now >= screenshotCooldownUntil) {
                    screenshotSequence = ScreenshotSequence.WAIT_FIST
                    screenshotStageAt = now
                    screenshotArmedAt = 0L
                    lastOpenPalmAt = now
                    output += GestureEvent.Feedback("五指张开已识别：请保持")
                    return true
                }
                if (directionOpen && (features.palmVerticalScroll || features.palmLeftScroll || features.palmRightScroll)) {
                    screenshotSequence = ScreenshotSequence.OPEN_CANDIDATE
                    screenshotStageAt = now
                    openPalmStart = palm
                    openPalmAxis = PalmAxis.NONE
                    lastOpenPalmAt = now
                    output += GestureEvent.Feedback("食指、中指、无名指和小指并拢：请挥动")
                    return true
                }
            }
            ScreenshotSequence.OPEN_CANDIDATE -> {
                if (directionOpen) lastOpenPalmAt = now
                if (!directionOpen) {
                    // Landmark detection can briefly lose one finger during a fast wave.
                    if (now - lastOpenPalmAt <= 260) return true
                    screenshotSequence = ScreenshotSequence.IDLE
                    openPalmStart = null
                    openPalmAxis = PalmAxis.NONE
                    return false
                }
                val elapsed = now - screenshotStageAt
                if (elapsed < openPalmSettleMs) {
                    // Ignore the small sideways jump that commonly occurs while the palm opens.
                    openPalmStart = palm
                    return true
                }
                val start = openPalmStart
                if (start != null && now >= backCooldownUntil) {
                    val dx = palm.x - start.x
                    val dy = palm.y - start.y
                    val absDx = kotlin.math.abs(dx)
                    val absDy = kotlin.math.abs(dy)
                    if (openPalmAxis == PalmAxis.NONE && kotlin.math.max(absDx, absDy) >= .035f * movementScale) {
                        openPalmAxis = when {
                            absDx >= absDy * 1.45f -> PalmAxis.HORIZONTAL
                            absDy >= absDx * 1.35f -> PalmAxis.VERTICAL
                            else -> PalmAxis.NONE
                        }
                    }
                    if (openPalmAxis == PalmAxis.HORIZONTAL && features.palmLeftScroll && dx <= -.075f * movementScale && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = true, source = GestureEvent.MotionSource.PALM)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        backCooldownUntil = now + localRepeatGuardMs
                        return true
                    }
                    if (openPalmAxis == PalmAxis.HORIZONTAL && features.palmRightScroll && dx >= .075f * movementScale && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = false, source = GestureEvent.MotionSource.PALM)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        backCooldownUntil = now + localRepeatGuardMs
                        return true
                    }
                    if (openPalmAxis == PalmAxis.VERTICAL && features.palmVerticalScroll && dy <= -.065f * movementScale && elapsed <= 5000) {
                        output += GestureEvent.Swipe(up = true, source = GestureEvent.MotionSource.PALM)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        return true
                    }
                    if (openPalmAxis == PalmAxis.VERTICAL && features.palmVerticalScroll && dy >= .055f * movementScale && elapsed <= 5000) {
                        output += GestureEvent.Swipe(up = false, source = GestureEvent.MotionSource.PALM)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        return true
                    }
                    if (elapsed > 5000) {
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                    }
                }
                return true
            }
            ScreenshotSequence.INDEX_HORIZONTAL_SWIPE -> {
                if (!indexOnly || kotlin.math.abs(indexAngleDegrees) < 55f) {
                    screenshotSequence = ScreenshotSequence.IDLE
                    openPalmStart = null
                    return false
                }
                val start = openPalmStart
                if (start != null) {
                    val dx = palm.x - start.x
                    val dy = palm.y - start.y
                    val elapsed = now - screenshotStageAt
                    if (features.indexLeftScroll && dx <= -.05f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = true, source = GestureEvent.MotionSource.INDEX_FINGER)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        return true
                    }
                    if (features.indexRightScroll && dx >= .05f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = false, source = GestureEvent.MotionSource.INDEX_FINGER)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        return true
                    }
                    if (elapsed > 5000) {
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                    }
                }
                return true
            }
            ScreenshotSequence.INDEX_FINGER_SCROLL -> {
                if (!indexOnly) {
                    screenshotSequence = ScreenshotSequence.IDLE
                    return false
                }
                val elapsed = now - screenshotStageAt
                val triggerAngle = 24f * movementScale
                val angleChange = indexAngleDegrees - indexScrollStartAngle
                if (angleChange <= -triggerAngle && elapsed <= 5000) {
                    output += GestureEvent.Swipe(up = true, source = GestureEvent.MotionSource.INDEX_FINGER)
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    return true
                }
                if (angleChange >= triggerAngle && elapsed <= 5000) {
                    output += GestureEvent.Swipe(up = false, source = GestureEvent.MotionSource.INDEX_FINGER)
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    return true
                }
                if (elapsed > 5000) {
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                }
                return true
            }
            ScreenshotSequence.WAIT_FIST -> {
                if (screenshotOpen) {
                    lastOpenPalmAt = now
                    if (screenshotArmedAt == 0L && now - screenshotStageAt >= 300) {
                        screenshotArmedAt = now
                        output += GestureEvent.Feedback("五指张开已确认：请握拳", 100)
                    }
                    if (now - screenshotStageAt > 5000) screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    return true
                }
                if (fist && screenshotArmedAt != 0L) {
                    screenshotSequence = ScreenshotSequence.FIST_HOLD
                    screenshotStageAt = now
                    screenshotArmedAt = 0L
                    output += GestureEvent.Feedback("请短暂保持握拳")
                    return true
                }
                if (now - lastOpenPalmAt <= 260) return true
                if (now - screenshotStageAt > 5000 || !fist) {
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    return true
                }
                return true
            }
            ScreenshotSequence.FIST_HOLD -> {
                if (fist) {
                    if (now - screenshotStageAt >= 180 && screenshotArmedAt == 0L) {
                        screenshotArmedAt = now
                        output += GestureEvent.Feedback("握拳已识别：请再次张开五指", 100)
                    }
                    return true
                }
                if (screenshotOpen && screenshotArmedAt != 0L) {
                    screenshotSequence = ScreenshotSequence.WAIT_FINAL_OPEN
                    screenshotStageAt = now
                    lastOpenPalmAt = now
                    output += GestureEvent.Feedback("请保持五指张开")
                    return true
                }
                if (now - screenshotStageAt > 1800) screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                return true
            }
            ScreenshotSequence.WAIT_FINAL_OPEN -> {
                if (screenshotOpen) {
                    lastOpenPalmAt = now
                    if (now - screenshotStageAt >= 250) {
                        output += GestureEvent.Screenshot
                        screenshotSequence = ScreenshotSequence.WAIT_SCREENSHOT_RELEASE
                        screenshotCooldownUntil = now + localRepeatGuardMs
                    }
                    return true
                }
                if (now - lastOpenPalmAt > 220) screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                return true
            }
            ScreenshotSequence.WAIT_RELEASE -> {
                if (!directionOpen && !screenshotOpen && !indexOnly && !fist) screenshotSequence = ScreenshotSequence.IDLE
                return true
            }
            ScreenshotSequence.WAIT_SCREENSHOT_RELEASE -> {
                if (!screenshotOpen && !fist) screenshotSequence = ScreenshotSequence.IDLE
                return true
            }
        }
        return false
    }
    private fun resetTransient() {
        pinch = Pinch.READY
        indexClick = IndexClick.READY
        indexClickAt = 0
        indexClickPoint = null
        screenshotSequence = ScreenshotSequence.IDLE
        appSequence = AppSequence.IDLE
        candidateAt = 0
        releaseAt = 0
        screenshotStageAt = 0
        screenshotArmedAt = 0
        openPalmStart = null
        openPalmAxis = PalmAxis.NONE
        lastOpenPalmAt = 0
        indexScrollStartAngle = 0f
        vHoldAt = 0
        vLatched = false
        thumbsUpHold = StaticHold.READY
        thumbsUpHoldAt = 0L
        okHold = StaticHold.READY
        okHoldAt = 0L
        fistHold = StaticHold.READY
        fistHoldAt = 0L
        pinkyHold = StaticHold.READY
        pinkyHoldAt = 0L
        lotusHold = StaticHold.READY
        lotusHoldAt = 0L
        orchidHold = StaticHold.READY
        orchidHoldAt = 0L
        leftLHold = StaticHold.READY
        leftLHoldAt = 0L
        lShapeHold = StaticHold.READY
        lShapeHoldAt = 0L
        cShapeHold = StaticHold.READY
        cShapeHoldAt = 0L
        loveHold = StaticHold.READY
        loveHoldAt = 0L
        six666Hold = StaticHold.READY
        six666HoldAt = 0L
        clawDragState = ClawDragState.READY
        clawConfirmAt = 0L
        clawAnchor = null
        clawPalmAt = null
        twoFingerState = TwoFingerSwipeState.IDLE
        twoFingerStart = null
        twoFingerStageAt = 0L
        lastVolumeTickAt = 0L
        twoFingerTapState = TwoFingerTapState.IDLE
        twoFingerTapPoseAt = 0L
        twoFingerTapBentAt = 0L
        twoFingerTapFirstCycleAt = 0L

        lastFeedbackAt = 0
    }
    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
    private fun vectorAngleDegrees(aStart: Point, aEnd: Point, bStart: Point, bEnd: Point): Float {
        val ax = aEnd.x - aStart.x
        val ay = aEnd.y - aStart.y
        val bx = bEnd.x - bStart.x
        val by = bEnd.y - bStart.y
        val magnitude = (hypot(ax, ay) * hypot(bx, by)).coerceAtLeast(.000001f)
        val cosine = ((ax * bx + ay * by) / magnitude).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cosine).toDouble()).toFloat()
    }
}
