package com.kllxx.memorylane.feature.onboarding.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<OnboardingEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<OnboardingEffect> = _effects.asSharedFlow()

    private var permissionDenials = 0

    fun onIntent(intent: OnboardingIntent) {
        val wasLastPage = _state.value.isLastPage
        if (intent == OnboardingIntent.PermissionResult(granted = false)) permissionDenials++

        _state.update { OnboardingReducer.reduce(it, intent, permissionDenials) }

        when (intent) {
            OnboardingIntent.Skip -> completeOnboarding()
            OnboardingIntent.NextPage -> if (wasLastPage) completeOnboarding()
            OnboardingIntent.RequestPermissions ->
                _effects.tryEmit(OnboardingEffect.RequestPermissionLauncher)
            is OnboardingIntent.PermissionResult ->
                if (intent.granted || permissionDenials >= 2) completeOnboarding()
            OnboardingIntent.PreviousPage,
            OnboardingIntent.DismissRationale -> Unit
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            appSettingsRepository.setOnboardingCompleted()
            _effects.emit(OnboardingEffect.NavigateToSwipe)
        }
    }
}
