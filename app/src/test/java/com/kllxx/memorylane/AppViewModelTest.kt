package com.kllxx.memorylane

import android.app.Application
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.navigation.StartDestination
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(Instant.parse("2026-10-15T12:00:00Z"), ZoneOffset.UTC)
    private val appSettingsRepository = mockk<AppSettingsRepository>()
    private val swipeRepository = mockk<SwipeRepository>()
    private val photoRepository = mockk<PhotoRepository> {
        every { hasMediaPermission() } returns true
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun givenState(
        onboardingCompleted: Boolean,
        sortedToday: Boolean = false,
    ) {
        every { appSettingsRepository.getSettings() } returns
            flowOf(AppSettings(onboardingCompleted = onboardingCompleted))
        // A day from the past that was sorted today: only completedAt decides the routing
        coEvery { swipeRepository.getSessionByCompletedAt(today) } returns
            if (sortedToday) SwipeSession(today.minusDays(3), completed = true, savedBytes = 0L, completedAt = today) else null
        coEvery { swipeRepository.cleanupStalePendingDeletes() } returns Unit
    }

    private fun viewModel() = AppViewModel(appSettingsRepository, swipeRepository, photoRepository, clock)

    private suspend fun assertResolvesTo(expected: StartDestination) {
        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(expected, (awaitItem() as AppUiState.Ready).startDestination)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `today comes from the injected clock`() = runTest {
        givenState(onboardingCompleted = true)
        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(today, (awaitItem() as AppUiState.Ready).today)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `not ready until the settings have been emitted`() = runTest {
        val settings = MutableSharedFlow<AppSettings>(replay = 1)
        every { appSettingsRepository.getSettings() } returns settings
        coEvery { swipeRepository.getSessionByCompletedAt(any()) } returns null
        coEvery { swipeRepository.cleanupStalePendingDeletes() } returns Unit

        val viewModel = viewModel()
        viewModel.uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            expectNoEvents()
            settings.emit(AppSettings(onboardingCompleted = true, themeMode = ThemeMode.Light))
            assertEquals(ThemeMode.Light, (awaitItem() as AppUiState.Ready).themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ready with the stored theme and never a default one first`() = runTest {
        // Dark is the AppSettings default: it must not show up for a user who stored Light
        every { appSettingsRepository.getSettings() } returns
            flowOf(AppSettings(onboardingCompleted = true, themeMode = ThemeMode.Light))
        coEvery { swipeRepository.getSessionByCompletedAt(any()) } returns null
        coEvery { swipeRepository.cleanupStalePendingDeletes() } returns Unit

        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(ThemeMode.Light, (awaitItem() as AppUiState.Ready).themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a theme change after startup is applied to the ready state`() = runTest {
        val settings = MutableStateFlow(AppSettings(onboardingCompleted = true, themeMode = ThemeMode.Light))
        every { appSettingsRepository.getSettings() } returns settings
        coEvery { swipeRepository.getSessionByCompletedAt(any()) } returns null
        coEvery { swipeRepository.cleanupStalePendingDeletes() } returns Unit

        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(ThemeMode.Light, (awaitItem() as AppUiState.Ready).themeMode)
            settings.value = settings.value.copy(themeMode = ThemeMode.System)
            assertEquals(ThemeMode.System, (awaitItem() as AppUiState.Ready).themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding not completed starts at onboarding`
() = runTest {
        givenState(onboardingCompleted = false, sortedToday = true)
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `onboarded with nothing sorted today starts at swipe`() = runTest {
        givenState(onboardingCompleted = true, sortedToday = false)
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarded with a day sorted today starts at main`() = runTest {
        givenState(onboardingCompleted = true, sortedToday = true)
        assertResolvesTo(StartDestination.Main)
    }

    @Test
    fun `routing asks for sessions completed today, not for the date a session is about`() = runTest {
        givenState(onboardingCompleted = true, sortedToday = false)
        assertResolvesTo(StartDestination.Swipe)
        coVerify(exactly = 1) { swipeRepository.getSessionByCompletedAt(today) }
    }

    @Test
    fun `missing media permission starts at swipe even when today is completed`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false
        givenState(onboardingCompleted = true, sortedToday = true)
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarding not completed starts at onboarding even without media permission`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false
        givenState(onboardingCompleted = false)
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `a failure while resolving falls back to onboarding instead of hanging`() = runTest {
        every { appSettingsRepository.getSettings() } returns flowOf(AppSettings(onboardingCompleted = true))
        coEvery { swipeRepository.getSessionByCompletedAt(any()) } throws IllegalStateException("db closed")
        coEvery { swipeRepository.cleanupStalePendingDeletes() } returns Unit
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `stale pending deletes are cleaned up after routing`() = runTest {
        givenState(onboardingCompleted = true, sortedToday = true)
        assertResolvesTo(StartDestination.Main)
        coVerify(exactly = 1) { swipeRepository.cleanupStalePendingDeletes() }
    }

    @Test
    fun `a failing cleanup does not change where the app starts`() = runTest {
        givenState(onboardingCompleted = true, sortedToday = true)
        coEvery { swipeRepository.cleanupStalePendingDeletes() } throws IllegalStateException("db locked")
        assertResolvesTo(StartDestination.Main)
    }
}
