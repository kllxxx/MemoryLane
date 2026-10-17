package com.kllxx.memorylane.feature.swipe.impl.timeline

import android.net.Uri
import app.cash.turbine.test
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
import kotlinx.coroutines.test.StandardTestDispatcher
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
class TimelineViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var repository: PhotoRepository
    private lateinit var viewModel: TimelineViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        every { repository.hasMediaPermission() } returns false
        coEvery { repository.getFirstPhotoForDate(any()) } returns null
        coEvery { repository.getPhotosForDate(any()) } returns emptyList()
        viewModel = createViewModel()
    }

    private fun createViewModel() = TimelineViewModel(repository, clock)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `RequestPermission emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(TimelineIntent.RequestPermission)
            assertEquals(TimelineEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay emits NavigateToSwipe`() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 3, 3)
        viewModel.effects.test {
            viewModel.onIntent(TimelineIntent.OpenDay(date))
            assertEquals(TimelineEffect.NavigateToSwipe(date), awaitItem())
        }
    }

    @Test
    fun `LoadDays without permission does not touch the repository`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.LoadDays)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getPhotosForDate(any()) }
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    @Test
    fun `granting permission builds the year and loads the current month window`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.PermissionResult(true))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(365, state.days.size)
        assertEquals(LocalDate.of(2026, 1, 1), state.days.first().date)
        assertEquals(LocalDate.of(2026, 12, 31), state.days.last().date)
        coVerify(exactly = 1) { repository.getPhotosForDate(LocalDate.of(2026, 10, 1)) }
        coVerify(exactly = 0) { repository.getPhotosForDate(LocalDate.of(2026, 9, 30)) }
        coVerify(exactly = 0) { repository.getPhotosForDate(LocalDate.of(2026, 11, 16)) }
    }

    @Test
    fun `LoadRange loads only dates that are not loaded yet`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.PermissionResult(true))
        advanceUntilIdle()

        val from = LocalDate.of(2026, 10, 20)
        viewModel.onIntent(TimelineIntent.LoadRange(from, from.plusDays(40)))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getPhotosForDate(LocalDate.of(2026, 10, 20)) }
        coVerify(exactly = 1) { repository.getPhotosForDate(LocalDate.of(2026, 11, 20)) }
    }

    @Test
    fun `repository failure surfaces as error`() = runTest(dispatcher) {
        coEvery { repository.getPhotosForDate(any()) } throws IllegalStateException("boom")

        viewModel.onIntent(TimelineIntent.PermissionResult(true))
        advanceUntilIdle()

        assertEquals("boom", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoadDays without permission does not start loading`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.LoadDays)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.hasPermission)
    }

    @Test
    fun `a granted permission at creation loads the year without any intent`() = runTest(dispatcher) {
        every { repository.hasMediaPermission() } returns true
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(365, state.days.size)
        coVerify(exactly = 1) { repository.getPhotosForDate(LocalDate.of(2026, 10, 1)) }
    }

    @Test
    fun `a missing permission at creation loads nothing and shows no spinner`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasPermission)
        assertFalse(viewModel.state.value.isLoading)
        coVerify(exactly = 0) { repository.getPhotosForDate(any()) }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.RequestPermission)
        viewModel.effects.test {
            assertEquals(TimelineEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay ignores a day in the future and opens today`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(TimelineIntent.OpenDay(today.plusDays(1)))
            expectNoEvents()
            viewModel.onIntent(TimelineIntent.OpenDay(today))
            assertEquals(TimelineEffect.NavigateToSwipe(today), awaitItem())
            viewModel.onIntent(TimelineIntent.OpenDay(today.minusDays(30)))
            assertEquals(TimelineEffect.NavigateToSwipe(today.minusDays(30)), awaitItem())
        }
    }

    @Test
    fun `RefreshDay reloads only that day`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.PermissionResult(true))
        advanceUntilIdle()
        val day = LocalDate.of(2026, 10, 5)
        val photo = Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2025, 10, 5), sizeBytes = 10)
        coEvery { repository.getFirstPhotoForDate(day) } returns photo
        coEvery { repository.getPhotosForDate(day) } returns listOf(photo)

        viewModel.onIntent(TimelineIntent.RefreshDay(day))
        advanceUntilIdle()

        val item = viewModel.state.value.days.first { it.date == day }
        assertEquals(1, item.totalCount)
        assertEquals(photo, item.firstPhoto)
        coVerify(exactly = 2) { repository.getPhotosForDate(day) }
        coVerify(exactly = 1) { repository.getPhotosForDate(day.plusDays(1)) }
    }

    @Test
    fun `RefreshDay without permission does nothing`() = runTest(dispatcher) {
        viewModel.onIntent(TimelineIntent.RefreshDay(LocalDate.of(2026, 10, 5)))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getPhotosForDate(any()) }
    }
}
