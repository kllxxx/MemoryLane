package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface SwipeRepository {
    /** Records [decision] for the photo of the day [date], replacing the one it had. */
    suspend fun saveDecision(photoId: Long, date: LocalDate, decision: PhotoDecision)

    /** The decisions of the day [date] only, by photo id. Undecided photos are not in the map. */
    suspend fun getDecisionsByDate(date: LocalDate): Map<Long, PhotoDecision>

    /** Takes the decision of the photo back, so it is undecided again. */
    suspend fun removeDecision(photoId: Long)

    /** Sets [decision] on every photo of [photoIds] that has one, e.g. to [PhotoDecision.TRASHED] once they are gone. */
    suspend fun setDecisions(photoIds: Collection<Long>, decision: PhotoDecision)

    suspend fun saveSession(session: SwipeSession)

    /** A completed session whose [SwipeSession.completedAt] is [date], or null. */
    suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession?
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
