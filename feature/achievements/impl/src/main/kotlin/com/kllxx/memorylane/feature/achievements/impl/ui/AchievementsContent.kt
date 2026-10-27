package com.kllxx.memorylane.feature.achievements.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.EmptyState
import com.kllxx.memorylane.core.ui.component.IconTile
import com.kllxx.memorylane.core.ui.component.PrimaryButton
import com.kllxx.memorylane.core.ui.component.RowDivider
import com.kllxx.memorylane.core.ui.component.RowValue
import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.SectionCard
import com.kllxx.memorylane.core.ui.component.SectionHeader
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.feature.achievements.impl.AchievementsIntent
import com.kllxx.memorylane.feature.achievements.impl.AchievementsState
import com.kllxx.memorylane.feature.achievements.impl.FormattedSize
import com.kllxx.memorylane.feature.achievements.impl.R
import com.kllxx.memorylane.feature.achievements.impl.SizeUnit

internal const val CURRENT_STREAK_TAG = "current-streak"
internal const val BEST_STREAK_TAG = "best-streak"
internal const val FREED_SPACE_TAG = "freed-space"
internal const val FREED_UNIT_TAG = "freed-unit"
internal const val VIEWED_TAG = "viewed-photos"
internal const val DELETED_TAG = "deleted-photos"
internal const val EMPTY_STATE_TAG = "achievements-empty"
internal const val GO_TO_CALENDAR_TAG = "go-to-calendar"

internal fun streakUnitTag(valueTag: String) = "$valueTag-unit"

private val CardGap = 12.dp
private val CardIconTileSize = 32.dp

@Composable
internal fun AchievementsContent(
    state: AchievementsState,
    onIntent: (AchievementsIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The body style adds letter spacing and a tall line height that the design does not have
    ProvideTextStyle(TextStyle(letterSpacing = 0.sp, lineHeight = 1.3.em)) {
        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            Text(
                text = stringResource(R.string.achievements_title),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            when (state) {
                AchievementsState.Loading -> Box(Modifier.weight(1f))
                AchievementsState.Empty -> AchievementsEmpty(onIntent, Modifier.weight(1f))
                is AchievementsState.Stats -> AchievementsStats(state, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun AchievementsStats(state: AchievementsState.Stats, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
    ) {
        SectionHeader(stringResource(R.string.achievements_section_streak))
        Row(horizontalArrangement = Arrangement.spacedBy(CardGap)) {
            StreakCard(
                icon = painterResource(UiR.drawable.ic_streak),
                label = stringResource(R.string.achievements_current),
                days = state.currentStreak,
                valueTag = CURRENT_STREAK_TAG,
                modifier = Modifier.weight(1f),
            )
            StreakCard(
                icon = painterResource(UiR.drawable.ic_best_streak),
                label = stringResource(R.string.achievements_best),
                days = state.bestStreak,
                valueTag = BEST_STREAK_TAG,
                modifier = Modifier.weight(1f),
            )
        }
        SectionHeader(stringResource(R.string.achievements_section_stats))
        FreedSpaceCard(state.freedSpace)
        Spacer(Modifier.height(CardGap))
        RowsCard {
            SettingsRow(
                icon = painterResource(UiR.drawable.ic_photos_viewed),
                title = stringResource(R.string.achievements_viewed),
                trailing = { RowValue(state.viewedPhotos, Modifier.testTag(VIEWED_TAG)) },
            )
            RowDivider()
            SettingsRow(
                icon = painterResource(UiR.drawable.ic_photos_deleted),
                title = stringResource(R.string.achievements_deleted),
                trailing = { RowValue(state.deletedPhotos, Modifier.testTag(DELETED_TAG)) },
            )
        }
    }
}

@Composable
private fun StreakCard(
    icon: Painter,
    label: String,
    days: Int,
    valueTag: String,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            CardHeader(icon, label)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = days.toString(),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.alignByBaseline().testTag(valueTag),
                )
                Text(
                    text = pluralStringResource(R.plurals.achievements_streak_unit, days),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.alignByBaseline().testTag(streakUnitTag(valueTag)),
                )
            }
        }
    }
}

@Composable
private fun FreedSpaceCard(size: FormattedSize, modifier: Modifier = Modifier) {
    SectionCard(modifier) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(CardGap)) {
            CardHeader(painterResource(UiR.drawable.ic_freed_space), stringResource(R.string.achievements_freed_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = size.value,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.alignByBaseline().testTag(FREED_SPACE_TAG),
                )
                Text(
                    text = stringResource(
                        when (size.unit) {
                            SizeUnit.MB -> R.string.achievements_unit_mb
                            SizeUnit.GB -> R.string.achievements_unit_gb
                        },
                    ),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.alignByBaseline().testTag(FREED_UNIT_TAG),
                )
            }
            Text(
                text = stringResource(R.string.achievements_freed_caption),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun CardHeader(icon: Painter, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        IconTile(icon, CardIconTileSize)
        Text(text = label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun AchievementsEmpty(onIntent: (AchievementsIntent) -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = painterResource(UiR.drawable.ic_best_streak),
        title = stringResource(R.string.achievements_empty_title),
        body = stringResource(R.string.achievements_empty_body),
        modifier = modifier.testTag(EMPTY_STATE_TAG),
        action = {
            PrimaryButton(
                text = stringResource(R.string.achievements_empty_button),
                onClick = { onIntent(AchievementsIntent.GoToCalendarClicked) },
                modifier = Modifier.testTag(GO_TO_CALENDAR_TAG),
            )
        },
    )
}
