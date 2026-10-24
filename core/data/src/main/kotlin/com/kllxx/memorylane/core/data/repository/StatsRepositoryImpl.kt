package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class StatsRepositoryImpl @Inject constructor(
    private val reviewedPhotoDao: ReviewedPhotoDao,
    private val swipeSessionDao: SwipeSessionDao,
    private val clock: Clock,
) : StatsRepository {

    override val stats: Flow<AchievementStats> = combine(
        swipeSessionDao.observeCompleted(),
        reviewedPhotoDao.observeCount(),
        swipeSessionDao.observeDeletedCount(),
        swipeSessionDao.observeFreedBytes(),
    ) { sessions, viewed, deleted, freedBytes ->
        // Streaks follow the day the work was done; sessions saved before that was tracked carry the epoch marker
        val doneOn = sessions.map { it.completedAt }.filter { it != LocalDate.EPOCH }
        AchievementStats(
            currentStreak = StreakCalculator.currentStreak(doneOn, LocalDate.now(clock)),
            bestStreak = StreakCalculator.bestStreak(doneOn),
            viewedPhotos = viewed,
            deletedPhotos = deleted,
            freedBytes = freedBytes,
        )
    }

    override val freedBytes: Flow<Long> = swipeSessionDao.observeFreedBytes()

    override suspend fun markReviewed(mediaId: Long) =
        reviewedPhotoDao.insert(ReviewedPhotoEntity(mediaId, LocalDate.now(clock)))
}
