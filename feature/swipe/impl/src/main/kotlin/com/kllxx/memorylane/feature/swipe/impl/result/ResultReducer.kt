package com.kllxx.memorylane.feature.swipe.impl.result

object ResultReducer {

    /** Done only leads to an effect, so the state stays as it is. */
    fun reduce(state: ResultState, intent: ResultIntent): ResultState = when (intent) {
        ResultIntent.Done -> state
    }

    fun reduce(state: ResultState, event: ResultEvent): ResultState = when (event) {
        // A streak of zero is no chip
        is ResultEvent.StreakLoaded -> state.copy(streakDays = event.currentStreak.takeIf { it > 0 })
    }
}
