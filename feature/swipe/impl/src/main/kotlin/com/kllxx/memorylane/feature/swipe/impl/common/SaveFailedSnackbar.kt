package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.feature.swipe.impl.R

/**
 * Lays the message that a decision could not be saved over [content]. It is shown when [saveFailed] turns true, and
 * [onShown] clears the flag, so a later failure in the same visit is not announced again (the ViewModel sends it once).
 */
@Composable
fun SaveFailedSnackbar(
    saveFailed: Boolean,
    onShown: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val hostState = remember { SnackbarHostState() }
    val message = stringResource(R.string.swipe_save_failed)
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            // Clearing the flag restarts this effect, so it goes once the message is gone
            hostState.showSnackbar(message)
            onShown()
        }
    }
    Box(modifier.fillMaxSize()) {
        content()
        SnackbarHost(
            hostState = hostState,
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        )
    }
}
