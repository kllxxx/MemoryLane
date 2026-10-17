package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileReducerTest {

    private val initial = ProfileState()

    @Test
    fun `Load sets loading`() {
        assertEquals(initial.copy(isLoading = true), ProfileReducer.reduce(initial, ProfileIntent.Load))
    }

    @Test
    fun `SetTheme updates theme only`() {
        val result = ProfileReducer.reduce(initial, ProfileIntent.SetTheme(ThemeMode.Light))
        assertEquals(initial.copy(themeMode = ThemeMode.Light), result)
    }

    @Test
    fun `SetNotifications updates notifications only`() {
        val result = ProfileReducer.reduce(initial, ProfileIntent.SetNotifications(false))
        assertEquals(initial.copy(notificationsEnabled = false), result)
    }

    @Test
    fun `SetStreakGoal updates goal only`() {
        val result = ProfileReducer.reduce(initial, ProfileIntent.SetStreakGoal(StreakGoal.YEAR))
        assertEquals(initial.copy(streakGoal = StreakGoal.YEAR), result)
    }

    @Test
    fun `RepeatOnboarding leaves state unchanged`() {
        assertEquals(initial, ProfileReducer.reduce(initial, ProfileIntent.RepeatOnboarding))
    }

    @Test
    fun `SettingsLoaded applies settings and clears loading`() {
        val result = ProfileReducer.reduce(
            initial.copy(isLoading = true),
            ProfileEvent.SettingsLoaded(ThemeMode.System, notificationsEnabled = false, streakGoal = StreakGoal.MONTH),
        )
        assertEquals(
            ProfileState(ThemeMode.System, notificationsEnabled = false, streakGoal = StreakGoal.MONTH, isLoading = false),
            result,
        )
    }
}
