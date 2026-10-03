package com.example.wristroulette.model

import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt

enum class BetType(
    val displayName: String
) {
    RED("RED"),
    BLACK("BLACK"),
    ODD("ODD"),
    EVEN("EVEN"),
    LOW("LOW 1–18"),
    HIGH("HIGH 19–36"),
    STRAIGHT("STRAIGHT")
}

data class BetSlip(
    val stakes: Map<BetType, Int> = emptyMap(),
    val straightNumber: Int = 0
) {
    init {
        require(straightNumber in 0..36)
        require(stakes.values.all { it >= 0 })
    }

    val totalStake: Int
        get() = stakes.values.sum()

    fun stakeFor(type: BetType): Int = stakes[type] ?: 0

    fun availableCredits(bank: Int): Int = (bank - totalStake).coerceAtLeast(0)

    fun addStake(
        type: BetType,
        requestedCredits: Int,
        bank: Int
    ): BetSlip {
        require(requestedCredits > 0)
        val creditsToAdd = minOf(requestedCredits, availableCredits(bank))
        if (creditsToAdd == 0) return this

        return copy(
            stakes = stakes + (type to stakeFor(type) + creditsToAdd)
        )
    }

    fun selectStraightNumber(number: Int): BetSlip {
        require(number in 0..36)
        return copy(straightNumber = number)
    }

    fun clearStakes(): BetSlip = if (stakes.isEmpty()) {
        this
    } else {
        copy(stakes = emptyMap())
    }
}

data class BetSettlement(
    val winningNumber: Int,
    val totalStake: Int,
    val totalReturn: Int,
    val netCredits: Int,
    val winningBetTypes: Set<BetType>
) {
    val isWin: Boolean
        get() = netCredits > 0
}

fun settleBets(
    betSlip: BetSlip,
    winningNumber: Int
): BetSettlement {
    require(winningNumber in 0..36)

    val winningBets = betSlip.stakes
        .filter { (type, stake) -> stake > 0 && type.winsOn(winningNumber, betSlip) }
    val totalReturn = winningBets.entries.sumOf { (type, stake) ->
        stake * type.returnMultiplier
    }

    return BetSettlement(
        winningNumber = winningNumber,
        totalStake = betSlip.totalStake,
        totalReturn = totalReturn,
        netCredits = totalReturn - betSlip.totalStake,
        winningBetTypes = winningBets.keys
    )
}

private fun BetType.winsOn(
    winningNumber: Int,
    betSlip: BetSlip
): Boolean = when (this) {
    BetType.RED -> winningNumber in EUROPEAN_RED_NUMBERS
    BetType.BLACK -> winningNumber != 0 && winningNumber !in EUROPEAN_RED_NUMBERS
    BetType.ODD -> winningNumber != 0 && winningNumber % 2 != 0
    BetType.EVEN -> winningNumber != 0 && winningNumber % 2 == 0
    BetType.LOW -> winningNumber in 1..18
    BetType.HIGH -> winningNumber in 19..36
    BetType.STRAIGHT -> winningNumber == betSlip.straightNumber
}

private val BetType.returnMultiplier: Int
    get() = when (this) {
        BetType.STRAIGHT -> 36
        else -> 2
    }

fun stakeIncrementForPress(durationMs: Long): Int = when {
    durationMs >= 3_000L -> 20
    durationMs >= 1_000L -> 5
    else -> 1
}

fun isInStraightNumberEdgeRing(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    minimumRadiusFraction: Float = 0.40f
): Boolean {
    val centerX = width / 2f
    val centerY = height / 2f
    val dx = x - centerX
    val dy = y - centerY
    val radius = sqrt(dx * dx + dy * dy)
    return radius >= minOf(width, height) * minimumRadiusFraction
}

fun canStartStraightNumberSelection(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    navigationRowStartFraction: Float = 0.76f
): Boolean = y < height * navigationRowStartFraction &&
    isInStraightNumberEdgeRing(x, y, width, height)

fun straightNumberForPosition(
    x: Float,
    y: Float,
    width: Float,
    height: Float
): Int {
    val centerX = width / 2f
    val centerY = height / 2f
    val angleFromTopClockwise = atan2(
        y = y - centerY,
        x = x - centerX
    ) + Math.PI / 2.0
    val normalizedAngle = (
        angleFromTopClockwise % (Math.PI * 2.0) + Math.PI * 2.0
        ) % (Math.PI * 2.0)
    val sectorPosition = normalizedAngle / (Math.PI * 2.0) * 37.0
    return floor(sectorPosition + 0.000_001)
        .toInt() % 37
}
