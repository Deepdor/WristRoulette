package com.example.wristroulette.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

class BallPhysicsTest {
    @Test
    fun trackerAcceptsTwoMeaningfulSamplesOverAShortWindow() {
        val tracker = BallThrowTracker()
        tracker.addSample(0, 0.0)

        assertNull(tracker.estimateThrow())

        tracker.addSample(100, 0.3)
        assertNotNull(tracker.estimateThrow())
    }

    @Test
    fun veryBriefMotionIsRejectedWithDiagnosticDetails() {
        val tracker = BallThrowTracker()
        tracker.addSample(0, 0.0)
        tracker.addSample(10, 0.2)

        val estimate = tracker.estimate()

        assertNull(estimate.throwResult)
        assertEquals(BallThrowRejection.TOO_SHORT, estimate.rejection)
        assertEquals(2, estimate.sampleCount)
        assertEquals(10L, estimate.durationMillis)
    }

    @Test
    fun clockwiseAndCounterclockwiseThrowsPreserveDirection() {
        val clockwise = trackerForAngles(0.0, 0.12, 0.24, 0.36).estimateThrow()!!
        val counterclockwise = trackerForAngles(0.36, 0.24, 0.12, 0.0).estimateThrow()!!

        assertTrue(clockwise.angularVelocityDegreesPerSecond > 0f)
        assertTrue(counterclockwise.angularVelocityDegreesPerSecond < 0f)
        assertEquals(
            clockwise.angularVelocityDegreesPerSecond,
            -counterclockwise.angularVelocityDegreesPerSecond,
            0.01f
        )
    }

    @Test
    fun angleWrapDoesNotCreateFalseReverseVelocitySpike() {
        val throwResult = trackerForAngles(
            2.0 * PI - 0.18,
            2.0 * PI - 0.06,
            0.06,
            0.18
        ).estimateThrow()!!

        assertTrue(throwResult.angularVelocityDegreesPerSecond > 0f)
        assertTrue(throwResult.angularVelocityDegreesPerSecond < 1_000f)
    }

    @Test
    fun fasterFingerMotionCreatesFasterBall() {
        val slow = trackerForAngles(0.0, 0.05, 0.10, 0.15).estimateThrow()!!
        val fast = trackerForAngles(0.0, 0.20, 0.40, 0.60).estimateThrow()!!

        assertTrue(
            fast.angularVelocityDegreesPerSecond >
                slow.angularVelocityDegreesPerSecond
        )
    }

    @Test
    fun verySlowNoiseDoesNotLaunchBall() {
        val tracker = BallThrowTracker()
        tracker.addSample(0, 0.0)
        tracker.addSample(60, 0.01)
        tracker.addSample(120, 0.02)

        assertNull(tracker.estimateThrow())
    }

    @Test
    fun outerTrackBallMovesIndependentlyAndDecays() {
        val initial = BallPhysicsState(
            angleDegrees = 180f,
            angularVelocityDegreesPerSecond = -900f
        )
        val next = advanceBallOuterTrack(initial, 1f / 60f)

        assertTrue(next.angleDegrees < initial.angleDegrees)
        assertTrue(next.angularVelocityDegreesPerSecond in -900f..<0f)
        assertEquals(initial.radiusFraction, next.radiusFraction, 0f)
        assertEquals(0f, next.radialVelocity, 0f)
    }

    @Test
    fun ballDropsContinuouslyAfterReachingTheSpeedThreshold() {
        val outer = BallPhysicsState(
            angleDegrees = 30f,
            angularVelocityDegreesPerSecond =
                BallPhysicsTuning.DROP_START_DEGREES_PER_SECOND
        )
        val dropping = advanceBallPhysics(
            state = outer,
            wheelAngleDegrees = 0f,
            deltaSeconds = 1f / 60f
        )
        val movedInward = advanceBallPhysics(
            state = dropping,
            wheelAngleDegrees = 0f,
            deltaSeconds = 1f / 60f
        )

        assertEquals(BallMotionPhase.DROPPING, dropping.phase)
        assertEquals(outer.radiusFraction, dropping.radiusFraction, 0f)
        assertTrue(movedInward.radiusFraction < dropping.radiusFraction)
        assertTrue(movedInward.radiusFraction > BallPhysicsTuning.POCKET_RADIUS_FRACTION)
    }

    @Test
    fun deflectorPerturbationsAreSeededAndRepeatable() {
        val beforeDeflector = BallPhysicsState(
            angleDegrees = 100f,
            angularVelocityDegreesPerSecond = 220f,
            radiusFraction = 0.426f,
            radialVelocity = -0.09f,
            phase = BallMotionPhase.DROPPING,
            chaosSeed = 17
        )
        val first = advanceBallPhysics(beforeDeflector, 0f, 0.05f)
        val repeated = advanceBallPhysics(beforeDeflector, 0f, 0.05f)
        val differentSeed = advanceBallPhysics(
            beforeDeflector.copy(chaosSeed = 93),
            0f,
            0.05f
        )

        assertEquals(first, repeated)
        assertEquals(1, first.nextDeflectorIndex)
        assertTrue(
            first.angularVelocityDegreesPerSecond !=
                differentSeed.angularVelocityDegreesPerSecond
        )
    }

    @Test
    fun relativeAngleMapsToTheAuthoritativeEuropeanPocketOrder() {
        val wheelAngle = 47f
        EUROPEAN_WHEEL_ORDER.indices.forEach { index ->
            val ballAngle = wheelAngle + pocketCenterRelativeAngleDegrees(index)
            assertEquals(index, pocketIndexForAngles(ballAngle, wheelAngle))
            assertEquals(
                EUROPEAN_WHEEL_ORDER[index],
                EUROPEAN_WHEEL_ORDER[pocketIndexForAngles(ballAngle, wheelAngle)]
            )
        }
    }

    @Test
    fun simulationCapturesAndSnapsTheBallToTheWinningPocketCenter() {
        val wheelAngle = 83f
        val captured = simulateBallToCapture(chaosSeed = 42, wheelAngle = wheelAngle)
        val pocketIndex = captured.capturedPocketIndex!!

        assertEquals(BallMotionPhase.CAPTURED, captured.phase)
        assertEquals(0f, captured.angularVelocityDegreesPerSecond, 0f)
        assertEquals(BallPhysicsTuning.POCKET_RADIUS_FRACTION, captured.radiusFraction, 0f)
        assertEquals(
            pocketIndex,
            pocketIndexForAngles(captured.angleDegrees, wheelAngle)
        )
    }

    @Test
    fun differentChaosSeedsDoNotTriviallyRepeatOneOutcome() {
        val outcomes = (1..12).map { seed ->
            simulateBallToCapture(chaosSeed = seed, wheelAngle = 125f)
                .capturedPocketIndex
        }.toSet()

        assertTrue(outcomes.size >= 3)
    }

    @Test
    fun touchAngleUsesTopAsZeroAndIncreasesClockwise() {
        assertEquals(0.0, touchAngleRadians(100f, 0f, 100f, 100f), 0.0001)
        assertEquals(PI / 2.0, touchAngleRadians(200f, 100f, 100f, 100f), 0.0001)
        assertEquals(PI, touchAngleRadians(100f, 200f, 100f, 100f), 0.0001)
    }

    @Test
    fun throwZoneAllowsTheEdgeExceptForTheWearBackQuadrant() {
        assertTrue(
            isInBallThrowZone(
                x = 100f,
                y = 10f,
                width = 200f,
                height = 200f
            )
        )
        assertTrue(
            isInBallThrowZone(
                x = 190f,
                y = 100f,
                width = 200f,
                height = 200f
            )
        )
        assertTrue(
            !isInBallThrowZone(
                x = 10f,
                y = 100f,
                width = 200f,
                height = 200f
            )
        )
        assertTrue(
            !isInBallThrowZone(
                x = 100f,
                y = 100f,
                width = 200f,
                height = 200f
            )
        )
    }
}

private fun trackerForAngles(vararg angles: Double): BallThrowTracker =
    BallThrowTracker().apply {
        angles.forEachIndexed { index, angle ->
            addSample(timeMillis = index * 50L, wrappedAngleRadians = angle)
        }
    }

private fun simulateBallToCapture(
    chaosSeed: Int,
    wheelAngle: Float
): BallPhysicsState {
    var state = BallPhysicsState(
        angleDegrees = 15f,
        angularVelocityDegreesPerSecond = 900f,
        chaosSeed = chaosSeed
    )

    repeat(1_200) {
        if (state.phase == BallMotionPhase.CAPTURED) return state
        state = advanceBallPhysics(
            state = state,
            wheelAngleDegrees = wheelAngle,
            deltaSeconds = 1f / 60f
        )
    }
    error("Ball did not reach a pocket")
}
