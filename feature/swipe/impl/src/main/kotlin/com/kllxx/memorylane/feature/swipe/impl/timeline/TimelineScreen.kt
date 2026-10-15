package com.kllxx.memorylane.feature.swipe.impl.timeline

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import java.time.LocalDate

@Composable
fun TimelineScreen(
    onNavigateToSwipe: (LocalDate) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TimelineEffect.NavigateToSwipe -> onNavigateToSwipe(effect.date)
                else -> Unit
            }
        }
    }

    Text("Timeline")
}
