package com.kllxx.memorylane.feature.profile.impl.ui

import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.SettingsRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

private val SegmentedShape = RoundedCornerShape(14.dp)
private val SegmentShape = RoundedCornerShape(10.dp)

internal fun themeSegmentTag(mode: ThemeMode) = "theme-segment-${mode.name}"

@Composable
internal fun ThemeCard(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        Column {
            SettingsRow(
                icon = Icons.Filled.Palette,
                title = stringResource(R.string.profile_theme_title),
                subtitle = stringResource(R.string.profile_theme_subtitle),
            )
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(SegmentedShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SegmentedShape)
                    .padding(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    ThemeSegment(
                        label = stringResource(mode.labelRes()),
                        selected = mode == state.themeMode,
                        onClick = { onIntent(ProfileIntent.SelectTheme(mode)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(themeSegmentTag(mode)),
                    )
                }
            }
        }
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.profile_theme_system
    ThemeMode.Light -> R.string.profile_theme_light
    ThemeMode.Dark -> R.string.profile_theme_dark
}

@Composable
private fun ThemeSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(SegmentShape)
            .background(if (selected) colors.primary else colors.surface)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) colors.onPrimary else colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
