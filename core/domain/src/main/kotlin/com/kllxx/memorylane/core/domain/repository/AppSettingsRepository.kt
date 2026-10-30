package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

interface AppSettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setStreakGoal(goal: StreakGoal)
    suspend fun setReminderTime(time: LocalTime)
    suspend fun markNotificationPermissionRequested()
    suspend fun setSwipeButtonsEnabled(enabled: Boolean)
    suspend fun setLocationGrantSeen(granted: Boolean)
}
