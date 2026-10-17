package com.kllxx.memorylane.feature.profile.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfileState())
    val state: StateFlow<ProfileState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ProfileEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ProfileEffect> = _effects.asSharedFlow()

    private var loadJob: Job? = null

    fun onIntent(intent: ProfileIntent) {
        _state.update { ProfileReducer.reduce(it, intent) }

        when (intent) {
            ProfileIntent.Load -> load()
            is ProfileIntent.SetTheme ->
                viewModelScope.launch { appSettingsRepository.setThemeMode(intent.mode) }
            is ProfileIntent.SetNotifications ->
                viewModelScope.launch { appSettingsRepository.setNotificationsEnabled(intent.enabled) }
            is ProfileIntent.SetStreakGoal ->
                viewModelScope.launch { appSettingsRepository.setStreakGoal(intent.goal) }
            ProfileIntent.RepeatOnboarding -> viewModelScope.launch {
                appSettingsRepository.setOnboardingCompleted(false)
                _effects.emit(ProfileEffect.NavigateToOnboarding)
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
}
