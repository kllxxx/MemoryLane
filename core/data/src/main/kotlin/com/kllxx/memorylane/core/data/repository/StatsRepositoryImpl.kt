package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.data.db.dao.ReviewedPhotoDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.ReviewedPhotoEntity
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class StatsRepositoryImpl @Inject constructor(
    private val reviewedPhotoDao: ReviewedPhotoDao,
    private val swipeSessionDao: SwipeSessionDao,
    private val photoRepository: PhotoRepository,
    private val permissionsRepository: PermissionsRepository,
    private val streakStore: AppSettingsDataStore,
    private val clock: Clock,
) : StatsRepository {

    /**
     * With access to the photos the streaks are worked out and stored. Without it the gallery cannot tell which days are
     * empty, so nothing is worked out: the stored values stay, a streak is never lost because the gallery is unreadable.
     * Until the index is read the stored values stand in too, and nothing is stored, as a guess must not replace them.
     */
    override val streaks: Flow<Streaks> = combine(
        swipeSessionDao.observeCompleted(),
        photoRepository.onThisDayIndex,
        permissionsRepository.state.map { it.photos }.distinctUntilChanged(),
    ) { sessions, index, access -> Triple(sessions, index, access) }
        .transform { (sessions, index, access) ->
            val stored = streakStore.lastStreaks()
            when {
                !access -> emit(stored ?: Streaks.None)
                index == null -> emit(stored ?: compute(sessions, null))
                else -> {
                    val streaks = compute(sessions, index)
                    if (streaks != stored) streakStore.saveStreaks(streaks)
                    emit(streaks)
                }
            }
        }
        .distinctUntilChanged()

    private fun compute(sessions: List<SwipeSessionEntity>, index: OnThisDayIndex?): Streaks {
        val realDays = SessionCompletion.realDays(sessions.map { it.toDomain() })
        // Without the index (not read yet) no day is known to be empty, so none counts as such
        return StreakCalculator.streaks(realDays, LocalDate.now(clock)) { date -> index?.hasPhotosFor(date) ?: true }
    }

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
