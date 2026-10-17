package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal

sealed interface AchievementsIntent {
    data object Load : AchievementsIntent
    data class SetStreakGoal(val goal: StreakGoal) : AchievementsIntent
}
