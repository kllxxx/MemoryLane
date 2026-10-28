package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.format.FormattedSize

sealed interface AchievementsState {
    data object Loading : AchievementsState

    /** Nothing has been swiped yet: there is no statistic to show. */
    data object Empty : AchievementsState

    /** Counts are formatted by the mapper, so the UI only displays them. */
    data class Stats(
        val currentStreak: Int,
        val bestStreak: Int,
        val freedSpace: FormattedSize,
        val viewedPhotos: String,
        val deletedPhotos: String,
    ) : AchievementsState
}
