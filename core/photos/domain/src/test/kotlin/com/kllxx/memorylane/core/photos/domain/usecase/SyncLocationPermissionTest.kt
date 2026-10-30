package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.permission.PermissionsState
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoLocationCache
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SyncLocationPermissionTest {

    private val permissionState = MutableStateFlow(PermissionsState(location = false))
    private val permissionsRepository = mockk<PermissionsRepository> {
        every { state } returns permissionState
        every { refresh() } just runs
    }
    private val appSettingsRepository = mockk<AppSettingsRepository> {
        coEvery { setLocationGrantSeen(any()) } just runs
    }
    private val locationCache = mockk<PhotoLocationCache> {
        coEvery { clear() } just runs
    }

    private val sync = SyncLocationPermission(permissionsRepository, appSettingsRepository, locationCache)

    private fun givenSeen(seen: Boolean) {
        every { appSettingsRepository.getSettings() } returns flowOf(AppSettings(locationGrantSeen = seen))
    }

    @Test
    fun `the cache is cleared once the permission goes from denied to granted`() = runTest {
        givenSeen(false)
        permissionState.value = PermissionsState(location = true)

        sync()

        coVerifyOrder {
            locationCache.clear()
            appSettingsRepository.setLocationGrantSeen(true)
        }
    }

    @Test
    fun `a missing stored answer counts as denied`() = runTest {
        // AppSettings() has no answer stored: locationGrantSeen is false
        every { appSettingsRepository.getSettings() } returns flowOf(AppSettings())
        permissionState.value = PermissionsState(location = true)

        sync()

        coVerify(exactly = 1) { locationCache.clear() }
    }

    @Test
    fun `a granted permission that was already seen leaves the cache alone`() = runTest {
        givenSeen(true)
        permissionState.value = PermissionsState(location = true)

        sync()

        coVerify(exactly = 0) { locationCache.clear() }
        coVerify(exactly = 0) { appSettingsRepository.setLocationGrantSeen(any()) }
    }

    @Test
    fun `a revoked permission is stored but the cache stays, nothing could be read anyway`() = runTest {
        givenSeen(true)
        permissionState.value = PermissionsState(location = false)

        sync()

        coVerify(exactly = 0) { locationCache.clear() }
        coVerify(exactly = 1) { appSettingsRepository.setLocationGrantSeen(false) }
    }

    @Test
    fun `a permission that stays denied changes nothing`() = runTest {
        givenSeen(false)
        permissionState.value = PermissionsState(location = false)

        sync()

        coVerify(exactly = 0) { locationCache.clear() }
        coVerify(exactly = 0) { appSettingsRepository.setLocationGrantSeen(any()) }
    }

    @Test
    fun `the permission is read from the system first`() = runTest {
        givenSeen(true)
        permissionState.value = PermissionsState(location = true)

        sync()

        verify(exactly = 1) { permissionsRepository.refresh() }
    }

    @Test
    fun `a failed clearing is not remembered, so the next call tries again`() = runTest {
        givenSeen(false)
        permissionState.value = PermissionsState(location = true)
        coEvery { locationCache.clear() } throws IllegalStateException("disk full")

        runCatching { sync() }

        coVerify(exactly = 0) { appSettingsRepository.setLocationGrantSeen(any()) }
    }
}
