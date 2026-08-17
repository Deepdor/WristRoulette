package com.example.wristroulette.model

import java.security.SecureRandom

data class RngSelection(
    val result: Int,
    val sectorIndex: Int
)

fun interface BoundedRandom {
    fun nextInt(bound: Int): Int
}

private class SecureBoundedRandom : BoundedRandom {
    private val random = SecureRandom()

    override fun nextInt(bound: Int): Int = random.nextInt(bound)
}

class RngEngine(
    private val random: BoundedRandom = SecureBoundedRandom()
) {
    fun roll(span: RngSpan): RngSelection {
        val logicalResults = span.logicalResults
        val result = logicalResults.first + random.nextInt(logicalResults.count())
        val matchingSectors = span.visualSectorValues()
            .mapIndexedNotNull { index, value -> index.takeIf { value == result } }

        check(matchingSectors.isNotEmpty()) {
            "No visual sector exists for RNG result $result in ${span.name}"
        }

        return RngSelection(
            result = result,
            sectorIndex = matchingSectors[random.nextInt(matchingSectors.size)]
        )
    }
}

fun restingRotationForSector(
    sectorIndex: Int,
    sectorCount: Int
): Float {
    require(sectorCount > 0)
    require(sectorIndex in 0 until sectorCount)
    val sectorAngle = 360f / sectorCount
    return normalizeDegrees(-((sectorIndex + 0.5f) * sectorAngle))
}

fun spinTargetRotation(
    currentRotation: Float,
    sectorIndex: Int,
    sectorCount: Int,
    fullTurns: Int
): Float {
    require(fullTurns >= 1)
    val restingRotation = restingRotationForSector(sectorIndex, sectorCount)
    val forwardDelta = normalizeDegrees(
        restingRotation - normalizeDegrees(currentRotation)
    )
    return currentRotation + fullTurns * 360f + forwardDelta
}

fun normalizeDegrees(value: Float): Float = ((value % 360f) + 360f) % 360f
