package com.kllxx.memorylane

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.navigation.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val swipeRepository: SwipeRepository,
    private val photoRepository: PhotoRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<StartDestination?>(null)
    val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

    // The one "today" of the navigation graph, taken from the injected clock when the start is resolved
    private val _today = MutableStateFlow(LocalDate.now(clock))
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    // Read synchronously so the very first frame already uses the stored theme (no dark -> light flicker)
    private val initialThemeMode: ThemeMode = runBlocking {
        appSettingsRepository.getSettings().first().themeMode
    }

    val themeMode: StateFlow<ThemeMode> = appSettingsRepository.getSettings()
        .map { it.themeMode }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), initialThemeMode)

    init {
        viewModelScope.launch {
            val currentDay = LocalDate.now(clock)
            _today.value = currentDay
            _startDestination.value = try {
                val settings = appSettingsRepository.getSettings().first()
                when {
                    !settings.onboardingCompleted -> StartDestination.Onboarding
                    // Without access Swipe reports PermissionRequired, so a completed day must not hide that
                    !photoRepository.hasMediaPermission() -> StartDestination.Swipe
                    // Some day was already sorted today; otherwise (including an unfinished session,
                    // e.g. the app was killed on Review) it goes back to Swipe
                    swipeRepository.getSessionByCompletedAt(currentDay) != null -> StartDestination.Main
                    else -> StartDestination.Swipe
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A start destination must always resolve, otherwise the app sits on the loading spinner
                Log.e(TAG, "Could not resolve the start destination", e)
                StartDestination.Onboarding
            }
            cleanupStalePendingDeletes()
        }
    }

    /** Housekeeping after routing: a failure here is logged and never affects where the app starts. */
    private suspend fun cleanupStalePendingDeletes() {
        try {
            swipeRepository.cleanupStalePendingDeletes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not clean up the stale pending deletes", e)
        }
    }

    private companion object {
        const val TAG = "AppViewModel"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
