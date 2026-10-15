package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.net.Uri
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        viewModel = SwipeViewModel(photoRepository, swipeRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.load() {
        viewModel.onIntent(SwipeIntent.LoadPhotos(date))
        advanceUntilIdle()
    }

    @Test
    fun `LoadPhotos loads photos for the date`() = runTest(dispatcher) {
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
            assertEquals(SwipeEffect.NavigateToReview(date), awaitItem())
        }
        // Bytes are only counted by Review once the photos are really deleted
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 0)) }
    }

    @Test
    fun `Close navigates to timeline`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(SwipeIntent.Close)
            assertEquals(SwipeEffect.NavigateToTimeline, awaitItem())
        }
    }
}
