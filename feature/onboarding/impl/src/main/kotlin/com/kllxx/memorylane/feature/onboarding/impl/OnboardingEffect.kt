package com.kllxx.memorylane.feature.onboarding.impl

sealed interface OnboardingEffect {
    data object NavigateToSwipe : OnboardingEffect
    data object RequestPhotoPermissionLauncher : OnboardingEffect
    data object RequestNotificationPermissionLauncher : OnboardingEffect
    data object OpenAppSettings : OnboardingEffect
}
