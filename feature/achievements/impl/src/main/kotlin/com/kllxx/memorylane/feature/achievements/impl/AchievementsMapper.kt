package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.format.formatSize

import com.kllxx.memorylane.core.domain.model.AchievementStats

private const val NON_BREAKING_SPACE = ' '
private const val GROUP_SIZE = 3

/** The statistic is only worth showing once a photo has been swiped, even if the streak is still 0. */
internal fun AchievementStats.toState(): AchievementsState =
    if (viewedPhotos == 0) {
        AchievementsState.Empty
    } else {
        AchievementsState.Stats(
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            freedSpace = formatSize(freedBytes),
            viewedPhotos = formatCount(viewedPhotos),
            deletedPhotos = formatCount(deletedPhotos),
        )
    }

/** Groups thousands with a non-breaking space: `1 248`, `12 304`. */
internal fun formatCount(count: Int): String =
    count.toString().reversed().chunked(GROUP_SIZE).joinToString(NON_BREAKING_SPACE.toString()).reversed()
