package com.kllxx.memorylane.feature.profile.impl

import androidx.lifecycle.viewModelScope
import app.cash.turbine.test
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.AppInfo
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.notification.NotificationStatusProvider
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
    private lateinit var statsRepository: StatsRepository
    private lateinit var notificationStatus: NotificationStatusProvider
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var streaks: MutableStateFlow<Streaks>
    private lateinit var viewModel: ProfileViewModel
    private val errorHandler = mockk<ErrorHandler> { every { handle(any(), any()) } returns UiError.Generic }

    private val content: ProfileState.Content get() = viewModel.state.value as ProfileState.Content

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = MutableStateFlow(AppSettings())
        streaks = MutableStateFlow(Streaks.None)
        repository = mockk(relaxUnitFun = true)
        statsRepository = mockk()
        notificationStatus = mockk()
        every { notificationStatus.areNotificationsEnabled() } returns true
        every { repository.getSettings() } returns settings
        every { statsRepository.streaks } returns streaks
        viewModel = ProfileViewModel(repository, statsRepository, notificationStatus, AppInfo(VERSION), errorHandler)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Notifications are off for the user, and the system state is [enabled] with the given request option. */
    private fun TestScope.givenUserOff(systemEnabled: Boolean, canRequest: Boolean) {
        settings.value = AppSettings(notificationsEnabled = false)
        advanceUntilIdle()
        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(systemEnabled, canRequest))
    }

    // --- Initial state -------------------------------------------------------------------------

    @Test
    fun `initial state comes from the repository flows`() = runTest(dispatcher) {
        settings.value = AppSettings(
            themeMode = ThemeMode.Light,
            notificationsEnabled = false,
            streakGoal = StreakGoal.D100,
            reminderTime = LocalTime.of(7, 45),
            notificationPermissionRequested = true,
        )
        streaks.value = Streaks(current = 3, best = 3)
        advanceUntilIdle()

        assertEquals(
            ProfileState.Content(
                currentStreak = 3,
                goal = StreakGoal.D100,
                userNotificationsEnabled = false,
                permissionRequested = true,
                reminderTime = LocalTime.of(7, 45),
                themeMode = ThemeMode.Light,
                versionName = VERSION,
            ),
            viewModel.state.value,
        )
    }

    @Test
    fun `a stored change reaches the state`() = runTest(dispatcher) {
        settings.value = AppSettings(themeMode = ThemeMode.System)
        advanceUntilIdle()

        assertEquals(ThemeMode.System, content.themeMode)
    }

    @Test
    fun `streak below the goal is not reached and above it is`() = runTest(dispatcher) {
        streaks.value = Streaks(current = 5, best = 5)
        settings.value = AppSettings(streakGoal = StreakGoal.D7)
        advanceUntilIdle()
        assertFalse(content.streakProgress.goalReached)
        assertEquals(2, content.streakProgress.daysLeft)

        streaks.value = Streaks(current = 8, best = 8)
        advanceUntilIdle()
        assertTrue(content.streakProgress.goalReached)
        assertEquals(1f, content.streakProgress.progress, 0f)
    }

    // --- Goal, theme, time -------------------------------------------------------------------

    @Test
    fun `SelectGoal updates the state and stores the goal`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SelectGoal(StreakGoal.D365))
        assertEquals(StreakGoal.D365, content.goal)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setStreakGoal(StreakGoal.D365) }
    }

    @Test
    fun `SelectTheme updates the state and stores the theme`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
        assertEquals(ThemeMode.Light, content.themeMode)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.Light) }
    }

    @Test
    fun `SwipeButtonsToggled updates the state and stores the setting`() = runTest(dispatcher) {
        assertFalse(content.swipeButtonsEnabled)

        viewModel.onIntent(ProfileIntent.SwipeButtonsToggled(true))
        assertTrue(content.swipeButtonsEnabled)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setSwipeButtonsEnabled(true) }

        viewModel.onIntent(ProfileIntent.SwipeButtonsToggled(false))
        advanceUntilIdle()

        assertFalse(content.swipeButtonsEnabled)
        coVerify(exactly = 1) { repository.setSwipeButtonsEnabled(false) }
    }

    @Test
    fun `the stored buttons mode is shown`() = runTest(dispatcher) {
        settings.value = AppSettings(swipeButtonsEnabled = true)
        advanceUntilIdle()

        assertTrue(content.swipeButtonsEnabled)
    }

    @Test
    fun `the buttons mode changed on the Swipe screen shows up here without a write`() = runTest(dispatcher) {
        // The Swipe screen writes the same setting, so the shared settings flow reports it
        settings.value = AppSettings(swipeButtonsEnabled = true)
        advanceUntilIdle()
        assertTrue(content.swipeButtonsEnabled)

        settings.value = AppSettings(swipeButtonsEnabled = false)
        advanceUntilIdle()

        assertFalse(content.swipeButtonsEnabled)
        coVerify(exactly = 0) { repository.setSwipeButtonsEnabled(any()) }
    }

    @Test
    fun `a failed write of the buttons mode puts the stored one back`() = runTest(dispatcher) {
        coEvery { repository.setSwipeButtonsEnabled(any()) } throws java.io.IOException("disk full")

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SwipeButtonsToggled(true))
            assertTrue(content.swipeButtonsEnabled)
            advanceUntilIdle()

            assertEquals(ProfileEffect.ShowError(UiError(R.string.profile_settings_error)), awaitItem())
            assertFalse(content.swipeButtonsEnabled)
        }
    }

    @Test
    fun `time picker opens, dismisses and confirms with a stored time`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        assertTrue(content.isTimePickerVisible)

        viewModel.onIntent(ProfileIntent.DismissTimePicker)
        assertFalse(content.isTimePickerVisible)
        coVerify(exactly = 0) { repository.setReminderTime(any()) }

        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        viewModel.onIntent(ProfileIntent.ConfirmTime(LocalTime.of(9, 30)))
        advanceUntilIdle()

        assertFalse(content.isTimePickerVisible)
        assertEquals(LocalTime.of(9, 30), content.reminderTime)
        coVerify(exactly = 1) { repository.setReminderTime(LocalTime.of(9, 30)) }
    }

    @Test
    fun `time row is disabled while notifications are off`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = true, canRequest = false)
        assertFalse(content.notificationsOn)

        viewModel.onIntent(ProfileIntent.OpenTimePicker)

        assertFalse(content.isTimePickerVisible)
    }

    @Test
    fun `time row is disabled while the system blocks notifications`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = false))

        assertFalse(content.notificationsOn)
        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        assertFalse(content.isTimePickerVisible)
    }

    // --- Turning notifications ON --------------------------------------------------------------

    @Test
    fun `turning ON with the system enabled only sets the user flag`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = true, canRequest = false)

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))
            advanceUntilIdle()

            assertTrue(content.notificationsOn)
            coVerify(exactly = 1) { repository.setNotificationsEnabled(true) }
            expectNoEvents()
        }
    }

    @Test
    fun `turning ON while the permission can be requested asks for it`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = true)

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))
            advanceUntilIdle()

            assertEquals(ProfileEffect.RequestNotificationPermission, awaitItem())
            assertFalse(content.notificationsOn)
            assertFalse(content.pendingEnable)
            coVerify(exactly = 1) { repository.markNotificationPermissionRequested() }
            coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
        }
    }

    @Test
    fun `granted permission turns notifications on`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = true)
        viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))

        viewModel.onIntent(ProfileIntent.PermissionResult(granted = true))
        advanceUntilIdle()

        assertTrue(content.notificationsOn)
        coVerify(exactly = 1) { repository.setNotificationsEnabled(true) }
    }

    @Test
    fun `denied permission keeps the switch off`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = true)
        viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))

        viewModel.effects.test {
            awaitItem() // RequestNotificationPermission
            viewModel.onIntent(ProfileIntent.PermissionResult(granted = false))
            advanceUntilIdle()

            assertFalse(content.notificationsOn)
            coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
            expectNoEvents()
        }
    }

    @Test
    fun `turning ON when permanently denied opens the system settings and waits`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))
            advanceUntilIdle()

            assertEquals(ProfileEffect.OpenNotificationSettings, awaitItem())
            assertTrue(content.pendingEnable)
            assertFalse(content.notificationsOn)
            coVerify(exactly = 0) { repository.markNotificationPermissionRequested() }
            coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
        }
    }

    @Test
    fun `resume with notifications allowed in the settings finishes the pending enable`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)
        viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))

        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))
        advanceUntilIdle()

        assertTrue(content.notificationsOn)
        assertFalse(content.pendingEnable)
        coVerify(exactly = 1) { repository.setNotificationsEnabled(true) }
    }

    @Test
    fun `resume with notifications still blocked clears the pending enable`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)
        viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))

        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = false))
        advanceUntilIdle()

        assertFalse(content.pendingEnable)
        assertFalse(content.notificationsOn)
        coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
    }

    @Test
    fun `a resume without a pending enable never turns notifications on by itself`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)

        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))
        advanceUntilIdle()

        assertFalse(content.notificationsOn)
        coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
    }

    // --- Turning notifications OFF -------------------------------------------------------------

    @Test
    fun `turning OFF only clears the user flag and emits no effect`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = false))
            advanceUntilIdle()

            assertFalse(content.userNotificationsEnabled)
            assertTrue(content.systemNotificationsEnabled)
            assertFalse(content.notificationsOn)
            coVerify(exactly = 1) { repository.setNotificationsEnabled(false) }
            coVerify(exactly = 0) { repository.markNotificationPermissionRequested() }
            expectNoEvents()
        }
    }

    // --- About ---------------------------------------------------------------------------------

    @Test
    fun `ReplayOnboardingClicked navigates without touching the stored settings`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.ReplayOnboardingClicked)
            advanceUntilIdle()

            assertEquals(ProfileEffect.NavigateToOnboarding, awaitItem())
            coVerify(exactly = 0) { repository.setOnboardingCompleted(any()) }
            coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
            coVerify(exactly = 0) { repository.setThemeMode(any()) }
            coVerify(exactly = 0) { repository.setStreakGoal(any()) }
        }
    }

    @Test
    fun `ContactSupportClicked emits the version name`() = runTest(dispatcher) {
        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.ContactSupportClicked)

            assertEquals(ProfileEffect.ContactSupport(VERSION), awaitItem())
        }
    }

    // --- Failed writes -------------------------------------------------------------------------

    @Test
    fun `a failed write puts the stored value back and reports the error`() = runTest(dispatcher) {
        coEvery { repository.setThemeMode(any()) } throws java.io.IOException("disk full")

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
            assertEquals(ThemeMode.Light, content.themeMode)
            advanceUntilIdle()

            assertEquals(ProfileEffect.ShowError(UiError(R.string.profile_settings_error)), awaitItem())
            assertEquals(ThemeMode.Dark, content.themeMode)
        }
    }

    // --- Loading -------------------------------------------------------------------------------

    private fun newViewModel(streakFlow: kotlinx.coroutines.flow.Flow<Streaks> = streaks): ProfileViewModel {
        every { statsRepository.streaks } returns streakFlow
        return ProfileViewModel(repository, statsRepository, notificationStatus, AppInfo(VERSION), errorHandler)
    }

    @Test
    fun `the state is loading until both the settings and the streak have arrived`() = runTest(dispatcher) {
        val pendingSessions = kotlinx.coroutines.flow.MutableSharedFlow<Streaks>()
        val vm = newViewModel(pendingSessions)
        advanceUntilIdle()

        // Settings are there, the streak is not
        assertEquals(ProfileState.Loading, vm.state.value)

        pendingSessions.emit(Streaks(current = 1, best = 1))
        advanceUntilIdle()

        assertEquals(1, (vm.state.value as ProfileState.Content).currentStreak)
    }

    @Test
    fun `the state is loading until the settings have arrived`() = runTest(dispatcher) {
        val pendingSettings = kotlinx.coroutines.flow.MutableSharedFlow<AppSettings>()
        every { repository.getSettings() } returns pendingSettings
        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(ProfileState.Loading, vm.state.value)

        pendingSettings.emit(AppSettings(themeMode = ThemeMode.Light))
        advanceUntilIdle()

        assertEquals(ThemeMode.Light, (vm.state.value as ProfileState.Content).themeMode)
    }

    @Test
    fun `the first content already has the real system notification state`() = runTest(dispatcher) {
        every { notificationStatus.areNotificationsEnabled() } returns false

        val vm = newViewModel()
        advanceUntilIdle()

        val first = vm.state.value as ProfileState.Content
        assertFalse(first.systemNotificationsEnabled)
        assertFalse(first.notificationsOn)
    }

    @Test
    fun `a resume check still updates the system notification state afterwards`() = runTest(dispatcher) {
        every { notificationStatus.areNotificationsEnabled() } returns false
        val vm = newViewModel()
        advanceUntilIdle()

        vm.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))

        assertTrue((vm.state.value as ProfileState.Content).systemNotificationsEnabled)
    }

    @Test
    fun `the system notification state is read once, not on every settings change`() = runTest(dispatcher) {
        val vm = newViewModel()
        advanceUntilIdle()
        every { notificationStatus.areNotificationsEnabled() } returns false

        settings.value = AppSettings(themeMode = ThemeMode.System)
        advanceUntilIdle()

        assertTrue((vm.state.value as ProfileState.Content).systemNotificationsEnabled)
    }

    @Test
    fun `intents while loading change and store nothing`() = runTest(dispatcher) {
        val vm = newViewModel(kotlinx.coroutines.flow.MutableSharedFlow())
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
            vm.onIntent(ProfileIntent.ReplayOnboardingClicked)
            advanceUntilIdle()

            expectNoEvents()
        }
        assertEquals(ProfileState.Loading, vm.state.value)
        coVerify(exactly = 0) { repository.setThemeMode(any()) }
    }

    @Test
    fun `a resume check while loading is remembered for the first content`() = runTest(dispatcher) {
        val pendingSessions = kotlinx.coroutines.flow.MutableSharedFlow<Streaks>()
        every { notificationStatus.areNotificationsEnabled() } returns false
        settings.value = AppSettings(notificationsEnabled = false)
        val vm = newViewModel(pendingSessions)
        advanceUntilIdle()
        vm.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = true))
        pendingSessions.emit(Streaks.None)
        advanceUntilIdle()

        vm.effects.test {
            vm.onIntent(ProfileIntent.NotificationsToggled(on = true))
            advanceUntilIdle()

            assertEquals(ProfileEffect.RequestNotificationPermission, awaitItem())
        }
    }

    @Test
    fun `a failing streak does not keep the screen loading`() = runTest(dispatcher) {
        val vm = newViewModel(kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") })
        advanceUntilIdle()

        assertEquals(0, (vm.state.value as ProfileState.Content).currentStreak)
    }

    @Test
    fun `a failing streak is logged with what failed`() = runTest(dispatcher) {
        newViewModel(kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") })
        advanceUntilIdle()

        verify(exactly = 1) { errorHandler.handle(any<IllegalStateException>(), "load the streak") }
    }

    @Test
    fun `a failed write is logged with the setting that failed`() = runTest(dispatcher) {
        coEvery { repository.setThemeMode(any()) } throws java.io.IOException("disk full")

        viewModel.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
        advanceUntilIdle()

        verify(exactly = 1) { errorHandler.handle(any<java.io.IOException>(), "store the theme setting") }
    }

    @Test
    fun `a cancelled write is neither an error nor a reverted setting`() = runTest(dispatcher) {
        coEvery { repository.setThemeMode(any()) } coAnswers { awaitCancellation() }

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
            advanceUntilIdle()
            viewModel.viewModelScope.cancel()
            advanceUntilIdle()

            expectNoEvents()
        }
        assertEquals(ThemeMode.Light, content.themeMode)
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `settings that cannot be read show the defaults instead of an endless loading`() = runTest(dispatcher) {
        every { repository.getSettings() } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") }
        every { notificationStatus.areNotificationsEnabled() } returns false

        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(
            ProfileState.Content(
                currentStreak = 0,
                systemNotificationsEnabled = false,
                versionName = VERSION,
            ).copy(
                goal = AppSettings().streakGoal,
                userNotificationsEnabled = AppSettings().notificationsEnabled,
                themeMode = AppSettings().themeMode,
                swipeButtonsEnabled = AppSettings().swipeButtonsEnabled,
                reminderTime = AppSettings().reminderTime,
                permissionRequested = AppSettings().notificationPermissionRequested,
            ),
            vm.state.value,
        )
        verify(exactly = 1) { errorHandler.handle(any<IllegalStateException>(), "load the profile") }
    }

    @Test
    fun `no error is shown when the defaults stand in for the settings`() = runTest(dispatcher) {
        every { repository.getSettings() } returns kotlinx.coroutines.flow.flow { throw IllegalStateException("db closed") }
        val vm = newViewModel()

        vm.effects.test {
            advanceUntilIdle()
            expectNoEvents()
        }
        assertTrue(vm.state.value is ProfileState.Content)
    }

    @Test
    fun `a settings flow that fails after the content was shown keeps the content`() = runTest(dispatcher) {
        val failing = kotlinx.coroutines.flow.flow {
            emit(AppSettings(themeMode = ThemeMode.Light))
            throw IllegalStateException("db closed")
        }
        every { repository.getSettings() } returns failing

        val vm = newViewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.Light, (vm.state.value as ProfileState.Content).themeMode)
    }

    private companion object {
        const val VERSION = "1.2.3"
    }
}
