package com.kllxx.memorylane.core.domain.model

import com.kllxx.memorylane.core.domain.permission.AppPermission
import java.time.LocalTime

data class AppSettings(
    val onboardingCompleted: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** App-level switch, independent of the system permission. */
    val notificationsEnabled: Boolean = true,
    val streakGoal: StreakGoal = StreakGoal.D30,
    val reminderTime: LocalTime = LocalTime.of(20, 0),
    /** Set after the first POST_NOTIFICATIONS request, to tell "not asked yet" from "permanently denied". */
    val notificationPermissionRequested: Boolean = false,
    /** Swipe shows the keep and delete buttons on the card, next to the gesture. */
    val swipeButtonsEnabled: Boolean = false,
    /** Whether the location permission was granted when it was last looked at, to tell a new grant from an old one. */
    val locationGrantSeen: Boolean = false,
    /** The permissions the system prompt was shown for, to tell "not asked yet" from "denied for good". Photos and location go together. */
    val requestedPermissions: Set<AppPermission> = emptySet(),
)
