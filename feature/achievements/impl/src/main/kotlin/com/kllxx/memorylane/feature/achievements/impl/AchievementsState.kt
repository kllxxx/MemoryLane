package com.kllxx.memorylane.feature.achievements.impl

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

enum class SizeUnit { MB, GB }

/** The number and its unit are shown as separate texts. */
data class FormattedSize(val value: String, val unit: SizeUnit)
