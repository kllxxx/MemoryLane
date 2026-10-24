package com.kllxx.memorylane.feature.achievements.impl

object AchievementsReducer {

    /** The calendar click only leads to an effect, so the state stays as it is. */
    fun reduce(state: AchievementsState, intent: AchievementsIntent): AchievementsState = when (intent) {
        AchievementsIntent.GoToCalendarClicked -> state
    }

    internal fun reduce(state: AchievementsState, event: AchievementsEvent): AchievementsState = when (event) {
        is AchievementsEvent.StatsLoaded -> event.stats.toState()
    }
}
