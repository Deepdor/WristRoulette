package com.example.wristroulette.data

import com.example.wristroulette.model.RngSpan
import com.example.wristroulette.model.BetSlip
import com.example.wristroulette.model.BetType
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
        assertEquals(BetRecords(), state.records)
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
    fun resetSessionPreservesLifetimeRecords() {
        val backend = MemoryPreferenceBackend()
        val store = AppPreferencesStore(backend)
        val records = BetRecords(
            wins = 9,
            biggestWin = 140,
            highestBank = 260,
            highestSessionSpins = 31
        )
        store.saveRecords(records)
        store.saveSession(SessionState(bank = 4, spinCount = 7))

        store.resetSession()

        assertEquals(records, AppPreferencesStore(backend).load().records)
    }

    @Test
    fun resetSessionRecordsTheCompletedSessionLength() {
        val backend = MemoryPreferenceBackend()
        val store = AppPreferencesStore(backend)
        store.saveSession(SessionState(bank = 44, spinCount = 12))

        store.resetSession()

        val state = store.load()
        assertEquals(SessionState(), state.session)
        assertEquals(12, state.records.highestSessionSpins)
    }

    @Test
    fun completedWinningRoundUpdatesSessionAndRecords() {
        val completed = completeRound(
            session = SessionState(bank = 100, spinCount = 2),
            records = BetRecords(),
            betSlip = BetSlip().addStake(BetType.RED, 10, bank = 100),
            winningNumber = 1
        )

        assertEquals(SessionState(bank = 110, spinCount = 3), completed.session)
        assertEquals(1, completed.records.wins)
        assertEquals(10, completed.records.biggestWin)
        assertEquals(110, completed.records.highestBank)
        assertEquals(0, completed.records.highestSessionSpins)
        assertEquals(false, completed.sessionEnded)
    }

    @Test
    fun losingFinalCreditsEndsSessionAndRecordsItsLength() {
        val completed = completeRound(
            session = SessionState(bank = 5, spinCount = 4),
            records = BetRecords(highestSessionSpins = 3),
            betSlip = BetSlip().addStake(BetType.RED, 5, bank = 5),
            winningNumber = 0
        )

        assertEquals(SessionState(bank = 0, spinCount = 5), completed.session)
        assertEquals(5, completed.records.highestSessionSpins)
        assertEquals(true, completed.sessionEnded)
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
        assertEquals(BetRecords(), state.records)
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
