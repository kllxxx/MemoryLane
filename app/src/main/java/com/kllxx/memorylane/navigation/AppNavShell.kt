package com.kllxx.memorylane.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost

/** Height of the Material3 navigation bar without the system navigation bar inset below it. */
private val NavigationBarContainerHeight = 80.dp

/**
 * The NavHost with the bottom bar laid over it. The NavHost always fills the whole screen and no
 * padding depends on the current destination: each screen applies its own insets, so the screen that is
 * fading out keeps its layout while the next destination takes over.
 */
@Composable
internal fun AppNavShell(
    navController: NavHostController,
    startDestination: Any,
    showBottomBar: Boolean,
    bottomBar: @Composable () -> Unit,
    builder: NavGraphBuilder.() -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize(),
            builder = builder,
        )
        AnimatedVisibility(
            visible = showBottomBar,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            bottomBar()
        }
    }
}

/** Insets of a tab screen: the system bars, with room at the bottom for the bar laid over the screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun Modifier.tabScreenInsets(): Modifier {
    val bottom: Dp = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + NavigationBarContainerHeight
    return windowInsetsPadding(
        ScaffoldDefaults.contentWindowInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
    ).padding(bottom = bottom)
}

/** Insets of a full screen without the bottom bar. */
@Composable
internal fun Modifier.fullScreenInsets(): Modifier = windowInsetsPadding(ScaffoldDefaults.contentWindowInsets)
