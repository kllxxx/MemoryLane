package com.kllxx.memorylane.core.data.repository

import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

internal class AppSettingsRepositoryImpl @Inject constructor(
    private val dataStore: AppSettingsDataStore,
) : AppSettingsRepository {

    override fun getSettings(): Flow<AppSettings> = dataStore.settings

    override suspend fun setOnboardingCompleted() = dataStore.setOnboardingCompleted()

    override suspend fun setThemeMode(themeMode: ThemeMode) = dataStore.setThemeMode(themeMode)

    override suspend fun setNotificationsEnabled(enabled: Boolean) =
        dataStore.setNotificationsEnabled(enabled)

    override suspend fun setStreakGoal(goal: StreakGoal) = dataStore.setStreakGoal(goal)
}
