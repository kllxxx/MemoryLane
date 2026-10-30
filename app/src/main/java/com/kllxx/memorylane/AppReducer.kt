package com.kllxx.memorylane

object AppReducer {

    /** The reminder only leads to an effect, so the state stays as it is. */
    fun reduce(state: AppUiState, intent: AppIntent): AppUiState = when (intent) {
        AppIntent.ReminderOpened,
        AppIntent.Resumed -> state
    }

    fun reduce(state: AppUiState, event: AppEvent): AppUiState = when (event) {
        is AppEvent.Resolved -> AppUiState.Ready(event.themeMode, event.startDestination)
        // A theme that arrives before the app is ready is part of the first frame, which reads the settings itself
        is AppEvent.ThemeChanged -> if (state is AppUiState.Ready) state.copy(themeMode = event.themeMode) else state
    }
}
