package com.kllxx.memorylane.feature.onboarding.impl

sealed interface OnboardingIntent {
    data object NextPage : OnboardingIntent
    data object PreviousPage : OnboardingIntent
    data object Skip : OnboardingIntent
    data object RequestPermissions : OnboardingIntent
    data class PermissionResult(val granted: Boolean) : OnboardingIntent
    data object DismissRationale : OnboardingIntent
}
