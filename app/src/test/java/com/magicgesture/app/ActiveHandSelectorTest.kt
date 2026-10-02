package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActiveHandSelectorTest {
    @Test fun nearestHandOwnsControlEvenWhenFarHandKeepsGesturing() {
        val selector = ActiveHandSelector()
        val near = hand(centerX = .35f, scale = .20f, marker = .31f)
        val far = hand(centerX = .72f, scale = .10f, marker = .79f)

        assertNull(selector.select(listOf(near, far), 0L))
        val selected = selector.select(listOf(near, far), 200L)!!
        assertEquals(.31f, selected.points[4].x)
        assertFalse(selected.ownershipChanged)

        repeat(10) { frame ->
            val stillNear = hand(.35f, .20f, marker = .31f)
            val movingFar = hand(.72f, .10f, marker = .80f + frame * .001f)
            assertEquals(.31f, selector.select(listOf(movingFar, stillNear), 250L + frame * 50L)!!.points[4].x)
        }
    }

    @Test fun challengerMustStayTwentyPercentLargerForTwoHundredFiftyMilliseconds() {
        val selector = ActiveHandSelector()
        val original = hand(.30f, .15f, marker = .32f, handedness = "Left")
        assertNull(selector.select(listOf(original), 0L))
        selector.select(listOf(original), 200L)

        val challenger = hand(.72f, .20f, marker = .77f, handedness = "Right")
        assertEquals(.32f, selector.select(listOf(original, challenger), 250L)!!.points[4].x)
        assertEquals(.32f, selector.select(listOf(original, challenger), 450L)!!.points[4].x)
        val takeover = selector.select(listOf(original, challenger), 500L)!!
        assertEquals(.77f, takeover.points[4].x)
        assertTrue(takeover.ownershipChanged)
    }

    @Test fun smallScaleNoiseNeverCausesOwnershipFlapping() {
        val selector = ActiveHandSelector()
        var owner = hand(.35f, .15f, marker = .33f, handedness = "Left")
        assertNull(selector.select(listOf(owner), 0L))
        selector.select(listOf(owner), 200L)

        repeat(20) { frame ->
            owner = hand(.35f, if (frame % 2 == 0) .15f else .145f, marker = .33f, handedness = "Left")
            val other = hand(.70f, if (frame % 2 == 0) .16f else .155f, marker = .74f, handedness = "Right")
            val selected = selector.select(listOf(other, owner), 250L + frame * 50L)!!
            assertEquals(.33f, selected.points[4].x)
            assertFalse(selected.ownershipChanged)
        }
    }

    @Test fun missingOwnerIsNotReplacedUntilThreeHundredMilliseconds() {
        val selector = ActiveHandSelector()
        val owner = hand(.30f, .18f, marker = .34f, handedness = "Left")
        // Same handedness is the harder case: spatial tracking must still reject this other person.
        val replacement = hand(.70f, .16f, marker = .75f, handedness = "Left")
        assertNull(selector.select(listOf(owner), 0L))
        selector.select(listOf(owner), 200L)

        assertNull(selector.select(listOf(replacement), 250L))
        assertNull(selector.select(listOf(replacement), 500L))
        val selected = selector.select(listOf(replacement), 550L)!!
        assertEquals(.75f, selected.points[4].x)
        assertTrue(selected.ownershipChanged)
    }

    @Test fun resetRequiresFreshAcquisition() {
        val selector = ActiveHandSelector()
        val hand = hand(.5f, .18f, marker = .55f)
        assertNull(selector.select(listOf(hand), 0L))
        assertTrue(selector.select(listOf(hand), 200L) != null)
        selector.reset()
        assertNull(selector.select(listOf(hand), 250L))
        assertTrue(selector.select(listOf(hand), 450L) != null)
    }

    private fun hand(
        centerX: Float,
        scale: Float,
        marker: Float,
        handedness: String? = null
    ): HandCandidate {
        val points = MutableList(21) { Point(centerX, .5f) }
        points[0] = Point(centerX, .5f + scale * .5f)
        points[5] = Point(centerX - scale * .5f, .5f)
        points[9] = Point(centerX, .5f - scale * .5f)
        points[13] = Point(centerX + scale * .25f, .5f)
        points[17] = Point(centerX + scale * .5f, .5f)
        points[4] = Point(marker, .4f)
        return HandCandidate(points, handedness)
    }
}
