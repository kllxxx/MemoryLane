package com.kllxx.memorylane.feature.swipe.impl.result

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.result.ui.ResultContent

/** «Итоги». The day is over, so Back leaves like «Готово» does: to Calendar, not back into the deck. */
@Composable
fun ResultScreen(
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ResultViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ResultEffect.NavigateToCalendar -> onNavigateToCalendar()
            }
        }
    }
    BackHandler { viewModel.onIntent(ResultIntent.Done) }

    ResultContent(state = state, onIntent = viewModel::onIntent, modifier = modifier)
}
