package com.kllxx.memorylane.feature.swipe.impl.timeline

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.timeline.ui.TimelineContent
import java.time.LocalDate

@Composable
fun TimelineScreen(
    onNavigateToSwipe: (LocalDate) -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onIntent(TimelineIntent.PermissionResult(granted)) }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is TimelineEffect.NavigateToSwipe -> onNavigateToSwipe(effect.date)
                TimelineEffect.RequestPermissionLauncher -> permissionLauncher.launch(mediaReadPermission())
            }
        }
    }

    TimelineContent(state = state, onIntent = viewModel::onIntent)
}

private fun mediaReadPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
