package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import java.time.LocalTime
import javax.inject.Inject

internal class AppSettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppSettingsDataStore,
) : AppSettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStore.settings

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        dataStore.setOnboardingCompleted(completed)

    override suspend fun setThemeMode(themeMode: ThemeMode) = dataStore.setThemeMode(themeMode)

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        dataStore.setNotificationsEnabled(enabled)

    override suspend fun setStreakGoal(goal: StreakGoal) = dataStore.setStreakGoal(goal)

    override suspend fun setReminderTime(time: LocalTime) = dataStore.setReminderTime(time)

    override suspend fun markNotificationPermissionRequested() =
        dataStore.markNotificationPermissionRequested()

    override suspend fun setSwipeButtonsEnabled(enabled: Boolean) = dataStore.setSwipeButtonsEnabled(enabled)

    override suspend fun setLocationGrantSeen(granted: Boolean) = dataStore.setLocationGrantSeen(granted)

    override suspend fun markPermissionsRequested(permissions: Set<AppPermission>) =
        dataStore.markPermissionsRequested(permissions)
}
