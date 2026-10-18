package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.data.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.mapper.toDomain
import com.kllxx.memorylane.core.data.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject

internal class SwipeRepositoryImpl @Inject constructor(
    private val pendingDeleteDao: PendingDeleteDao,
    private val swipeSessionDao: SwipeSessionDao,
) : SwipeRepository {

    override suspend fun savePendingDelete(pendingDelete: PendingDelete) =
        pendingDeleteDao.insert(pendingDelete.toEntity())

    override suspend fun getPendingDeletesByDate(date: LocalDate): List<PendingDelete> =
        pendingDeleteDao.getByDate(date).map { it.toDomain() }

    override suspend fun removePendingDelete(photoId: Long) = pendingDeleteDao.deleteByPhotoId(photoId)

    override suspend fun clearPendingDeletesByDate(date: LocalDate) = pendingDeleteDao.clearByDate(date)

    override suspend fun saveSession(session: SwipeSession) =
        swipeSessionDao.insert(session.toEntity())

    override suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession? =
        swipeSessionDao.getCompletedOn(date)?.toDomain()

    override suspend fun cleanupStalePendingDeletes() = pendingDeleteDao.clearForCompletedSessions()

    override fun getCompletedSessions(): Flow<List<SwipeSession>> =
        swipeSessionDao.observeCompleted().map { list -> list.map { it.toDomain() } }
}
