package com.magicgesture.app

import kotlin.math.hypot
import kotlin.math.atan2

/** Pure logic. Coordinates are normalized to [0,1], timestamps are monotonic milliseconds. */
data class Point(val x: Float, val y: Float)
sealed interface GestureEvent {
    data class Cursor(val x: Float, val y: Float) : GestureEvent
    data class Click(val x: Float, val y: Float) : GestureEvent
    data class Swipe(val up: Boolean) : GestureEvent
    data class HorizontalSwipe(val left: Boolean) : GestureEvent
    data object Screenshot : GestureEvent
    data object Like : GestureEvent
    data object Back : GestureEvent
    data object Home : GestureEvent
    data object Recents : GestureEvent
    data class Feedback(val message: String, val progress: Int? = null) : GestureEvent
}
class GestureEngine(
    private val movementScale: Float = 1f,
    private var features: GestureFeatureConfig = GestureFeatureConfig()
) {
    private enum class Pinch { READY, CANDIDATE, FIRED }
    private enum class IndexClick { READY, STABILIZING, ARMED, BENT }
    private enum class ScreenshotSequence {
        IDLE, OPEN_CANDIDATE, WAIT_FIST, FIST_HOLD, INDEX_FINGER_SCROLL, INDEX_HORIZONTAL_SWIPE,
        WAIT_RELEASE, WAIT_SCREENSHOT_RELEASE
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
    private var lastOpenPalmAt = 0L
    private var indexScrollStartAngle = 0f
    private var vHoldAt = 0L
    private var vLatched = false
    private var lastFeedbackAt = 0L
    private var cooldownUntil = 0L
    private val actionProtectionMs = 2000L
    private var lastSeenAt = 0L
    private var smoothed: Point? = null
    private var paused = false

    @Synchronized fun resume() { paused = false; resetTransient() }
    @Synchronized fun stop() { paused = true; resetTransient(); smoothed = null }
    @Synchronized fun updateFeatures(value: GestureFeatureConfig) { features = value; resetTransient() }
    @Synchronized fun lost(now: Long) { if (now - lastSeenAt >= 300) { resetTransient(); smoothed = null } }
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
        output += GestureEvent.Cursor(cursor.x, cursor.y)
        val handScale = dist(points[5], points[17]).coerceAtLeast(.001f)
        val ratio = dist(points[4], points[8]) / handScale
        val fourFingersOpen = listOf(8 to 6, 12 to 10, 16 to 14, 20 to 18).all { (tipIndex, pipIndex) ->
            dist(points[tipIndex], points[0]) > dist(points[pipIndex], points[0]) * 1.10f
        }
        val thumbOpen = dist(points[4], points[5]) > dist(points[3], points[5]) * 1.05f
        val fiveFingersOpen = fourFingersOpen && (thumbOpen || ratio > .32f)
        val screenshotPalmOpen = fourFingersOpen && thumbOpen && ratio > .45f
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
        val middleFolded = dist(points[12], points[0]) < dist(points[10], points[0]) * 1.08f
        val ringFolded = dist(points[16], points[0]) < dist(points[14], points[0]) * 1.08f
        val pinkyFolded = dist(points[20], points[0]) < dist(points[18], points[0]) * 1.08f
        val indexOnlyPose = indexOpen && middleFolded && ringFolded && pinkyFolded
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
        val vPose = indexOpen && middleOpen && ringFolded && pinkyFolded && dist(points[8], points[12]) / handScale > .28f
        if (now < cooldownUntil) return output
        if (features.recents && vPose) {
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
                    output += GestureEvent.Recents
                    cooldownUntil = now + actionProtectionMs
                    vLatched = true
                }
            }
            return output
        } else {
            vHoldAt = 0L
            vLatched = false
        }
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
                        cooldownUntil = now + actionProtectionMs
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
        if (advanceOpenPalmSequence(fiveFingersOpen, screenshotPalmOpen, indexOnlyPose, indexAngleDegrees, fist, palm, now, output)) {
            if (output.any { it is GestureEvent.Swipe || it is GestureEvent.HorizontalSwipe }) {
                indexClick = IndexClick.READY
                indexClickPoint = null
            }
            pinch = Pinch.READY
            candidateAt = 0L
            releaseAt = 0L
            return output
        }
        // Finger-heart uses thumb/index proximity independently from index-bend clicking.
        val fingerHeartPose = ratio < .40f
        val fingersClearlyReleased = ratio > .58f
        if (features.like) when (pinch) {
            Pinch.READY -> if (fingerHeartPose && now >= cooldownUntil) {
                pinch = Pinch.CANDIDATE
                candidateAt = now
                releaseAt = 0L
            }
            Pinch.CANDIDATE -> when {
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
                    cooldownUntil = now + actionProtectionMs
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
    private fun advanceOpenPalmSequence(
        open: Boolean,
        screenshotOpen: Boolean,
        indexOnly: Boolean,
        indexAngleDegrees: Float,
        fist: Boolean,
        palm: Point,
        now: Long,
        output: MutableList<GestureEvent>
    ): Boolean {
        if (!features.scroll && !features.back && !features.home && !features.screenshot) return false
        when (screenshotSequence) {
            ScreenshotSequence.IDLE -> {
                if (features.scroll && indexOnly && kotlin.math.abs(indexAngleDegrees) <= 35f) {
                    screenshotSequence = ScreenshotSequence.INDEX_FINGER_SCROLL
                    screenshotStageAt = now
                    indexScrollStartAngle = indexAngleDegrees
                    output += GestureEvent.Feedback("水平食指已识别：请上挑或下挑")
                    return true
                }
                if (indexOnly && kotlin.math.abs(indexAngleDegrees) >= 65f && (features.back || features.home)) {
                    screenshotSequence = ScreenshotSequence.INDEX_HORIZONTAL_SWIPE
                    screenshotStageAt = now
                    openPalmStart = palm
                    output += GestureEvent.Feedback("竖直食指已识别：请向左或向右移动")
                    return true
                }
                if (open && (features.scroll || features.back || features.home || features.screenshot) && now >= screenshotCooldownUntil) {
                    screenshotSequence = ScreenshotSequence.OPEN_CANDIDATE
                    screenshotStageAt = now
                    screenshotArmedAt = 0L
                    openPalmStart = palm
                    lastOpenPalmAt = now
                    output += GestureEvent.Feedback("张掌已识别：挥动或握拳")
                    return true
                }
            }
            ScreenshotSequence.OPEN_CANDIDATE -> {
                if (open) lastOpenPalmAt = now
                if (!open) {
                    // Allow a brief transition while the fingers close into a fist.
                    if (features.screenshot && fist && screenshotArmedAt != 0L) {
                        screenshotSequence = ScreenshotSequence.FIST_HOLD
                        screenshotStageAt = now
                        screenshotArmedAt = 0L
                        openPalmStart = palm
                        output += GestureEvent.Feedback("请短暂保持握拳")
                        return true
                    }
                    // A quick close is more likely a pinch/finger-heart than a screenshot.
                    if (fist) {
                        screenshotSequence = ScreenshotSequence.IDLE
                        openPalmStart = null
                        return false
                    }
                    // Landmark detection can briefly lose one finger during a fast wave.
                    if (now - lastOpenPalmAt <= 260) return true
                    screenshotSequence = ScreenshotSequence.IDLE
                    openPalmStart = null
                    return false
                }
                val start = openPalmStart
                if (start != null && now >= backCooldownUntil) {
                    val dx = palm.x - start.x
                    val dy = palm.y - start.y
                    val elapsed = now - screenshotStageAt
                    if (features.back && dx <= -.075f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.08f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = true)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        backCooldownUntil = now + actionProtectionMs
                        cooldownUntil = backCooldownUntil
                        return true
                    }
                    if (features.home && dx >= .075f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.08f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = false)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        backCooldownUntil = now + actionProtectionMs
                        cooldownUntil = backCooldownUntil
                        return true
                    }
                    if (features.scroll && dy <= -.065f * movementScale && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.08f && elapsed <= 5000) {
                        output += GestureEvent.Swipe(up = true)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        cooldownUntil = now + actionProtectionMs
                        return true
                    }
                    if (features.scroll && dy >= .055f * movementScale && kotlin.math.abs(dy) > kotlin.math.abs(dx) * 1.05f && elapsed <= 5000) {
                        output += GestureEvent.Swipe(up = false)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        cooldownUntil = now + actionProtectionMs
                        return true
                    }
                    val movement = kotlin.math.max(kotlin.math.abs(dx), kotlin.math.abs(dy))
                    if (features.screenshot && screenshotOpen && screenshotArmedAt == 0L && elapsed >= 700 && movement < .035f) {
                        screenshotArmedAt = now
                        output += GestureEvent.Feedback("截图已准备：请握拳", 100)
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
                    if (features.back && dx <= -.10f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = true)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        cooldownUntil = now + actionProtectionMs
                        return true
                    }
                    if (features.home && dx >= .10f * movementScale && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.25f && elapsed <= 5000) {
                        output += GestureEvent.HorizontalSwipe(left = false)
                        screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                        openPalmStart = null
                        cooldownUntil = now + actionProtectionMs
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
                    output += GestureEvent.Swipe(up = true)
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    cooldownUntil = now + actionProtectionMs
                    return true
                }
                if (angleChange >= triggerAngle && elapsed <= 5000) {
                    output += GestureEvent.Swipe(up = false)
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    cooldownUntil = now + actionProtectionMs
                    return true
                }
                if (elapsed > 5000) {
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                }
                return true
            }
            ScreenshotSequence.WAIT_FIST -> {
                if (now - screenshotArmedAt > 5000) {
                    screenshotSequence = ScreenshotSequence.WAIT_RELEASE
                    return true
                }
                if (fist) {
                    screenshotSequence = ScreenshotSequence.FIST_HOLD
                    screenshotStageAt = now
                    screenshotArmedAt = 0L
                    openPalmStart = palm
                    output += GestureEvent.Feedback("请短暂保持握拳")
                }
                return true
            }
            ScreenshotSequence.FIST_HOLD -> {
                if (!fist) {
                    if (now - screenshotStageAt >= 180) {
                        output += GestureEvent.Screenshot
                        screenshotSequence = ScreenshotSequence.WAIT_SCREENSHOT_RELEASE
                        screenshotCooldownUntil = now + actionProtectionMs
                        cooldownUntil = screenshotCooldownUntil
                    } else {
                        screenshotSequence = ScreenshotSequence.WAIT_FIST
                        screenshotArmedAt = now
                    }
                    return true
                }
                if (now - screenshotStageAt >= 180 && screenshotArmedAt == 0L) {
                    screenshotArmedAt = now
                    output += GestureEvent.Feedback("握拳已识别：松开完成截图", 100)
                }
                return true
            }
            ScreenshotSequence.WAIT_RELEASE -> {
                if (!open && !indexOnly) screenshotSequence = ScreenshotSequence.IDLE
                return true
            }
            ScreenshotSequence.WAIT_SCREENSHOT_RELEASE -> {
                if (!open && !fist) screenshotSequence = ScreenshotSequence.IDLE
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
        candidateAt = 0
        releaseAt = 0
        screenshotStageAt = 0
        screenshotArmedAt = 0
        openPalmStart = null
        lastOpenPalmAt = 0
        indexScrollStartAngle = 0f
        vHoldAt = 0
        vLatched = false
        lastFeedbackAt = 0
    }
    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
