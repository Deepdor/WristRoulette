package com.example.wristroulette.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BettingTest {
    @Test
    fun pressDurationResolvesToHighestThresholdOnce() {
        assertEquals(1, stakeIncrementForPress(200))
        assertEquals(1, stakeIncrementForPress(999))
        assertEquals(5, stakeIncrementForPress(1_000))
        assertEquals(5, stakeIncrementForPress(2_999))
        assertEquals(20, stakeIncrementForPress(3_000))
        assertEquals(20, stakeIncrementForPress(8_000))
    }

    @Test
    fun stakeIsCappedByRemainingAvailableBank() {
        val slip = BetSlip()
            .addStake(BetType.RED, requestedCredits = 20, bank = 23)
            .addStake(BetType.BLACK, requestedCredits = 20, bank = 23)

        assertEquals(20, slip.stakeFor(BetType.RED))
        assertEquals(3, slip.stakeFor(BetType.BLACK))
        assertEquals(23, slip.totalStake)
        assertEquals(0, slip.availableCredits(bank = 23))
    }

    @Test
    fun fullBankPreventsAnyAdditionalStake() {
        val fullSlip = BetSlip().addStake(BetType.RED, 20, bank = 20)

        assertEquals(
            fullSlip,
            fullSlip.addStake(BetType.ODD, requestedCredits = 1, bank = 20)
        )
    }

    @Test
    fun straightNumberSelectionKeepsItsExistingStake() {
        val slip = BetSlip(straightNumber = 7)
            .addStake(BetType.STRAIGHT, requestedCredits = 5, bank = 100)
            .selectStraightNumber(32)

        assertEquals(32, slip.straightNumber)
        assertEquals(5, slip.stakeFor(BetType.STRAIGHT))
    }

    @Test
    fun betZeroClearsAllStakesButKeepsStraightSelection() {
        val cleared = BetSlip(straightNumber = 32)
            .addStake(BetType.RED, requestedCredits = 20, bank = 100)
            .addStake(BetType.STRAIGHT, requestedCredits = 5, bank = 100)
            .clearStakes()

        assertEquals(0, cleared.totalStake)
        assertTrue(cleared.stakes.isEmpty())
        assertEquals(32, cleared.straightNumber)
        assertEquals(100, cleared.availableCredits(bank = 100))
    }

    @Test
    fun standardPayoutsReturnStakeAndProfit() {
        val slip = BetSlip(straightNumber = 7)
            .addStake(BetType.RED, requestedCredits = 2, bank = 100)
            .addStake(BetType.ODD, requestedCredits = 3, bank = 100)
            .addStake(BetType.LOW, requestedCredits = 4, bank = 100)
            .addStake(BetType.STRAIGHT, requestedCredits = 5, bank = 100)

        val settlement = settleBets(slip, winningNumber = 7)

        assertEquals(14, settlement.totalStake)
        assertEquals(198, settlement.totalReturn)
        assertEquals(184, settlement.netCredits)
        assertEquals(
            setOf(BetType.RED, BetType.ODD, BetType.LOW, BetType.STRAIGHT),
            settlement.winningBetTypes
        )
        assertTrue(settlement.isWin)
    }

    @Test
    fun blackEvenHighBetsWinOnTwenty() {
        val slip = BetSlip()
            .addStake(BetType.BLACK, requestedCredits = 1, bank = 100)
            .addStake(BetType.EVEN, requestedCredits = 1, bank = 100)
            .addStake(BetType.HIGH, requestedCredits = 1, bank = 100)

        val settlement = settleBets(slip, winningNumber = 20)

        assertEquals(6, settlement.totalReturn)
        assertEquals(3, settlement.netCredits)
        assertEquals(
            setOf(BetType.BLACK, BetType.EVEN, BetType.HIGH),
            settlement.winningBetTypes
        )
    }

    @Test
    fun zeroLosesEveryEvenMoneyBet() {
        val slip = BetSlip()
            .addStake(BetType.RED, requestedCredits = 1, bank = 100)
            .addStake(BetType.BLACK, requestedCredits = 1, bank = 100)
            .addStake(BetType.ODD, requestedCredits = 1, bank = 100)
            .addStake(BetType.EVEN, requestedCredits = 1, bank = 100)
            .addStake(BetType.LOW, requestedCredits = 1, bank = 100)
            .addStake(BetType.HIGH, requestedCredits = 1, bank = 100)

        val settlement = settleBets(slip, winningNumber = 0)

        assertEquals(0, settlement.totalReturn)
        assertEquals(-6, settlement.netCredits)
        assertTrue(settlement.winningBetTypes.isEmpty())
        assertFalse(settlement.isWin)
    }

    @Test
    fun straightZeroPaysThirtyFiveToOneProfit() {
        val slip = BetSlip(straightNumber = 0)
            .addStake(BetType.STRAIGHT, requestedCredits = 2, bank = 100)

        val settlement = settleBets(slip, winningNumber = 0)

        assertEquals(72, settlement.totalReturn)
        assertEquals(70, settlement.netCredits)
        assertEquals(setOf(BetType.STRAIGHT), settlement.winningBetTypes)
    }

    @Test
    fun offsettingBetsAreA_pushRatherThanAWin() {
        val slip = BetSlip()
            .addStake(BetType.RED, requestedCredits = 5, bank = 100)
            .addStake(BetType.BLACK, requestedCredits = 5, bank = 100)

        val settlement = settleBets(slip, winningNumber = 1)

        assertEquals(10, settlement.totalStake)
        assertEquals(10, settlement.totalReturn)
        assertEquals(0, settlement.netCredits)
        assertFalse(settlement.isWin)
    }

    @Test
    fun circularEdgeMapsClockwiseFromZeroAtTheTop() {
        assertEquals(0, straightNumberForPosition(120f, 0f, 240f, 240f))
        assertEquals(9, straightNumberForPosition(240f, 120f, 240f, 240f))
        assertEquals(18, straightNumberForPosition(120f, 240f, 240f, 240f))
        assertEquals(27, straightNumberForPosition(0f, 120f, 240f, 240f))
    }

    @Test
    fun edgeRingRejectsCentralTouches() {
        assertFalse(isInStraightNumberEdgeRing(120f, 120f, 240f, 240f))
        assertTrue(isInStraightNumberEdgeRing(120f, 10f, 240f, 240f))
    }

    @Test
    fun straightSelectionCannotStartOverBottomNavigation() {
        assertTrue(canStartStraightNumberSelection(120f, 10f, 240f, 240f))
        assertFalse(canStartStraightNumberSelection(30f, 220f, 240f, 240f))
    }
}
