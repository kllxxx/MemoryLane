package com.kllxx.memorylane.core.domain.model

/**
 * @property viewedPhotos unique photos that ever got a swipe decision.
 * @property deletedPhotos photos actually deleted, after the system dialog was confirmed.
 * @property freedBytes bytes freed by those deletions, all time.
 */
data class AchievementStats(
    val currentStreak: Int,
    val bestStreak: Int,
    val viewedPhotos: Int,
    val deletedPhotos: Int,
    val freedBytes: Long,
)
