package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.AchievementStats
import kotlinx.coroutines.flow.Flow

interface StatsRepository {
    val stats: Flow<AchievementStats>

    /** Total bytes freed by confirmed deletions, shared with the end of the swipe flow. */
    val freedBytes: Flow<Long>

    /** Records that the photo got a swipe decision. The same id again changes nothing. */
    suspend fun markReviewed(mediaId: Long)
}
