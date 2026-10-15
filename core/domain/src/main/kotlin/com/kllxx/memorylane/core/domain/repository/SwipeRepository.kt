package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SwipeRepository {
    suspend fun savePendingDelete(pendingDelete: PendingDelete)
    suspend fun getPendingDeletes(): List<PendingDelete>
    suspend fun clearPendingDeletes()

    /** Clears only the pending deletes of the session on [date], keeping other sessions' deletes. */
    suspend fun clearPendingDeletesByDate(date: LocalDate)
    suspend fun saveSession(session: SwipeSession)
    suspend fun getLastSession(): SwipeSession?
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
