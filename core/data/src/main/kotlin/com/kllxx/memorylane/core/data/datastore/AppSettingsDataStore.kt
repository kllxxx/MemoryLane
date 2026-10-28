package com.kllxx.memorylane.core.data.datastore

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.LocalTime
import javax.inject.Inject

internal class AppSettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    val settings: Flow<AppSettings> = dataStore.data
        .catch { e ->
            if (e !is IOException) throw e
            Log.e(TAG, "Could not read the settings, using the defaults", e)
            emit(emptyPreferences())
        }
        .map { prefs ->
            val defaults = AppSettings()
            AppSettings(
                onboardingCompleted = prefs[ONBOARDING_COMPLETED] ?: defaults.onboardingCompleted,
                themeMode = prefs[THEME_MODE]
                    ?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } }
                    ?: defaults.themeMode,
                notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: defaults.notificationsEnabled,
                streakGoal = prefs[STREAK_GOAL]?.let(::streakGoalOf) ?: defaults.streakGoal,
                reminderTime = prefs[REMINDER_MINUTE_OF_DAY]
                    ?.takeIf { it in 0 until MINUTES_PER_DAY }
                    ?.let { LocalTime.of(it / MINUTES_PER_HOUR, it % MINUTES_PER_HOUR) }
                    ?: defaults.reminderTime,
                notificationPermissionRequested = prefs[NOTIFICATION_PERMISSION_REQUESTED]
                    ?: defaults.notificationPermissionRequested,
                swipeButtonsEnabled = prefs[SWIPE_BUTTONS_ENABLED] ?: defaults.swipeButtonsEnabled,
            )
        }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = completed }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.name }
    }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun setStreakGoal(goal: StreakGoal) {
        dataStore.edit { it[STREAK_GOAL] = goal.name }
    }

    suspend fun setReminderTime(time: LocalTime) {
        dataStore.edit { it[REMINDER_MINUTE_OF_DAY] = time.hour * MINUTES_PER_HOUR + time.minute }
    }

    suspend fun markNotificationPermissionRequested() {
        dataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED] = true }
    }

    suspend fun setSwipeButtonsEnabled(enabled: Boolean) {
        dataStore.edit { it[SWIPE_BUTTONS_ENABLED] = enabled }
    }

    private companion object {
        const val TAG = "AppSettingsDataStore"
        const val MINUTES_PER_HOUR = 60
        const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR

        /** Goals stored before the enum was renamed; the dropped 180-day goal was replaced by the 100-day one. */
        val LEGACY_STREAK_GOALS = mapOf(
            "WEEK" to StreakGoal.D7,
            "MONTH" to StreakGoal.D30,
            "HALF_YEAR" to StreakGoal.D100,
            "YEAR" to StreakGoal.D365,
        )

        fun streakGoalOf(name: String): StreakGoal? =
            StreakGoal.entries.firstOrNull { it.name == name } ?: LEGACY_STREAK_GOALS[name]

        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val STREAK_GOAL = stringPreferencesKey("streak_goal")
        val REMINDER_MINUTE_OF_DAY = intPreferencesKey("reminder_minute_of_day")
        val NOTIFICATION_PERMISSION_REQUESTED = booleanPreferencesKey("notification_permission_requested")
        val SWIPE_BUTTONS_ENABLED = booleanPreferencesKey("swipe_buttons_enabled")
    }
}
