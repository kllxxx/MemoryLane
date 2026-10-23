package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import java.time.LocalTime

data class ProfileState(
    val currentStreak: Int = 0,
    val goal: StreakGoal = StreakGoal.D30,
    val userNotificationsEnabled: Boolean = true,
    val systemNotificationsEnabled: Boolean = true,
    val pendingEnable: Boolean = false,
    /** Whether POST_NOTIFICATIONS was already requested once, needed to tell "never asked" from "permanently denied". */
    val permissionRequested: Boolean = false,
    val reminderTime: LocalTime = LocalTime.of(20, 0),
    val themeMode: ThemeMode = ThemeMode.Dark,
    val versionName: String = "",
    val isTimePickerVisible: Boolean = false,
) {
    val notificationsOn: Boolean get() = userNotificationsEnabled && systemNotificationsEnabled

    val streakProgress: StreakProgress get() = StreakProgress.of(currentStreak, goal)
}
