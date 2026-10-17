package com.kllxx.memorylane.core.domain.repository

import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface AppSettingsRepository {
    fun getSettings(): Flow<AppSettings>
    suspend fun setOnboardingCompleted(completed: Boolean)
    suspend fun setThemeMode(themeMode: ThemeMode)
    suspend fun setNotificationsEnabled(enabled: Boolean)
    suspend fun setStreakGoal(goal: StreakGoal)
}
