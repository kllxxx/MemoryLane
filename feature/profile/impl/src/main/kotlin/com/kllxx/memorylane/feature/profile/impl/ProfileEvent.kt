package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.permission.PermissionsState

sealed interface ProfileEvent {
    /** Everything the screen shows, read once; turns [ProfileState.Loading] into content. */
    data class Loaded(
        val settings: AppSettings,
        val currentStreak: Int,
        val systemNotificationsEnabled: Boolean,
        val versionName: String,
        val permissions: PermissionsState = PermissionsState(),
    ) : ProfileEvent

    data class SettingsLoaded(val settings: AppSettings) : ProfileEvent
    data class StreakLoaded(val currentStreak: Int) : ProfileEvent
    data object AwaitingSystemSettings : ProfileEvent

    /** The system changed a permission, or it was read again. */
    data class PermissionsChanged(val permissions: PermissionsState) : ProfileEvent
}
