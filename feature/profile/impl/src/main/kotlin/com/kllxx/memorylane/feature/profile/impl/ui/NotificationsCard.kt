package com.kllxx.memorylane.feature.profile.impl.ui

import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.RowDivider
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.core.ui.component.RowValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R
import java.time.format.DateTimeFormatter

internal const val NOTIFICATIONS_SWITCH_TAG = "notifications-switch"
internal const val REMINDER_TIME_ROW_TAG = "reminder-time-row"

private val TimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
internal fun NotificationsCard(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        SettingsRow(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.profile_notifications_title),
            subtitle = stringResource(
                if (state.notificationsOn) R.string.profile_notifications_on else R.string.profile_notifications_off,
            ),
            trailing = {
                Switch(
                    checked = state.notificationsOn,
                    onCheckedChange = { onIntent(ProfileIntent.NotificationsToggled(it)) },
                    colors = SwitchDefaults.colors(
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
                    modifier = Modifier.testTag(NOTIFICATIONS_SWITCH_TAG),
                )
            },
        )
        RowDivider()
        SettingsRow(
            icon = Icons.Filled.Schedule,
            title = stringResource(R.string.profile_reminder_time),
            enabled = state.notificationsOn,
            onClick = { onIntent(ProfileIntent.OpenTimePicker) },
            modifier = Modifier.testTag(REMINDER_TIME_ROW_TAG),
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    RowValue(state.reminderTime.format(TimeFormat))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}
