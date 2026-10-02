package com.magicgesture.app

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/** One MediaPipe hand candidate before gesture recognition. */
data class HandCandidate(
    val points: List<Point>,
    val handedness: String? = null
)

data class ActiveHandSelection(
    val points: List<Point>,
    /** True when recognition must discard every transient state before consuming this hand. */
    val ownershipChanged: Boolean
)

/**
 * Gives exactly one visually nearest hand exclusive control.
 *
 * Depth is estimated only from the palm skeleton (wrist and MCP joints), not fingertips, so a
 * gesture opening or folding its fingers does not by itself steal control. A challenger must be
 * clearly larger for a sustained interval; a briefly occluded owner keeps its identity.
 */
class ActiveHandSelector(
    private val acquireMs: Long = 200L,
    private val takeoverRatio: Float = 1.20f,
    private val takeoverMs: Long = 250L,
    private val missingTakeoverMs: Long = 300L
) {
    private data class Snapshot(
        val candidate: HandCandidate,
        val center: Point,
        val scale: Float
    )

    private var active: Snapshot? = null
    private var acquisition: Snapshot? = null
    private var acquisitionAt = 0L
    private var challenger: Snapshot? = null
    private var challengerAt = 0L
    private var missingSince = 0L

    @Synchronized
    fun select(candidates: List<HandCandidate>, now: Long): ActiveHandSelection? {
        val hands = candidates.mapNotNull(::snapshot)
        val owner = active
        if (owner == null) return acquire(hands, now)

        val matchedOwner = bestIdentityMatch(owner, hands)
        if (matchedOwner == null) {
            challenger = null
            challengerAt = 0L
            if (missingSince == 0L) missingSince = now
            if (now - missingSince < missingTakeoverMs || hands.isEmpty()) return null

            val nearest = hands.maxByOrNull { it.scale } ?: return null
            active = nearest
            missingSince = 0L
            acquisition = null
            return ActiveHandSelection(nearest.candidate.points, ownershipChanged = true)
        }

        missingSince = 0L
        val others = hands.filterNot { it === matchedOwner }
        val nearestChallenger = others.maxByOrNull { it.scale }
        if (nearestChallenger != null && nearestChallenger.scale >= matchedOwner.scale * takeoverRatio) {
            val previousChallenger = challenger
            if (previousChallenger == null || !sameIdentity(previousChallenger, nearestChallenger)) {
                challenger = nearestChallenger
                challengerAt = now
            } else {
                challenger = nearestChallenger
                if (now - challengerAt >= takeoverMs) {
                    active = nearestChallenger
                    challenger = null
                    challengerAt = 0L
                    return ActiveHandSelection(nearestChallenger.candidate.points, ownershipChanged = true)
                }
            }
        } else {
            challenger = null
            challengerAt = 0L
        }

        active = matchedOwner
        return ActiveHandSelection(matchedOwner.candidate.points, ownershipChanged = false)
    }

    @Synchronized
    fun reset() {
        active = null
        acquisition = null
        acquisitionAt = 0L
        challenger = null
        challengerAt = 0L
        missingSince = 0L
    }

    private fun acquire(hands: List<Snapshot>, now: Long): ActiveHandSelection? {
        val nearest = hands.maxByOrNull { it.scale } ?: run {
            acquisition = null
            acquisitionAt = 0L
            return null
        }
        val previous = acquisition
        if (previous == null || !sameIdentity(previous, nearest)) {
            acquisition = nearest
            acquisitionAt = now
            return null
        }
        acquisition = nearest
        if (now - acquisitionAt < acquireMs) return null
        active = nearest
        acquisition = null
        acquisitionAt = 0L
        return ActiveHandSelection(nearest.candidate.points, ownershipChanged = false)
    }

    private fun snapshot(candidate: HandCandidate): Snapshot? {
        if (candidate.points.size <= 17) return null
        val palmIndices = intArrayOf(0, 5, 9, 13, 17)
        val center = Point(
            palmIndices.sumOf { candidate.points[it].x.toDouble() }.toFloat() / palmIndices.size,
            palmIndices.sumOf { candidate.points[it].y.toDouble() }.toFloat() / palmIndices.size
        )
        val palmWidth = distance(candidate.points[5], candidate.points[17])
        val palmHeight = distance(candidate.points[0], candidate.points[9])
        val scale = sqrt((palmWidth * palmHeight).coerceAtLeast(0f))
        if (!scale.isFinite() || scale < .001f) return null
        return Snapshot(candidate, center, scale)
    }

    private fun bestIdentityMatch(owner: Snapshot, hands: List<Snapshot>): Snapshot? {
        if (hands.isEmpty()) return null
        val pool = owner.candidate.handedness?.let { side ->
            hands.filter { it.candidate.handedness == null || it.candidate.handedness == side }
        } ?: hands
        // If MediaPipe confidently reports only the opposite hand, the owner is absent. Falling
        // back to that hand would bypass the 300ms disappearance guard and transfer state.
        if (pool.isEmpty()) return null
        val best = pool.minByOrNull { identityDistance(owner, it) } ?: return null
        return best.takeIf { identityDistance(owner, it) <= 1.5f }
    }

    private fun sameIdentity(a: Snapshot, b: Snapshot): Boolean {
        if (a.candidate.handedness != null && b.candidate.handedness != null &&
            a.candidate.handedness != b.candidate.handedness) return false
        return identityDistance(a, b) <= 1.5f
    }

    private fun identityDistance(a: Snapshot, b: Snapshot): Float {
        val averageScale = ((a.scale + b.scale) * .5f).coerceAtLeast(.001f)
        val centerDistance = distance(a.center, b.center) / averageScale
        val scaleDifference = abs(a.scale - b.scale) / averageScale
        return centerDistance + scaleDifference * .5f
    }

    private fun distance(a: Point, b: Point): Float = hypot(a.x - b.x, a.y - b.y)
}
