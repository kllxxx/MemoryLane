package com.kllxx.memorylane.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.kllxx.memorylane.core.domain.model.ThemeMode

/**
 * @param themeMode dark by default. TODO(DataStore): the app/Profile layer should collect the
 * persisted [ThemeMode] from DataStore and pass it here; no storage is read in :core:ui.
 */
@Composable
fun MemoryLaneTheme(
    themeMode: ThemeMode = ThemeMode.Dark,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    CompositionLocalProvider(LocalSemanticColors provides DefaultSemanticColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = MemoryLaneTypography,
            shapes = MemoryLaneMaterialShapes,
            content = content,
        )
    }
}
