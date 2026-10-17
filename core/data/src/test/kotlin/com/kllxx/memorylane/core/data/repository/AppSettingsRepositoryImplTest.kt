package com.kllxx.memorylane.core.data.repository

import app.cash.turbine.test
import com.kllxx.memorylane.core.data.datastore.AppSettingsDataStore
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.model.ThemeMode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.assertEquals

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

    @Test
    fun `setters delegate to the data store`() = runTest {
        coEvery { dataStore.setOnboardingCompleted(any()) } returns Unit
        coEvery { dataStore.setThemeMode(any()) } returns Unit
        coEvery { dataStore.setNotificationsEnabled(any()) } returns Unit
        coEvery { dataStore.setStreakGoal(any()) } returns Unit

        repository.setOnboardingCompleted(true)
        repository.setThemeMode(ThemeMode.System)
        repository.setNotificationsEnabled(false)
        repository.setStreakGoal(StreakGoal.MONTH)

        coVerify(exactly = 1) { dataStore.setOnboardingCompleted(true) }
        coVerify(exactly = 1) { dataStore.setThemeMode(ThemeMode.System) }
        coVerify(exactly = 1) { dataStore.setNotificationsEnabled(false) }
        coVerify(exactly = 1) { dataStore.setStreakGoal(StreakGoal.MONTH) }
    }
}
