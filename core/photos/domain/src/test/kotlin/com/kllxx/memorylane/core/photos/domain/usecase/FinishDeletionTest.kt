package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FinishDeletionTest {

    private val day = LocalDate.of(2021, 6, 22)
    private val deck = DeckKey.Day(day)
    private val photoRepository = mockk<PhotoRepository> {
        coEvery { refreshOnThisDayIndex() } returns Unit
    }
    private val swipeRepository = mockk<SwipeRepository> {
        coEvery { getDecisions(deck) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.TRASHED, 3L to PhotoDecision.KEEP)
    }
    private val completeDay = mockk<CompleteDay> { coEvery { this@mockk.invoke(any()) } returns Unit }

    private val finish = FinishDeletion(photoRepository, swipeRepository, completeDay)

    @Test
    fun `after a deletion the index is refreshed and the result has the deleted and the kept numbers`() = runTest {
        val result = finish.afterDeletion(deck, DeletionOutcome(deletedCount = 2, freedBytes = 400))

        assertEquals(DeckResult(deletedCount = 2, freedBytes = 400, keptCount = 2, totalCount = 3), result)
        coVerify(exactly = 1) { photoRepository.refreshOnThisDayIndex() }
        coVerify(exactly = 0) { completeDay(any()) }
    }

    @Test
    fun `a failed refresh does not stop the result`() = runTest {
        coEvery { photoRepository.refreshOnThisDayIndex() } throws IllegalStateException("io")

        val result = finish.afterDeletion(deck, DeletionOutcome(1, 10))

        assertEquals(DeckResult(1, 10, keptCount = 2, totalCount = 3), result)
    }

    @Test
    fun `decisions that cannot be read give zero kept and total`() = runTest {
        coEvery { swipeRepository.getDecisions(deck) } throws IllegalStateException("db")

        val result = finish.afterDeletion(deck, DeletionOutcome(1, 10))

        assertEquals(DeckResult(1, 10, keptCount = 0, totalCount = 0), result)
    }

    @Test
    fun `without deleting a day is completed and nothing is deleted or refreshed`() = runTest {
        val result = finish.withoutDeleting(deck)

        assertEquals(DeckResult(0, 0, keptCount = 2, totalCount = 3), result)
        coVerify(exactly = 1) { completeDay(day) }
        coVerify(exactly = 0) { photoRepository.refreshOnThisDayIndex() }
    }

    @Test
    fun `without deleting the undated deck completes nothing`() = runTest {
        coEvery { swipeRepository.getDecisions(DeckKey.Undated) } returns mapOf(7L to PhotoDecision.KEEP)

        val result = finish.withoutDeleting(DeckKey.Undated)

        assertEquals(DeckResult(0, 0, keptCount = 1, totalCount = 1), result)
        coVerify(exactly = 0) { completeDay(any()) }
    }

    @Test
    fun `a failed session write does not stop the result`() = runTest {
        coEvery { completeDay(any()) } throws IllegalStateException("db")

        assertEquals(DeckResult(0, 0, keptCount = 2, totalCount = 3), finish.withoutDeleting(deck))
    }
}
