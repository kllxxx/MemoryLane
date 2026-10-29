package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.AppSettings

object ProfileReducer {

    /**
     * The state right after an intent. Persisting the changed values and the effects are up to the
     * ViewModel; turning ON while the system blocks notifications needs a dialog or the system
     * settings, so it leaves the state alone here.
     */
    fun reduce(state: ProfileState.Content, intent: ProfileIntent): ProfileState.Content = when (intent) {
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
        is ProfileIntent.SwipeButtonsToggled -> state.copy(swipeButtonsEnabled = intent.on)
        ProfileIntent.ReplayOnboardingClicked,
        ProfileIntent.ContactSupportClicked -> state
    }

    /** Updates of a screen that is still loading are dropped: [ProfileEvent.Loaded] reads everything fresh. */
    internal fun reduce(state: ProfileState, event: ProfileEvent): ProfileState = when (event) {
        is ProfileEvent.Loaded -> ProfileState.Content(
            currentStreak = event.currentStreak,
            systemNotificationsEnabled = event.systemNotificationsEnabled,
            versionName = event.versionName,
        ).withSettings(event.settings)
        is ProfileEvent.SettingsLoaded -> state.ifContent { it.withSettings(event.settings) }
        is ProfileEvent.StreakLoaded -> state.ifContent { it.copy(currentStreak = event.currentStreak) }
        // Waiting for the user to come back from the system settings
        ProfileEvent.AwaitingSystemSettings -> state.ifContent { it.copy(pendingEnable = true) }
    }

    private fun ProfileState.Content.withSettings(settings: AppSettings) = copy(
        themeMode = settings.themeMode,
        swipeButtonsEnabled = settings.swipeButtonsEnabled,
        userNotificationsEnabled = settings.notificationsEnabled,
        goal = settings.streakGoal,
        reminderTime = settings.reminderTime,
        permissionRequested = settings.notificationPermissionRequested,
    )

    private inline fun ProfileState.ifContent(block: (ProfileState.Content) -> ProfileState): ProfileState =
        if (this is ProfileState.Content) block(this) else this
}
