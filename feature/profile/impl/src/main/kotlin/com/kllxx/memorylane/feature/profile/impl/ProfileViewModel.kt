package com.kllxx.memorylane.feature.profile.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = Channel<ProfileEffect>(Channel.BUFFERED)
    val effects: Flow<ProfileEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null

    fun onIntent(intent: ProfileIntent) {
        val before = _state.value
        _state.update { ProfileReducer.reduce(it, intent) }

        when (intent) {
            ProfileIntent.Load -> load()
            is ProfileIntent.SetTheme -> launchCatching(
                setting = "theme",
                revert = ProfileEvent.ThemeReverted(before.themeMode),
            ) { appSettingsRepository.setThemeMode(intent.mode) }
            is ProfileIntent.SetNotifications -> launchCatching(
                setting = "notifications",
                revert = ProfileEvent.NotificationsReverted(before.notificationsEnabled),
            ) { appSettingsRepository.setNotificationsEnabled(intent.enabled) }
            is ProfileIntent.SetStreakGoal -> launchCatching(
                setting = "streak goal",
                revert = ProfileEvent.StreakGoalReverted(before.streakGoal),
            ) { appSettingsRepository.setStreakGoal(intent.goal) }
            ProfileIntent.RepeatOnboarding -> viewModelScope.launch {
                // The flow starts over either way; a failed write only means the flag keeps its old value
                try {
                    appSettingsRepository.setOnboardingCompleted(false)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not reset the onboarding flag", e)
                }
                _effects.trySend(ProfileEffect.NavigateToOnboarding)
            }
        }
    }

    /** The state already shows the new value, so a failed write puts [revert] back and tells the UI. */
    private fun launchCatching(setting: String, revert: ProfileEvent, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not store the $setting setting", e)
                _state.update { ProfileReducer.reduce(it, revert) }
                _effects.trySend(ProfileEffect.ShowSettingsError)
            }
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            appSettingsRepository.getSettings().collect { settings ->
                _state.update {
                    ProfileReducer.reduce(
                        it,
                        ProfileEvent.SettingsLoaded(
                            themeMode = settings.themeMode,
                            notificationsEnabled = settings.notificationsEnabled,
                            streakGoal = settings.streakGoal,
                        ),
                    )
                }
            }
        }
    }

    private companion object {
        const val TAG = "ProfileViewModel"
    }
}
