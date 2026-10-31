package com.kllxx.memorylane.core.data.repository

import app.cash.turbine.test
import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.LocalTime

class AppSettingsRepositoryImplTest {

    private val dataStore = mockk<AppSettingsDataStore>()
    private val repository = AppSettingsRepositoryImpl(dataStore)

    @Test
    fun `getSettings exposes the data store flow`() = runTest {
        val settings = AppSettings(onboardingCompleted = true, themeMode = ThemeMode.Light)
        every { dataStore.settings } returns flowOf(settings)

        repository.getSettings().test {
            assertEquals(settings, awaitItem())
            awaitComplete()
        }
    }
}

/** One row per setter of [AppSettingsRepository]: it must reach the matching data store member, once, unchanged. */
@RunWith(Parameterized::class)
internal class AppSettingsRepositorySetterTest(
    @Suppress("unused") private val name: String,
    private val call: suspend (AppSettingsRepository) -> Unit,
    private val verifyOnStore: suspend (AppSettingsDataStore) -> Unit,
) {

    @Test
    fun `the setter calls its data store counterpart once with the same argument and nothing else`() = runTest {
        val dataStore = mockk<AppSettingsDataStore>(relaxed = true)

        call(AppSettingsRepositoryImpl(dataStore))

        verifyOnStore(dataStore)
        confirmVerified(dataStore)
    }

    companion object {
        private fun row(
            name: String,
            call: suspend (AppSettingsRepository) -> Unit,
            verifyOnStore: suspend (AppSettingsDataStore) -> Unit,
        ): Array<Any> = arrayOf(name, call, verifyOnStore)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun setters(): List<Array<Any>> = listOf(
            row(
                "setOnboardingCompleted",
                { it.setOnboardingCompleted(true) },
                { coVerify(exactly = 1) { it.setOnboardingCompleted(true) } },
            ),
            row(
                "setThemeMode",
                { it.setThemeMode(ThemeMode.System) },
                { coVerify(exactly = 1) { it.setThemeMode(ThemeMode.System) } },
            ),
            row(
                "setNotificationsEnabled",
                { it.setNotificationsEnabled(false) },
                { coVerify(exactly = 1) { it.setNotificationsEnabled(false) } },
            ),
            row(
                "setStreakGoal",
                { it.setStreakGoal(StreakGoal.D100) },
                { coVerify(exactly = 1) { it.setStreakGoal(StreakGoal.D100) } },
            ),
            row(
                "setReminderTime",
                { it.setReminderTime(LocalTime.of(8, 30)) },
                { coVerify(exactly = 1) { it.setReminderTime(LocalTime.of(8, 30)) } },
            ),
            row(
                "markNotificationPermissionRequested",
                { it.markNotificationPermissionRequested() },
                { coVerify(exactly = 1) { it.markNotificationPermissionRequested() } },
            ),
            row(
                "setSwipeButtonsEnabled",
                { it.setSwipeButtonsEnabled(true) },
                { coVerify(exactly = 1) { it.setSwipeButtonsEnabled(true) } },
            ),
            row(
                "setLocationGrantSeen",
                { it.setLocationGrantSeen(true) },
                { coVerify(exactly = 1) { it.setLocationGrantSeen(true) } },
            ),
            row(
                "markPermissionsRequested",
                { it.markPermissionsRequested(setOf(AppPermission.Photos, AppPermission.Location)) },
                {
                    coVerify(exactly = 1) {
                        it.markPermissionsRequested(setOf(AppPermission.Photos, AppPermission.Location))
                    }
                },
            ),
        )
    }
}
