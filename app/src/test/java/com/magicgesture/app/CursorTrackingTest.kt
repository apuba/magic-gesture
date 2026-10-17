package com.magicgesture.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CursorTrackingTest {
    @Test
    fun smallMovementKeepsStableDamping() {
        assertEquals(.35f, cursorTrackingAlpha(0f), .0001f)
        assertTrue(cursorTrackingAlpha(.01f) < .40f)
    }

    @Test
    fun deliberateMovementCatchesUpAndRemainsBounded() {
        assertTrue(cursorTrackingAlpha(.08f) > .55f)
        assertEquals(.88f, cursorTrackingAlpha(.50f), .0001f)
    }

    @Test
    fun tinyLandmarkNoiseDoesNotMoveTheCursor() {
        val old = Point(.5f, .5f)
        assertEquals(old, smoothCursor(old, Point(.504f, .503f)))
        assertEquals(old.x, smoothCursor(old, Point(.51f, .52f)).x, .0001f)
        assertTrue(smoothCursor(old, Point(.513f, .5f)).x > old.x)
        assertTrue(smoothCursor(old, Point(.5f, .007f + old.y)).y > old.y)
    }

    @Test
    fun fastMotionKeepsTheSameResponsiveCeilingOnBothAxes() {
        val old = Point(0f, 0f)
        assertEquals(.88f, smoothCursor(old, Point(1f, 0f)).x, .0001f)
        assertEquals(.88f, smoothCursor(old, Point(0f, 1f)).y, .0001f)
    }

    @Test
    fun fingertipOutlierIsRejectedButWholeHandTranslationPasses() {
        val previousTarget = Point(.8f, .4f)
        val previousPalm = Point(.65f, .6f)
        assertEquals(
            previousTarget,
            stableCursorTarget(previousTarget, previousPalm, Point(.05f, .95f), Point(.63f, .61f))
        )
        assertEquals(
            Point(.4f, .4f),
            stableCursorTarget(previousTarget, previousPalm, Point(.4f, .4f), Point(.37f, .6f))
        )
    }

    @Test
    fun cursorPredictionRequiresContinuousDirectionAndCapsItsLead() {
        val first = predictCursorTarget(Point(.20f, .50f), Point(0f, 0f), Point(.30f, .50f))
        assertEquals(Point(.30f, .50f), first.target)

        val continuing = predictCursorTarget(Point(.30f, .50f), first.motion, Point(.40f, .50f))
        assertEquals(.455f, continuing.target.x, .0001f)
        assertEquals(.50f, continuing.target.y, .0001f)

        val capped = predictCursorTarget(Point(.40f, .50f), continuing.motion, Point(.80f, .50f))
        assertEquals(.88f, capped.target.x, .0001f)

        val reversed = predictCursorTarget(Point(.80f, .50f), capped.motion, Point(.60f, .50f))
        assertEquals(Point(.60f, .50f), reversed.target)

        val stopped = predictCursorTarget(Point(.60f, .50f), reversed.motion, Point(.60f, .50f))
        assertEquals(Point(.60f, .50f), stopped.target)
    }
}
