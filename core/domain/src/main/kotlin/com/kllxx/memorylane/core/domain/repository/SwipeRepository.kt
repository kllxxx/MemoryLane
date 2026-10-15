package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow

interface SwipeRepository {
    suspend fun savePendingDelete(pendingDelete: PendingDelete)
    suspend fun getPendingDeletes(): List<PendingDelete>
    suspend fun clearPendingDeletes()
    suspend fun saveSession(session: SwipeSession)
    suspend fun getLastSession(): SwipeSession?
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
