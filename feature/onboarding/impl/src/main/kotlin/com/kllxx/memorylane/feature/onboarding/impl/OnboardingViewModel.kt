package com.kllxx.memorylane.feature.onboarding.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
class OnboardingViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    private var permissionDenials = 0

    fun onIntent(intent: OnboardingIntent) {
        val wasLastPage = _state.value.isLastPage
        if (intent == OnboardingIntent.PermissionResult(granted = false)) permissionDenials++

        _state.update { OnboardingReducer.reduce(it, intent, permissionDenials) }

        when (intent) {
            OnboardingIntent.Skip -> completeOnboarding()
            OnboardingIntent.NextPage -> if (wasLastPage) completeOnboarding()
            OnboardingIntent.RequestPermissions ->
                _effects.trySend(OnboardingEffect.RequestPermissionLauncher)
            is OnboardingIntent.PermissionResult ->
                if (intent.granted || permissionDenials >= 2) completeOnboarding()
            OnboardingIntent.PreviousPage,
            OnboardingIntent.DismissRationale -> Unit
        }
    }

    private fun completeOnboarding() {
        viewModelScope.launch {
            // If the flag cannot be stored onboarding simply shows again next launch, so the user moves on anyway
            try {
                appSettingsRepository.setOnboardingCompleted(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not store that onboarding is completed", e)
            }
            _effects.trySend(OnboardingEffect.NavigateToSwipe)
        }
    }

    private companion object {
        const val TAG = "OnboardingViewModel"
    }
}
