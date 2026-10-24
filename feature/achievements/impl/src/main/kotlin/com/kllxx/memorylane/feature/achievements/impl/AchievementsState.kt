package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal

data class AchievementsState(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val totalFreedBytes: Long = 0L,
    val totalSortedDays: Int = 0,
    val streakGoal: StreakGoal = StreakGoal.D7,
    val streakGoalProgress: Float = 0f,
    val isLoading: Boolean = false,
)
