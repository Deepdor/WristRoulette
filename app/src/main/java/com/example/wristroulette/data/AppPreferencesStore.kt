package com.example.wristroulette.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.example.wristroulette.model.RngSpan

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

data class PersistedAppState(
    val settings: AppSettings = AppSettings(),
    val session: SessionState = SessionState()
)

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

    fun resetSession(): SessionState {
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
    }
}
