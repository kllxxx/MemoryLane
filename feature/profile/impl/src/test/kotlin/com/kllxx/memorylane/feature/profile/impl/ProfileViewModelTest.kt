package com.kllxx.memorylane.feature.profile.impl

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var repository: AppSettingsRepository
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = MutableStateFlow(AppSettings())
        repository = mockk()
        every { repository.getSettings() } returns settings
        coEvery { repository.setThemeMode(any()) } returns Unit
        coEvery { repository.setNotificationsEnabled(any()) } returns Unit
        coEvery { repository.setStreakGoal(any()) } returns Unit
        coEvery { repository.setOnboardingCompleted(any()) } returns Unit
        viewModel = ProfileViewModel(repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `Load sets loading then applies stored settings`() = runTest(dispatcher) {
        settings.value = AppSettings(
            themeMode = ThemeMode.Light,
            notificationsEnabled = false,
            streakGoal = StreakGoal.HALF_YEAR,
        )

        viewModel.onIntent(ProfileIntent.Load)
        assertTrue(viewModel.state.value.isLoading)
        advanceUntilIdle()

        assertEquals(
            ProfileState(ThemeMode.Light, notificationsEnabled = false, streakGoal = StreakGoal.HALF_YEAR),
            viewModel.state.value,
        )
    }

    @Test
    fun `Load follows later settings changes`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.Load)
        advanceUntilIdle()

        settings.value = AppSettings(themeMode = ThemeMode.System)
        advanceUntilIdle()

        assertEquals(ThemeMode.System, viewModel.state.value.themeMode)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `SetTheme persists and updates state`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SetTheme(ThemeMode.Light))
        assertEquals(ThemeMode.Light, viewModel.state.value.themeMode)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.Light) }
    }

    @Test
    fun `SetNotifications persists and updates state`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SetNotifications(false))
        assertFalse(viewModel.state.value.notificationsEnabled)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setNotificationsEnabled(false) }
    }

    @Test
    fun `SetStreakGoal persists and updates state`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SetStreakGoal(StreakGoal.MONTH))
        assertEquals(StreakGoal.MONTH, viewModel.state.value.streakGoal)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setStreakGoal(StreakGoal.MONTH) }
    }

    @Test
    fun `RepeatOnboarding resets the flag then navigates`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.RepeatOnboarding)
            expectNoEvents()
            advanceUntilIdle()

            assertEquals(ProfileEffect.NavigateToOnboarding, awaitItem())
        }
        coVerifyOrder { repository.setOnboardingCompleted(false) }
        coVerify(exactly = 1) { repository.setOnboardingCompleted(false) }
    }

    @Test
    fun `settings intents emit no effects`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.Load)
            viewModel.onIntent(ProfileIntent.SetTheme(ThemeMode.Light))
            advanceUntilIdle()
            expectNoEvents()
        }
    }

    @Test
    fun `a failing theme write puts the old theme back and reports the error`() = runTest(dispatcher) {
        coEvery { repository.setThemeMode(any()) } throws IllegalStateException("disk full")
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SetTheme(ThemeMode.Light))
            assertEquals(ThemeMode.Light, viewModel.state.value.themeMode)
            advanceUntilIdle()
            assertEquals(ProfileState().themeMode, viewModel.state.value.themeMode)
            assertEquals(ProfileEffect.ShowSettingsError, awaitItem())
        }
    }

    @Test
    fun `a failing notifications write puts the old value back and reports the error`() = runTest(dispatcher) {
        coEvery { repository.setNotificationsEnabled(any()) } throws IllegalStateException("disk full")
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SetNotifications(false))
            advanceUntilIdle()
            assertEquals(ProfileState().notificationsEnabled, viewModel.state.value.notificationsEnabled)
            assertEquals(ProfileEffect.ShowSettingsError, awaitItem())
        }
    }

    @Test
    fun `a failing streak goal write puts the old goal back and reports the error`() = runTest(dispatcher) {
        coEvery { repository.setStreakGoal(any()) } throws IllegalStateException("disk full")
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SetStreakGoal(StreakGoal.YEAR))
            advanceUntilIdle()
            assertEquals(ProfileState().streakGoal, viewModel.state.value.streakGoal)
            assertEquals(ProfileEffect.ShowSettingsError, awaitItem())
        }
    }

    @Test
    fun `a failed write reverts only its own setting`() = runTest(dispatcher) {
        coEvery { repository.setThemeMode(any()) } throws IllegalStateException("disk full")
        viewModel.onIntent(ProfileIntent.SetTheme(ThemeMode.Light))
        viewModel.onIntent(ProfileIntent.SetNotifications(false))
        advanceUntilIdle()
        assertEquals(ProfileState().themeMode, viewModel.state.value.themeMode)
        assertEquals(false, viewModel.state.value.notificationsEnabled)
    }


    @Test
    fun `RepeatOnboarding navigates even when resetting the flag fails`() = runTest(dispatcher) {
        coEvery { repository.setOnboardingCompleted(false) } throws IllegalStateException("disk full")
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.RepeatOnboarding)
            advanceUntilIdle()
            assertEquals(ProfileEffect.NavigateToOnboarding, awaitItem())
        }
    }
}
