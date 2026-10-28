package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class StatsRepositoryImpl @Inject constructor(
    private val reviewedPhotoDao: ReviewedPhotoDao,
    private val swipeSessionDao: SwipeSessionDao,
    private val photoRepository: PhotoRepository,
    private val clock: Clock,
) : StatsRepository {

    override val streaks: Flow<Streaks> = combine(
        swipeSessionDao.observeCompleted(),
        photoRepository.onThisDayIndex,
    ) { sessions, index ->
        val realDays = SessionCompletion.realDays(sessions.map { it.toDomain() })
        // Without the index (not read yet, or no access to the photos) no day is known to be empty, so none counts as such
        StreakCalculator.streaks(realDays, LocalDate.now(clock)) { date -> index?.hasPhotosFor(date) ?: true }
    }.distinctUntilChanged()

    override val stats: Flow<AchievementStats> = combine(
        streaks,
        reviewedPhotoDao.observeCount(),
        swipeSessionDao.observeDeletedCount(),
        swipeSessionDao.observeFreedBytes(),
    ) { streaks, viewed, deleted, freedBytes ->
        AchievementStats(
            currentStreak = streaks.current,
            bestStreak = streaks.best,
            viewedPhotos = viewed,
            deletedPhotos = deleted,
            freedBytes = freedBytes,
        )
    }

    override val freedBytes: Flow<Long> = swipeSessionDao.observeFreedBytes()

    override suspend fun markReviewed(mediaId: Long) =
        reviewedPhotoDao.insert(ReviewedPhotoEntity(mediaId, LocalDate.now(clock)))
}
