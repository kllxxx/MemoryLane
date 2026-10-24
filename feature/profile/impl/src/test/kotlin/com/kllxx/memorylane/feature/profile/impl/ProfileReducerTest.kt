package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileReducerTest {

    private val initial = ProfileState()

    @Test
    fun `defaults match the spec`() {
        assertEquals(StreakGoal.D30, initial.goal)
        assertEquals(ThemeMode.Dark, initial.themeMode)
        assertEquals(LocalTime.of(20, 0), initial.reminderTime)
        assertTrue(initial.notificationsOn)
        assertFalse(initial.pendingEnable)
        assertFalse(initial.isTimePickerVisible)
    }

    @Test
    fun `notificationsOn needs both the user flag and the system`() {
        assertTrue(initial.copy(userNotificationsEnabled = true, systemNotificationsEnabled = true).notificationsOn)
        assertFalse(initial.copy(userNotificationsEnabled = false, systemNotificationsEnabled = true).notificationsOn)
        assertFalse(initial.copy(userNotificationsEnabled = true, systemNotificationsEnabled = false).notificationsOn)
        assertFalse(initial.copy(userNotificationsEnabled = false, systemNotificationsEnabled = false).notificationsOn)
    }

    @Test
    fun `SelectGoal and SelectTheme change only their own field`() {
        assertEquals(
            initial.copy(goal = StreakGoal.D7),
            ProfileReducer.reduce(initial, ProfileIntent.SelectGoal(StreakGoal.D7)),
        )
        assertEquals(
            initial.copy(themeMode = ThemeMode.System),
            ProfileReducer.reduce(initial, ProfileIntent.SelectTheme(ThemeMode.System)),
        )
    }

    @Test
    fun `toggling ON with the system enabled sets the user flag`() {
        val off = initial.copy(userNotificationsEnabled = false, systemNotificationsEnabled = true)

        assertEquals(initial, ProfileReducer.reduce(off, ProfileIntent.NotificationsToggled(on = true)))
    }

    @Test
    fun `toggling ON with the system blocked leaves the state to the effect`() {
        val blocked = initial.copy(userNotificationsEnabled = false, systemNotificationsEnabled = false)

        assertEquals(blocked, ProfileReducer.reduce(blocked, ProfileIntent.NotificationsToggled(on = true)))
    }

    @Test
    fun `toggling OFF clears the user flag and a pending enable, not the system flag`() {
        val pending = initial.copy(pendingEnable = true)

        val result = ProfileReducer.reduce(pending, ProfileIntent.NotificationsToggled(on = false))

        assertFalse(result.userNotificationsEnabled)
        assertFalse(result.pendingEnable)
        assertTrue(result.systemNotificationsEnabled)
    }

    @Test
    fun `system check stores the system state`() {
        val result = ProfileReducer.reduce(
            initial,
            ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = true),
        )

        assertFalse(result.systemNotificationsEnabled)
    }

    @Test
    fun `system check resolves a pending enable either way`() {
        val pending = initial.copy(
            userNotificationsEnabled = false,
            systemNotificationsEnabled = false,
            pendingEnable = true,
        )

        val allowed = ProfileReducer.reduce(pending, ProfileIntent.SystemNotificationsChecked(true, false))
        val blocked = ProfileReducer.reduce(pending, ProfileIntent.SystemNotificationsChecked(false, false))

        assertTrue(allowed.notificationsOn)
        assertFalse(allowed.pendingEnable)
        assertFalse(blocked.userNotificationsEnabled)
        assertFalse(blocked.pendingEnable)
    }

    @Test
    fun `permission result grants or keeps the switch off`() {
        val off = initial.copy(userNotificationsEnabled = false, systemNotificationsEnabled = false)

        assertTrue(ProfileReducer.reduce(off, ProfileIntent.PermissionResult(granted = true)).notificationsOn)
        assertFalse(ProfileReducer.reduce(off, ProfileIntent.PermissionResult(granted = false)).notificationsOn)
    }

    @Test
    fun `time picker needs notifications on to open`() {
        assertTrue(ProfileReducer.reduce(initial, ProfileIntent.OpenTimePicker).isTimePickerVisible)

        val off = initial.copy(userNotificationsEnabled = false)
        assertFalse(ProfileReducer.reduce(off, ProfileIntent.OpenTimePicker).isTimePickerVisible)
    }

    @Test
    fun `time picker closes on dismiss and on confirm, and confirm sets the time`() {
        val open = initial.copy(isTimePickerVisible = true)

        assertFalse(ProfileReducer.reduce(open, ProfileIntent.DismissTimePicker).isTimePickerVisible)
        val confirmed = ProfileReducer.reduce(open, ProfileIntent.ConfirmTime(LocalTime.of(6, 15)))
        assertFalse(confirmed.isTimePickerVisible)
        assertEquals(LocalTime.of(6, 15), confirmed.reminderTime)
    }

    @Test
    fun `navigation intents leave the state alone`() {
        assertEquals(initial, ProfileReducer.reduce(initial, ProfileIntent.ReplayOnboardingClicked))
        assertEquals(initial, ProfileReducer.reduce(initial, ProfileIntent.ContactSupportClicked))
    }

    @Test
    fun `SettingsLoaded applies the stored settings and keeps the screen-only state`() {
        val state = initial.copy(pendingEnable = true, isTimePickerVisible = true, currentStreak = 4)

        val result = ProfileReducer.reduce(
            state,
            ProfileEvent.SettingsLoaded(
                AppSettings(
                    themeMode = ThemeMode.Light,
                    notificationsEnabled = false,
                    streakGoal = StreakGoal.D365,
                    reminderTime = LocalTime.of(8, 0),
                    notificationPermissionRequested = true,
                ),
            ),
        )

        assertEquals(
            state.copy(
                themeMode = ThemeMode.Light,
                userNotificationsEnabled = false,
                goal = StreakGoal.D365,
                reminderTime = LocalTime.of(8, 0),
                permissionRequested = true,
            ),
            result,
        )
    }

    @Test
    fun `StreakLoaded and AwaitingSystemSettings change one field each`() {
        assertEquals(initial.copy(currentStreak = 9), ProfileReducer.reduce(initial, ProfileEvent.StreakLoaded(9)))
        assertEquals(
            initial.copy(pendingEnable = true),
            ProfileReducer.reduce(initial, ProfileEvent.AwaitingSystemSettings),
        )
    }
}
