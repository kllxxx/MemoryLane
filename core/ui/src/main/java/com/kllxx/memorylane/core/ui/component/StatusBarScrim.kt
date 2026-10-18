package com.kllxx.memorylane.core.ui.component

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Paints the status bar area with [color] and uses light status bar icons while shown.
 * The screen has to lay itself out edge to edge, without the status bar inset applied above it.
 */
@Composable
fun StatusBarScrim(color: Color, lightIcons: Boolean = true) {
    Spacer(
        Modifier
            .fillMaxWidth()
            .windowInsetsTopHeight(WindowInsets.statusBars)
            .background(color),
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        DisposableEffect(view, lightIcons) {
            val window = view.context.findActivity()?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val previous = controller?.isAppearanceLightStatusBars
            // Light status bar = dark icons, so white icons on a dark scrim means false
            controller?.isAppearanceLightStatusBars = !lightIcons
            onDispose {
                if (previous != null) controller.isAppearanceLightStatusBars = previous
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
