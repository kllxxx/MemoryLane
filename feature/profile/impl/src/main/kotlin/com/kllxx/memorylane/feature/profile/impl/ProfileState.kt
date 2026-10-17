package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode

data class ProfileState(
    val themeMode: ThemeMode = ThemeMode.Dark,
    val notificationsEnabled: Boolean = true,
    val streakGoal: StreakGoal = StreakGoal.WEEK,
    val isLoading: Boolean = false,
)
