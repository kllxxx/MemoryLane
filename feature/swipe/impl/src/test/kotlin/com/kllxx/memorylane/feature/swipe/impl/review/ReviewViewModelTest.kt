package com.kllxx.memorylane.feature.swipe.impl.review

import android.content.IntentSender
import android.net.Uri
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReviewViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val date = LocalDate.of(2026, 10, 15)
    private val otherDate = LocalDate.of(2026, 3, 3)
    private val uri = mockk<Uri>(relaxed = true)
    private val photos = listOf(
        Photo(1, uri, LocalDate.of(2024, 10, 15), sizeBytes = 100),
        Photo(2, uri, LocalDate.of(2025, 10, 15), sizeBytes = 200),
        Photo(3, uri, LocalDate.of(2023, 10, 15), sizeBytes = 400),
    )
    private lateinit var photoRepository: PhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var viewModel: ReviewViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // android.util.Log is not available in plain JVM tests
        mockkStatic(Log::class)
        every { Log.e(any(), any(), any()) } returns 0
        photoRepository = mockk()
        swipeRepository = mockk(relaxed = true)
        coEvery { photoRepository.getPhotosForDate(date) } returns photos
        coEvery { swipeRepository.getPendingDeletes() } returns listOf(
            PendingDelete(1, date),
            PendingDelete(3, date),
            PendingDelete(99, otherDate),
        )
        every { swipeRepository.getCompletedSessions() } returns flowOf(
            listOf(SwipeSession(date, true, 0), SwipeSession(otherDate, true, 1_000)),
        )
        val savedStateHandle = mockk<SavedStateHandle>()
        every { savedStateHandle.get<String>("date") } returns date.toString()
        every { savedStateHandle.get<Int>("totalPhotosCount") } returns photos.size
        viewModel = ReviewViewModel(swipeRepository, photoRepository, savedStateHandle)
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    private fun TestScope.loadStats() {
        viewModel.onIntent(ReviewIntent.LoadStats)
        advanceUntilIdle()
    }

    @Test
    fun `LoadStats loads only this date's pending deletes and stats`() = runTest(dispatcher) {
        loadStats()

        val state = viewModel.state.value
        assertEquals(listOf(1L, 3L), state.pendingDeleteIds)
        assertEquals(2, state.deletedCount)
        assertEquals(1, state.keptCount)
        assertEquals(500, state.sessionFreedBytes)
        assertEquals(1_000, state.totalFreedBytes)
        assertFalse(state.isFinished)
    }

    @Test
    fun `keptCount comes from the route's total photo count`() = runTest(dispatcher) {
        // The photo list could differ from what was swiped, e.g. a photo was added in between
        coEvery { photoRepository.getPhotosForDate(date) } returns photos + photos
        loadStats()

        assertEquals(1, viewModel.state.value.keptCount)
    }

    @Test
    fun `LoadStats with nothing pending for the date navigates to timeline`() = runTest(dispatcher) {
        coEvery { swipeRepository.getPendingDeletes() } returns listOf(PendingDelete(99, otherDate))

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.LoadStats)
            advanceUntilIdle()
            assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
        }
        assertEquals(ReviewState(), viewModel.state.value)
    }

    @Test
    fun `LoadStats failure navigates to timeline`() = runTest(dispatcher) {
        coEvery { photoRepository.getPhotosForDate(date) } throws SecurityException("permission revoked")

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.LoadStats)
            advanceUntilIdle()
            assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
        }
    }

    @Test
    fun `direct delete saves freed bytes, clears this date and plays animation before navigating`() =
        runTest(dispatcher) {
            coEvery { photoRepository.deletePhotos(listOf(1L, 3L)) } returns DeleteResult.Deleted
            loadStats()

            viewModel.effects.test {
                viewModel.onIntent(ReviewIntent.ConfirmDelete)
                advanceTimeBy(1)
                assertEquals(ReviewEffect.ShowFreedSpaceAnimation, awaitItem())
                expectNoEvents()
                advanceTimeBy(ReviewViewModel.ANIMATION_DURATION_MS)
                assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
            }

            val state = viewModel.state.value
            assertTrue(state.isFinished)
            assertFalse(state.isDeleting)
            assertEquals(1_500, state.totalFreedBytes)
            coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 500)) }
            coVerify(exactly = 1) { swipeRepository.clearPendingDeletesByDate(date) }
            coVerify(exactly = 0) { swipeRepository.clearPendingDeletes() }
        }

    @Test
    fun `failing to save the session after the delete still plays the animation and leaves review`() =
        runTest(dispatcher) {
            coEvery { photoRepository.deletePhotos(listOf(1L, 3L)) } returns DeleteResult.Deleted
            coEvery { swipeRepository.saveSession(any()) } throws IllegalStateException("disk full")
            loadStats()

            viewModel.effects.test {
                viewModel.onIntent(ReviewIntent.ConfirmDelete)
                advanceUntilIdle()
                assertEquals(ReviewEffect.ShowFreedSpaceAnimation, awaitItem())
                assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
            }
            assertTrue(viewModel.state.value.isFinished)
            // The two writes are independent, so the second still runs
            coVerify(exactly = 1) { swipeRepository.clearPendingDeletesByDate(date) }
        }

    @Test
    fun `failing to clear the pending deletes after the delete still leaves review`() = runTest(dispatcher) {
        coEvery { photoRepository.deletePhotos(listOf(1L, 3L)) } returns DeleteResult.Deleted
        coEvery { swipeRepository.clearPendingDeletesByDate(any()) } throws IllegalStateException("disk full")
        loadStats()

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(ReviewEffect.ShowFreedSpaceAnimation, awaitItem())
            assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 500)) }
    }

    @Test
    fun `delete requiring confirmation emits system delete effect and waits`() = runTest(dispatcher) {
        val sender = mockk<IntentSender>()
        coEvery { photoRepository.deletePhotos(listOf(1L, 3L)) } returns DeleteResult.RequiresConfirmation(sender)
        loadStats()

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.ConfirmDelete)
            advanceUntilIdle()
            assertEquals(ReviewEffect.RequestSystemDelete(sender), awaitItem())
            expectNoEvents()
        }
        assertTrue(viewModel.state.value.isDeleting)
        coVerify(exactly = 0) { swipeRepository.clearPendingDeletesByDate(any()) }
    }

    @Test
    fun `DeleteResult success after system dialog finishes the review`() = runTest(dispatcher) {
        loadStats()

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.DeleteResult(success = true, freedBytes = 500))
            advanceUntilIdle()
            assertEquals(ReviewEffect.ShowFreedSpaceAnimation, awaitItem())
            assertEquals(ReviewEffect.NavigateToTimeline, awaitItem())
        }
        coVerify(exactly = 1) { swipeRepository.saveSession(SwipeSession(date, true, 500)) }
        coVerify(exactly = 1) { swipeRepository.clearPendingDeletesByDate(date) }
    }

    @Test
    fun `DeleteResult failure keeps pending deletes and emits nothing`() = runTest(dispatcher) {
        loadStats()

        viewModel.effects.test {
            viewModel.onIntent(ReviewIntent.DeleteResult(success = false, freedBytes = 0))
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(listOf(1L, 3L), viewModel.state.value.pendingDeleteIds)
        coVerify(exactly = 0) { swipeRepository.saveSession(any()) }
    }

    @Test
    fun `SecurityException during delete is reported as a failure`() = runTest(dispatcher) {
        coEvery { photoRepository.deletePhotos(any()) } throws SecurityException("nope")
        loadStats()

        viewModel.onIntent(ReviewIntent.ConfirmDelete)
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isDeleting)
        assertEquals(listOf(1L, 3L), viewModel.state.value.pendingDeleteIds)
        coVerify(exactly = 0) { swipeRepository.clearPendingDeletesByDate(any()) }
    }

    @Test
    fun `ConfirmDelete does nothing before stats are loaded`() = runTest(dispatcher) {
        viewModel.onIntent(ReviewIntent.ConfirmDelete)
        advanceUntilIdle()
        coVerify(exactly = 0) { photoRepository.deletePhotos(any()) }
    }
}
