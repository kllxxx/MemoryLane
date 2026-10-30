package com.kllxx.memorylane

import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.navigation.StartDestination
import org.junit.Assert.assertEquals
import org.junit.Test

class AppReducerTest {

    private val ready = AppUiState.Ready(ThemeMode.Light, StartDestination.Main)

    @Test
    fun `resolved settings make the state ready`() {
        val state = AppReducer.reduce(
            AppUiState.Loading,
            AppEvent.Resolved(ThemeMode.Light, StartDestination.Onboarding),
        )

        assertEquals(AppUiState.Ready(ThemeMode.Light, StartDestination.Onboarding), state)
    }

    @Test
    fun `a theme change replaces the theme of the ready state only`() {
        assertEquals(
            AppUiState.Ready(ThemeMode.System, StartDestination.Main),
            AppReducer.reduce(ready, AppEvent.ThemeChanged(ThemeMode.System)),
        )
        assertEquals(
            AppUiState.Loading,
            AppReducer.reduce(AppUiState.Loading, AppEvent.ThemeChanged(ThemeMode.System)),
        )
    }

    @Test
    fun `the reminder leaves the state alone`() {
        assertEquals(ready, AppReducer.reduce(ready, AppIntent.ReminderOpened))
        assertEquals(AppUiState.Loading, AppReducer.reduce(AppUiState.Loading, AppIntent.ReminderOpened))
    }
}
