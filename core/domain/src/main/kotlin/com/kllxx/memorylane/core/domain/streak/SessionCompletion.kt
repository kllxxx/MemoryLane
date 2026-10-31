package com.kllxx.memorylane.core.domain.streak

import com.kllxx.memorylane.core.domain.model.SwipeSession
import java.time.LocalDate

/**
 * What a session says about the day it is for. [SwipeSession.completedAt] is the date the work was done, and only that
 * decides whether the day is a real day for the streak: it has to be the date of the day itself.
 */
object SessionCompletion {

    /**
     * The date to store when the day [date] is completed on [today]. A day that already was completed on its own date
     * keeps it: once a day counts, it keeps counting, whatever happens to it later. Any other day gets the date it is
     * really completed on, so a day that was done on the wrong date first can still become a real day, and a day that
     * was swiped on one date and deleted on the next is completed on the second.
     */
    fun completedAt(existing: SwipeSession?, date: LocalDate, today: LocalDate): LocalDate =
        if (existing != null && existing.completed && existing.completedAt == date) date else today

    /**
     * The session of [date] once [deletedCount] photos of it were deleted, [freedBytes] in all, on [today]. The day is
     * completed by this. A day that was completed already keeps the date it was completed on, whichever it is; any other
     * is completed [today].
     */
    fun afterDeletion(
        existing: SwipeSession?,
        date: LocalDate,
        today: LocalDate,
        freedBytes: Long,
        deletedCount: Int,
    ): SwipeSession = SwipeSession(
        date = date,
        completed = true,
        savedBytes = (existing?.savedBytes ?: 0) + freedBytes,
        completedAt = if (existing?.completed == true) existing.completedAt else today,
        deletedCount = (existing?.deletedCount ?: 0) + deletedCount,
    )

    /** The days completed on their own date. Sessions saved before the date was tracked carry the epoch and never match. */
    fun realDays(sessions: Collection<SwipeSession>): Set<LocalDate> =
        sessions.filter { it.completed && it.completedAt == it.date }.mapTo(HashSet()) { it.date }
}
