package com.kllxx.memorylane.feature.profile.impl

import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import java.time.LocalTime

sealed interface ProfileState {

    /** The stored settings, the streak and the system notification state have not all arrived yet. */
    data object Loading : ProfileState

    /** Only ever built from real values; the defaults here are for tests and previews. */
    data class Content(
        val currentStreak: Int = 0,
        val goal: StreakGoal = StreakGoal.D30,
        val userNotificationsEnabled: Boolean = true,
        val systemNotificationsEnabled: Boolean = true,
        val pendingEnable: Boolean = false,
        /** Whether POST_NOTIFICATIONS was already requested once, needed to tell "never asked" from "permanently denied". */
        val permissionRequested: Boolean = false,
        val reminderTime: LocalTime = LocalTime.of(20, 0),
        val themeMode: ThemeMode = ThemeMode.Dark,
        /** The ✕ and ✓ buttons on the Swipe screen; the setting the Swipe screen shows as its «Кнопки» action. */
        val swipeButtonsEnabled: Boolean = false,
        val versionName: String = "",
        val isTimePickerVisible: Boolean = false,
    ) : ProfileState {
        val notificationsOn: Boolean get() = userNotificationsEnabled && systemNotificationsEnabled

        val streakProgress: StreakProgress get() = StreakProgress.of(currentStreak, goal)
    }
}
