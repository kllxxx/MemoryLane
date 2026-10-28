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
                is SwipeEffect.NavigateToBeforeDelete -> onNavigateToReview(effect.date, effect.totalPhotosCount)
                // The result screen comes with the delete flow; until then Calendar is where the day ends
                is SwipeEffect.NavigateToResult -> onNavigateToCalendar()
                SwipeEffect.NavigateToCalendar -> onNavigateToCalendar()
                SwipeEffect.PermissionRequired -> Unit
                // The screen that builds the intents comes with the Swipe UI
                is SwipeEffect.ShareImage, is SwipeEffect.OpenInGallery -> Unit
            }
        }
    }

    Text("Swipe")
}
