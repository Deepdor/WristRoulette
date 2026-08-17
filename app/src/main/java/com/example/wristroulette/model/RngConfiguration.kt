package com.example.wristroulette.model

val EUROPEAN_WHEEL_ORDER: List<Int> = listOf(
    0,
    32, 15, 19, 4, 21, 2, 25, 17, 34,
    6, 27, 13, 36, 11, 30, 8, 23, 10,
    5, 24, 16, 33, 1, 20, 14, 31, 9,
    22, 18, 29, 7, 28, 12, 35, 3, 26
)

val EUROPEAN_RED_NUMBERS: Set<Int> = setOf(
    1, 3, 5, 7, 9,
    12, 14, 16, 18,
    19, 21, 23, 25, 27,
    30, 32, 34, 36
)

enum class RngSpan(
    val displayName: String
) {
    EUROPEAN_ROULETTE("EUROPEAN"),
    D20("D20 (1–20)"),
    D12("D12 (1–12)"),
    D6("D6 (1–6)");

    val logicalResults: IntRange
        get() = when (this) {
            EUROPEAN_ROULETTE -> 0..36
            D20 -> 1..20
            D12 -> 1..12
            D6 -> 1..6
        }

    val visualSectorCount: Int
        get() = visualSectorValues().size

    fun visualSectorValues(): List<Int> = when (this) {
        EUROPEAN_ROULETTE -> EUROPEAN_WHEEL_ORDER
        D20 -> (0..20).toList()
        D12 -> repeatingRouletteStyleValues(sideCount = 12)
        D6 -> repeatingRouletteStyleValues(sideCount = 6)
    }

    fun next(): RngSpan = entries[(ordinal + 1) % entries.size]
}

private fun repeatingRouletteStyleValues(sideCount: Int): List<Int> =
    listOf(0) + List(36) { index -> (index % sideCount) + 1 }
