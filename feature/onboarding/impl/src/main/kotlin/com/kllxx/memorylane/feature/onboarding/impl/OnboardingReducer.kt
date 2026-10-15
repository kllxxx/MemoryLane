package com.kllxx.memorylane.feature.onboarding.impl

object OnboardingReducer {

    /** [permissionDenials] is the total number of denials, including the one carried by [intent]. */
    fun reduce(
        state: OnboardingState,
        intent: OnboardingIntent,
        permissionDenials: Int = 0,
    ): OnboardingState = when (intent) {
        OnboardingIntent.NextPage -> state.withPage(state.currentPage + 1)
        OnboardingIntent.PreviousPage -> state.withPage(state.currentPage - 1)
        OnboardingIntent.Skip -> state
        OnboardingIntent.RequestPermissions -> state.copy(isLoading = true)
        is OnboardingIntent.PermissionResult ->
            if (intent.granted) {
                state.copy(
                    permissionsGranted = true,
                    isLoading = false,
                    showPermissionRationale = false,
                )
            } else {
                state.copy(
                    isLoading = false,
                    showPermissionRationale = permissionDenials <= 1,
                )
            }
        OnboardingIntent.DismissRationale -> state.copy(showPermissionRationale = false)
    }

    private fun OnboardingState.withPage(page: Int): OnboardingState {
        val clamped = page.coerceIn(0, totalPages - 1)
        return copy(currentPage = clamped, isLastPage = clamped == totalPages - 1)
    }
}
