package com.kllxx.memorylane.feature.profile.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.AppInfo
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val swipeRepository: SwipeRepository,
    appInfo: AppInfo,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState(versionName = appInfo.versionName))
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = _effects.receiveAsFlow()

    // Last values the repository reported, what a failed write goes back to
    private var storedSettings: AppSettings? = null
    private var canRequestPermission = false

    init {
        observeSettings()
        observeStreak()
    }

    fun onIntent(intent: ProfileIntent) {
        val before = _state.value
        val after = ProfileReducer.reduce(before, intent)
        _state.value = after

        persistChanges(before, after)

        when (intent) {
            is ProfileIntent.SystemNotificationsChecked -> canRequestPermission = intent.canRequestPermission
            is ProfileIntent.NotificationsToggled -> if (intent.on && !before.systemNotificationsEnabled) {
                turnOnWithoutSystemAccess()
            }
            ProfileIntent.ReplayOnboardingClicked -> _effects.trySend(ProfileEffect.NavigateToOnboarding)
            ProfileIntent.ContactSupportClicked ->
                _effects.trySend(ProfileEffect.ContactSupport(after.versionName))
            is ProfileIntent.PermissionResult,
            is ProfileIntent.SelectGoal,
            is ProfileIntent.SelectTheme,
            is ProfileIntent.ConfirmTime,
            ProfileIntent.OpenTimePicker,
            ProfileIntent.DismissTimePicker -> Unit
        }
    }

    /** The system blocks notifications: ask for the permission while that is possible, else send the user to the settings. */
    private fun turnOnWithoutSystemAccess() {
        if (canRequestPermission) {
            persist("notification permission") { appSettingsRepository.markNotificationPermissionRequested() }
            _effects.trySend(ProfileEffect.RequestNotificationPermission)
        } else {
            _state.update { ProfileReducer.reduce(it, ProfileEvent.AwaitingSystemSettings) }
            _effects.trySend(ProfileEffect.OpenNotificationSettings)
        }
    }

    private fun persistChanges(before: ProfileState, after: ProfileState) {
        if (after.goal != before.goal) persist("streak goal") { appSettingsRepository.setStreakGoal(after.goal) }
        if (after.themeMode != before.themeMode) persist("theme") { appSettingsRepository.setThemeMode(after.themeMode) }
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
                storedSettings?.let { stored ->
                    _state.update { ProfileReducer.reduce(it, ProfileEvent.SettingsLoaded(stored)) }
                }
                _effects.trySend(ProfileEffect.ShowSettingsError)
            }
        }
    }

    private fun observeSettings() {
        viewModelScope.launch {
            appSettingsRepository.getSettings().collect { settings ->
                storedSettings = settings
                _state.update { ProfileReducer.reduce(it, ProfileEvent.SettingsLoaded(settings)) }
            }
        }
    }

    private fun observeStreak() {
        viewModelScope.launch {
            try {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    // Same source as the Timeline chip; sessions saved before the day was tracked carry the epoch marker
                    val doneOn = sessions.map { it.completedAt }.filter { it != LocalDate.EPOCH }
                    val streak = StreakCalculator.currentStreak(doneOn, LocalDate.now(clock))
                    _state.update { ProfileReducer.reduce(it, ProfileEvent.StreakLoaded(streak)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the streak", e)
            }
        }
    }

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
