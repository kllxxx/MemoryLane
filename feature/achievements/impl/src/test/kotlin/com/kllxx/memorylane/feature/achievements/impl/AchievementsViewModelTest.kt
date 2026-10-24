package com.kllxx.memorylane.feature.achievements.impl

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AchievementStats
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AchievementsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val statsFlow = MutableSharedFlow<AchievementStats>(replay = 1)
    private lateinit var statsRepository: StatsRepository

    private fun stats(
        viewed: Int = 5,
        current: Int = 2,
        best: Int = 4,
        deleted: Int = 1,
        freedBytes: Long = 640_000_000,
    ) = AchievementStats(current, best, viewed, deleted, freedBytes)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        statsRepository = mockk()
        every { statsRepository.stats } returns statsFlow
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AchievementsViewModel(statsRepository)

    @Test
    fun `the state is loading until the first stats arrive`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AchievementsState.Loading, vm.state.value)
    }

    @Test
    fun `no viewed photo gives the empty state`() = runTest(dispatcher) {
        statsFlow.tryEmit(stats(viewed = 0))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AchievementsState.Empty, vm.state.value)
    }

    @Test
    fun `a viewed photo gives the stats state`() = runTest(dispatcher) {
        statsFlow.tryEmit(stats())
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            AchievementsState.Stats(2, 4, FormattedSize("640", SizeUnit.MB), "5", "1"),
            vm.state.value,
        )
    }

    @Test
    fun `a viewed photo shows the stats state even with a zero streak`() = runTest(dispatcher) {
        statsFlow.tryEmit(stats(viewed = 1, current = 0, best = 0, deleted = 0, freedBytes = 0))
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(
            AchievementsState.Stats(0, 0, FormattedSize("0", SizeUnit.MB), "1", "0"),
            vm.state.value,
        )
    }

    @Test
    fun `the state follows the stats when they change`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.state.test {
            assertEquals(AchievementsState.Loading, awaitItem())

            statsFlow.emit(stats(viewed = 0))
            assertEquals(AchievementsState.Empty, awaitItem())

            statsFlow.emit(stats(viewed = 1, current = 1, best = 1, deleted = 0, freedBytes = 0))
            assertEquals(AchievementsState.Stats(1, 1, FormattedSize("0", SizeUnit.MB), "1", "0"), awaitItem())

            statsFlow.emit(stats(viewed = 1_248, current = 7, best = 21, deleted = 312, freedBytes = 2_440_000_000))
            assertEquals(
                AchievementsState.Stats(7, 21, FormattedSize("2,4", SizeUnit.GB), "1 248", "312"),
                awaitItem(),
            )
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failing stats flow leaves the state as it was`() = runTest(dispatcher) {
        every { statsRepository.stats } returns flow { throw IllegalStateException("db closed") }
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(AchievementsState.Loading, vm.state.value)
    }

    @Test
    fun `the calendar click navigates to the timeline`() = runTest(dispatcher) {
        statsFlow.tryEmit(stats(viewed = 0))
        val vm = viewModel()
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(AchievementsIntent.GoToCalendarClicked)
            assertEquals(AchievementsEffect.NavigateToTimeline, awaitItem())
        }
        assertEquals(AchievementsState.Empty, vm.state.value)
    }
}
