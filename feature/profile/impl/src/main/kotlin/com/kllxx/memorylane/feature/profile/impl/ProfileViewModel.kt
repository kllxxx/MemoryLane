package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.AppInfo
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.notification.NotificationStatusProvider
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val statsRepository: StatsRepository,
    private val notificationStatusProvider: NotificationStatusProvider,
    private val permissionsRepository: PermissionsRepository,
    appInfo: AppInfo,
    errorHandler: ErrorHandler,
) : MviViewModel<ProfileState, ProfileIntent, ProfileEvent, ProfileEffect>(
    initialState = ProfileState.Loading,
    reducer = ProfileReducer::reduce,
    errorHandler = errorHandler,
) {

    private val versionName = appInfo.versionName

    // Last values the repository reported, what a failed write goes back to
    private var storedSettings: AppSettings? = null
    private var canRequestPermission = false

    // The missing permissions the system can still show its prompt for, reported by the screen on every resume
    private var canRequestPermissions: Set<AppPermission> = emptySet()

    init {
        observeProfile()
        observePermissions()
    }

    override fun onIntent(intent: ProfileIntent) {
        // Remembered even while loading: the first resume check can arrive before the content does
        if (intent is ProfileIntent.SystemNotificationsChecked) canRequestPermission = intent.canRequestPermission
        if (intent is ProfileIntent.PermissionsChecked) {
            canRequestPermissions = intent.canRequest
            // A permission may have been changed in the system settings or in a dialog
            permissionsRepository.refresh()
        }
        // Nothing is shown while loading, so there is nothing to act on
        val before = currentState as? ProfileState.Content ?: return
        update { ProfileReducer.reduce(it as ProfileState.Content, intent) }
        val after = currentState as ProfileState.Content

        persistChanges(before, after)

        when (intent) {
            is ProfileIntent.NotificationsToggled -> if (intent.on && !before.systemNotificationsEnabled) {
                turnOnWithoutSystemAccess()
            }
            is ProfileIntent.PermissionRowClicked -> onPermissionRowClicked(intent.permission, before)
            ProfileIntent.ReplayOnboardingClicked -> sendEffect(ProfileEffect.NavigateToOnboarding)
            ProfileIntent.ContactSupportClicked ->
                sendEffect(ProfileEffect.ContactSupport(after.versionName))
            is ProfileIntent.SystemNotificationsChecked,
            is ProfileIntent.PermissionsChecked,
            is ProfileIntent.PermissionResult,
            is ProfileIntent.SelectGoal,
            is ProfileIntent.SelectTheme,
            is ProfileIntent.SwipeButtonsToggled,
            is ProfileIntent.ConfirmTime,
            ProfileIntent.OpenTimePicker,
            ProfileIntent.DismissTimePicker -> Unit
        }
    }

    /**
     * A row of «Разрешения»: a missing permission is asked for while the system can still show its prompt, otherwise the
     * person is sent to the settings of the app, where a granted one can be taken back. Photos and location are asked for
     * together.
     */
    private fun onPermissionRowClicked(permission: AppPermission, before: ProfileState.Content) {
        val granted = before.permissions.isGranted(permission)
        when {
            permission == AppPermission.Notifications -> if (!granted && canRequestPermission) {
                persist("notification permission") { appSettingsRepository.markNotificationPermissionRequested() }
                sendEffect(ProfileEffect.RequestNotificationPermission)
            } else {
                sendEffect(ProfileEffect.OpenNotificationSettings)
            }
            !granted && permission in canRequestPermissions -> {
                val asked = if (permission == AppPermission.Delete) setOf(permission) else setOf(AppPermission.Photos, AppPermission.Location)
                persist("requested permissions") { appSettingsRepository.markPermissionsRequested(asked) }
                sendEffect(ProfileEffect.RequestPermissions(before.permissions.requestFor(permission)))
            }
            else -> sendEffect(ProfileEffect.OpenAppSettings)
        }
    }

    /** The system blocks notifications: ask for the permission while that is possible, else send the user to the settings. */
    private fun turnOnWithoutSystemAccess() {
        if (canRequestPermission) {
            persist("notification permission") { appSettingsRepository.markNotificationPermissionRequested() }
            sendEffect(ProfileEffect.RequestNotificationPermission)
        } else {
            reduce(ProfileEvent.AwaitingSystemSettings)
            sendEffect(ProfileEffect.OpenNotificationSettings)
        }
    }

    private fun persistChanges(before: ProfileState.Content, after: ProfileState.Content) {
        if (after.goal != before.goal) persist("streak goal") { appSettingsRepository.setStreakGoal(after.goal) }
        if (after.themeMode != before.themeMode) persist("theme") { appSettingsRepository.setThemeMode(after.themeMode) }
        if (after.swipeButtonsEnabled != before.swipeButtonsEnabled) {
            persist("swipe buttons") { appSettingsRepository.setSwipeButtonsEnabled(after.swipeButtonsEnabled) }
        }
        if (after.userNotificationsEnabled != before.userNotificationsEnabled) {
            persist("notifications") { appSettingsRepository.setNotificationsEnabled(after.userNotificationsEnabled) }
        }
        if (after.reminderTime != before.reminderTime) {
            persist("reminder time") { appSettingsRepository.setReminderTime(after.reminderTime) }
        }
    }

    /** The state already shows the new value, so a failed write puts the stored one back and tells the UI. */
    private fun persist(setting: String, block: suspend () -> Unit) {
        launchSafe(
            what = "store the $setting setting",
            onError = {
                storedSettings?.let { reduce(ProfileEvent.SettingsLoaded(it)) }
                sendEffect(ProfileEffect.ShowError(UiError(R.string.profile_settings_error)))
            },
        ) { block() }
    }

    /**
     * Nothing is shown until the settings and the streak have both arrived, so the first frame never
     * flashes defaults. The system notification state is read at the same moment; later changes of it
     * come in through [ProfileIntent.SystemNotificationsChecked].
     */
    private fun observeProfile() {
        launchSafe("load the profile", onError = { showDefaultsIfStillLoading() }) {
            combine(appSettingsRepository.getSettings(), currentStreak()) { settings, streak -> settings to streak }
                .collect { (settings, streak) -> showProfile(settings, streak) }
        }
    }

    /** The rows follow the permissions: a change made in the system settings arrives here after the next refresh. */
    private fun observePermissions() {
        launchSafe("follow the permissions") {
            permissionsRepository.state.collect { reduce(ProfileEvent.PermissionsChanged(it)) }
        }
    }

    private fun showProfile(settings: AppSettings, streak: Int) {
        storedSettings = settings
        if (currentState is ProfileState.Loading) {
            reduce(
                ProfileEvent.Loaded(
                    settings = settings,
                    currentStreak = streak,
                    systemNotificationsEnabled = notificationStatusProvider.areNotificationsEnabled(),
                    permissions = permissionsRepository.state.value,
                    versionName = versionName,
                ),
            )
        } else {
            reduce(ProfileEvent.SettingsLoaded(settings))
            reduce(ProfileEvent.StreakLoaded(streak))
        }
    }

    /** Settings that cannot be read are not a reason to show nothing: the screen works with the defaults. */
    private fun showDefaultsIfStillLoading() {
        if (currentState is ProfileState.Loading) showProfile(AppSettings(), streak = 0)
    }

    /** The same streak as the Calendar chip, the Итоги chip and Achievements: they all read it from [StatsRepository]. */
    private fun currentStreak(): Flow<Int> = statsRepository.streaks
        .map { it.current }
        // The streak is a nice-to-have: a failure must not keep the whole screen on Loading
        .catchSafe("load the streak") { emit(0) }
}
