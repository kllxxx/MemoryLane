package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal

internal sealed interface AchievementsEvent {
    data class StatsLoaded(
        val currentStreak: Int,
        val bestStreak: Int,
        val totalFreedBytes: Long,
        val totalSortedDays: Int,
    ) : AchievementsEvent

    data class GoalLoaded(val goal: StreakGoal) : AchievementsEvent

    /** The write failed, so the goal goes back to the value it had before the change. */
    data class GoalReverted(val goal: StreakGoal) : AchievementsEvent
}
