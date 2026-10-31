package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CompleteDayTest {

    private val day = LocalDate.of(2021, 6, 22)
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val swipeRepository = mockk<SwipeRepository> {
        coEvery { saveSession(any()) } returns Unit
    }

    private val completeDay = CompleteDay(swipeRepository, clock)

    private fun givenSessions(vararg sessions: SwipeSession) {
        coEvery { swipeRepository.getCompletedSessions() } returns flowOf(sessions.toList())
    }

    @Test
    fun `a day without a session is saved as completed today`() = runTest {
        givenSessions()

        completeDay(day)

        coVerify(exactly = 1) {
            swipeRepository.saveSession(SwipeSession(day, true, savedBytes = 0, completedAt = today, deletedCount = 0))
        }
    }

    @Test
    fun `what the day freed is kept`() = runTest {
        givenSessions(SwipeSession(day, true, savedBytes = 700, completedAt = today, deletedCount = 4))

        completeDay(day)

        coVerify { swipeRepository.saveSession(SwipeSession(day, true, 700, today, 4)) }
    }

    @Test
    fun `a day completed on its own date keeps that date`() = runTest {
        givenSessions(SwipeSession(day, true, savedBytes = 0, completedAt = day))

        completeDay(day)

        coVerify { swipeRepository.saveSession(SwipeSession(day, true, 0, day, 0)) }
    }

    @Test
    fun `the session of another day is not looked at`() = runTest {
        givenSessions(SwipeSession(day.plusDays(1), true, savedBytes = 9, completedAt = day.plusDays(1), deletedCount = 9))

        completeDay(day)

        coVerify { swipeRepository.saveSession(SwipeSession(day, true, 0, today, 0)) }
    }

    @Test
    fun `a failure to read is thrown to the caller and nothing is saved`() = runTest {
        coEvery { swipeRepository.getCompletedSessions() } throws IllegalStateException("db")

        try {
            completeDay(day)
            fail("expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertEquals("db", e.message)
        }

        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }
}
