package com.kllxx.memorylane.feature.profile.impl

import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppInfo
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    private val today = LocalDate.of(2026, 10, 15)
    private val clock = Clock.fixed(today.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private lateinit var repository: AppSettingsRepository
    private lateinit var swipeRepository: SwipeRepository
    private lateinit var settings: MutableStateFlow<AppSettings>
    private lateinit var sessions: MutableStateFlow<List<SwipeSession>>
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = MutableStateFlow(AppSettings())
        sessions = MutableStateFlow(emptyList())
        repository = mockk(relaxUnitFun = true)
        swipeRepository = mockk()
        every { repository.getSettings() } returns settings
        every { swipeRepository.getCompletedSessions() } returns sessions
        viewModel = ProfileViewModel(repository, swipeRepository, AppInfo(VERSION), clock)
        dispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun session(completedAt: LocalDate) =
        SwipeSession(date = completedAt, completed = true, savedBytes = 0, completedAt = completedAt)

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
        sessions.value = listOf(session(today), session(today.minusDays(1)), session(today.minusDays(2)))
        advanceUntilIdle()

        assertEquals(
            ProfileState(
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
    fun `streak ignores sessions without a tracked completion day`() = runTest(dispatcher) {
        sessions.value = listOf(session(today), session(LocalDate.EPOCH))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.currentStreak)
    }

    @Test
    fun `a stored change reaches the state`() = runTest(dispatcher) {
        settings.value = AppSettings(themeMode = ThemeMode.System)
        advanceUntilIdle()

        assertEquals(ThemeMode.System, viewModel.state.value.themeMode)
    }

    @Test
    fun `streak below the goal is not reached and above it is`() = runTest(dispatcher) {
        sessions.value = (0L until 5).map { session(today.minusDays(it)) }
        settings.value = AppSettings(streakGoal = StreakGoal.D7)
        advanceUntilIdle()
        assertFalse(viewModel.state.value.streakProgress.goalReached)
        assertEquals(2, viewModel.state.value.streakProgress.daysLeft)

        sessions.value = (0L until 8).map { session(today.minusDays(it)) }
        advanceUntilIdle()
        assertTrue(viewModel.state.value.streakProgress.goalReached)
        assertEquals(1f, viewModel.state.value.streakProgress.progress, 0f)
    }

    // --- Goal, theme, time -------------------------------------------------------------------

    @Test
    fun `SelectGoal updates the state and stores the goal`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SelectGoal(StreakGoal.D365))
        assertEquals(StreakGoal.D365, viewModel.state.value.goal)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setStreakGoal(StreakGoal.D365) }
    }

    @Test
    fun `SelectTheme updates the state and stores the theme`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SelectTheme(ThemeMode.Light))
        assertEquals(ThemeMode.Light, viewModel.state.value.themeMode)
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.setThemeMode(ThemeMode.Light) }
    }

    @Test
    fun `time picker opens, dismisses and confirms with a stored time`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        assertTrue(viewModel.state.value.isTimePickerVisible)

        viewModel.onIntent(ProfileIntent.DismissTimePicker)
        assertFalse(viewModel.state.value.isTimePickerVisible)
        coVerify(exactly = 0) { repository.setReminderTime(any()) }

        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        viewModel.onIntent(ProfileIntent.ConfirmTime(LocalTime.of(9, 30)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isTimePickerVisible)
        assertEquals(LocalTime.of(9, 30), viewModel.state.value.reminderTime)
        coVerify(exactly = 1) { repository.setReminderTime(LocalTime.of(9, 30)) }
    }

    @Test
    fun `time row is disabled while notifications are off`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = true, canRequest = false)
        assertFalse(viewModel.state.value.notificationsOn)

        viewModel.onIntent(ProfileIntent.OpenTimePicker)

        assertFalse(viewModel.state.value.isTimePickerVisible)
    }

    @Test
    fun `time row is disabled while the system blocks notifications`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = false))

        assertFalse(viewModel.state.value.notificationsOn)
        viewModel.onIntent(ProfileIntent.OpenTimePicker)
        assertFalse(viewModel.state.value.isTimePickerVisible)
    }

    // --- Turning notifications ON --------------------------------------------------------------

    @Test
    fun `turning ON with the system enabled only sets the user flag`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = true, canRequest = false)

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))
            advanceUntilIdle()

            assertTrue(viewModel.state.value.notificationsOn)
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
            assertFalse(viewModel.state.value.notificationsOn)
            assertFalse(viewModel.state.value.pendingEnable)
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

        assertTrue(viewModel.state.value.notificationsOn)
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

            assertFalse(viewModel.state.value.notificationsOn)
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
            assertTrue(viewModel.state.value.pendingEnable)
            assertFalse(viewModel.state.value.notificationsOn)
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

        assertTrue(viewModel.state.value.notificationsOn)
        assertFalse(viewModel.state.value.pendingEnable)
        coVerify(exactly = 1) { repository.setNotificationsEnabled(true) }
    }

    @Test
    fun `resume with notifications still blocked clears the pending enable`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)
        viewModel.onIntent(ProfileIntent.NotificationsToggled(on = true))

        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = false, canRequestPermission = false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.pendingEnable)
        assertFalse(viewModel.state.value.notificationsOn)
        coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
    }

    @Test
    fun `a resume without a pending enable never turns notifications on by itself`() = runTest(dispatcher) {
        givenUserOff(systemEnabled = false, canRequest = false)

        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.notificationsOn)
        coVerify(exactly = 0) { repository.setNotificationsEnabled(any()) }
    }

    // --- Turning notifications OFF -------------------------------------------------------------

    @Test
    fun `turning OFF only clears the user flag and emits no effect`() = runTest(dispatcher) {
        viewModel.onIntent(ProfileIntent.SystemNotificationsChecked(enabled = true, canRequestPermission = false))

        viewModel.effects.test {
            viewModel.onIntent(ProfileIntent.NotificationsToggled(on = false))
            advanceUntilIdle()

            assertFalse(viewModel.state.value.userNotificationsEnabled)
            assertTrue(viewModel.state.value.systemNotificationsEnabled)
            assertFalse(viewModel.state.value.notificationsOn)
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
            assertEquals(ThemeMode.Light, viewModel.state.value.themeMode)
            advanceUntilIdle()

            assertEquals(ProfileEffect.ShowSettingsError, awaitItem())
            assertEquals(ThemeMode.Dark, viewModel.state.value.themeMode)
        }
    }

    private companion object {
        const val VERSION = "1.2.3"
    }
}
