package com.kllxx.memorylane.feature.onboarding.impl

import android.util.Log
import androidx.lifecycle.SavedStateHandle
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
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    // Kept across process death, so the rationale threshold does not start over
    private var permissionDenials: Int = savedStateHandle[PERMISSION_DENIALS_KEY] ?: 0

    fun onIntent(intent: OnboardingIntent) {
        val wasLastPage = _state.value.isLastPage
        if (intent == OnboardingIntent.PhotoPermissionResult(granted = false)) {
            permissionDenials++
            savedStateHandle[PERMISSION_DENIALS_KEY] = permissionDenials
        }

        _state.update { OnboardingReducer.reduce(it, intent, permissionDenials) }

        when (intent) {
            OnboardingIntent.Skip -> completeOnboarding()
            OnboardingIntent.NextPage -> if (wasLastPage) completeOnboarding()
            OnboardingIntent.RequestPhotoPermission ->
                _effects.trySend(OnboardingEffect.RequestPhotoPermissionLauncher)
            OnboardingIntent.RequestNotificationPermission ->
                _effects.trySend(OnboardingEffect.RequestNotificationPermissionLauncher)
            // Notifications are the last step: whatever the answer, onboarding is done
            is OnboardingIntent.NotificationPermissionResult -> completeOnboarding()
            OnboardingIntent.OpenSettings -> _effects.trySend(OnboardingEffect.OpenAppSettings)
            is OnboardingIntent.PhotoPermissionResult,
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

    internal companion object {
        const val TAG = "OnboardingViewModel"
        const val PERMISSION_DENIALS_KEY = "permission_denials"
    }
}
