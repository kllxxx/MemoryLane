package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SwipeRepository {
    suspend fun savePendingDelete(pendingDelete: PendingDelete)
    /** The pending deletes of the session on [date] only. */
    suspend fun getPendingDeletesByDate(date: LocalDate): List<PendingDelete>
    suspend fun removePendingDelete(photoId: Long)

    /** Clears only the pending deletes of the session on [date], keeping other sessions' deletes. */
    suspend fun clearPendingDeletesByDate(date: LocalDate)
    suspend fun saveSession(session: SwipeSession)

    /** A completed session whose [SwipeSession.completedAt] is [date], or null. */
    suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession?

    /**
     * Removes pending deletes left behind for dates whose session is already completed, e.g. when
     * the app was killed after the photos were deleted but before Review cleaned up.
     */
    suspend fun cleanupStalePendingDeletes()
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
