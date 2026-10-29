package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerColors
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kllxx.memorylane.core.ui.theme.MemoryLaneShapes
import com.kllxx.memorylane.feature.profile.impl.R
import java.time.LocalTime

/**
 * The time of the daily reminder, typed in: the keyboard input of Material 3 only, no clock dial and no switch to one.
 * [is24Hour] follows the system setting; with it off the field has the AM/PM choice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReminderTimeDialog(
    initial: LocalTime,
    is24Hour: Boolean,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = is24Hour,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MemoryLaneShapes.Dialog,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = stringResource(R.string.profile_reminder_time_dialog_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                TimeInput(state = pickerState, colors = reminderTimeColors())
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.profile_time_picker_cancel))
                    }
                    TextButton(onClick = { onConfirm(LocalTime.of(pickerState.hour, pickerState.minute)) }) {
                        Text(stringResource(R.string.profile_time_picker_confirm))
                    }
                }
            }
        }
    }
}

/**
 * Every colour of the picker comes from the theme, so no default of Material (the lavender of its own scheme) is left:
 * the field being typed in is primaryContainer with primary text, the other one is the card colour with onSurface text.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun reminderTimeColors(): TimePickerColors {
    val colors = MaterialTheme.colorScheme
    return TimePickerDefaults.colors(
        containerColor = colors.surface,
        clockDialColor = colors.surfaceVariant,
        clockDialSelectedContentColor = colors.onPrimary,
        clockDialUnselectedContentColor = colors.onSurface,
        selectorColor = colors.primary,
        periodSelectorBorderColor = colors.outlineVariant,
        periodSelectorSelectedContainerColor = colors.primaryContainer,
        periodSelectorUnselectedContainerColor = colors.surface,
        periodSelectorSelectedContentColor = colors.primary,
        periodSelectorUnselectedContentColor = colors.onSurfaceVariant,
        timeSelectorSelectedContainerColor = colors.primaryContainer,
        timeSelectorUnselectedContainerColor = colors.surfaceVariant,
        timeSelectorSelectedContentColor = colors.primary,
        timeSelectorUnselectedContentColor = colors.onSurface,
    )
}
