package com.kllxx.memorylane

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.time.TodayProvider
import com.kllxx.memorylane.navigation.StartDestination
import com.kllxx.memorylane.reminder.TodayReview
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Everything the first frame needs, loaded together: the splash screen is held while this is [Loading]. */
sealed interface AppUiState {
    data object Loading : AppUiState

    data class Ready(
        val themeMode: ThemeMode,
        val startDestination: StartDestination,
    ) : AppUiState
}

/** What the screen is asked to do outside of its own back stack. */
sealed interface AppEffect {
    /** The reminder was tapped and today has photos to review: Swipe for [date], with Calendar below it. */
    data class OpenToday(val date: LocalDate) : AppEffect
}

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val todayReview: TodayReview,
    private val todayProvider: TodayProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow<AppUiState>(AppUiState.Loading)
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private val _effects = Channel<AppEffect>(Channel.BUFFERED)
    val effects: Flow<AppEffect> = _effects.receiveAsFlow()

    init {
        viewModelScope.launch {
            // Used only if the settings cannot be read, otherwise the stored theme wins
            var themeMode = AppSettings().themeMode
            val startDestination = try {
                val settings = appSettingsRepository.getSettings().first()
                themeMode = settings.themeMode
                // The app opens on Calendar once it is set up: Calendar asks for the access to the photos itself
                if (settings.onboardingCompleted) StartDestination.Main else StartDestination.Onboarding
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // A start destination must always resolve, otherwise the app sits on the splash screen
                Log.e(TAG, "Could not resolve the start destination", e)
                StartDestination.Onboarding
            }
            _uiState.value = AppUiState.Ready(themeMode, startDestination)
            followThemeChanges()
        }
    }

    /**
     * The reminder was tapped, at a cold start or while the app runs. Swipe for today opens only when the app is set up
     * and today has photos that are not decided yet; before onboarding is finished the normal routing goes on, and
     * with nothing to review, or no access to the photos, Calendar is where the person stays.
     */
    fun onReminderOpened() {
        viewModelScope.launch {
            val ready = _uiState.first { it is AppUiState.Ready } as AppUiState.Ready
            if (ready.startDestination != StartDestination.Main) return@launch
            // The date at the moment of the tap: the process may have been alive since yesterday
            val today = todayProvider.now()
            if (todayReview.photosToReview(today) > 0) _effects.send(AppEffect.OpenToday(today))
        }
    }

    /** Keeps the theme in step with the Profile setting once the app is up. */
    private suspend fun followThemeChanges() {
        try {
            appSettingsRepository.getSettings()
                .map { it.themeMode }
                .distinctUntilChanged()
                .collect { themeMode ->
                    _uiState.update { state ->
                        if (state is AppUiState.Ready) state.copy(themeMode = themeMode) else state
                    }
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The theme stays as it was; the settings that could not be read are not worth a crash
            Log.e(TAG, "Could not follow the theme", e)
        }
    }

    private companion object {
        const val TAG = "AppViewModel"
    }
}
