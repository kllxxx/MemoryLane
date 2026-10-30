package com.kllxx.memorylane

import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.navigation.StartDestination

sealed interface AppEvent {
    /** The stored theme and the start destination are known: the first frame can be drawn. */
    data class Resolved(val themeMode: ThemeMode, val startDestination: StartDestination) : AppEvent

    data class ThemeChanged(val themeMode: ThemeMode) : AppEvent
}
