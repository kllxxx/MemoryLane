package com.kllxx.memorylane.core.photos.domain.usecase

import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoLocationCache
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Keeps the places of the photos in step with the permission to read where they were taken. Without it every photo
 * looks as if it had no place; once it is granted, what was found in the meantime is out of date and every place is
 * looked up again, so the cache is cleared when the permission goes from denied to granted. A missing stored answer
 * counts as denied: the first time the permission is seen granted, the cache starts clean.
 *
 * [invoke] reads the permission as the system has it now; it is called when the app comes to the foreground, where a
 * change made in the system settings shows up.
 */
class SyncLocationPermission @Inject constructor(
    private val permissionsRepository: PermissionsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val locationCache: PhotoLocationCache,
) {

    suspend operator fun invoke() {
        permissionsRepository.refresh()
        val granted = permissionsRepository.state.value.location
        val seen = appSettingsRepository.getSettings().first().locationGrantSeen
        if (granted == seen) return
        // The answer is stored after the clearing, so a clearing that failed is tried again next time
        if (granted) locationCache.clear()
        appSettingsRepository.setLocationGrantSeen(granted)
    }
}
