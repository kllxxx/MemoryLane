package com.kllxx.memorylane.feature.onboarding.impl

sealed interface OnboardingIntent {
    data object NextPage : OnboardingIntent
    data object PreviousPage : OnboardingIntent
    data object Skip : OnboardingIntent
    data object RequestPhotoPermission : OnboardingIntent
    data object RequestNotificationPermission : OnboardingIntent
    data class PhotoPermissionResult(val granted: Boolean) : OnboardingIntent
    data class NotificationPermissionResult(val granted: Boolean) : OnboardingIntent
    data object OpenSettings : OnboardingIntent

    /** Closes the rationale and moves on without photo access. */
    data object DismissRationale : OnboardingIntent
}
