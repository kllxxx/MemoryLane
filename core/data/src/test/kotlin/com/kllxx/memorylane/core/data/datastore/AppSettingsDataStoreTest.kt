package com.kllxx.memorylane.core.data.datastore

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.cash.turbine.test
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
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
            store.setOnboardingCompleted()
            assertEquals(true, awaitItem().onboardingCompleted)
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
            assertEquals(StreakGoal.WEEK, awaitItem().streakGoal)
            store.setStreakGoal(StreakGoal.YEAR)
            assertEquals(StreakGoal.YEAR, awaitItem().streakGoal)
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
}
