package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.common.UiError

sealed interface ProfileEffect {
    data object RequestNotificationPermission : ProfileEffect
    data object OpenNotificationSettings : ProfileEffect
    data object NavigateToOnboarding : ProfileEffect
    data class ContactSupport(val versionName: String) : ProfileEffect

    /** A setting could not be stored; the state keeps following the stored value. */
    data class ShowError(val error: UiError) : ProfileEffect
}
