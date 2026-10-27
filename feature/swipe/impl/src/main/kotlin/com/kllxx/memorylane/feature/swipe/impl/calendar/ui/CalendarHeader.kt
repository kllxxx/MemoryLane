package com.kllxx.memorylane.feature.swipe.impl.calendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.theme.Spacing
import com.kllxx.memorylane.feature.swipe.impl.R

@Composable
internal fun CalendarHeader(currentStreak: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.calendar_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        StreakChip(currentStreak)
    }
}

// TODO: localize full UI
@Composable
internal fun StreakChip(days: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(primary.copy(alpha = 0.08f))
            .border(BorderStroke(1.dp, primary.copy(alpha = 0.25f)), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = stringResource(R.string.calendar_streak_icon),
            tint = primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = pluralStringResource(R.plurals.calendar_streak_days, days, days),
            color = primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
