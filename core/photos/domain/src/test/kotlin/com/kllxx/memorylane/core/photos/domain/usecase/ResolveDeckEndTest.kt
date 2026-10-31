package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ResolveDeckEndTest {

    private val day = LocalDate.of(2021, 6, 22)
    private val completeDay = mockk<CompleteDay> { coEvery { this@mockk.invoke(any()) } returns Unit }
    private val swipeRepository = mockk<SwipeRepository> {
        coEvery { getPendingDeletes() } returns emptyMap()
    }

    private val resolve = ResolveDeckEnd(completeDay, swipeRepository)

    @Test
    fun `a day is completed, and with no marks anywhere the result follows`() = runTest {
        assertEquals(DeckEnd.Result, resolve(DeckKey.Day(day), hasMarksInDeck = false))

        coVerify(exactly = 1) { completeDay(day) }
    }

    @Test
    fun `a mark in the deck leads to before-delete without reading the stored marks`() = runTest {
        assertEquals(DeckEnd.BeforeDelete, resolve(DeckKey.Day(day), hasMarksInDeck = true))

        coVerify(exactly = 0) { swipeRepository.getPendingDeletes() }
    }

    @Test
    fun `a mark left on an earlier day leads to before-delete`() = runTest {
        coEvery { swipeRepository.getPendingDeletes() } returns mapOf(DeckKey.Day(day.minusDays(1)) to setOf(5L))

        assertEquals(DeckEnd.BeforeDelete, resolve(DeckKey.Day(day), hasMarksInDeck = false))
    }

    @Test
    fun `the undated deck is not a day and completes nothing`() = runTest {
        assertEquals(DeckEnd.Result, resolve(DeckKey.Undated, hasMarksInDeck = false))

        coVerify(exactly = 0) { completeDay(any()) }
    }

    @Test
    fun `a failed session write does not keep the user on the screen`() = runTest {
        coEvery { completeDay(any()) } throws IllegalStateException("db")

        assertEquals(DeckEnd.Result, resolve(DeckKey.Day(day), hasMarksInDeck = false))
    }

    @Test
    fun `stored marks that cannot be read count as none`() = runTest {
        coEvery { swipeRepository.getPendingDeletes() } throws IllegalStateException("db")

        assertEquals(DeckEnd.Result, resolve(DeckKey.Day(day), hasMarksInDeck = false))
    }

    @Test
    fun `cancellation is not swallowed`() = runTest {
        coEvery { completeDay(any()) } throws CancellationException("cancelled")

        try {
            resolve(DeckKey.Day(day), hasMarksInDeck = false)
            fail("expected CancellationException")
        } catch (e: CancellationException) {
            assertEquals("cancelled", e.message)
        }
    }
}
