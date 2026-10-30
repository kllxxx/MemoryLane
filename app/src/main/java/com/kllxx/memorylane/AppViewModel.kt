package com.kllxx.memorylane

import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.domain.model.AppSettings
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.time.TodayProvider
import com.kllxx.memorylane.core.photos.domain.usecase.SyncLocationPermission
import com.kllxx.memorylane.navigation.StartDestination
import com.kllxx.memorylane.reminder.TodayReview
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val todayReview: TodayReview,
    private val todayProvider: TodayProvider,
    private val syncLocationPermission: SyncLocationPermission,
    errorHandler: ErrorHandler,
) : MviViewModel<AppUiState, AppIntent, AppEvent, AppEffect>(
    initialState = AppUiState.Loading,
    reducer = AppReducer::reduce,
    errorHandler = errorHandler,
) {

    init {
        launchSafe("start the app") {
            // Used only if the settings cannot be read, otherwise the stored theme wins
            var themeMode = AppSettings().themeMode
            // A start destination must always resolve, otherwise the app sits on the splash screen
            val startDestination = runSafe("resolve the start destination", fallback = { StartDestination.Onboarding }) {
                val settings = appSettingsRepository.getSettings().first()
                themeMode = settings.themeMode
                // The app opens on Calendar once it is set up: Calendar asks for the access to the photos itself
                if (settings.onboardingCompleted) StartDestination.Main else StartDestination.Onboarding
            }
            reduce(AppEvent.Resolved(themeMode, startDestination))
            followThemeChanges()
        }
    }

    override fun onIntent(intent: AppIntent) {
        update { AppReducer.reduce(it, intent) }

        when (intent) {
            AppIntent.ReminderOpened -> onReminderOpened()
            AppIntent.Resumed -> onResumed()
        }
    }

    /**
     * The reminder was tapped, at a cold start or while the app runs. Swipe for today opens only when the app is set up
     * and today has photos that are not decided yet; before onboarding is finished the normal routing goes on, and
     * with nothing to review, or no access to the photos, Calendar is where the person stays.
     */
    private fun onReminderOpened() {
        launchSafe("open today from the reminder") {
            val ready = state.first { it is AppUiState.Ready } as AppUiState.Ready
            if (ready.startDestination != StartDestination.Main) return@launchSafe
            // The date at the moment of the tap: the process may have been alive since yesterday
            val today = todayProvider.now()
            if (todayReview.photosToReview(today) > 0) sendEffect(AppEffect.OpenToday(today))
        }
    }

    /** A permission can be changed in the system settings while the app is in the background. */
    private fun onResumed() {
        launchSafe("sync the permissions") { syncLocationPermission() }
    }

    /** Keeps the theme in step with the Profile setting once the app is up. */
    private suspend fun followThemeChanges() {
        // The theme stays as it was; the settings that could not be read are not worth a crash
        runSafe("follow the theme", fallback = {}) {
            appSettingsRepository.getSettings()
                .map { it.themeMode }
                .distinctUntilChanged()
                .collect { reduce(AppEvent.ThemeChanged(it)) }
        }
    }
}
