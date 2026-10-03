package com.example.wristroulette.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.wristroulette.model.BetSettlement
import com.example.wristroulette.model.BetSlip
import com.example.wristroulette.model.RngSpan
import com.example.wristroulette.model.settleBets
import kotlin.math.max

enum class BuzzDuration(
    val displayName: String,
    val durationMs: Long
) {
    OFF("OFF", 0L),
    SHORT("SHORT", 50L),
    MEDIUM("MEDIUM", 100L),
    LONG("LONG", 180L);

    fun next(): BuzzDuration = entries[(ordinal + 1) % entries.size]
}

enum class RngRollLength(
    val displayName: String,
    val durationMs: Int
) {
    SHORT("SHORT", 2_000),
    NORMAL("NORMAL", 4_000),
    LONG("LONG", 6_000);

    fun next(): RngRollLength = entries[(ordinal + 1) % entries.size]
}

data class AppSettings(
    val buzzDuration: BuzzDuration = BuzzDuration.SHORT,
    val rngRollLength: RngRollLength = RngRollLength.NORMAL,
    val rngSpan: RngSpan = RngSpan.EUROPEAN_ROULETTE
)

data class SessionState(
    val bank: Int = STARTING_BANK,
    val spinCount: Int = 0
) {
    companion object {
        const val STARTING_BANK = 100
    }
}

data class BetRecords(
    val wins: Int = 0,
    val biggestWin: Int = 0,
    val highestBank: Int = SessionState.STARTING_BANK,
    val highestSessionSpins: Int = 0
)

data class PersistedAppState(
    val settings: AppSettings = AppSettings(),
    val session: SessionState = SessionState(),
    val records: BetRecords = BetRecords()
)

data class CompletedRound(
    val settlement: BetSettlement,
    val session: SessionState,
    val records: BetRecords,
    val sessionEnded: Boolean
)

fun completeRound(
    session: SessionState,
    records: BetRecords,
    betSlip: BetSlip,
    winningNumber: Int
): CompletedRound {
    require(betSlip.totalStake <= session.bank)

    val settlement = settleBets(
        betSlip = betSlip,
        winningNumber = winningNumber
    )
    val nextBank = (session.bank.toLong() + settlement.netCredits)
        .coerceIn(0L, Int.MAX_VALUE.toLong())
        .toInt()
    val nextSpinCount = if (session.spinCount == Int.MAX_VALUE) {
        Int.MAX_VALUE
    } else {
        session.spinCount + 1
    }
    val nextSession = SessionState(
        bank = nextBank,
        spinCount = nextSpinCount
    )
    val sessionEnded = nextBank == 0
    val nextRecords = records.copy(
        wins = if (settlement.isWin && records.wins < Int.MAX_VALUE) {
            records.wins + 1
        } else {
            records.wins
        },
        biggestWin = max(records.biggestWin, settlement.netCredits),
        highestBank = max(records.highestBank, nextBank),
        highestSessionSpins = if (sessionEnded) {
            max(records.highestSessionSpins, nextSpinCount)
        } else {
            records.highestSessionSpins
        }
    )

    return CompletedRound(
        settlement = settlement,
        session = nextSession,
        records = nextRecords,
        sessionEnded = sessionEnded
    )
}

interface PreferenceBackend {
    fun getString(key: String, defaultValue: String): String
    fun getInt(key: String, defaultValue: Int): Int
    fun putString(key: String, value: String)
    fun putInt(key: String, value: Int)
}

private class SharedPreferencesBackend(
    private val preferences: SharedPreferences
) : PreferenceBackend {
    override fun getString(key: String, defaultValue: String): String =
        preferences.getString(key, defaultValue) ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        preferences.getInt(key, defaultValue)

    override fun putString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    override fun putInt(key: String, value: Int) {
        preferences.edit { putInt(key, value) }
    }
}

class AppPreferencesStore(
    private val backend: PreferenceBackend
) {
    constructor(context: Context) : this(
        SharedPreferencesBackend(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
    )

    fun load(): PersistedAppState = PersistedAppState(
        settings = AppSettings(
            buzzDuration = enumPreference(
                key = KEY_BUZZ_DURATION,
                defaultValue = BuzzDuration.SHORT
            ),
            rngRollLength = enumPreference(
                key = KEY_RNG_ROLL_LENGTH,
                defaultValue = RngRollLength.NORMAL
            ),
            rngSpan = enumPreference(
                key = KEY_RNG_SPAN,
                defaultValue = RngSpan.EUROPEAN_ROULETTE
            )
        ),
        session = SessionState(
            bank = backend.getInt(KEY_BANK, SessionState.STARTING_BANK)
                .coerceAtLeast(0),
            spinCount = backend.getInt(KEY_SPIN_COUNT, 0)
                .coerceAtLeast(0)
        ),
        records = BetRecords(
            wins = backend.getInt(KEY_WINS, 0).coerceAtLeast(0),
            biggestWin = backend.getInt(KEY_BIGGEST_WIN, 0).coerceAtLeast(0),
            highestBank = backend.getInt(
                KEY_HIGHEST_BANK,
                SessionState.STARTING_BANK
            ).coerceAtLeast(SessionState.STARTING_BANK),
            highestSessionSpins = backend.getInt(KEY_HIGHEST_SESSION_SPINS, 0)
                .coerceAtLeast(0)
        )
    )

    fun saveSettings(settings: AppSettings) {
        backend.putString(KEY_BUZZ_DURATION, settings.buzzDuration.name)
        backend.putString(KEY_RNG_ROLL_LENGTH, settings.rngRollLength.name)
        backend.putString(KEY_RNG_SPAN, settings.rngSpan.name)
    }

    fun saveSession(session: SessionState) {
        backend.putInt(KEY_BANK, session.bank.coerceAtLeast(0))
        backend.putInt(KEY_SPIN_COUNT, session.spinCount.coerceAtLeast(0))
    }

    fun saveRecords(records: BetRecords) {
        backend.putInt(KEY_WINS, records.wins.coerceAtLeast(0))
        backend.putInt(KEY_BIGGEST_WIN, records.biggestWin.coerceAtLeast(0))
        backend.putInt(
            KEY_HIGHEST_BANK,
            records.highestBank.coerceAtLeast(SessionState.STARTING_BANK)
        )
        backend.putInt(
            KEY_HIGHEST_SESSION_SPINS,
            records.highestSessionSpins.coerceAtLeast(0)
        )
    }

    fun resetSession(): SessionState {
        val currentState = load()
        saveRecords(
            currentState.records.copy(
                highestSessionSpins = max(
                    currentState.records.highestSessionSpins,
                    currentState.session.spinCount
                )
            )
        )
        val resetState = SessionState()
        saveSession(resetState)
        return resetState
    }

    private inline fun <reified T : Enum<T>> enumPreference(
        key: String,
        defaultValue: T
    ): T {
        val storedName = backend.getString(key, defaultValue.name)
        return enumValues<T>().firstOrNull { it.name == storedName } ?: defaultValue
    }

    private companion object {
        const val PREFERENCES_NAME = "wrist_roulette_preferences"
        const val KEY_BUZZ_DURATION = "buzz_duration"
        const val KEY_RNG_ROLL_LENGTH = "rng_roll_length"
        const val KEY_RNG_SPAN = "rng_span"
        const val KEY_BANK = "current_bank"
        const val KEY_SPIN_COUNT = "current_session_spin_count"
        const val KEY_WINS = "record_wins"
        const val KEY_BIGGEST_WIN = "record_biggest_win"
        const val KEY_HIGHEST_BANK = "record_highest_bank"
        const val KEY_HIGHEST_SESSION_SPINS = "record_highest_session_spins"
    }
}
