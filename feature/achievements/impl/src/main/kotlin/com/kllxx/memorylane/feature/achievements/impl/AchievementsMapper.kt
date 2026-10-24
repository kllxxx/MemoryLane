package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.domain.model.AchievementStats

private const val NON_BREAKING_SPACE = ' '
private const val GROUP_SIZE = 3
private const val BYTES_PER_MB = 1_000_000L
private const val BYTES_PER_GB = 1_000_000_000L
private const val BYTES_PER_TENTH_GB = BYTES_PER_GB / 10
private const val TENTHS_PER_GB = 10L

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

/**
 * SI units like the Android storage screens (1 GB = 1000³ bytes): whole MB below 1 GB (never rounded
 * up into 1000), above that one decimal with a comma and no trailing `,0`.
 */
internal fun formatSize(bytes: Long): FormattedSize {
    if (bytes < BYTES_PER_GB) return FormattedSize((bytes / BYTES_PER_MB).toString(), SizeUnit.MB)
    val tenths = (bytes + BYTES_PER_TENTH_GB / 2) / BYTES_PER_TENTH_GB
    val whole = tenths / TENTHS_PER_GB
    val fraction = tenths % TENTHS_PER_GB
    return FormattedSize(if (fraction == 0L) "$whole" else "$whole,$fraction", SizeUnit.GB)
}
