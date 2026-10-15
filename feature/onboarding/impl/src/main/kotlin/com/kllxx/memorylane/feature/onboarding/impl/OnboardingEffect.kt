package com.kllxx.memorylane.feature.onboarding.impl

sealed interface OnboardingEffect {
    data object NavigateToSwipe : OnboardingEffect
    data object RequestPermissionLauncher : OnboardingEffect
}
