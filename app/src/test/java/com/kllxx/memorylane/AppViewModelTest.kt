package com.kllxx.memorylane

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.navigation.StartDestination
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val appSettingsRepository = mockk<AppSettingsRepository>()
    private val swipeRepository = mockk<SwipeRepository>()

    @Before
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun givenState(onboardingCompleted: Boolean, lastSessionDate: LocalDate?) {
        every { appSettingsRepository.getSettings() } returns
            flowOf(AppSettings(onboardingCompleted = onboardingCompleted))
        coEvery { swipeRepository.getLastSession() } returns
            lastSessionDate?.let { SwipeSession(date = it, completed = true, savedBytes = 0L) }
    }

    private fun viewModel() = AppViewModel(appSettingsRepository, swipeRepository, clock)

    private suspend fun assertResolvesTo(expected: StartDestination) {
        viewModel().startDestination.test {
            assertNull(awaitItem())
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding not completed starts at onboarding`() = runTest {
        givenState(onboardingCompleted = false, lastSessionDate = today)
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `onboarded without any session starts at swipe`() = runTest {
        givenState(onboardingCompleted = true, lastSessionDate = null)
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarded with session from a previous day starts at swipe`() = runTest {
        givenState(onboardingCompleted = true, lastSessionDate = today.minusDays(1))
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarded with session today starts at main`() = runTest {
        givenState(onboardingCompleted = true, lastSessionDate = today)
        assertResolvesTo(StartDestination.Main)
    }
}
