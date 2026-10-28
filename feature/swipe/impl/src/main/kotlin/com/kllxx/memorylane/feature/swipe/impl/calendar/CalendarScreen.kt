package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.CalendarContent
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * [reselected] emits when the Calendar tab is tapped while Calendar is already shown: the list goes back to the current month.
 */
@Composable
fun CalendarScreen(
    onNavigateToSwipe: (LocalDate) -> Unit,
    reselected: Flow<Unit> = emptyFlow(),
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The banner of today is the first item of the list while it is shown, so the positions the ViewModel keeps are those
    // of a list without it, and are turned into positions of the list that is there now
    val bannerShown by rememberUpdatedState(state.todayBanner != null)

    // The list opens where the ViewModel last saw it, which is the current month the first time: the position is
    // never decided again when this screen is composed anew, so coming back does not jump. The list is created when
    // there is a list to show, so that the banner that is there then is counted; one that comes later is placed above
    // the first visible item by its key.
    val listReady = state.hasPermission && state.phase != CalendarPhase.Loading
    val listState = key(listReady) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = listIndexWithBanner(viewModel.scrollAnchor.index, bannerShown),
            initialFirstVisibleItemScrollOffset = viewModel.scrollAnchor.offset,
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.onScrolled(positionWithoutBanner(index, bannerShown), offset) }
    }

    LaunchedEffect(listState, reselected) {
        reselected.collect { listState.animateScrollToItem(listIndexWithBanner(viewModel.currentMonthIndex, bannerShown)) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onIntent(CalendarIntent.PermissionResult(granted)) }

    LaunchedEffect(Unit) {
        CalendarPerf.onRouteEntered()
        viewModel.onIntent(CalendarIntent.ScreenEntered)
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarEffect.NavigateToSwipe -> onNavigateToSwipe(effect.date)
                CalendarEffect.RequestPermissionLauncher -> permissionLauncher.launch(mediaReadPermission())
            }
        }
    }

    CalendarContent(state = state, listState = listState, onIntent = viewModel::onIntent)
}

private fun mediaReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
