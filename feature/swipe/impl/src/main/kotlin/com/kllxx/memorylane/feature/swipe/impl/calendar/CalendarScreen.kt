package com.kllxx.memorylane.feature.swipe.impl.calendar

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.calendar.ui.CalendarContent
import java.time.LocalDate

@Composable
fun CalendarScreen(
    onNavigateToSwipe: (LocalDate) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onIntent(CalendarIntent.PermissionResult(granted)) }

    LaunchedEffect(Unit) { CalendarPerf.onRouteEntered() }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CalendarEffect.NavigateToSwipe -> onNavigateToSwipe(effect.date)
                CalendarEffect.RequestPermissionLauncher -> permissionLauncher.launch(mediaReadPermission())
            }
        }
    }

    CalendarContent(state = state, onIntent = viewModel::onIntent)
}

private fun mediaReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
