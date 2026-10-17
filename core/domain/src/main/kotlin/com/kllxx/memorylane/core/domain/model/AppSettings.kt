package com.kllxx.memorylane.core.domain.model

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Dark,
    val notificationsEnabled: Boolean = true,
    val streakGoal: StreakGoal = StreakGoal.WEEK,
)
