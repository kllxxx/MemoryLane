package com.kllxx.memorylane.feature.profile.impl.ui

import com.kllxx.memorylane.core.ui.component.SectionHeader
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

@Composable
internal fun ProfileContent(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    snackbarHostState: SnackbarHostState,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
) {
    // The body style adds letter spacing and a tall line height that the design does not have
    ProvideTextStyle(TextStyle(letterSpacing = 0.sp, lineHeight = 1.3.em)) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surface,
        // The window insets are already applied by the navigation host's scaffold
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Text(
                text = stringResource(R.string.profile_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when (state) {
                // Nothing but the title until the real values are there, so no defaults flash by
                ProfileState.Loading -> Box(Modifier.weight(1f))
                is ProfileState.Content -> Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
                ) {
                    SectionHeader(stringResource(R.string.profile_section_streak))
                    StreakGoalCard(state, onIntent)
                    SectionHeader(stringResource(R.string.profile_section_notifications))
                    NotificationsCard(state, onIntent)
                    SectionHeader(stringResource(R.string.profile_section_appearance))
                    ThemeCard(state, onIntent)
                    SectionHeader(stringResource(R.string.profile_section_about))
                    AboutCard(state, onIntent)
                }
            }
        }
    }
    }

    if (state is ProfileState.Content && state.isTimePickerVisible) {
        ReminderTimeDialog(
            initial = state.reminderTime,
            is24Hour = is24Hour,
            onConfirm = { onIntent(ProfileIntent.ConfirmTime(it)) },
            onDismiss = { onIntent(ProfileIntent.DismissTimePicker) },
        )
    }
}
