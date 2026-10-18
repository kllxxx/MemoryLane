package com.kllxx.memorylane.feature.profile.impl

sealed interface ProfileEffect {
    data object NavigateToOnboarding : ProfileEffect

    /** A setting could not be stored and was put back; there is no UI for this yet. */
    data object ShowSettingsError : ProfileEffect
}
