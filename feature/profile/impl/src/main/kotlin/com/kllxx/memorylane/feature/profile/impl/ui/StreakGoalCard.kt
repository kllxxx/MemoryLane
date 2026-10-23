package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal fun goalChipTag(goal: StreakGoal) = "goal-chip-${goal.days}"

@Composable
internal fun StreakGoalCard(
    state: ProfileState,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.streakProgress
    ProfileCard(modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_streak_current),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = state.currentStreak.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = pluralStringResource(R.plurals.profile_streak_of_goal, state.goal.days, state.goal.days),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .semantics { progressBarRangeInfo = ProgressBarRangeInfo(progress.progress, 0f..1f) },
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.progress)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            Text(
                text = if (progress.goalReached) {
                    stringResource(R.string.profile_streak_goal_reached)
                } else {
                    pluralStringResource(R.plurals.profile_streak_days_left, progress.daysLeft, progress.daysLeft)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.profile_goal_label),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline(),
                )
                Text(
                    text = stringResource(R.string.profile_goal_unit),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.alignByBaseline(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreakGoal.entries.forEach { goal ->
                    GoalChip(
                        goal = goal,
                        selected = goal == state.goal,
                        onClick = { onIntent(ProfileIntent.SelectGoal(goal)) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun GoalChip(
    goal: StreakGoal,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.surface)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.dp, colors.outlineVariant), CircleShape))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .testTag(goalChipTag(goal)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = goal.days.toString(),
            color = if (selected) colors.onPrimary else colors.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
