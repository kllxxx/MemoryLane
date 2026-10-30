package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.AchievementStats

sealed interface AchievementsEvent {
    data class StatsLoaded(val stats: AchievementStats) : AchievementsEvent
}
