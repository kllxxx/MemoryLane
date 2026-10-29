package com.kllxx.memorylane.feature.swipe.impl.result.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.theme.semanticColors
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.common.ReviewButton
import com.kllxx.memorylane.feature.swipe.impl.common.labelRes
import com.kllxx.memorylane.feature.swipe.impl.result.ResultIntent
import com.kllxx.memorylane.feature.swipe.impl.result.ResultState
import com.kllxx.memorylane.core.domain.format.formatDayMonth
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)

/** «Итоги» for a [ResultState]; «Готово» goes out as an intent. */
@Composable
fun ResultContent(
    state: ResultState,
    onIntent: (ResultIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(78.dp))
            Celebration()
            Spacer(Modifier.height(22.dp))
            Text(
                text = stringResource(if (state.nothingDeleted) R.string.result_overline_sorted else R.string.result_overline_freed),
                modifier = Modifier.testTag(ResultTags.OVERLINE),
                color = colors.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.04.sp,
            )
            FreedValue(state)
            Caption(state)
            Spacer(Modifier.height(26.dp))
            Stats(state)
            state.streakDays?.let { days ->
                Spacer(Modifier.height(24.dp))
                StreakChip(days)
            }
            Spacer(Modifier.height(16.dp))
        }
        ReviewButton(
            text = stringResource(R.string.result_done),
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            onClick = { onIntent(ResultIntent.Done) },
            modifier = Modifier.padding(16.dp).testTag(ResultTags.DONE),
        )
    }
}

/** The 104dp circle with the celebration icon, inside the static confetti. */
@Composable
private fun Celebration() {
    Box(modifier = Modifier.size(ConfettiWidth, ConfettiHeight), contentAlignment = Alignment.Center) {
        Confetti()
        Box(
            modifier = Modifier.size(104.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_celebration),
                contentDescription = stringResource(R.string.result_celebration),
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** The freed space as a number and a unit in separate texts, or «Все фото оставлены» when nothing was deleted. */
@Composable
private fun FreedValue(state: ResultState) {
    val primary = MaterialTheme.colorScheme.primary
    if (state.nothingDeleted) {
        Text(
            text = stringResource(R.string.result_all_kept),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp).testTag(ResultTags.VALUE),
            color = primary,
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
    } else {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 2.dp)) {
            Text(
                text = state.freed.value,
                modifier = Modifier.testTag(ResultTags.VALUE),
                color = primary,
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(state.freed.unit.labelRes()),
                modifier = Modifier.padding(bottom = 8.dp).testTag(ResultTags.UNIT),
                color = primary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

/** «28 октября · 48 фото»: the day and how many photos it had. */
@Composable
private fun Caption(state: ResultState) {
    val dayMonth = remember(state.date) { formatDayMonth(state.date, Locale.getDefault()) }
    Text(
        text = stringResource(
            R.string.result_caption,
            dayMonth,
            pluralStringResource(R.plurals.result_caption_photos, state.totalCount, state.totalCount),
        ),
        modifier = Modifier.padding(top = 2.dp).testTag(ResultTags.CAPTION),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun Stats(state: ResultState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (!state.nothingDeleted) {
            StatCard(
                iconRes = UiR.drawable.ic_delete,
                iconTint = MaterialTheme.colorScheme.error,
                count = state.deletedCount,
                label = stringResource(R.string.result_deleted_label),
                tag = ResultTags.DELETED_CARD,
                modifier = Modifier.weight(1f),
            )
        }
        StatCard(
            iconRes = UiR.drawable.ic_check,
            iconTint = MaterialTheme.semanticColors.keep,
            count = state.keptCount,
            label = stringResource(R.string.result_kept_label),
            tag = ResultTags.KEPT_CARD,
            // Alone, the card keeps the width it has next to another one and sits in the middle
            modifier = if (state.nothingDeleted) Modifier.width(156.dp) else Modifier.weight(1f),
        )
    }
}

/** A 104dp card with an icon, a count and what it counts. */
@Composable
private fun StatCard(
    iconRes: Int,
    iconTint: Color,
    count: Int,
    label: String,
    tag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .height(104.dp)
            .testTag(tag)
            .clip(CardShape)
            .background(colors.surfaceVariant)
            .border(1.dp, colors.outlineVariant, CardShape)
            .padding(start = 15.dp, top = 15.dp, end = 15.dp),
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, modifier = Modifier.size(20.dp), tint = iconTint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = count.toString(),
            color = colors.onSurface,
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = label,
            color = colors.onSurfaceVariant,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun StreakChip(days: Int) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .testTag(ResultTags.STREAK)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.primaryContainer)
            .padding(start = 14.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painter = painterResource(UiR.drawable.ic_local_fire_department),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = colors.primary,
        )
        Text(
            text = pluralStringResource(R.plurals.result_streak, days, days),
            color = colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
