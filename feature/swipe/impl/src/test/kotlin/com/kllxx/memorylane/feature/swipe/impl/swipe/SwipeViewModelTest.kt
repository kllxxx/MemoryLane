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
import java.time.LocalDate
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

@OptIn(ExperimentalCoroutinesApi::class)
class SwipeViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2026, 10, 15)
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
        coEvery { photoRepository.getPhotosForDate(date, includeLocation = true) } returns photos
        coEvery { swipeRepository.getPendingDeletes() } returns emptyList()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
    }

    // The ViewModel starts loading as soon as it is created, so tests stub the repositories first
    private fun createViewModel() {
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("date") } returns date.toString()
        viewModel = SwipeViewModel(photoRepository, swipeRepository, savedStateHandle)
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
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0)) }
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
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, false, 0)) }
    }

    @Test
    fun `finishing keeps the saved bytes of an already completed session`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(SwipeSession(date, true, 300)))
        load()
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        viewModel.onIntent(SwipeIntent.SwipeLeft)
        advanceUntilIdle()
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 300)) }
    }

    @Test
    fun `an empty day saves a completed session and goes to timeline, never to review`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date, includeLocation = true) } returns emptyList()
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
            expectNoEvents()
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0)) }
    }

    @Test
    fun `a failed photo load goes to timeline without recording a session`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date, includeLocation = true) } throws SecurityException("nope")
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
        }
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `missing permission asks for it, records no session and does not navigate`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date, includeLocation = true) } throws PermissionDeniedException()
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
        coEvery { swipeRepository.getPendingDeletes() } returns listOf(
            PendingDelete(1, date),
            PendingDelete(99, LocalDate.of(2026, 3, 3)),
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
        coEvery { swipeRepository.getPendingDeletes() } returns listOf(PendingDelete(1, date), PendingDelete(2, date))
        createViewModel()
        viewModel.effects.test {
            advanceUntilIdle()
            assertEquals(SwipeEffect.NavigateToReview(date, totalPhotosCount = 2), awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, false, 0)) }
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
