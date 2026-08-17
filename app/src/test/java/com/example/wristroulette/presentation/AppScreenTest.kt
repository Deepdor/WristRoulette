package com.example.wristroulette.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppScreenTest {
    @Test
    fun mainHasNoParent() {
        assertNull(AppScreen.MAIN.parent())
    }

    @Test
    fun rootDestinationsReturnToMain() {
        val destinations = listOf(
            AppScreen.OPTIONS,
            AppScreen.RNG,
            AppScreen.BET_RECORD,
            AppScreen.BET
        )

        destinations.forEach { destination ->
            assertEquals(AppScreen.MAIN, destination.parent())
        }
    }

    @Test
    fun playReturnsToBet() {
        assertEquals(AppScreen.BET, AppScreen.PLAY.parent())
    }
}
