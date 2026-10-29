package com.kllxx.memorylane.core.photos.domain.usecase

import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PhotosToReviewTest {

    private val date = LocalDate.of(2026, 10, 15)
    private val uri = mockk<Uri>(relaxed = true)
    private val photoRepository = mockk<PhotoRepository> {
        every { hasMediaPermission() } returns true
    }
    private val swipeRepository = mockk<SwipeRepository>()

    private val review = PhotosToReview(photoRepository, swipeRepository)

    private fun givenPhotos(vararg ids: Long) {
        coEvery { photoRepository.getPhotosForDate(date) } returns
            ids.map { Photo(it, uri, LocalDateTime.of(2020, 10, 15, 9, 0), sizeBytes = 1) }
    }

    private fun givenDecisions(vararg decisions: Pair<Long, PhotoDecision>) {
        coEvery { swipeRepository.getDecisionsByDate(date) } returns mapOf(*decisions)
    }

    @Test
    fun `photos without any decision are all waiting`() = runTest {
        givenPhotos(1, 2, 3)
        givenDecisions()

        assertEquals(3, review(date))
    }

    @Test
    fun `a day with every photo kept is completed`() = runTest {
        givenPhotos(1, 2)
        givenDecisions(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)

        assertEquals(0, review(date))
    }

    @Test
    fun `a photo marked for deletion is decided, so a cancelled dialog leaves the day completed`() = runTest {
        givenPhotos(1, 2)
        givenDecisions(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        assertEquals(0, review(date))
    }

    @Test
    fun `trashed and permanently deleted photos are decided`() = runTest {
        givenPhotos(1, 2)
        givenDecisions(1L to PhotoDecision.TRASHED, 2L to PhotoDecision.DELETED)

        assertEquals(0, review(date))
    }

    @Test
    fun `one photo without a decision keeps the whole day waiting and the count is of photos`() = runTest {
        givenPhotos(1, 2, 3)
        givenDecisions(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)

        assertEquals(3, review(date))
    }

    @Test
    fun `a day without photos has nothing to review`() = runTest {
        givenPhotos()
        givenDecisions()

        assertEquals(0, review(date))
    }

    @Test
    fun `decisions of photos that no longer exist do not matter`() = runTest {
        givenPhotos(1, 2)
        givenDecisions(1L to PhotoDecision.KEEP, 9L to PhotoDecision.KEEP)

        assertEquals(2, review(date))
    }

    @Test
    fun `decisions of photos that no longer exist do not keep a finished day open`() = runTest {
        givenPhotos(1)
        givenDecisions(1L to PhotoDecision.KEEP, 9L to PhotoDecision.DELETE)

        assertEquals(0, review(date))
    }

    @Test
    fun `without access to the photos there is nothing to review and nothing is asked`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false

        assertEquals(0, review(date))
        coVerify(exactly = 0) { photoRepository.getPhotosForDate(any()) }
        coVerify(exactly = 0) { swipeRepository.getDecisionsByDate(any()) }
    }

    @Test
    fun `a failure to read the photos is thrown to the caller`() = runTest {
        coEvery { photoRepository.getPhotosForDate(date) } throws IllegalStateException("query failed")

        assertFails { review(date) }
    }

    @Test
    fun `a failure to read the decisions is thrown to the caller`() = runTest {
        givenPhotos(1)
        coEvery { swipeRepository.getDecisionsByDate(date) } throws IllegalStateException("db failed")

        assertFails { review(date) }
    }

    private suspend fun assertFails(block: suspend () -> Unit) {
        val failure = try {
            block()
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertNotNull("the failure reaches the caller", failure)
    }
}
