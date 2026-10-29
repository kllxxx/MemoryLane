package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal const val BUTTONS_MODE_ROW_TAG = "buttons-mode-row"
internal const val BUTTONS_MODE_SWITCH_TAG = "buttons-mode-switch"

/** «Разбор фото»: the ✕ and ✓ buttons on the Swipe screen, the same setting as its «Кнопки» quick action. */
@Composable
internal fun ReviewCard(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        SettingsRow(
            icon = painterResource(UiR.drawable.ic_touch_app),
            title = stringResource(R.string.profile_buttons_mode_title),
            subtitle = stringResource(R.string.profile_buttons_mode_subtitle),
            // The whole row is the switch for TalkBack and for touch; the Switch inside only shows the state
            modifier = Modifier
                .toggleable(
                    value = state.swipeButtonsEnabled,
                    role = Role.Switch,
                    onValueChange = { onIntent(ProfileIntent.SwipeButtonsToggled(it)) },
                )
                .testTag(BUTTONS_MODE_ROW_TAG),
            trailing = {
                Switch(
                    checked = state.swipeButtonsEnabled,
                    onCheckedChange = null,
                    colors = settingsSwitchColors(),
                    modifier = Modifier.testTag(BUTTONS_MODE_SWITCH_TAG),
                )
            },
        )
    }
}
