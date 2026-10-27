package com.kllxx.memorylane

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.navigation.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/** Everything the first frame needs, loaded together: the splash screen is held while this is [Loading]. */
sealed interface AppUiState {
    data object Loading : AppUiState

    data class Ready(
        val themeMode: ThemeMode,
        val startDestination: StartDestination,
        // The one "today" of the navigation graph, taken from the injected clock when the start is resolved
        val today: LocalDate,
    ) : AppUiState
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val swipeRepository: SwipeRepository,
    private val photoRepository: PhotoRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val currentDay = LocalDate.now(clock)
            // Used only if the settings cannot be read, otherwise the stored theme wins
            var themeMode = AppSettings().themeMode
            val startDestination = try {
                val settings = appSettingsRepository.getSettings().first()
                themeMode = settings.themeMode
                when {
                    !settings.onboardingCompleted -> StartDestination.Onboarding
                    // Without access Swipe reports PermissionRequired, so a completed day must not hide that
                    !photoRepository.hasMediaPermission() -> StartDestination.Swipe
                    // Some day was already sorted today; otherwise (including an unfinished session,
                    // e.g. the app was killed on Review) it goes back to Swipe
                    swipeRepository.getSessionByCompletedAt(currentDay) != null -> StartDestination.Main
                    // Nothing to review today: go straight to the calendar instead of bouncing off Swipe
                    hasNoPhotosToReview(currentDay) -> StartDestination.Main
                    else -> StartDestination.Swipe
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A start destination must always resolve, otherwise the app sits on the splash screen
                Log.e(TAG, "Could not resolve the start destination", e)
                StartDestination.Onboarding
            }
            _uiState.value = AppUiState.Ready(themeMode, startDestination, currentDay)
            launch { cleanupStalePendingDeletes() }
            followThemeChanges()
        }
    }

    /** A failed lookup is not an empty day: Swipe does its own loading and error handling. */
    private suspend fun hasNoPhotosToReview(day: LocalDate): Boolean = try {
        photoRepository.getPhotosForDate(day).isEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Could not look up the photos of $day", e)
        false
    }

    /** Keeps the theme in step with the Profile setting once the app is up. */
    private suspend fun followThemeChanges() {
        appSettingsRepository.getSettings()
            .map { it.themeMode }
            .distinctUntilChanged()
            .collect { themeMode ->
                _uiState.update { state ->
                    if (state is AppUiState.Ready) state.copy(themeMode = themeMode) else state
                }
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
    }
}
