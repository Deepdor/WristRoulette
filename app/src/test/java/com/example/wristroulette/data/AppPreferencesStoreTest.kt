package com.example.wristroulette.data

import com.example.wristroulette.model.RngSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class AppPreferencesStoreTest {
    @Test
    fun emptyStoreLoadsProductDefaults() {
        val state = AppPreferencesStore(MemoryPreferenceBackend()).load()

        assertEquals(BuzzDuration.SHORT, state.settings.buzzDuration)
        assertEquals(RngRollLength.NORMAL, state.settings.rngRollLength)
        assertEquals(RngSpan.EUROPEAN_ROULETTE, state.settings.rngSpan)
        assertEquals(100, state.session.bank)
        assertEquals(0, state.session.spinCount)
    }

    @Test
    fun settingsSurviveStoreRecreation() {
        val backend = MemoryPreferenceBackend()
        AppPreferencesStore(backend).saveSettings(
            AppSettings(
                buzzDuration = BuzzDuration.LONG,
                rngRollLength = RngRollLength.SHORT,
                rngSpan = RngSpan.D12
            )
        )

        val reloaded = AppPreferencesStore(backend).load()

        assertEquals(BuzzDuration.LONG, reloaded.settings.buzzDuration)
        assertEquals(RngRollLength.SHORT, reloaded.settings.rngRollLength)
        assertEquals(RngSpan.D12, reloaded.settings.rngSpan)
    }

    @Test
    fun resetSessionRestoresBankAndClearsSpinCount() {
        val backend = MemoryPreferenceBackend()
        val store = AppPreferencesStore(backend)
        store.saveSession(SessionState(bank = 37, spinCount = 12))

        val reset = store.resetSession()
        val reloaded = AppPreferencesStore(backend).load().session

        assertEquals(SessionState(), reset)
        assertEquals(SessionState(), reloaded)
    }

    @Test
    fun corruptValuesFallBackToSafeDefaults() {
        val backend = MemoryPreferenceBackend().apply {
            putString("buzz_duration", "UNKNOWN")
            putString("rng_roll_length", "UNKNOWN")
            putString("rng_span", "UNKNOWN")
            putInt("current_bank", -50)
            putInt("current_session_spin_count", -3)
        }

        val state = AppPreferencesStore(backend).load()

        assertEquals(AppSettings(), state.settings)
        assertEquals(SessionState(bank = 0, spinCount = 0), state.session)
    }
}

private class MemoryPreferenceBackend : PreferenceBackend {
    private val values = mutableMapOf<String, Any>()

    override fun getString(key: String, defaultValue: String): String =
        values[key] as? String ?: defaultValue

    override fun getInt(key: String, defaultValue: Int): Int =
        values[key] as? Int ?: defaultValue

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun putInt(key: String, value: Int) {
        values[key] = value
    }
}
