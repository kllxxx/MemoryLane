package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.Manifest
import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.ui.BeforeDeleteContent
import com.kllxx.memorylane.core.domain.model.DeckKey

/**
 * «Перед удалением». The screen owns the launchers: their results are kept by the activity, so an answer of the
 * system dialog that arrives after a rotation or after the process died reaches this screen again and is passed on.
 */
@Composable
fun BeforeDeleteScreen(
    onBack: () -> Unit,
    onNavigateToResult: (deck: DeckKey, deletedCount: Int, freedBytes: Long, keptCount: Int, totalCount: Int) -> Unit,
    onNavigateToCalendar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BeforeDeleteViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val trashLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        viewModel.onIntent(BeforeDeleteIntent.SystemDeleteResult(confirmed = result.resultCode == Activity.RESULT_OK))
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onIntent(BeforeDeleteIntent.WritePermissionResult(granted))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is BeforeDeleteEffect.LaunchTrashRequest ->
                    trashLauncher.launch(IntentSenderRequest.Builder(effect.intentSender).build())
                BeforeDeleteEffect.RequestWritePermission ->
                    permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                is BeforeDeleteEffect.NavigateToResult ->
                    onNavigateToResult(effect.deck, effect.deletedCount, effect.freedBytes, effect.keptCount, effect.totalCount)
                BeforeDeleteEffect.NavigateToCalendar -> onNavigateToCalendar()
                BeforeDeleteEffect.ShowDeleteError ->
                    Toast.makeText(context, R.string.before_delete_error, Toast.LENGTH_SHORT).show()
                BeforeDeleteEffect.ShowPermissionDenied ->
                    Toast.makeText(context, R.string.before_delete_permission_denied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    BeforeDeleteContent(state = state, onIntent = viewModel::onIntent, onBack = onBack, modifier = modifier)
}
