package com.kllxx.memorylane.feature.profile.impl

object ProfileReducer {

    fun reduce(state: ProfileState, intent: ProfileIntent): ProfileState = when (intent) {
        ProfileIntent.Load -> state.copy(isLoading = true)
        is ProfileIntent.SetTheme -> state.copy(themeMode = intent.mode)
        is ProfileIntent.SetNotifications -> state.copy(notificationsEnabled = intent.enabled)
        is ProfileIntent.SetStreakGoal -> state.copy(streakGoal = intent.goal)
        ProfileIntent.RepeatOnboarding -> state
    }

    internal fun reduce(state: ProfileState, event: ProfileEvent): ProfileState = when (event) {
        is ProfileEvent.SettingsLoaded -> state.copy(
            themeMode = event.themeMode,
            notificationsEnabled = event.notificationsEnabled,
            streakGoal = event.streakGoal,
            isLoading = false,
        )
    }
}
