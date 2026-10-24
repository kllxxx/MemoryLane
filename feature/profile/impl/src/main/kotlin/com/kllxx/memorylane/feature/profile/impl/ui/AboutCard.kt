package com.kllxx.memorylane.feature.profile.impl.ui

import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.RowDivider
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.core.ui.component.RowValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal const val REPLAY_ONBOARDING_ROW_TAG = "replay-onboarding-row"
internal const val CONTACT_SUPPORT_ROW_TAG = "contact-support-row"

@Composable
internal fun AboutCard(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        SettingsRow(
            icon = Icons.Filled.Replay,
            title = stringResource(R.string.profile_replay_onboarding),
            onClick = { onIntent(ProfileIntent.ReplayOnboardingClicked) },
            modifier = Modifier.testTag(REPLAY_ONBOARDING_ROW_TAG),
            trailing = { Chevron() },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Email,
            title = stringResource(R.string.profile_help_title),
            subtitle = stringResource(R.string.profile_help_subtitle),
            onClick = { onIntent(ProfileIntent.ContactSupportClicked) },
            modifier = Modifier.testTag(CONTACT_SUPPORT_ROW_TAG),
            trailing = { Chevron() },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Info,
            title = stringResource(R.string.profile_version),
            trailing = { RowValue(state.versionName) },
        )
    }
}

@Composable
private fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
