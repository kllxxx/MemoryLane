package com.kllxx.memorylane.feature.achievements.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.achievements.impl.ui.AchievementsContent

@Composable
fun AchievementsScreen(
    onNavigateToTimeline: () -> Unit,
    viewModel: AchievementsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigateToTimeline by rememberUpdatedState(onNavigateToTimeline)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AchievementsEffect.NavigateToTimeline -> currentOnNavigateToTimeline()
            }
        }
    }

    AchievementsContent(state = state, onIntent = viewModel::onIntent)
}
