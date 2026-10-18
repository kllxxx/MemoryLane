package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode

internal sealed interface ProfileEvent {
    data class SettingsLoaded(
        val themeMode: ThemeMode,
        val notificationsEnabled: Boolean,
        val streakGoal: StreakGoal,
    ) : ProfileEvent

    /** A write failed, so the setting goes back to the value it had before the change. */
    data class ThemeReverted(val themeMode: ThemeMode) : ProfileEvent
    data class NotificationsReverted(val enabled: Boolean) : ProfileEvent
    data class StreakGoalReverted(val goal: StreakGoal) : ProfileEvent
}
