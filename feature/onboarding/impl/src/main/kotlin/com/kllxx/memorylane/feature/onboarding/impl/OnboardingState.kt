package com.kllxx.memorylane.feature.onboarding.impl

data class OnboardingState(
    val currentPage: Int = 0,
    val totalPages: Int = OnboardingConstants.TOTAL_ONBOARDING_PAGES,
    val permissionsGranted: Boolean = false,
    val isLastPage: Boolean = false,
    val isLoading: Boolean = false,
    val showPermissionRationale: Boolean = false,
)
