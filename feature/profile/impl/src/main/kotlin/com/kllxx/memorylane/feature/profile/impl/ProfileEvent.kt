package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode

internal sealed interface ProfileEvent {
    data class SettingsLoaded(
        val themeMode: ThemeMode,
        val notificationsEnabled: Boolean,
        val streakGoal: StreakGoal,
    ) : ProfileEvent
}
