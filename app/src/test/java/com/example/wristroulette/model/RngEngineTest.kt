package com.example.wristroulette.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class RngEngineTest {
    @Test
    fun everyLogicalDiceResultMapsToAMatchingVisibleSector() {
        listOf(RngSpan.D6, RngSpan.D12, RngSpan.D20).forEach { span ->
            span.logicalResults.forEachIndexed { resultOffset, expectedResult ->
                val selection = RngEngine(
                    SequenceBoundedRandom(resultOffset, 0)
                ).roll(span)

                assertEquals(expectedResult, selection.result)
                assertEquals(
                    expectedResult,
                    span.visualSectorValues()[selection.sectorIndex]
                )
            }
        }
    }

    @Test
    fun logicalRollBoundDependsOnlyOnDieSides() {
        val random = RecordingBoundedRandom(0, 0)

        RngEngine(random).roll(RngSpan.D6)

        assertEquals(listOf(6, 6), random.bounds)
    }

    @Test
    fun repeatedLabelsOnlyAffectMatchingSectorChoice() {
        val sectors = RngSpan.D12.visualSectorValues()
        val matchingSectorIndexes = sectors.indices.filter { sectors[it] == 12 }
        val selection = RngEngine(
            SequenceBoundedRandom(11, 2)
        ).roll(RngSpan.D12)

        assertEquals(12, selection.result)
        assertEquals(matchingSectorIndexes[2], selection.sectorIndex)
    }

    @Test
    fun spinTargetCentersEverySectorUnderTheFixedArrow() {
        RngSpan.entries.forEach { span ->
            val sectorCount = span.visualSectorCount
            repeat(sectorCount) { sectorIndex ->
                val target = spinTargetRotation(
                    currentRotation = 137.25f,
                    sectorIndex = sectorIndex,
                    sectorCount = sectorCount,
                    fullTurns = 4
                )
                val expectedRest = restingRotationForSector(sectorIndex, sectorCount)

                assertTrue(
                    abs(normalizeDegrees(target) - expectedRest) < 0.001f
                )
            }
        }
    }
}

private open class SequenceBoundedRandom(
    private vararg val values: Int
) : BoundedRandom {
    private var index = 0

    override fun nextInt(bound: Int): Int {
        val value = values[index++]
        require(value in 0 until bound)
        return value
    }
}

private class RecordingBoundedRandom(
    vararg values: Int
) : SequenceBoundedRandom(*values) {
    val bounds = mutableListOf<Int>()

    override fun nextInt(bound: Int): Int {
        bounds += bound
        return super.nextInt(bound)
    }
}
