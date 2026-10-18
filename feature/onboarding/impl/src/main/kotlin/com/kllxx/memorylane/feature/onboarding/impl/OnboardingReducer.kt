package com.kllxx.memorylane.feature.onboarding.impl

object OnboardingReducer {

    /** [permissionDenials] is the total number of photo denials, including the one carried by [intent]. */
    fun reduce(
        state: OnboardingState,
        intent: OnboardingIntent,
        permissionDenials: Int = 0,
    ): OnboardingState = when (intent) {
        OnboardingIntent.NextPage -> state.withPage(state.currentPage + 1)
        OnboardingIntent.PreviousPage -> state.withPage(state.currentPage - 1)
        OnboardingIntent.Skip,
        OnboardingIntent.OpenSettings -> state
        OnboardingIntent.RequestPhotoPermission,
        OnboardingIntent.RequestNotificationPermission -> state.copy(isLoading = true)
        is OnboardingIntent.PhotoPermissionResult ->
            if (intent.granted) {
                state.copy(
                    permissionsGranted = true,
                    isLoading = false,
                    showPermissionRationale = false,
                ).withPage(OnboardingConstants.PHOTO_PAGE + 1)
            } else {
                // The system stops asking after repeated denials, so the user is sent to the settings
                state.copy(
                    isLoading = false,
                    showPermissionRationale = permissionDenials >= OnboardingConstants.PERMISSION_DENIAL_THRESHOLD,
                )
            }
        is OnboardingIntent.NotificationPermissionResult -> state.copy(isLoading = false)
        OnboardingIntent.DismissRationale ->
            state.copy(showPermissionRationale = false).withPage(OnboardingConstants.PHOTO_PAGE + 1)
    }

    private fun OnboardingState.withPage(page: Int): OnboardingState {
        val clamped = page.coerceIn(0, totalPages - 1)
        return copy(currentPage = clamped, isLastPage = clamped == totalPages - 1)
    }
}
