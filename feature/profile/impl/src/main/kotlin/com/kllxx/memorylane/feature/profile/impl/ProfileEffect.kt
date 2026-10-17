package com.kllxx.memorylane.feature.profile.impl

sealed interface ProfileEffect {
    data object NavigateToOnboarding : ProfileEffect
}
