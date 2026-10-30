package com.kllxx.memorylane.reminder

import com.kllxx.memorylane.core.domain.model.DeckKey
import android.app.Application
import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import com.kllxx.memorylane.ClockTodayProvider
import com.kllxx.memorylane.MutableClock
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class TodayReviewTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"))
    private val photos = listOf(1L, 2L, 3L).map {
        Photo(it, mockk<Uri>(relaxed = true), LocalDate.of(2024, 10, 15).atStartOfDay(), sizeBytes = 100)
    }
    private val photoRepository = mockk<PhotoRepository> {
        every { hasMediaPermission() } returns true
        coEvery { getPhotosForDate(today) } returns photos
    }
    private val swipeRepository = mockk<SwipeRepository> {
        coEvery { getDecisions(DeckKey.Day(today)) } returns emptyMap()
        coEvery { adoptUndatedDecisions(any(), any()) } returns Unit
    }

    private fun review() = TodayReview(photoRepository, swipeRepository, ClockTodayProvider(clock))

    @Test
    fun `undecided photos are all waiting`() = runTest {
        assertEquals(3, review().photosToReview())
    }

    @Test
    fun `a day with every photo decided has nothing to review`() = runTest {
        coEvery { swipeRepository.getDecisions(DeckKey.Day(today)) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP, 3L to PhotoDecision.TRASHED)
        assertEquals(0, review().photosToReview())
    }

    @Test
    fun `one photo left undecided keeps the whole day waiting`() = runTest {
        coEvery { swipeRepository.getDecisions(DeckKey.Day(today)) } returns mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP)
        assertEquals(3, review().photosToReview())
    }

    @Test
    fun `a pending delete is a decision, so the day is completed`() = runTest {
        coEvery { swipeRepository.getDecisions(DeckKey.Day(today)) } returns
            mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.KEEP, 3L to PhotoDecision.DELETE)
        assertEquals(0, review().photosToReview())
    }

    @Test
    fun `no photos today`() = runTest {
        coEvery { photoRepository.getPhotosForDate(today) } returns emptyList()
        assertEquals(0, review().photosToReview())
    }

    @Test
    fun `without access to the photos there is nothing to review and nothing is asked`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false
        assertEquals(0, review().photosToReview())
    }

    @Test
    fun `a failed lookup is nothing to review`() = runTest {
        coEvery { photoRepository.getPhotosForDate(today) } throws IllegalStateException("query failed")
        assertEquals(0, review().photosToReview())
    }
}
