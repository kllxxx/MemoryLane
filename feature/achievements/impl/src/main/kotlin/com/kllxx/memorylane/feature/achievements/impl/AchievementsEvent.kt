package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.AchievementStats

sealed interface AchievementsEvent {
    data class StatsLoaded(val stats: AchievementStats) : AchievementsEvent

    data class LoadFailed(val error: UiError) : AchievementsEvent
}
