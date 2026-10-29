package com.kllxx.memorylane.feature.profile.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.AppInfo
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.notification.NotificationStatusProvider
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.streak.Streaks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val statsRepository: StatsRepository,
    private val notificationStatusProvider: NotificationStatusProvider,
    appInfo: AppInfo,
) : ViewModel() {

    private val versionName = appInfo.versionName

    private val _state = MutableStateFlow<ProfileState>(ProfileState.Loading)
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = _effects.receiveAsFlow()

    // Last values the repository reported, what a failed write goes back to
    private var storedSettings: AppSettings? = null
    private var canRequestPermission = false

    init {
        observeProfile()
    }

    fun onIntent(intent: ProfileIntent) {
        // Remembered even while loading: the first resume check can arrive before the content does
        if (intent is ProfileIntent.SystemNotificationsChecked) canRequestPermission = intent.canRequestPermission
        // Nothing is shown while loading, so there is nothing to act on
        val before = _state.value as? ProfileState.Content ?: return
        val after = ProfileReducer.reduce(before, intent)
        _state.value = after

        persistChanges(before, after)

        when (intent) {
            is ProfileIntent.NotificationsToggled -> if (intent.on && !before.systemNotificationsEnabled) {
                turnOnWithoutSystemAccess()
            }
            ProfileIntent.ReplayOnboardingClicked -> _effects.trySend(ProfileEffect.NavigateToOnboarding)
            ProfileIntent.ContactSupportClicked ->
                _effects.trySend(ProfileEffect.ContactSupport(after.versionName))
            is ProfileIntent.SystemNotificationsChecked,
            is ProfileIntent.PermissionResult,
            is ProfileIntent.SelectGoal,
            is ProfileIntent.SelectTheme,
            is ProfileIntent.SwipeButtonsToggled,
            is ProfileIntent.ConfirmTime,
            ProfileIntent.OpenTimePicker,
            ProfileIntent.DismissTimePicker -> Unit
        }
    }

    private fun onEvent(event: ProfileEvent) {
        _state.update { ProfileReducer.reduce(it, event) }
    }

    /** The system blocks notifications: ask for the permission while that is possible, else send the user to the settings. */
    private fun turnOnWithoutSystemAccess() {
        if (canRequestPermission) {
            persist("notification permission") { appSettingsRepository.markNotificationPermissionRequested() }
            _effects.trySend(ProfileEffect.RequestNotificationPermission)
        } else {
            onEvent(ProfileEvent.AwaitingSystemSettings)
            _effects.trySend(ProfileEffect.OpenNotificationSettings)
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
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not store the $setting setting", e)
                storedSettings?.let { onEvent(ProfileEvent.SettingsLoaded(it)) }
                _effects.trySend(ProfileEffect.ShowSettingsError)
            }
        }
    }

    /**
     * Nothing is shown until the settings and the streak have both arrived, so the first frame never
     * flashes defaults. The system notification state is read at the same moment; later changes of it
     * come in through [ProfileIntent.SystemNotificationsChecked].
     */
    private fun observeProfile() {
        viewModelScope.launch {
            try {
                combine(appSettingsRepository.getSettings(), currentStreak()) { settings, streak -> settings to streak }
                    .collect { (settings, streak) ->
                        storedSettings = settings
                        if (_state.value is ProfileState.Loading) {
                            onEvent(
                                ProfileEvent.Loaded(
                                    settings = settings,
                                    currentStreak = streak,
                                    systemNotificationsEnabled = notificationStatusProvider.areNotificationsEnabled(),
                                    versionName = versionName,
                                ),
                            )
                        } else {
                            onEvent(ProfileEvent.SettingsLoaded(settings))
                            onEvent(ProfileEvent.StreakLoaded(streak))
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the profile", e)
            }
        }
    }

    /** The same streak as the Calendar chip, the Итоги chip and Achievements: they all read it from [StatsRepository]. */
    private fun currentStreak(): Flow<Int> = statsRepository.streaks
        .map { it.current }
        .catch { e ->
            // The streak is a nice-to-have: a failure must not keep the whole screen on Loading
            Log.e(TAG, "Could not load the streak", e)
            emit(0)
        }

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
