package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SwipeRepository {
    /** Records [decision] for the photo of the day [date], replacing the one it had. */
    suspend fun saveDecision(photoId: Long, date: LocalDate, decision: PhotoDecision)

    /** The decisions of the day [date] only, by photo id. Undecided photos are not in the map. */
    suspend fun getDecisionsByDate(date: LocalDate): Map<Long, PhotoDecision>

    /** The photos marked DELETE and not confirmed yet, by the day they were decided on. Days without any are not in the map. */
    suspend fun getPendingDeletes(): Map<LocalDate, Set<Long>>

    /** Takes the decision of the photo back, so it is undecided again. */
    suspend fun removeDecision(photoId: Long)

    /**
     * Confirms the deletion of the photos of [bytesById] (photo id to size in bytes) that are still marked DELETE, on
     * whichever day: they become [decision] (TRASHED or DELETED) in their own day, and their count and bytes are added
     * to the session of that day, which is completed. All in one transaction, and the outcome covers all days. Photos
     * not marked DELETE any more are ignored, so confirming the same photos twice counts them once and returns zero the
     * second time.
     */
    suspend fun confirmDeletion(bytesById: Map<Long, Long>, decision: PhotoDecision): DeletionOutcome

    /** Sets [decision] on every photo of [photoIds] that has one, e.g. to [PhotoDecision.TRASHED] once they are gone. */
    suspend fun setDecisions(photoIds: Collection<Long>, decision: PhotoDecision)

    suspend fun saveSession(session: SwipeSession)

    /** A completed session whose [SwipeSession.completedAt] is [date], or null. */
    suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession?
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
