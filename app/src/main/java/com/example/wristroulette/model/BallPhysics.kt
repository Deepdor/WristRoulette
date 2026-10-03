package com.example.wristroulette.model

import java.util.ArrayDeque
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sqrt

data class BallThrow(
    val launchAngleDegrees: Float,
    val angularVelocityDegreesPerSecond: Float
)

enum class BallThrowRejection {
    NOT_ENOUGH_SAMPLES,
    TOO_SHORT,
    TOO_LITTLE_MOVEMENT,
    TOO_SLOW
}

data class BallThrowEstimate(
    val throwResult: BallThrow?,
    val rejection: BallThrowRejection?,
    val sampleCount: Int,
    val durationMillis: Long,
    val touchVelocityRadiansPerSecond: Float?
)

enum class BallMotionPhase {
    OUTER_TRACK,
    DROPPING,
    CAPTURED
}

data class BallPhysicsState(
    val angleDegrees: Float,
    val angularVelocityDegreesPerSecond: Float,
    val radiusFraction: Float = BallPhysicsTuning.OUTER_TRACK_RADIUS_FRACTION,
    val radialVelocity: Float = 0f,
    val phase: BallMotionPhase = BallMotionPhase.OUTER_TRACK,
    val chaosSeed: Int = 0,
    val nextDeflectorIndex: Int = 0,
    val capturedPocketIndex: Int? = null
) {
    val isMoving: Boolean
        get() = phase != BallMotionPhase.CAPTURED
}

object BallPhysicsTuning {
    const val SAMPLE_WINDOW_MILLIS = 180L
    const val MIN_SAMPLE_DURATION_MILLIS = 24L
    const val MIN_ANGULAR_TRAVEL_RADIANS = 0.05f
    const val MIN_TOUCH_RADIANS_PER_SECOND = 0.60f
    const val MAX_TOUCH_RADIANS_PER_SECOND = 15f
    const val TOUCH_TO_BALL_SPEED_SCALE = 2.4f
    const val OUTER_TRACK_FRICTION_PER_SECOND = 0.85f
    const val OUTER_TRACK_STOP_DEGREES_PER_SECOND = 15f
    const val OUTER_TRACK_RADIUS_FRACTION = 0.44f
    const val MAX_FRAME_SECONDS = 0.05f
    const val THROW_ZONE_START_DEGREES = 315f
    const val THROW_ZONE_SWEEP_DEGREES = 270f
    const val DROP_START_DEGREES_PER_SECOND = 360f
    const val DROP_ANGULAR_FRICTION_PER_SECOND = 1.10f
    const val INITIAL_INWARD_VELOCITY = -0.035f
    const val INWARD_ACCELERATION_PER_SECOND = -0.018f
    const val MAX_INWARD_VELOCITY = -0.090f
    const val POCKET_RADIUS_FRACTION = 0.36f
    const val MAX_DEFLECTOR_ANGULAR_KICK = 120f
    const val MAX_DEFLECTOR_ANGLE_NUDGE = 4f

    val DEFLECTOR_RADII = floatArrayOf(0.425f, 0.405f, 0.385f, 0.365f)
}

private data class AngularTouchSample(
    val timeMillis: Long,
    val unwrappedAngleRadians: Double
)

class BallThrowTracker(
    private val sampleWindowMillis: Long = BallPhysicsTuning.SAMPLE_WINDOW_MILLIS
) {
    private val samples = ArrayDeque<AngularTouchSample>()
    private var lastWrappedAngleRadians: Double? = null
    private var unwrappedAngleRadians = 0.0
    private var latestWrappedAngleRadians = 0.0

    fun reset() {
        samples.clear()
        lastWrappedAngleRadians = null
        unwrappedAngleRadians = 0.0
        latestWrappedAngleRadians = 0.0
    }

    fun addSample(
        timeMillis: Long,
        wrappedAngleRadians: Double
    ) {
        val normalizedAngle = normalizeRadians(wrappedAngleRadians)
        val previousAngle = lastWrappedAngleRadians

        if (samples.isNotEmpty() && timeMillis <= samples.last.timeMillis) return

        if (previousAngle == null) {
            unwrappedAngleRadians = normalizedAngle
        } else {
            unwrappedAngleRadians += shortestSignedAngleDelta(
                fromRadians = previousAngle,
                toRadians = normalizedAngle
            )
        }

        lastWrappedAngleRadians = normalizedAngle
        latestWrappedAngleRadians = normalizedAngle
        samples.addLast(
            AngularTouchSample(
                timeMillis = timeMillis,
                unwrappedAngleRadians = unwrappedAngleRadians
            )
        )

        val oldestAllowedTime = timeMillis - sampleWindowMillis
        while (samples.isNotEmpty() && samples.first.timeMillis < oldestAllowedTime) {
            samples.removeFirst()
        }
    }

    fun estimateThrow(): BallThrow? = estimate().throwResult

    fun estimate(): BallThrowEstimate {
        val durationMillis = if (samples.size >= 2) {
            samples.last.timeMillis - samples.first.timeMillis
        } else {
            0L
        }
        if (samples.size < 2) {
            return rejectedEstimate(
                rejection = BallThrowRejection.NOT_ENOUGH_SAMPLES,
                durationMillis = durationMillis
            )
        }
        if (durationMillis < BallPhysicsTuning.MIN_SAMPLE_DURATION_MILLIS) {
            return rejectedEstimate(
                rejection = BallThrowRejection.TOO_SHORT,
                durationMillis = durationMillis
            )
        }
        val angularTravel = abs(
            samples.last.unwrappedAngleRadians - samples.first.unwrappedAngleRadians
        )
        if (angularTravel < BallPhysicsTuning.MIN_ANGULAR_TRAVEL_RADIANS) {
            return rejectedEstimate(
                rejection = BallThrowRejection.TOO_LITTLE_MOVEMENT,
                durationMillis = durationMillis
            )
        }

        val meanTime = samples.map { it.timeMillis.toDouble() }.average()
        val meanAngle = samples.map { it.unwrappedAngleRadians }.average()
        var covariance = 0.0
        var timeVariance = 0.0

        samples.forEach { sample ->
            val centeredTime = sample.timeMillis - meanTime
            covariance += centeredTime * (sample.unwrappedAngleRadians - meanAngle)
            timeVariance += centeredTime * centeredTime
        }
        if (timeVariance == 0.0) {
            return rejectedEstimate(
                rejection = BallThrowRejection.TOO_SHORT,
                durationMillis = durationMillis
            )
        }

        val touchVelocityRadiansPerSecond = covariance / timeVariance * 1_000.0
        val ballVelocity = ballVelocityFromTouch(
            touchVelocityRadiansPerSecond.toFloat()
        )
        if (ballVelocity == 0f) {
            return rejectedEstimate(
                rejection = BallThrowRejection.TOO_SLOW,
                durationMillis = durationMillis,
                touchVelocityRadiansPerSecond =
                    touchVelocityRadiansPerSecond.toFloat()
            )
        }

        return BallThrowEstimate(
            throwResult = BallThrow(
                launchAngleDegrees = Math.toDegrees(latestWrappedAngleRadians)
                    .toFloat(),
                angularVelocityDegreesPerSecond = ballVelocity
            ),
            rejection = null,
            sampleCount = samples.size,
            durationMillis = durationMillis,
            touchVelocityRadiansPerSecond =
                touchVelocityRadiansPerSecond.toFloat()
        )
    }

    private fun rejectedEstimate(
        rejection: BallThrowRejection,
        durationMillis: Long,
        touchVelocityRadiansPerSecond: Float? = null
    ): BallThrowEstimate = BallThrowEstimate(
        throwResult = null,
        rejection = rejection,
        sampleCount = samples.size,
        durationMillis = durationMillis,
        touchVelocityRadiansPerSecond = touchVelocityRadiansPerSecond
    )
}

fun touchAngleRadians(
    x: Float,
    y: Float,
    centerX: Float,
    centerY: Float
): Double = normalizeRadians(
    atan2(
        y = (y - centerY).toDouble(),
        x = (x - centerX).toDouble()
    ) + PI / 2.0
)

fun isInBallThrowEdgeRing(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    minimumRadiusFraction: Float = 0.30f
): Boolean {
    val dx = x - width / 2f
    val dy = y - height / 2f
    return sqrt(dx * dx + dy * dy) >=
        minOf(width, height) * minimumRadiusFraction
}

fun isInBallThrowZone(
    x: Float,
    y: Float,
    width: Float,
    height: Float
): Boolean {
    if (!isInBallThrowEdgeRing(x, y, width, height)) return false

    val angleDegrees = Math.toDegrees(
        touchAngleRadians(
            x = x,
            y = y,
            centerX = width / 2f,
            centerY = height / 2f
        )
    ).toFloat()
    val degreesFromStart = normalizeWheelAngle(
        angleDegrees - BallPhysicsTuning.THROW_ZONE_START_DEGREES
    )
    return degreesFromStart <= BallPhysicsTuning.THROW_ZONE_SWEEP_DEGREES
}

fun ballVelocityFromTouch(touchRadiansPerSecond: Float): Float {
    val magnitude = abs(touchRadiansPerSecond)
    if (magnitude < BallPhysicsTuning.MIN_TOUCH_RADIANS_PER_SECOND) return 0f

    val clampedMagnitude = magnitude.coerceAtMost(
        BallPhysicsTuning.MAX_TOUCH_RADIANS_PER_SECOND
    )
    return sign(touchRadiansPerSecond) * Math.toDegrees(
        clampedMagnitude.toDouble()
    ).toFloat() * BallPhysicsTuning.TOUCH_TO_BALL_SPEED_SCALE
}

fun advanceBallOuterTrack(
    state: BallPhysicsState,
    deltaSeconds: Float
): BallPhysicsState {
    if (!state.isMoving || deltaSeconds <= 0f) return state

    val dt = deltaSeconds.coerceAtMost(BallPhysicsTuning.MAX_FRAME_SECONDS)
    val decay = exp(
        (-BallPhysicsTuning.OUTER_TRACK_FRICTION_PER_SECOND * dt).toDouble()
    ).toFloat()
    val nextVelocity = state.angularVelocityDegreesPerSecond * decay
    val displacement = state.angularVelocityDegreesPerSecond /
        BallPhysicsTuning.OUTER_TRACK_FRICTION_PER_SECOND * (1f - decay)

    return state.copy(
        angleDegrees = normalizeWheelAngle(state.angleDegrees + displacement),
        angularVelocityDegreesPerSecond = if (
            abs(nextVelocity) < BallPhysicsTuning.OUTER_TRACK_STOP_DEGREES_PER_SECOND
        ) {
            0f
        } else {
            nextVelocity
        }
    )
}

fun advanceBallPhysics(
    state: BallPhysicsState,
    wheelAngleDegrees: Float,
    deltaSeconds: Float
): BallPhysicsState {
    if (!state.isMoving || deltaSeconds <= 0f) return state

    val dt = deltaSeconds.coerceAtMost(BallPhysicsTuning.MAX_FRAME_SECONDS)
    return when (state.phase) {
        BallMotionPhase.OUTER_TRACK -> {
            val next = decayBallAngularMotion(
                state = state,
                deltaSeconds = dt,
                frictionPerSecond = BallPhysicsTuning.OUTER_TRACK_FRICTION_PER_SECOND
            )
            if (
                abs(next.angularVelocityDegreesPerSecond) <=
                BallPhysicsTuning.DROP_START_DEGREES_PER_SECOND
            ) {
                next.copy(
                    phase = BallMotionPhase.DROPPING,
                    radialVelocity = BallPhysicsTuning.INITIAL_INWARD_VELOCITY
                )
            } else {
                next
            }
        }

        BallMotionPhase.DROPPING -> advanceDroppingBall(
            state = state,
            wheelAngleDegrees = wheelAngleDegrees,
            deltaSeconds = dt
        )

        BallMotionPhase.CAPTURED -> state
    }
}

fun pocketIndexForAngles(
    ballAngleDegrees: Float,
    wheelAngleDegrees: Float
): Int {
    val relativeAngle = normalizeWheelAngle(ballAngleDegrees - wheelAngleDegrees)
    val sectorAngle = 360f / EUROPEAN_WHEEL_ORDER.size
    return floor(relativeAngle / sectorAngle).toInt()
        .coerceIn(EUROPEAN_WHEEL_ORDER.indices)
}

fun pocketCenterRelativeAngleDegrees(pocketIndex: Int): Float {
    require(pocketIndex in EUROPEAN_WHEEL_ORDER.indices)
    val sectorAngle = 360f / EUROPEAN_WHEEL_ORDER.size
    return (pocketIndex + 0.5f) * sectorAngle
}

private fun advanceDroppingBall(
    state: BallPhysicsState,
    wheelAngleDegrees: Float,
    deltaSeconds: Float
): BallPhysicsState {
    var next = decayBallAngularMotion(
        state = state,
        deltaSeconds = deltaSeconds,
        frictionPerSecond = BallPhysicsTuning.DROP_ANGULAR_FRICTION_PER_SECOND
    )
    val nextRadialVelocity = maxOf(
        BallPhysicsTuning.MAX_INWARD_VELOCITY,
        state.radialVelocity +
            BallPhysicsTuning.INWARD_ACCELERATION_PER_SECOND * deltaSeconds
    )
    next = next.copy(
        radiusFraction = state.radiusFraction +
            (state.radialVelocity + nextRadialVelocity) * 0.5f * deltaSeconds,
        radialVelocity = nextRadialVelocity
    )

    var deflectorIndex = next.nextDeflectorIndex
    while (
        deflectorIndex < BallPhysicsTuning.DEFLECTOR_RADII.size &&
        next.radiusFraction <= BallPhysicsTuning.DEFLECTOR_RADII[deflectorIndex]
    ) {
        val chaos = deterministicChaos(next.chaosSeed, deflectorIndex)
        val radialChaos = deterministicChaos(next.chaosSeed xor 0x5A17, deflectorIndex)
        next = next.copy(
            angleDegrees = normalizeWheelAngle(
                next.angleDegrees +
                    chaos * BallPhysicsTuning.MAX_DEFLECTOR_ANGLE_NUDGE
            ),
            angularVelocityDegreesPerSecond =
                next.angularVelocityDegreesPerSecond +
                    chaos * BallPhysicsTuning.MAX_DEFLECTOR_ANGULAR_KICK,
            radialVelocity = next.radialVelocity * (1f + radialChaos * 0.12f)
        )
        deflectorIndex++
    }
    next = next.copy(nextDeflectorIndex = deflectorIndex)

    if (next.radiusFraction > BallPhysicsTuning.POCKET_RADIUS_FRACTION) {
        return next
    }

    val pocketIndex = pocketIndexForAngles(
        ballAngleDegrees = next.angleDegrees,
        wheelAngleDegrees = wheelAngleDegrees
    )
    return next.copy(
        angleDegrees = normalizeWheelAngle(
            wheelAngleDegrees + pocketCenterRelativeAngleDegrees(pocketIndex)
        ),
        angularVelocityDegreesPerSecond = 0f,
        radiusFraction = BallPhysicsTuning.POCKET_RADIUS_FRACTION,
        radialVelocity = 0f,
        phase = BallMotionPhase.CAPTURED,
        capturedPocketIndex = pocketIndex
    )
}

private fun decayBallAngularMotion(
    state: BallPhysicsState,
    deltaSeconds: Float,
    frictionPerSecond: Float
): BallPhysicsState {
    val decay = exp((-frictionPerSecond * deltaSeconds).toDouble()).toFloat()
    val nextVelocity = state.angularVelocityDegreesPerSecond * decay
    val displacement = state.angularVelocityDegreesPerSecond /
        frictionPerSecond * (1f - decay)
    return state.copy(
        angleDegrees = normalizeWheelAngle(state.angleDegrees + displacement),
        angularVelocityDegreesPerSecond = nextVelocity
    )
}

private fun deterministicChaos(seed: Int, deflectorIndex: Int): Float {
    var value = seed.toLong() and 0xFFFF_FFFFL
    repeat(deflectorIndex + 1) {
        value = (1_664_525L * value + 1_013_904_223L) and 0xFFFF_FFFFL
    }
    return (value.toDouble() / 0xFFFF_FFFFL.toDouble()).toFloat() * 2f - 1f
}

private fun shortestSignedAngleDelta(
    fromRadians: Double,
    toRadians: Double
): Double = ((toRadians - fromRadians + PI) % (2.0 * PI) + 2.0 * PI) %
    (2.0 * PI) - PI

private fun normalizeRadians(value: Double): Double =
    ((value % (2.0 * PI)) + 2.0 * PI) % (2.0 * PI)
