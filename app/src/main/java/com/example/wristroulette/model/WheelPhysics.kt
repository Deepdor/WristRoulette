package com.example.wristroulette.model

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

data class WheelPhysicsState(
    val angleDegrees: Float = 0f,
    val angularVelocityDegreesPerSecond: Float = 0f
) {
    val isSpinning: Boolean
        get() = angularVelocityDegreesPerSecond != 0f
}

enum class WheelPhysicsPhase {
    WAITING_FOR_BALL,
    BALL_IN_PLAY
}

object WheelPhysicsTuning {
    const val MIN_GESTURE_RADIANS_PER_SECOND = 5f
    const val MAX_GESTURE_RADIANS_PER_SECOND = 20f
    const val MIN_LAUNCH_DEGREES_PER_SECOND = 540f
    const val MAX_LAUNCH_DEGREES_PER_SECOND = 1_440f
    const val WAITING_FOR_BALL_FRICTION_PER_SECOND = 0.35f
    const val WAITING_FOR_BALL_MIN_SPEED = 240f
    const val BALL_IN_PLAY_FRICTION_PER_SECOND = 0.62f
    const val STOP_DEGREES_PER_SECOND = 6f
    const val MAX_FRAME_SECONDS = 0.05f
}

fun wheelVelocityFromSwing(swingRadiansPerSecond: Float): Float {
    val magnitude = abs(swingRadiansPerSecond)
    if (magnitude < WheelPhysicsTuning.MIN_GESTURE_RADIANS_PER_SECOND) return 0f

    val clampedMagnitude = magnitude.coerceAtMost(
        WheelPhysicsTuning.MAX_GESTURE_RADIANS_PER_SECOND
    )
    val normalizedStrength = (
        clampedMagnitude - WheelPhysicsTuning.MIN_GESTURE_RADIANS_PER_SECOND
        ) / (
        WheelPhysicsTuning.MAX_GESTURE_RADIANS_PER_SECOND -
            WheelPhysicsTuning.MIN_GESTURE_RADIANS_PER_SECOND
        )
    val launchMagnitude = WheelPhysicsTuning.MIN_LAUNCH_DEGREES_PER_SECOND +
        normalizedStrength * (
        WheelPhysicsTuning.MAX_LAUNCH_DEGREES_PER_SECOND -
            WheelPhysicsTuning.MIN_LAUNCH_DEGREES_PER_SECOND
        )
    return sign(swingRadiansPerSecond) * launchMagnitude
}

fun advanceWheelPhysics(
    state: WheelPhysicsState,
    deltaSeconds: Float,
    phase: WheelPhysicsPhase = WheelPhysicsPhase.BALL_IN_PLAY
): WheelPhysicsState {
    if (!state.isSpinning || deltaSeconds <= 0f) return state

    val frictionPerSecond = when (phase) {
        WheelPhysicsPhase.WAITING_FOR_BALL ->
            WheelPhysicsTuning.WAITING_FOR_BALL_FRICTION_PER_SECOND
        WheelPhysicsPhase.BALL_IN_PLAY ->
            WheelPhysicsTuning.BALL_IN_PLAY_FRICTION_PER_SECOND
    }
    val dt = deltaSeconds.coerceAtMost(WheelPhysicsTuning.MAX_FRAME_SECONDS)
    val decay = exp(
        (-frictionPerSecond * dt).toDouble()
    ).toFloat()
    val decayedVelocity = state.angularVelocityDegreesPerSecond * decay
    val displacement = state.angularVelocityDegreesPerSecond /
        frictionPerSecond * (1f - decay)
    val nextVelocity = when (phase) {
        WheelPhysicsPhase.WAITING_FOR_BALL -> sign(decayedVelocity) * maxOf(
            abs(decayedVelocity),
            WheelPhysicsTuning.WAITING_FOR_BALL_MIN_SPEED
        )
        WheelPhysicsPhase.BALL_IN_PLAY -> if (
            abs(decayedVelocity) < WheelPhysicsTuning.STOP_DEGREES_PER_SECOND
        ) {
            0f
        } else {
            decayedVelocity
        }
    }

    return WheelPhysicsState(
        angleDegrees = normalizeWheelAngle(state.angleDegrees + displacement),
        angularVelocityDegreesPerSecond = nextVelocity
    )
}

fun normalizeWheelAngle(angleDegrees: Float): Float =
    ((angleDegrees % 360f) + 360f) % 360f
