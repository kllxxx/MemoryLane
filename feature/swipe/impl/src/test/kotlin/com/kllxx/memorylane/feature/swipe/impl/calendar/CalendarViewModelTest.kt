package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.net.Uri
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.flowOf
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
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CalendarViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var repository: PhotoRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var viewModel: CalendarViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk()
        swipeRepository = mockk()
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
        every { repository.hasMediaPermission() } returns false
        coEvery { repository.getDayInfo(any()) } returns (null to 0)
        viewModel = createViewModel()
    }

    private fun createViewModel() = CalendarViewModel(repository, swipeRepository, clock)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `RequestPermission emits launcher effect`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.RequestPermission)
            assertEquals(CalendarEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay emits NavigateToSwipe`() = runTest(dispatcher) {
        val date = LocalDate.of(2026, 3, 3)
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(date))
            assertEquals(CalendarEffect.NavigateToSwipe(date), awaitItem())
        }
    }

    @Test
    fun `LoadDays without permission does not touch the repository`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.LoadDays)
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getDayInfo(any()) }
        assertTrue(viewModel.state.value.days.isEmpty())
    }

    @Test
    fun `granting permission builds the year and looks up every day of it once`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.hasPermission)
        assertFalse(state.isLoading)
        assertEquals(365, state.days.size)
        assertEquals(LocalDate.of(2026, 1, 1), state.days.first().date)
        assertEquals(LocalDate.of(2026, 12, 31), state.days.last().date)
        // Every month has to be known to have photos or not, so the whole year is looked up
        state.days.forEach { coVerify(exactly = 1) { repository.getDayInfo(it.date) } }
    }

    @Test
    fun `the current month is looked up before the rest of the year`() = runTest(dispatcher) {
        val order = mutableListOf<LocalDate>()
        coEvery { repository.getDayInfo(any()) } answers {
            order += firstArg<LocalDate>()
            null to 0
        }

        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        assertEquals(LocalDate.of(2026, 10, 1), order.first())
    }

    @Test
    fun `a day without photos is Empty and a day with photos is Loaded`() = runTest(dispatcher) {
        val photo = Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2024, 3, 9), 10)
        coEvery { repository.getDayInfo(LocalDate.of(2026, 3, 9)) } returns (photo to 4)

        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        val days = viewModel.state.value.days.associateBy { it.date }
        assertEquals(DayLoadState.Empty, days.getValue(LocalDate.of(2026, 10, 1)).loadState)
        assertEquals(DayLoadState.Loaded(photo, 4), days.getValue(LocalDate.of(2026, 3, 9)).loadState)
    }

    @Test
    fun `a failing month stops the rest of the year from loading`() = runTest(dispatcher) {
        coEvery { repository.getDayInfo(LocalDate.of(2026, 2, 1)) } throws IllegalStateException("boom")

        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        assertEquals(CalendarError.LoadFailed, viewModel.state.value.error)
        coVerify(exactly = 0) { repository.getDayInfo(LocalDate.of(2026, 3, 1)) }
    }

    @Test
    fun `a failed load leaves the days NotLoaded`() = runTest(dispatcher) {
        coEvery { repository.getDayInfo(any()) } throws IllegalStateException("boom")

        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.days.all { it.loadState == DayLoadState.NotLoaded })
    }

    @Test
    fun `LoadRange loads only dates that are not loaded yet`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        val from = LocalDate.of(2026, 10, 20)
        viewModel.onIntent(CalendarIntent.LoadRange(from, from.plusDays(40)))
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.getDayInfo(LocalDate.of(2026, 10, 20)) }
        coVerify(exactly = 1) { repository.getDayInfo(LocalDate.of(2026, 11, 20)) }
    }

    @Test
    fun `repository failure surfaces as error`() = runTest(dispatcher) {
        coEvery { repository.getDayInfo(any()) } throws IllegalStateException("boom")

        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()

        assertEquals(CalendarError.LoadFailed, viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `LoadDays without permission does not start loading`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.LoadDays)
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
        coVerify(exactly = 1) { repository.getDayInfo(LocalDate.of(2026, 10, 1)) }
    }

    @Test
    fun `a missing permission at creation loads nothing and shows no spinner`() = runTest(dispatcher) {
        advanceUntilIdle()
        assertFalse(viewModel.state.value.hasPermission)
        assertFalse(viewModel.state.value.isLoading)
        coVerify(exactly = 0) { repository.getDayInfo(any()) }
    }

    @Test
    fun `an effect sent before anyone collects is not lost`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.RequestPermission)
        viewModel.effects.test {
            assertEquals(CalendarEffect.RequestPermissionLauncher, awaitItem())
        }
    }

    @Test
    fun `OpenDay ignores a day in the future and opens today`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(CalendarIntent.OpenDay(today.plusDays(1)))
            expectNoEvents()
            viewModel.onIntent(CalendarIntent.OpenDay(today))
            assertEquals(CalendarEffect.NavigateToSwipe(today), awaitItem())
            viewModel.onIntent(CalendarIntent.OpenDay(today.minusDays(30)))
            assertEquals(CalendarEffect.NavigateToSwipe(today.minusDays(30)), awaitItem())
        }
    }

    @Test
    fun `RefreshDay reloads only that day`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.PermissionResult(true))
        advanceUntilIdle()
        val day = LocalDate.of(2026, 10, 5)
        val photo = Photo(1, mockk<Uri>(relaxed = true), LocalDate.of(2025, 10, 5), sizeBytes = 10)
        coEvery { repository.getDayInfo(day) } returns (photo to 1)

        viewModel.onIntent(CalendarIntent.RefreshDay(day))
        advanceUntilIdle()

        val item = viewModel.state.value.days.first { it.date == day }
        assertEquals(DayLoadState.Loaded(photo, 1), item.loadState)
        coVerify(exactly = 2) { repository.getDayInfo(day) }
        coVerify(exactly = 1) { repository.getDayInfo(day.plusDays(1)) }
    }

    @Test
    fun `RefreshDay without permission does nothing`() = runTest(dispatcher) {
        viewModel.onIntent(CalendarIntent.RefreshDay(LocalDate.of(2026, 10, 5)))
        advanceUntilIdle()
        coVerify(exactly = 0) { repository.getDayInfo(any()) }
    }

    @Test
    fun `today comes from the clock`() {
        assertEquals(today, viewModel.state.value.today)
    }

    @Test
    fun `completed sessions give the sorted dates and the streak`() = runTest(dispatcher) {
        val sessions = listOf(
            SwipeSession(LocalDate.of(2026, 3, 3), true, 0, completedAt = LocalDate.of(2026, 10, 15)),
            SwipeSession(LocalDate.of(2026, 5, 9), true, 0, completedAt = LocalDate.of(2026, 10, 14)),
            // Saved before completedAt was tracked: counts as sorted but not for the streak
            SwipeSession(LocalDate.of(2026, 7, 1), true, 0, completedAt = LocalDate.EPOCH),
        )
        every { swipeRepository.getCompletedSessions() } returns flowOf(sessions)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(setOf(LocalDate.of(2026, 3, 3), LocalDate.of(2026, 5, 9), LocalDate.of(2026, 7, 1)), state.sortedDates)
        assertEquals(2, state.currentStreak)
    }

    @Test
    fun `a failing sessions flow leaves no sorted days`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("db") }

        viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.sortedDates.isEmpty())
        assertEquals(0, viewModel.state.value.currentStreak)
    }
}
