package com.kllxx.memorylane.feature.swipe.impl

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeEffect
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeIntentFactory
import com.kllxx.memorylane.feature.swipe.impl.swipe.SwipeViewModel
import com.kllxx.memorylane.feature.swipe.impl.swipe.ui.SwipeContent
import java.time.LocalDate

@Composable
fun SwipeScreen(
    onNavigateToReview: (date: LocalDate, totalPhotosCount: Int) -> Unit,
    onNavigateToCalendar: () -> Unit,
    viewModel: SwipeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SwipeEffect.NavigateToBeforeDelete -> onNavigateToReview(effect.date, effect.totalPhotosCount)
                // The result screen comes with the delete flow; until then Calendar is where the day ends
                is SwipeEffect.NavigateToResult -> onNavigateToCalendar()
                SwipeEffect.NavigateToCalendar -> onNavigateToCalendar()
                // Calendar asks for the permission, so the user is not left on an empty screen
                SwipeEffect.PermissionRequired -> onNavigateToCalendar()
                is SwipeEffect.ShareImage -> context.launchOrToast(
                    SwipeIntentFactory.share(effect.uri, context.getString(R.string.swipe_share_chooser)),
                )
                is SwipeEffect.OpenInGallery -> context.launchOrToast(SwipeIntentFactory.view(effect.uri))
            }
        }
    }

    SwipeContent(state = state, onIntent = viewModel::onIntent)
}

/** Starts [intent]; when no app can handle it the user gets a short message instead of a crash. */
private fun Context.launchOrToast(intent: Intent) {
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(this, R.string.swipe_no_app, Toast.LENGTH_SHORT).show()
    }
}
