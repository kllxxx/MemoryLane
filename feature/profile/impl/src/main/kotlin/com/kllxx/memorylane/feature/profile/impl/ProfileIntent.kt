package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode

sealed interface ProfileIntent {
    data object Load : ProfileIntent
    data class SetTheme(val mode: ThemeMode) : ProfileIntent
    data class SetNotifications(val enabled: Boolean) : ProfileIntent
    data class SetStreakGoal(val goal: StreakGoal) : ProfileIntent
    data object RepeatOnboarding : ProfileIntent
}
