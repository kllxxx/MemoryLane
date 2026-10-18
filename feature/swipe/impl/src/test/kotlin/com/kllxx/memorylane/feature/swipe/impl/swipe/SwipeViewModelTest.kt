package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2026, 10, 15)
    private val today = LocalDate.of(2026, 10, 20)
    private val earlier = LocalDate.of(2026, 10, 16)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val uri = mockk<Uri>(relaxed = true)
    private val photos = listOf(
        Photo(1, uri, LocalDate.of(2024, 10, 15), sizeBytes = 100),
        Photo(2, uri, LocalDate.of(2025, 10, 15), sizeBytes = 200),
    )
    private lateinit var photoRepository: PhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var viewModel: SwipeViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        photoRepository = mockk()
        swipeRepository = mockk(relaxed = true)
        coEvery { photoRepository.getPhotosForDate(date) } returns photos
        coEvery { photoRepository.getPhotoLocation(any()) } returns null
        coEvery { swipeRepository.getPendingDeletesByDate(date) } returns emptyList()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
    }

    // The ViewModel starts loading as soon as it is created, so tests stub the repositories first
    private fun createViewModel() {
        val savedStateHandle = SavedStateHandle(mapOf("date" to date.toString()))
        viewModel = SwipeViewModel(photoRepository, swipeRepository, clock, savedStateHandle)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // Photos for the route date are requested by the ViewModel's init
    private fun TestScope.load() {
        if (!::viewModel.isInitialized) createViewModel()
        advanceUntilIdle()
    }

    @Test
    fun `photos for the route date are loaded without any intent`() = runTest(dispatcher) {
        load()
        val state = viewModel.state.value
        assertEquals(photos, state.photos)
        assertEquals(false, state.isLoading)
        assertEquals(date, state.date)
    }

    @Test
    fun `SwipeLeft persists the pending delete`() = runTest(dispatcher) {
        load()
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.state.value.pendingDeletes)
        coVerify(exactly = 1) { swipeRepository.savePendingDelete(PendingDelete(1, date)) }
    }

    @Test
    fun `SwipeRight does not persist anything`() = runTest(dispatcher) {
        load()
        viewModel.onIntent(SwipeIntent.SwipeRight)
        advanceUntilIdle()
        coVerify(exactly = 0) { swipeRepository.savePendingDelete(any()) }
    }

    @Test
    fun `finishing with nothing pending saves session and navigates to timeline`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.SwipeRight)
            viewModel.onIntent(SwipeIntent.SwipeRight)
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `finishing with pending deletes saves session and navigates to review`() = runTest(dispatcher) {
        load()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.SwipeLeft)
            viewModel.onIntent(SwipeIntent.SwipeLeft)
            advanceUntilIdle()
            assertTrue(viewModel.state.value.isFinished)
            assertEquals(SwipeEffect.NavigateToReview(date, totalPhotosCount = 2), awaitItem())
        }
        // Only Review completes the session, once the photos are really deleted
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, false, 0, today)) }
    }

    @Test
    fun `finishing keeps the saved bytes of an already completed session`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(SwipeSession(date, true, 300, earlier)))
        load()
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        advanceUntilIdle()
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300, earlier)) }
    }

    @Test
    fun `an empty day saves a completed session and goes to timeline, never to review`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } returns emptyList()
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0, today)) }
    }

    @Test
    fun `a failed photo load goes to timeline without recording a session`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws SecurityException("nope")
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `finishing an already completed day keeps the day it was completed on`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(SwipeSession(date, true, 300, earlier)))
        load()
        viewModel.onIntent(SwipeIntent.SwipeRight)
        viewModel.onIntent(SwipeIntent.SwipeRight)
        advanceUntilIdle()
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300, earlier)) }
    }

    @Test
    fun `the location of the first card is looked up after loading`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotoLocation(1) } returns "Paris, Rue de Rivoli"
        load()
        assertEquals("Paris, Rue de Rivoli", viewModel.state.value.photos[0].location)
        assertEquals(null, viewModel.state.value.photos[1].location)
        // Photos are never geocoded in bulk: only the card on top is requested
        coVerify(exactly = 0) { photoRepository.getPhotoLocation(2) }
    }

    @Test
    fun `the next card's location is looked up when it becomes current`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotoLocation(1) } returns "Paris"
        coEvery { photoRepository.getPhotoLocation(2) } returns "Rome"
        load()
        viewModel.onIntent(SwipeIntent.SwipeRight)
        advanceUntilIdle()
        assertEquals("Paris", viewModel.state.value.photos[0].location)
        assertEquals("Rome", viewModel.state.value.photos[1].location)
        coVerify(exactly = 1) { photoRepository.getPhotoLocation(1) }
        coVerify(exactly = 1) { photoRepository.getPhotoLocation(2) }
    }

    @Test
    fun `a photo without a location stays without one and is not asked again`() = runTest(dispatcher) {
        load()
        viewModel.onIntent(SwipeIntent.SwipeRight)
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.photos[0].location)
        coVerify(exactly = 1) { photoRepository.getPhotoLocation(1) }
    }

    @Test
    fun `a failing location lookup does not stop swiping`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotoLocation(any()) } throws IllegalStateException("no network")
        load()
        viewModel.onIntent(SwipeIntent.SwipeRight)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentIndex)
    }

    @Test
    fun `a failing pending delete write does not stop swiping`() = runTest(dispatcher) {
        coEvery { swipeRepository.savePendingDelete(any()) } throws IllegalStateException("disk full")
        load()
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.currentIndex)
        assertEquals(listOf(1L), viewModel.state.value.pendingDeletes)
    }

    @Test
    fun `a failing session write still navigates once everything is swiped`() = runTest(dispatcher) {
        coEvery { swipeRepository.saveSession(any()) } throws IllegalStateException("disk full")
        load()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.SwipeLeft)
            viewModel.onIntent(SwipeIntent.SwipeLeft)
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToReview(date, totalPhotosCount = 2), awaitItem())
        }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws PermissionDeniedException()
        createViewModel()
        advanceUntilIdle()
        viewModel.effects.test {
            assertEquals(SwipeEffect.PermissionRequired, awaitItem())
        }
    }

    @Test
    fun `missing permission asks for it, records no session and does not navigate`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws PermissionDeniedException()
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.PermissionRequired, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `resuming skips photos already marked for deletion on this date`() = runTest(dispatcher) {
        coEvery { swipeRepository.getPendingDeletesByDate(date) } returns listOf(
            PendingDelete(1, date),
        )
        load()

        val state = viewModel.state.value
        assertEquals(1, state.currentIndex)
        assertEquals(listOf(1L), state.pendingDeletes)
        assertEquals(false, state.isFinished)

        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.SwipeLeft)
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToReview(date, totalPhotosCount = 2), awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.savePendingDelete(PendingDelete(2, date)) }
        coVerify(exactly = 0) { swipeRepository.savePendingDelete(PendingDelete(1, date)) }
    }

    @Test
    fun `resuming with every photo already marked goes straight to review`() = runTest(dispatcher) {
        coEvery { swipeRepository.getPendingDeletesByDate(date) } returns listOf(PendingDelete(1, date), PendingDelete(2, date))
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToReview(date, totalPhotosCount = 2), awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, false, 0, today)) }
    }

    @Test
    fun `Close navigates to timeline`() = runTest(dispatcher) {
        createViewModel()
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.Close)
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
        }
    }
}
