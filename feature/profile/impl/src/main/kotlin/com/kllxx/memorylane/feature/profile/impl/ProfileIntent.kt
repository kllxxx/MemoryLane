package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import java.time.LocalTime

sealed interface ProfileIntent {
    data class SelectGoal(val goal: StreakGoal) : ProfileIntent
    data class NotificationsToggled(val on: Boolean) : ProfileIntent

    /** Sent on ON_RESUME and after the permission dialog. */
    data class SystemNotificationsChecked(
        val enabled: Boolean,
        /** API 33+, not granted and not permanently denied. */
        val canRequestPermission: Boolean,
    ) : ProfileIntent

    data class PermissionResult(val granted: Boolean) : ProfileIntent
    data object OpenTimePicker : ProfileIntent
    data object DismissTimePicker : ProfileIntent
    data class ConfirmTime(val time: LocalTime) : ProfileIntent
    data class SelectTheme(val mode: ThemeMode) : ProfileIntent
    data object ReplayOnboardingClicked : ProfileIntent
    data object ContactSupportClicked : ProfileIntent
}
