package com.kllxx.memorylane.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalTime
import org.junit.Assert.assertEquals

class AppSettingsDataStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var prefs: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>
    private lateinit var store: AppSettingsDataStore

    @Before
    fun setUp() {
        prefs = PreferenceDataStoreFactory.create(scope = scope) {
            tmp.root.resolve("settings.preferences_pb")
        }
        store = AppSettingsDataStore(prefs)
    }

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `emits defaults when nothing is stored`() = runTest {
        store.settings.test {
            assertEquals(AppSettings(), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setOnboardingCompleted updates the flow`() = runTest {
        store.settings.test {
            assertEquals(false, awaitItem().onboardingCompleted)
            store.setOnboardingCompleted(true)
            assertEquals(true, awaitItem().onboardingCompleted)
            store.setOnboardingCompleted(false)
            assertEquals(false, awaitItem().onboardingCompleted)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setThemeMode updates the flow`() = runTest {
        store.settings.test {
            awaitItem()
            store.setThemeMode(ThemeMode.Light)
            assertEquals(ThemeMode.Light, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setNotificationsEnabled updates the flow`() = runTest {
        store.settings.test {
            assertEquals(true, awaitItem().notificationsEnabled)
            store.setNotificationsEnabled(false)
            assertEquals(false, awaitItem().notificationsEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setStreakGoal updates the flow`() = runTest {
        store.settings.test {
            assertEquals(StreakGoal.D30, awaitItem().streakGoal)
            store.setStreakGoal(StreakGoal.D365)
            assertEquals(StreakGoal.D365, awaitItem().streakGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown stored streak goal falls back to default`() = runTest {
        prefs.edit { it[stringPreferencesKey("streak_goal")] = "DECADE" }
        store.settings.test {
            assertEquals(AppSettings().streakGoal, awaitItem().streakGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unknown stored theme falls back to default`() = runTest {
        prefs.edit { it[stringPreferencesKey("theme_mode")] = "Sepia" }
        store.settings.test {
            assertEquals(AppSettings().themeMode, awaitItem().themeMode)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `streak goals stored under the old names are migrated`() = runTest {
        val expected = mapOf(
            "WEEK" to StreakGoal.D7,
            "MONTH" to StreakGoal.D30,
            "HALF_YEAR" to StreakGoal.D100,
            "YEAR" to StreakGoal.D365,
        )
        expected.forEach { (stored, goal) ->
            prefs.edit { it[stringPreferencesKey("streak_goal")] = stored }
            store.settings.test {
                assertEquals(goal, awaitItem().streakGoal)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    @Test
    fun `an unknown streak goal name falls back to the default`() = runTest {
        prefs.edit { it[stringPreferencesKey("streak_goal")] = "FORTNIGHT" }
        store.settings.test {
            assertEquals(AppSettings().streakGoal, awaitItem().streakGoal)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `reminder time defaults to 20 00`() = runTest {
        store.settings.test {
            assertEquals(LocalTime.of(20, 0), awaitItem().reminderTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `setReminderTime round-trips the time`() = runTest {
        store.settings.test {
            awaitItem()
            listOf(LocalTime.of(0, 0), LocalTime.of(7, 5), LocalTime.of(23, 59)).forEach { time ->
                store.setReminderTime(time)
                assertEquals(time, awaitItem().reminderTime)
            }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `out of range stored reminder time falls back to the default`() = runTest {
        prefs.edit { it[intPreferencesKey("reminder_minute_of_day")] = 24 * 60 }
        store.settings.test {
            assertEquals(AppSettings().reminderTime, awaitItem().reminderTime)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the swipe buttons are off until they are turned on`() = runTest {
        store.settings.test {
            assertEquals(false, awaitItem().swipeButtonsEnabled)
            store.setSwipeButtonsEnabled(true)
            assertEquals(true, awaitItem().swipeButtonsEnabled)
            store.setSwipeButtonsEnabled(false)
            assertEquals(false, awaitItem().swipeButtonsEnabled)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the swipe buttons setting is stored under swipe_buttons_enabled`() = runTest {
        store.setSwipeButtonsEnabled(true)

        assertEquals(true, prefs.data.first()[booleanPreferencesKey("swipe_buttons_enabled")])
    }

    @Test
    fun `markNotificationPermissionRequested updates the flow`() = runTest {
        store.settings.test {
            assertEquals(false, awaitItem().notificationPermissionRequested)
            store.markNotificationPermissionRequested()
            assertEquals(true, awaitItem().notificationPermissionRequested)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
