package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.streak.Streaks
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    val stats: Flow<AchievementStats>

    /**
     * The current and the best streak by the rules of the app: real days, completed on their own date, and no-photo days
     * (see `StreakCalculator`). It follows the completed days and the photo index, so every screen shows the same numbers.
     */
    val streaks: Flow<Streaks>

    /** Total bytes freed by confirmed deletions, shared with the end of the swipe flow. */
    val freedBytes: Flow<Long>

    /** Records that the photo got a swipe decision. The same id again changes nothing. */
    suspend fun markReviewed(mediaId: Long)
}
