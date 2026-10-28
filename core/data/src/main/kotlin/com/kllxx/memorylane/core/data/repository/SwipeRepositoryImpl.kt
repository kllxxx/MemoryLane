package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.db.dao.PhotoDecisionDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.data.mapper.toDecisionOrNull
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.data.mapper.toEntity
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

internal class SwipeRepositoryImpl @Inject constructor(
    private val photoDecisionDao: PhotoDecisionDao,
    private val swipeSessionDao: SwipeSessionDao,
    private val clock: Clock,
) : SwipeRepository {

    override suspend fun saveDecision(photoId: Long, date: LocalDate, decision: PhotoDecision) =
        photoDecisionDao.upsert(
            PhotoDecisionEntity(
                photoId = photoId,
                sessionDate = date,
                decision = decision.name,
                decidedAt = clock.millis(),
            ),
        )

    override suspend fun getDecisionsByDate(date: LocalDate): Map<Long, PhotoDecision> =
        photoDecisionDao.getByDate(date)
            .mapNotNull { row -> row.toDecisionOrNull()?.let { row.photoId to it } }
            .toMap()

    override suspend fun removeDecision(photoId: Long) = photoDecisionDao.deleteById(photoId)

    override suspend fun setDecisions(photoIds: Collection<Long>, decision: PhotoDecision) {
        val now = clock.millis()
        photoIds.chunked(SQL_VARIABLE_LIMIT).forEach { photoDecisionDao.updateDecisions(it, decision.name, now) }
    }

    override suspend fun saveSession(session: SwipeSession) =
        swipeSessionDao.insert(session.toEntity())

    override suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession? =
        swipeSessionDao.getCompletedOn(date)?.toDomain()

    override fun getCompletedSessions(): Flow<List<SwipeSession>> =
        swipeSessionDao.observeCompleted().map { list -> list.map { it.toDomain() } }

    private companion object {
        // SQLite allows 999 variables per statement on older Android versions
        const val SQL_VARIABLE_LIMIT = 500
    }
}
