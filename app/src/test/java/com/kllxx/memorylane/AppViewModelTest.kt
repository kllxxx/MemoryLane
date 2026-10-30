package com.kllxx.memorylane

import android.app.Application
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.navigation.StartDestination
import com.kllxx.memorylane.reminder.TodayReview
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
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class AppViewModelTest {

    private val today = LocalDate.of(2026, 10, 15)
    private val clock = MutableClock(Instant.parse("2026-10-15T12:00:00Z"))
    private val todayProvider = ClockTodayProvider(clock)
    private val appSettingsRepository = mockk<AppSettingsRepository>()
    private val todayReview = mockk<TodayReview> {
        coEvery { photosToReview(any()) } returns 4
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun givenOnboarding(completed: Boolean) {
        every { appSettingsRepository.getSettings() } returns flowOf(AppSettings(onboardingCompleted = completed))
    }

    private fun viewModel() = AppViewModel(appSettingsRepository, todayReview, todayProvider)

    private suspend fun assertResolvesTo(expected: StartDestination) {
        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(expected, (awaitItem() as AppUiState.Ready).startDestination)
            cancelAndIgnoreRemainingEvents()
        }
    }


    @Test
    fun `not ready until the settings have been emitted`() = runTest {
        val settings = MutableSharedFlow<AppSettings>(replay = 1)
        every { appSettingsRepository.getSettings() } returns settings

        viewModel().uiState.test {
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

        viewModel().uiState.test {
            assertEquals(AppUiState.Loading, awaitItem())
            assertEquals(ThemeMode.Light, (awaitItem() as AppUiState.Ready).themeMode)
            settings.value = settings.value.copy(themeMode = ThemeMode.System)
            assertEquals(ThemeMode.System, (awaitItem() as AppUiState.Ready).themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onboarding not completed starts at onboarding`() = runTest {
        givenOnboarding(completed = false)
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `onboarded always starts at main, whatever today holds`() = runTest {
        givenOnboarding(completed = true)
        assertResolvesTo(StartDestination.Main)
        // The start does not look at the photos at all: that is Calendar's job
        coVerify(exactly = 0) { todayReview.photosToReview(any()) }
    }

    @Test
    fun `a failure while resolving falls back to onboarding instead of hanging`() = runTest {
        every { appSettingsRepository.getSettings() } throws IllegalStateException("db closed")
        assertResolvesTo(StartDestination.Onboarding)
    }

    @Test
    fun `reminder tap with photos to review opens today`() = runTest {
        givenOnboarding(completed = true)
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            assertEquals(AppEffect.OpenToday(today), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reminder tap before the start is resolved waits for it, a cold start`() = runTest {
        val settings = MutableSharedFlow<AppSettings>(replay = 1)
        every { appSettingsRepository.getSettings() } returns settings
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            expectNoEvents()
            settings.emit(AppSettings(onboardingCompleted = true))
            assertEquals(AppEffect.OpenToday(today), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reminder tap before onboarding is finished goes on with the normal routing`() = runTest {
        givenOnboarding(completed = false)
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 0) { todayReview.photosToReview(any()) }
    }

    @Test
    fun `reminder tap with nothing to review stays on calendar`() = runTest {
        // No access to the photos, no photos today or a completed day all come back as 0
        coEvery { todayReview.photosToReview(any()) } returns 0
        givenOnboarding(completed = true)
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every reminder tap is routed, a second one too`() = runTest {
        givenOnboarding(completed = true)
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            assertEquals(AppEffect.OpenToday(today), awaitItem())
            viewModel.onReminderOpened()
            assertEquals(AppEffect.OpenToday(today), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a reminder tap after midnight opens the new day, not the day the app started on`() = runTest {
        givenOnboarding(completed = true)
        val viewModel = viewModel()
        viewModel.effects.test {
            viewModel.onReminderOpened()
            assertEquals(AppEffect.OpenToday(today), awaitItem())

            // The process survived the night
            clock.set(Instant.parse("2026-10-16T00:05:00Z"))
            viewModel.onReminderOpened()

            assertEquals(AppEffect.OpenToday(LocalDate.of(2026, 10, 16)), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        // The count and the destination are for the same day
        coVerify { todayReview.photosToReview(LocalDate.of(2026, 10, 16)) }
    }
}
