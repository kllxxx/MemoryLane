package com.kllxx.memorylane.core.data.impl.repository

import com.kllxx.memorylane.core.data.api.model.PendingDelete
import com.kllxx.memorylane.core.data.api.model.SwipeSession
import com.kllxx.memorylane.core.data.api.repository.SwipeRepository
import com.kllxx.memorylane.core.data.impl.db.dao.PendingDeleteDao
import com.kllxx.memorylane.core.data.impl.db.dao.SwipeSessionDao
import com.kllxx.memorylane.core.data.impl.mapper.toDomain
import com.kllxx.memorylane.core.data.impl.mapper.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class SwipeRepositoryImpl @Inject constructor(
    private val pendingDeleteDao: PendingDeleteDao,
    private val swipeSessionDao: SwipeSessionDao,
) : SwipeRepository {

    override suspend fun savePendingDelete(pendingDelete: PendingDelete) =
        pendingDeleteDao.insert(pendingDelete.toEntity())

    override suspend fun getPendingDeletes(): List<PendingDelete> =
        pendingDeleteDao.getAll().map { it.toDomain() }

    override suspend fun clearPendingDeletes() = pendingDeleteDao.clear()

    override suspend fun saveSession(session: SwipeSession) =
        swipeSessionDao.insert(session.toEntity())

    override suspend fun getLastSession(): SwipeSession? =
        swipeSessionDao.getLast()?.toDomain()

    override fun getCompletedSessions(): Flow<List<SwipeSession>> =
        swipeSessionDao.observeCompleted().map { list -> list.map { it.toDomain() } }
}
