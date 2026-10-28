package com.kllxx.memorylane.feature.swipe.impl.result

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ResultViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.of(2026, 10, 29)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val swipeRepository = mockk<SwipeRepository>()
    private val statsRepository = mockk<StatsRepository>()
    private val streaks = MutableStateFlow(Streaks.None)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { swipeRepository.getCompletedSessions() } returns flowOf(emptyList())
        every { statsRepository.streaks } returns streaks
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun create(
        date: LocalDate = today,
        deleted: Int = 11,
        freed: Long = 320_000_000,
        kept: Int = 37,
        total: Int = 48,
    ): ResultViewModel {
        val handle = SavedStateHandle(
            mapOf(
                "date" to date.toString(),
                "deletedCount" to deleted,
                "freedBytes" to freed,
                "keptCount" to kept,
                "totalCount" to total,
            ),
        )
        return ResultViewModel(swipeRepository, statsRepository, clock, handle)
    }

    private fun session(date: LocalDate, completedAt: LocalDate) =
        SwipeSession(date, completed = true, savedBytes = 0, completedAt = completedAt)

    @Test
    fun `the numbers of the route are the state, the freed space formatted`() = runTest(dispatcher) {
        val viewModel = create()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(today, state.date)
        assertEquals(11, state.deletedCount)
        assertEquals(37, state.keptCount)
        assertEquals(48, state.totalCount)
        assertEquals(FormattedSize("320", SizeUnit.MB), state.freed)
        assertFalse(state.nothingDeleted)
    }

    @Test
    fun `nothing deleted is a day without deleted photos`() = runTest(dispatcher) {
        val viewModel = create(deleted = 0, freed = 0, kept = 48)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.nothingDeleted)
        assertEquals(FormattedSize("0", SizeUnit.MB), viewModel.state.value.freed)
    }

    @Test
    fun `a day completed today shows the current streak, the one of the stats`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(session(today, completedAt = today)))
        streaks.value = Streaks(current = 3, best = 5)
        val viewModel = create()
        advanceUntilIdle()

        assertEquals(3, viewModel.state.value.streakDays)
    }

    @Test
    fun `a streak of zero is no chip`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(session(today, completedAt = today)))
        val viewModel = create()
        advanceUntilIdle()

        assertNull(viewModel.state.value.streakDays)
    }

    @Test
    fun `a day of another date shows no streak chip`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(session(LocalDate.of(2015, 1, 15), today)))
        val viewModel = create(date = LocalDate.of(2015, 1, 15))
        advanceUntilIdle()

        assertNull(viewModel.state.value.streakDays)
    }

    @Test
    fun `today's day that was completed on an earlier day shows no streak chip`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } returns flowOf(listOf(session(today, completedAt = today.minusDays(3))))
        val viewModel = create()
        advanceUntilIdle()

        assertNull(viewModel.state.value.streakDays)
    }

    @Test
    fun `today's day that is not completed shows no streak chip`() = runTest(dispatcher) {
        val viewModel = create()
        advanceUntilIdle()

        assertNull(viewModel.state.value.streakDays)
    }

    @Test
    fun `a streak that cannot be read leaves the rest of the screen`() = runTest(dispatcher) {
        every { swipeRepository.getCompletedSessions() } throws IllegalStateException("db")
        val viewModel = create()
        advanceUntilIdle()

        assertNull(viewModel.state.value.streakDays)
        assertEquals(11, viewModel.state.value.deletedCount)
    }

    @Test
    fun `done goes to the calendar`() = runTest(dispatcher) {
        val viewModel = create()
        advanceUntilIdle()

        viewModel.effects.test {
            viewModel.onIntent(ResultIntent.Done)

            assertEquals(ResultEffect.NavigateToCalendar, awaitItem())
        }
    }

    @Test
    fun `the route is the contract between the two screens`() {
        val route = ResultRoute("2015-01-15", deletedCount = 2, freedBytes = 5, keptCount = 1, totalCount = 3)

        assertEquals(LocalDate.of(2015, 1, 15), route.toState().date)
        assertEquals(2, route.toState().deletedCount)
    }
}
