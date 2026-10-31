package com.kllxx.memorylane.core.domain.streak

import com.kllxx.memorylane.core.domain.model.SwipeSession
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SessionCompletionTest {

    private val day = LocalDate.of(2026, 5, 18)
    private val other = LocalDate.of(2026, 10, 29)

    private fun session(date: LocalDate = day, completed: Boolean = true, completedAt: LocalDate) =
        SwipeSession(date, completed, savedBytes = 0, completedAt = completedAt)

    // --- the date to store ---

    @Test
    fun `a day completed for the first time gets the date it is completed on`() {
        assertEquals(day, SessionCompletion.completedAt(null, day, today = day))
        assertEquals(other, SessionCompletion.completedAt(null, day, today = other))
    }

    @Test
    fun `a day that was completed on its own date keeps it, whenever it is completed again`() {
        val existing = session(completedAt = day)

        assertEquals(day, SessionCompletion.completedAt(existing, day, today = other))
        assertEquals(day, SessionCompletion.completedAt(existing, day, today = day))
    }

    @Test
    fun `a day that was completed on the wrong date becomes a real day when it is completed on its own`() {
        val existing = session(completedAt = other)

        assertEquals(day, SessionCompletion.completedAt(existing, day, today = day))
    }

    @Test
    fun `a day that was completed on the wrong date gets the new wrong date, which still does not count`() {
        val existing = session(completedAt = other)

        assertEquals(other.plusDays(1), SessionCompletion.completedAt(existing, day, today = other.plusDays(1)))
    }

    @Test
    fun `a session that was only started gets the real completion date even if it was saved on the day itself`() {
        // Swiped on the day (saved as not completed with that date), deletion confirmed the next day
        val started = session(completed = false, completedAt = day)

        assertEquals(day.plusDays(1), SessionCompletion.completedAt(started, day, today = day.plusDays(1)))
    }

    @Test
    fun `an unfinished session that is finished on its own date is a real day`() {
        val started = session(completed = false, completedAt = day)

        assertEquals(day, SessionCompletion.completedAt(started, day, today = day))
    }

    // --- after a deletion ---

    @Test
    fun `the first deletion of a day creates a completed session completed today`() {
        val result = SessionCompletion.afterDeletion(null, day, today = other, freedBytes = 500, deletedCount = 2)

        assertEquals(SwipeSession(day, true, savedBytes = 500, completedAt = other, deletedCount = 2), result)
    }

    @Test
    fun `a deletion adds to what the day had freed and keeps the date it was completed on`() {
        val existing = SwipeSession(day, true, savedBytes = 100, completedAt = day, deletedCount = 1)

        val result = SessionCompletion.afterDeletion(existing, day, today = other, freedBytes = 50, deletedCount = 3)

        assertEquals(SwipeSession(day, true, savedBytes = 150, completedAt = day, deletedCount = 4), result)
    }

    @Test
    fun `a completed day keeps even a completion date that is not its own`() {
        val existing = SwipeSession(day, true, savedBytes = 0, completedAt = LocalDate.EPOCH)

        assertEquals(LocalDate.EPOCH, SessionCompletion.afterDeletion(existing, day, other, 10, 1).completedAt)
    }

    @Test
    fun `an unfinished day is completed today by a deletion`() {
        val existing = SwipeSession(day, false, savedBytes = 0, completedAt = day)

        assertEquals(other, SessionCompletion.afterDeletion(existing, day, other, 10, 1).completedAt)
    }

    // --- real days ---

    @Test
    fun `a real day is a completed session whose completion date is its own date`() {
        val sessions = listOf(
            session(date = day, completedAt = day),
            session(date = day.plusDays(1), completedAt = other),
            session(date = day.plusDays(2), completedAt = LocalDate.EPOCH),
            session(date = day.plusDays(3), completed = false, completedAt = day.plusDays(3)),
        )

        assertEquals(setOf(day), SessionCompletion.realDays(sessions))
    }

    @Test
    fun `no sessions, no real days`() {
        assertEquals(emptySet<LocalDate>(), SessionCompletion.realDays(emptyList()))
    }
}
