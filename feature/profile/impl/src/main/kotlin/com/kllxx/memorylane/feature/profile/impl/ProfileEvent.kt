package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.AppSettings

internal sealed interface ProfileEvent {
    data class SettingsLoaded(val settings: AppSettings) : ProfileEvent
    data class StreakLoaded(val currentStreak: Int) : ProfileEvent
    data object AwaitingSystemSettings : ProfileEvent
}
