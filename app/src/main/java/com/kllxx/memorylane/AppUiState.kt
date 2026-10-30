package com.kllxx.memorylane

import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.navigation.StartDestination

/** Everything the first frame needs, loaded together: the splash screen is held while this is [Loading]. */
sealed interface AppUiState {
    data object Loading : AppUiState

    data class Ready(
        val themeMode: ThemeMode,
        val startDestination: StartDestination,
    ) : AppUiState
}
