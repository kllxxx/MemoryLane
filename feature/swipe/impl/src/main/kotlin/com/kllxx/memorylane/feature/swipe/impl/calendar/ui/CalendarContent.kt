package com.kllxx.memorylane.feature.swipe.impl.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.EmptyState
import com.kllxx.memorylane.core.ui.component.PrimaryButton
import com.kllxx.memorylane.core.ui.theme.Spacing
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarError
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarIntent
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarMonth
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarPerf
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarPhase
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarState
import com.kllxx.memorylane.feature.swipe.impl.calendar.DayItem
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

private const val WEEKDAY_HEADER_KEY = "weekdays"
private const val MONTH_KEY_PREFIX = "month-"
private const val ROW_KEY_PREFIX = "row-"

internal const val WEEKDAY_HEADER_TAG = "calendar-weekdays"
internal const val EMPTY_STATE_TAG = "calendar-empty"

@Composable
internal fun CalendarContent(
    state: CalendarState,
    listState: LazyListState,
    onIntent: (CalendarIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        CalendarHeader(currentStreak = state.currentStreak)
        when {
            !state.hasPermission -> PermissionPrompt(onRequest = { onIntent(CalendarIntent.RequestPermission) })
            // The photos are not known yet: the top bar only, no spinner
            state.phase == CalendarPhase.Loading -> Unit
            state.phase == CalendarPhase.Empty -> CalendarEmpty(Modifier.weight(1f))
            else -> {
                if (state.error != null) {
                    ErrorBanner(state.error, onRetry = { onIntent(CalendarIntent.LoadDays) })
                }
                MonthList(
                    months = state.months,
                    today = state.today,
                    sortedDates = state.sortedDates,
                    listState = listState,
                    onIntent = onIntent,
                )
            }
        }
    }
}

@Composable
private fun CalendarEmpty(modifier: Modifier = Modifier) {
    EmptyState(
        icon = painterResource(UiR.drawable.ic_nav_calendar_outlined),
        title = stringResource(R.string.calendar_empty_title),
        body = stringResource(R.string.calendar_empty_body),
        modifier = modifier.testTag(EMPTY_STATE_TAG),
    )
}

@Composable
private fun MonthList(
    months: ImmutableList<CalendarMonth>,
    today: LocalDate,
    sortedDates: ImmutableSet<LocalDate>,
    listState: LazyListState,
    onIntent: (CalendarIntent) -> Unit,
) {
    val locale = currentLocale()
    val weekdays = remember(locale) {
        DayOfWeek.entries.map { it.getDisplayName(TextStyle.SHORT, locale).uppercase(locale) }
    }
    val onOpenDay = remember(onIntent) { { date: LocalDate -> onIntent(CalendarIntent.OpenDay(date)) } }

    LogCalendarPerf()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
    ) {
        stickyHeader(key = WEEKDAY_HEADER_KEY) { WeekdayHeader(weekdays) }
        months.forEach { month ->
            item(key = MONTH_KEY_PREFIX + month.yearMonth) { MonthSeparator(month.yearMonth, locale) }
            items(items = month.rows, key = { row -> rowKey(row) }) { row ->
                DayRow(row = row, today = today, sortedDates = sortedDates, onOpenDay = onOpenDay)
            }
        }
    }
}

@Composable
private fun WeekdayHeader(weekdays: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.CalendarHorizontal, vertical = 8.dp)
            .testTag(WEEKDAY_HEADER_TAG),
        horizontalArrangement = Arrangement.spacedBy(Spacing.CalendarGap),
    ) {
        weekdays.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun MonthSeparator(yearMonth: YearMonth, locale: Locale) {
    val name = yearMonth.month.getDisplayName(TextStyle.FULL_STANDALONE, locale).uppercase(locale)
    val line = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.CalendarHorizontal)
            .padding(top = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = line)
        Text(
            text = "$name ${yearMonth.year}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = line)
    }
}

@Composable
private fun DayRow(
    row: ImmutableList<DayItem?>,
    today: LocalDate,
    sortedDates: ImmutableSet<LocalDate>,
    onOpenDay: (LocalDate) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.CalendarHorizontal)
            .padding(bottom = Spacing.CalendarGap),
        horizontalArrangement = Arrangement.spacedBy(Spacing.CalendarGap),
    ) {
        row.forEach { item ->
            if (item == null) {
                Spacer(Modifier.weight(1f))
            } else {
                DayCell(
                    item = item,
                    isToday = item.date == today,
                    isFuture = item.date.isAfter(today),
                    isSorted = item.date in sortedDates,
                    onClick = { onOpenDay(item.date) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun PermissionPrompt(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.ScreenHorizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(R.string.calendar_permission_text),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        PrimaryButton(text = stringResource(R.string.calendar_permission_button), onClick = onRequest)
    }
}

@Composable
private fun ErrorBanner(error: CalendarError, onRetry: () -> Unit) {
    // LoadFailed is the only error for now
    check(error == CalendarError.LoadFailed)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.ScreenHorizontal),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.calendar_error_text),
            color = MaterialTheme.colorScheme.error,
            fontSize = 13.sp,
        )
        TextButton(onClick = onRetry) { Text(stringResource(R.string.calendar_error_retry)) }
    }
}

/** Measurements only: when the content was first drawn and when its thumbnails have all loaded. */
@Composable
private fun LogCalendarPerf() {
    LaunchedEffect(Unit) {
        // The next frame starts after the one that committed the content, so by then the content has been drawn
        withFrameNanos { }
        CalendarPerf.onFirstContentFrame()
    }
    LaunchedEffect(Unit) { CalendarPerf.logSummaryWhenThumbnailsLoaded() }
}

private fun rowKey(row: List<DayItem?>): String = ROW_KEY_PREFIX + row.first { it != null }!!.date

@Composable
private fun currentLocale(): Locale = LocalConfiguration.current.locales[0]
