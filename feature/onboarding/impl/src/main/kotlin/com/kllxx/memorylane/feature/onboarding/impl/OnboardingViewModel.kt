package com.kllxx.memorylane.feature.onboarding.impl

import androidx.lifecycle.SavedStateHandle
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
) : MviViewModel<OnboardingState, OnboardingIntent, Nothing, OnboardingEffect>(
    initialState = OnboardingState(),
    // Nothing happens to the state that is not an intent
    reducer = { state, _ -> state },
    errorHandler = errorHandler,
) {

    // Kept across process death, so the rationale threshold does not start over
    private var permissionDenials: Int = savedStateHandle[PERMISSION_DENIALS_KEY] ?: 0

    override fun onIntent(intent: OnboardingIntent) {
        val wasLastPage = currentState.isLastPage
        if (intent == OnboardingIntent.PhotoPermissionResult(granted = false)) {
            permissionDenials++
            savedStateHandle[PERMISSION_DENIALS_KEY] = permissionDenials
        }

        update { OnboardingReducer.reduce(it, intent, permissionDenials) }

        when (intent) {
            OnboardingIntent.Skip -> completeOnboarding()
            OnboardingIntent.NextPage -> if (wasLastPage) completeOnboarding()
            OnboardingIntent.RequestPhotoPermission ->
                sendEffect(OnboardingEffect.RequestPhotoPermissionLauncher)
            OnboardingIntent.RequestNotificationPermission ->
                sendEffect(OnboardingEffect.RequestNotificationPermissionLauncher)
            // Notifications are the last step: whatever the answer, onboarding is done
            is OnboardingIntent.NotificationPermissionResult -> completeOnboarding()
            OnboardingIntent.OpenSettings -> sendEffect(OnboardingEffect.OpenAppSettings)
            is OnboardingIntent.PhotoPermissionResult,
            OnboardingIntent.PreviousPage,
            OnboardingIntent.DismissRationale -> Unit
        }
    }

    private fun completeOnboarding() {
        launchSafe("complete onboarding") {
            // If the flag cannot be stored onboarding simply shows again next launch, so the user moves on anyway
            runSafe("store that onboarding is completed", fallback = {}) {
                appSettingsRepository.setOnboardingCompleted(true)
            }
            sendEffect(OnboardingEffect.NavigateToSwipe)
        }
    }

    internal companion object {
        const val PERMISSION_DENIALS_KEY = "permission_denials"
    }
}
