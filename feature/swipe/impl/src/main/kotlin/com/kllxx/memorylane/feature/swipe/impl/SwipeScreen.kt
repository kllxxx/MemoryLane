package com.kllxx.memorylane.feature.swipe.impl

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeEffect
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeViewModel
import java.time.LocalDate

@Composable
fun SwipeScreen(
    onNavigateToReview: (date: LocalDate, totalPhotosCount: Int) -> Unit,
    onNavigateToCalendar: () -> Unit,
    viewModel: SwipeViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SwipeEffect.NavigateToReview -> onNavigateToReview(effect.date, effect.totalPhotosCount)
                SwipeEffect.NavigateToCalendar -> onNavigateToCalendar()
                SwipeEffect.PermissionRequired -> Unit
            }
        }
    }

    Text("Swipe")
}
