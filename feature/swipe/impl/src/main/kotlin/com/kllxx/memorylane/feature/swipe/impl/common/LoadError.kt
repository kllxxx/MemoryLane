package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.ui.component.PrimaryButton
import com.kllxx.memorylane.feature.swipe.impl.R

internal const val LOAD_ERROR_TAG = "load-error"
internal const val LOAD_ERROR_RETRY_TAG = "load-error-retry"

/** A screen that could not load: what went wrong and a way to try again. Leaving is the screen's own close button. */
@Composable
internal fun LoadError(error: UiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp).testTag(LOAD_ERROR_TAG),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.message),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(
            text = stringResource(R.string.load_error_retry),
            onClick = onRetry,
            modifier = Modifier.testTag(LOAD_ERROR_RETRY_TAG),
        )
    }
}
