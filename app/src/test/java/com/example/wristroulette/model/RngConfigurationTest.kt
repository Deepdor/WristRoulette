package com.example.wristroulette.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RngConfigurationTest {
    @Test
    fun diceLogicalResultsUseConventionalOneBasedRanges() {
        assertEquals(1..20, RngSpan.D20.logicalResults)
        assertEquals(1..12, RngSpan.D12.logicalResults)
        assertEquals(1..6, RngSpan.D6.logicalResults)

        assertFalse(0 in RngSpan.D20.logicalResults)
        assertFalse(0 in RngSpan.D12.logicalResults)
        assertFalse(0 in RngSpan.D6.logicalResults)
    }

    @Test
    fun d20UsesOneZeroAndOneSectorPerFace() {
        assertEquals((0..20).toList(), RngSpan.D20.visualSectorValues())
        assertEquals(21, RngSpan.D20.visualSectorCount)
    }

    @Test
    fun d12KeepsZeroAndRepeatsFacesAcrossThirtySixSectors() {
        assertRepeatedThirtySixSectorLayout(RngSpan.D12, sideCount = 12)
    }

    @Test
    fun d6KeepsZeroAndRepeatsFacesAcrossThirtySixSectors() {
        assertRepeatedThirtySixSectorLayout(RngSpan.D6, sideCount = 6)
    }

    @Test
    fun europeanLayoutUsesTheAuthoritativeWheelOrder() {
        assertEquals(EUROPEAN_WHEEL_ORDER, RngSpan.EUROPEAN_ROULETTE.visualSectorValues())
        assertEquals(37, RngSpan.EUROPEAN_ROULETTE.visualSectorCount)
        assertEquals(0..36, RngSpan.EUROPEAN_ROULETTE.logicalResults)
    }

    private fun assertRepeatedThirtySixSectorLayout(
        span: RngSpan,
        sideCount: Int
    ) {
        val sectors = span.visualSectorValues()

        assertEquals(37, sectors.size)
        assertEquals(0, sectors.first())
        assertEquals(1, sectors.count { it == 0 })

        val faceCounts = sectors.drop(1).groupingBy { it }.eachCount()
        val expectedCountPerFace = 36 / sideCount
        (1..sideCount).forEach { face ->
            assertEquals(expectedCountPerFace, faceCounts[face])
        }
    }
}
