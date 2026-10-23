package com.kllxx.memorylane.feature.profile.impl

object ProfileReducer {

    /**
     * The state right after an intent. Persisting the changed values and the effects are up to the
     * ViewModel; turning ON while the system blocks notifications needs a dialog or the system
     * settings, so it leaves the state alone here.
     */
    fun reduce(state: ProfileState, intent: ProfileIntent): ProfileState = when (intent) {
        is ProfileIntent.SystemNotificationsChecked -> state.copy(
            systemNotificationsEnabled = intent.enabled,
            // Coming back from the system settings with notifications allowed finishes the pending enable
            userNotificationsEnabled = state.userNotificationsEnabled || (state.pendingEnable && intent.enabled),
            // Resolved either way after a resume
            pendingEnable = false,
        )
        is ProfileIntent.NotificationsToggled -> when {
            !intent.on -> state.copy(userNotificationsEnabled = false, pendingEnable = false)
            state.systemNotificationsEnabled -> state.copy(userNotificationsEnabled = true)
            else -> state
        }
        is ProfileIntent.PermissionResult -> state.copy(
            systemNotificationsEnabled = intent.granted,
            userNotificationsEnabled = state.userNotificationsEnabled || intent.granted,
        )
        ProfileIntent.OpenTimePicker -> if (state.notificationsOn) state.copy(isTimePickerVisible = true) else state
        ProfileIntent.DismissTimePicker -> state.copy(isTimePickerVisible = false)
        is ProfileIntent.ConfirmTime -> state.copy(isTimePickerVisible = false, reminderTime = intent.time)
        is ProfileIntent.SelectGoal -> state.copy(goal = intent.goal)
        is ProfileIntent.SelectTheme -> state.copy(themeMode = intent.mode)
        ProfileIntent.ReplayOnboardingClicked,
        ProfileIntent.ContactSupportClicked -> state
    }

    internal fun reduce(state: ProfileState, event: ProfileEvent): ProfileState = when (event) {
        is ProfileEvent.SettingsLoaded -> state.copy(
            themeMode = event.settings.themeMode,
            userNotificationsEnabled = event.settings.notificationsEnabled,
            goal = event.settings.streakGoal,
            reminderTime = event.settings.reminderTime,
            permissionRequested = event.settings.notificationPermissionRequested,
        )
        is ProfileEvent.StreakLoaded -> state.copy(currentStreak = event.currentStreak)
        // Waiting for the user to come back from the system settings
        ProfileEvent.AwaitingSystemSettings -> state.copy(pendingEnable = true)
    }
}
