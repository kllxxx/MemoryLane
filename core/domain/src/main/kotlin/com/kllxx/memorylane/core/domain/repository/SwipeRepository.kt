package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.flow.Flow

interface SwipeRepository {
    /**
     * Records [decision] for the photo of the deck [deck], replacing the one it had. [takenAt] is kept with it; null
     * leaves the time stored before.
     */
    suspend fun saveDecision(photoId: Long, deck: DeckKey, decision: PhotoDecision, takenAt: LocalDateTime? = null)

    /**
     * When the photos of [photoIds] were taken, as the deck had it when they were decided. A photo whose decision was
     * saved without it, or was never saved, is missing. It stays after the photo is trashed, which a gallery query cannot
     * tell any more.
     */
    suspend fun getTakenAt(photoIds: Collection<Long>): Map<Long, LocalDateTime>

    /** The decisions of the deck [deck] only, by photo id. Undecided photos are not in the map. */
    suspend fun getDecisions(deck: DeckKey): Map<Long, PhotoDecision>

    /**
     * Gives the photos of [photoIds] that were decided in the undated deck to the day [date], which they have now got
     * a date on: the decision is kept and the photo leaves the undated deck. Photos with a decision in another deck, or
     * with none, are left alone.
     */
    suspend fun adoptUndatedDecisions(photoIds: Collection<Long>, date: LocalDate)

    /** The photos marked DELETE and not confirmed yet, by the deck they were decided in. Decks without any are not in the map. */
    suspend fun getPendingDeletes(): Map<DeckKey, Set<Long>>

    /** Takes the decision of the photo back, so it is undecided again. */
    suspend fun removeDecision(photoId: Long)

    /**
     * Confirms the deletion of the photos of [bytesById] (photo id to size in bytes) that are still marked DELETE, in
     * whichever deck: they become [decision] (TRASHED or DELETED) in their own deck, and their count and bytes are added
     * to the totals of that deck, a day's session being completed. All in one transaction, and the outcome covers all
     * decks. Photos not marked DELETE any more are ignored, so confirming the same photos twice counts them once and
     * returns zero the second time.
     */
    suspend fun confirmDeletion(bytesById: Map<Long, Long>, decision: PhotoDecision): DeletionOutcome

    /** Sets [decision] on every photo of [photoIds] that has one, e.g. to [PhotoDecision.TRASHED] once they are gone. */
    suspend fun setDecisions(photoIds: Collection<Long>, decision: PhotoDecision)

    /** Saves the session of a day. The undated deck has no session of its own to show: it never counts as a day. */
    suspend fun saveSession(session: SwipeSession)

    /** A completed session of a real day whose [SwipeSession.completedAt] is [date], or null. */
    suspend fun getSessionByCompletedAt(date: LocalDate): SwipeSession?

    /** The completed sessions of real days; the undated deck is never one of them. */
    fun getCompletedSessions(): Flow<List<SwipeSession>>
}
