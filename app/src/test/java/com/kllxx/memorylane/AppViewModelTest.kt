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
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
        lastSessionDate: LocalDate?,
        sessionCompleted: Boolean = true,
    ) {
        every { appSettingsRepository.getSettings() } returns
            flowOf(AppSettings(onboardingCompleted = onboardingCompleted))
        coEvery { swipeRepository.getLastSession() } returns
            lastSessionDate?.let { SwipeSession(date = it, completed = sessionCompleted, savedBytes = 0L) }
    }

    private fun viewModel() = AppViewModel(appSettingsRepository, swipeRepository, photoRepository, clock)

    private suspend fun assertResolvesTo(expected: StartDestination) {
        viewModel().startDestination.test {
            assertNull(awaitItem())
            assertEquals(expected, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `themeMode starts with the stored value and follows later changes`() = runTest {
        val settings = MutableStateFlow(AppSettings(themeMode = ThemeMode.Light))
        every { appSettingsRepository.getSettings() } returns settings
        coEvery { swipeRepository.getLastSession() } returns null

        val viewModel = viewModel()
        // Available immediately, before any coroutine has been dispatched
        assertEquals(ThemeMode.Light, viewModel.themeMode.value)

        viewModel.themeMode.test {
            assertEquals(ThemeMode.Light, awaitItem())
            settings.value = AppSettings(themeMode = ThemeMode.System)
            assertEquals(ThemeMode.System, awaitItem())
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
    fun `onboarded with incomplete session today starts at swipe`() = runTest {
        givenState(onboardingCompleted = true, lastSessionDate = today, sessionCompleted = false)
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarded with session today starts at main`()= runTest {
        givenState(onboardingCompleted = true, lastSessionDate = today)
        assertResolvesTo(StartDestination.Main)
    }

    @Test
    fun `missing media permission starts at swipe even when today is completed`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false
        givenState(onboardingCompleted = true, lastSessionDate = today)
        assertResolvesTo(StartDestination.Swipe)
    }

    @Test
    fun `onboarding not completed starts at onboarding even without media permission`() = runTest {
        every { photoRepository.hasMediaPermission() } returns false
        givenState(onboardingCompleted = false, lastSessionDate = null)
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `a failure while resolving falls back to onboarding instead of hanging`() = runTest {
        every { appSettingsRepository.getSettings() } returns flowOf(AppSettings(onboardingCompleted = true))
        coEvery { swipeRepository.getLastSession() } throws IllegalStateException("db closed")
        assertResolvesTo(StartDestination.Onboarding)
    }
}
