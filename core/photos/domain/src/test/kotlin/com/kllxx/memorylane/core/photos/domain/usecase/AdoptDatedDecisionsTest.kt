package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class AdoptDatedDecisionsTest {

    private val day = LocalDate.of(2021, 6, 22)
    private val otherDay = LocalDate.of(2023, 1, 30)
    private val photoRepository = mockk<PhotoRepository> {
        every { hasMediaPermission() } returns true
    }
    private val swipeRepository = mockk<SwipeRepository> {
        coEvery { adoptUndatedDecisions(any(), any()) } returns Unit
    }

    private val adopt = AdoptDatedDecisions(photoRepository, swipeRepository)

    private fun givenUndatedDecisions(vararg decisions: Pair<Long, PhotoDecision>) {
        coEvery { swipeRepository.getDecisions(DeckKey.Undated) } returns mapOf(*decisions)
    }

    @Test
    fun `a marked photo that got a date goes to its day`() = runTest {
        givenUndatedDecisions(1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)
        coEvery { photoRepository.getPhotoDates(listOf(1L)) } returns mapOf(1L to day)

        assertEquals(1, adopt(undatedIds = setOf(2L)))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(1L), day) }
    }

    @Test
    fun `photos that are still undated are not looked at`() = runTest {
        givenUndatedDecisions(1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP)

        assertEquals(0, adopt(undatedIds = setOf(1L, 2L)))

        coVerify(exactly = 0) { photoRepository.getPhotoDates(any()) }
        coVerify(exactly = 0) { swipeRepository.adoptUndatedDecisions(any(), any()) }
    }

    @Test
    fun `photos that got dates on different days go to their own days`() = runTest {
        givenUndatedDecisions(1L to PhotoDecision.DELETE, 2L to PhotoDecision.KEEP, 3L to PhotoDecision.DELETE)
        coEvery { photoRepository.getPhotoDates(listOf(1L, 2L, 3L)) } returns mapOf(1L to day, 2L to otherDay, 3L to day)

        assertEquals(3, adopt(undatedIds = emptySet()))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(1L, 3L), day) }
        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(2L), otherDay) }
    }

    @Test
    fun `a photo without a date, or gone, keeps its decision where it is`() = runTest {
        givenUndatedDecisions(1L to PhotoDecision.DELETE, 2L to PhotoDecision.DELETE)
        coEvery { photoRepository.getPhotoDates(listOf(1L, 2L)) } returns mapOf(2L to day)

        assertEquals(1, adopt(undatedIds = emptySet()))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(2L), day) }
        coVerify(exactly = 0) { swipeRepository.adoptUndatedDecisions(match { 1L in it }, any()) }
    }

    @Test
    fun `no decision in the undated deck, nothing to look at`() = runTest {
        givenUndatedDecisions()

        assertEquals(0, adopt(undatedIds = emptySet()))

        coVerify(exactly = 0) { photoRepository.getPhotoDates(any()) }
    }

    @Test
    fun `without access to the photos nothing is moved`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false

        assertEquals(0, adopt(undatedIds = emptySet()))

        coVerify(exactly = 0) { swipeRepository.getDecisions(any()) }
    }

    @Test
    fun `a failure to read the dates is for the caller to handle`() = runTest {
        givenUndatedDecisions(1L to PhotoDecision.DELETE)
        coEvery { photoRepository.getPhotoDates(any()) } throws PermissionDeniedException()

        try {
            adopt(emptySet())
            fail("expected PermissionDeniedException")
        } catch (e: PermissionDeniedException) {
            // expected
        }
    }

    @Test
    fun `the photos of a day take their decisions from the undated deck to that day`() = runTest {
        adopt.forDay(day, listOf(1L, 2L))

        coVerify(exactly = 1) { swipeRepository.adoptUndatedDecisions(listOf(1L, 2L), day) }
    }

    @Test
    fun `a day without photos moves nothing`() = runTest {
        adopt.forDay(day, emptyList())

        coVerify(exactly = 0) { swipeRepository.adoptUndatedDecisions(any(), any()) }
    }
}
