package com.example.wristroulette.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WheelPhysicsTest {
    @Test
    fun subThresholdMovementDoesNotLaunchWheel() {
        assertEquals(0f, wheelVelocityFromSwing(4.99f), 0f)
        assertEquals(0f, wheelVelocityFromSwing(-4.99f), 0f)
    }

    @Test
    fun launchPreservesDirectionAndScalesWithStrength() {
        val weak = wheelVelocityFromSwing(5f)
        val normal = wheelVelocityFromSwing(12f)
        val strong = wheelVelocityFromSwing(20f)

        assertTrue(weak > 0f)
        assertTrue(normal > weak)
        assertTrue(strong > normal)
        assertEquals(-normal, wheelVelocityFromSwing(-12f), 0.001f)
    }

    @Test
    fun extremeMovementIsClampedAtStrongSpinRange() {
        assertEquals(
            wheelVelocityFromSwing(20f),
            wheelVelocityFromSwing(25f),
            0.001f
        )
        assertEquals(
            wheelVelocityFromSwing(-20f),
            wheelVelocityFromSwing(-100f),
            0.001f
        )
    }

    @Test
    fun frictionAdvancesAngleAndReducesSpeedWithoutReversing() {
        val positive = advanceWheelPhysics(
            WheelPhysicsState(angleDegrees = 15f, angularVelocityDegreesPerSecond = 900f),
            deltaSeconds = 1f / 60f
        )
        val negative = advanceWheelPhysics(
            WheelPhysicsState(angleDegrees = 15f, angularVelocityDegreesPerSecond = -900f),
            deltaSeconds = 1f / 60f
        )

        assertTrue(positive.angleDegrees > 15f)
        assertTrue(positive.angularVelocityDegreesPerSecond in 0f..<900f)
        assertTrue(negative.angleDegrees < 15f)
        assertTrue(negative.angularVelocityDegreesPerSecond in -900f..<0f)
    }

    @Test
    fun strongerSwingSpinsLongerAndTravelsFarther() {
        val weak = simulateSpin(wheelVelocityFromSwing(5f))
        val strong = simulateSpin(wheelVelocityFromSwing(20f))

        assertTrue(weak.durationSeconds >= 6f)
        assertTrue(strong.durationSeconds > weak.durationSeconds)
        assertTrue(strong.distanceDegrees > weak.distanceDegrees)
        assertEquals(0f, weak.finalState.angularVelocityDegreesPerSecond, 0f)
        assertEquals(0f, strong.finalState.angularVelocityDegreesPerSecond, 0f)
        assertTrue(
            abs(strong.finalState.angleDegrees - weak.finalState.angleDegrees) > 0.1f
        )
    }

    @Test
    fun longFrameGapIsClampedToAvoidBackgroundJump() {
        val state = WheelPhysicsState(angularVelocityDegreesPerSecond = 900f)

        assertEquals(
            advanceWheelPhysics(state, WheelPhysicsTuning.MAX_FRAME_SECONDS),
            advanceWheelPhysics(state, 2f)
        )
    }

    @Test
    fun wheelMaintainsUsefulCoastUntilBallIsThrown() {
        var state = WheelPhysicsState(
            angularVelocityDegreesPerSecond = 500f
        )

        repeat(1_200) {
            state = advanceWheelPhysics(
                state,
                deltaSeconds = 1f / 60f,
                phase = WheelPhysicsPhase.WAITING_FOR_BALL
            )
        }

        assertEquals(
            WheelPhysicsTuning.WAITING_FOR_BALL_MIN_SPEED,
            state.angularVelocityDegreesPerSecond,
            0.01f
        )
        assertTrue(state.isSpinning)
    }

    @Test
    fun wheelResumesNormalDecayAfterBallLaunch() {
        var state = WheelPhysicsState(
            angularVelocityDegreesPerSecond =
                WheelPhysicsTuning.WAITING_FOR_BALL_MIN_SPEED
        )

        repeat(1_200) {
            state = advanceWheelPhysics(
                state,
                deltaSeconds = 1f / 60f,
                phase = WheelPhysicsPhase.BALL_IN_PLAY
            )
        }

        assertEquals(0f, state.angularVelocityDegreesPerSecond, 0f)
    }
}

private data class SimulatedSpin(
    val finalState: WheelPhysicsState,
    val durationSeconds: Float,
    val distanceDegrees: Float
)

private fun simulateSpin(initialVelocity: Float): SimulatedSpin {
    val stepSeconds = 1f / 60f
    var state = WheelPhysicsState(angularVelocityDegreesPerSecond = initialVelocity)
    var duration = 0f
    var distance = 0f

    while (state.isSpinning && duration < 30f) {
        distance += abs(state.angularVelocityDegreesPerSecond) * stepSeconds
        state = advanceWheelPhysics(state, stepSeconds)
        duration += stepSeconds
    }

    return SimulatedSpin(
        finalState = state,
        durationSeconds = duration,
        distanceDegrees = distance
    )
}
