package com.kllxx.memorylane.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The colours of a decision. They mean the same thing in every theme, so unlike the roles of the colour scheme they
 * are the same in light and dark: `colorScheme.secondary` is a grey-green in the dark theme, which does not read as
 * "keep". [onKeep] and [onDelete] are for text and icons on top of them.
 */
@Immutable
class SemanticColors(
    val keep: Color,
    val onKeep: Color,
    val delete: Color,
    val onDelete: Color,
)

/** Keep is the brand emerald and delete is the error red of the colour scheme, in light and dark alike. */
internal val DefaultSemanticColors = SemanticColors(
    keep = Emerald,
    onKeep = BabyPowder,
    delete = RedCrayola,
    onDelete = BabyPowder,
)

internal val LocalSemanticColors = staticCompositionLocalOf { DefaultSemanticColors }

/** The colours of a keep and a delete decision; see [SemanticColors]. */
val MaterialTheme.semanticColors: SemanticColors
    @Composable
    @ReadOnlyComposable
    get() = LocalSemanticColors.current
