package com.kllxx.memorylane.feature.achievements.impl

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val today = LocalDate.now()
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var appSettingsRepository: AppSettingsRepository
    private lateinit var settings: MutableStateFlow<AppSettings>

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        swipeRepository = mockk()
        appSettingsRepository = mockk()
        settings = MutableStateFlow(AppSettings())
        every { appSettingsRepository.getSettings() } returns settings
        coEvery { appSettingsRepository.setStreakGoal(any()) } returns Unit
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** A session that was completed on [completedAt], about a different day: only completedAt matters for streaks. */
    private fun session(completedAt: LocalDate, completed: Boolean, savedBytes: Long) =
        SwipeSession(date = completedAt.minusYears(1), completed = completed, savedBytes = savedBytes, completedAt = completedAt)

    private fun viewModel(sessions: List<SwipeSession>): AchievementsViewModel {
        every { swipeRepository.getCompletedSessions() } returns flowOf(sessions)
        return AchievementsViewModel(swipeRepository, appSettingsRepository)
    }

    @Test
    fun `Load computes streaks and totals from completed sessions`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                session(today, true, 100),
                session(today.minusDays(1), true, 200),
                session(today.minusDays(5), true, 300),
                session(today.minusDays(6), true, 400),
                session(today.minusDays(7), true, 500),
                session(today.minusDays(2), false, 9_999),
            ),
        )

        vm.onIntent(AchievementsIntent.Load)
        advanceUntilIdle()

        val state = vm.state.value
        assertEquals(2, state.currentStreak)
        assertEquals(3, state.bestStreak)
        assertEquals(1_500L, state.totalFreedBytes)
        assertEquals(5, state.totalSortedDays)
        assertEquals(2f / 7, state.streakGoalProgress, 0.0001f)
        assertFalse(state.isLoading)
    }

    @Test
    fun `Load sets loading before data arrives`() = runTest(dispatcher) {
        val vm = viewModel(emptyList())

        vm.onIntent(AchievementsIntent.Load)
        assertEquals(true, vm.state.value.isLoading)
        advanceUntilIdle()
        assertFalse(vm.state.value.isLoading)
    }

    @Test
    fun `Load picks up the stored streak goal`() = runTest(dispatcher) {
        settings.value = AppSettings(streakGoal = StreakGoal.MONTH)
        val vm = viewModel(listOf(session(today, true, 1)))

        vm.onIntent(AchievementsIntent.Load)
        advanceUntilIdle()

        assertEquals(StreakGoal.MONTH, vm.state.value.streakGoal)
        assertEquals(1f / 30, vm.state.value.streakGoalProgress, 0.0001f)
    }

    @Test
    fun `SetStreakGoal persists the goal and updates state`() = runTest(dispatcher) {
        val vm = viewModel(listOf(session(today, true, 1)))
        vm.onIntent(AchievementsIntent.Load)
        advanceUntilIdle()

        vm.onIntent(AchievementsIntent.SetStreakGoal(StreakGoal.YEAR))
        assertEquals(StreakGoal.YEAR, vm.state.value.streakGoal)
        assertEquals(1f / 365, vm.state.value.streakGoalProgress, 0.0001f)
        advanceUntilIdle()

        coVerify(exactly = 1) { appSettingsRepository.setStreakGoal(StreakGoal.YEAR) }
    }

    @Test
    fun `emits no effects`() = runTest(dispatcher) {
        val vm = viewModel(emptyList())
        vm.effects.test {
            vm.onIntent(AchievementsIntent.Load)
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `a failing goal write does not crash and keeps the new goal in state`() = runTest(dispatcher) {
        coEvery { appSettingsRepository.setStreakGoal(any()) } throws IllegalStateException("disk full")
        val vm = viewModel(emptyList())
        vm.onIntent(AchievementsIntent.SetStreakGoal(StreakGoal.YEAR))
        advanceUntilIdle()
        assertEquals(StreakGoal.YEAR, vm.state.value.streakGoal)
    }

    @Test
    fun `sorting several days in one sitting is a streak of one`() = runTest(dispatcher) {
        // Three different days sorted today: the days they are about must not count
        val vm = viewModel(
            listOf(
                SwipeSession(today.minusDays(3), true, 10, completedAt = today),
                SwipeSession(today.minusDays(2), true, 10, completedAt = today),
                SwipeSession(today.minusDays(1), true, 10, completedAt = today),
            ),
        )

        vm.onIntent(AchievementsIntent.Load)
        advanceUntilIdle()

        assertEquals(1, vm.state.value.currentStreak)
        assertEquals(1, vm.state.value.bestStreak)
        assertEquals(3, vm.state.value.totalSortedDays)
    }

    @Test
    fun `sessions from before completion days were tracked do not count towards streaks`() = runTest(dispatcher) {
        val vm = viewModel(
            listOf(
                SwipeSession(today.minusDays(9), true, 700, completedAt = LocalDate.EPOCH),
                SwipeSession(today.minusDays(8), true, 300, completedAt = LocalDate.EPOCH),
            ),
        )

        vm.onIntent(AchievementsIntent.Load)
        advanceUntilIdle()

        assertEquals(0, vm.state.value.currentStreak)
        assertEquals(0, vm.state.value.bestStreak)
        // They still count as sorted days and freed space
        assertEquals(2, vm.state.value.totalSortedDays)
        assertEquals(1_000L, vm.state.value.totalFreedBytes)
    }
}
