package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal

object AchievementsReducer {

    fun reduce(state: AchievementsState, intent: AchievementsIntent): AchievementsState = when (intent) {
        AchievementsIntent.Load -> state.copy(isLoading = true)
    }

    internal fun reduce(state: AchievementsState, event: AchievementsEvent): AchievementsState = when (event) {
        is AchievementsEvent.StatsLoaded -> state.copy(
            currentStreak = event.currentStreak,
            bestStreak = event.bestStreak,
            totalFreedBytes = event.totalFreedBytes,
            totalSortedDays = event.totalSortedDays,
            streakGoalProgress = progress(event.currentStreak, state.streakGoal),
            isLoading = false,
        )
        is AchievementsEvent.GoalLoaded -> state.withGoal(event.goal)
    }

    private fun AchievementsState.withGoal(goal: StreakGoal) = copy(
        streakGoal = goal,
        streakGoalProgress = progress(currentStreak, goal),
    )

    private fun progress(currentStreak: Int, goal: StreakGoal): Float =
        (currentStreak.toFloat() / goal.days).coerceIn(0f, 1f)
}
