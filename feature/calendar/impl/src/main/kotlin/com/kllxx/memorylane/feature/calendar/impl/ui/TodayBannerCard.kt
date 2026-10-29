package com.kllxx.memorylane.feature.calendar.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.theme.Spacing
import com.kllxx.memorylane.feature.calendar.impl.R
import com.kllxx.memorylane.core.domain.format.formatDayMonth
import java.time.LocalDate

internal const val TODAY_BANNER_TAG = "calendar-today-banner"
internal const val TODAY_BANNER_COUNT_TAG = "calendar-today-banner-count"

internal const val UNDATED_BANNER_TAG = "calendar-undated-banner"
internal const val UNDATED_BANNER_COUNT_TAG = "calendar-undated-banner-count"

private val BannerShape = RoundedCornerShape(16.dp)

/**
 * The banner of today: «28 ОКТЯБРЯ», «Сегодняшние фото» and how many photos there are. Pinned between the app bar and
 * the list; the item is 92dp high, the card 72dp with 8dp above it and 12dp below, as in the design.
 */
@Composable
internal fun TodayBannerCard(
    date: LocalDate,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val overline = remember(date, locale) { formatDayMonth(date, locale).uppercase(locale) }
    CalendarBannerCard(
        overline = overline,
        title = stringResource(R.string.calendar_today_title),
        countText = pluralStringResource(R.plurals.calendar_today_photos, count, count),
        onClick = onClick,
        modifier = modifier,
        tag = TODAY_BANNER_TAG,
        countTag = TODAY_BANNER_COUNT_TAG,
    )
}

/** The banner of the photos without a date, the last item of the calendar list: «БЕЗ ДАТЫ», «Фото без даты», «3 фото». */
@Composable
internal fun UndatedBannerCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CalendarBannerCard(
        overline = stringResource(R.string.calendar_undated_overline),
        title = stringResource(R.string.calendar_undated_title),
        countText = pluralStringResource(R.plurals.calendar_today_photos, count, count),
        onClick = onClick,
        modifier = modifier,
        tag = UNDATED_BANNER_TAG,
        countTag = UNDATED_BANNER_COUNT_TAG,
    )
}

/** The one card of both banners: an overline and a title on the left, the count and a chevron on the right. */
@Composable
internal fun CalendarBannerCard(
    overline: String,
    title: String,
    countText: String,
    onClick: () -> Unit,
    tag: String,
    countTag: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.CalendarHorizontal)
            .padding(top = 8.dp, bottom = 12.dp)
            .height(72.dp)
            .testTag(tag)
            .clip(BannerShape)
            .background(colors.surface)
            .border(1.5.dp, colors.primary, BannerShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.5.dp, end = 13.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = overline,
                color = colors.onSurfaceVariant,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.88.sp,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = title,
                color = colors.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Text(
            text = countText,
            modifier = Modifier.testTag(countTag),
            color = colors.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            painter = painterResource(UiR.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = colors.primary,
        )
    }
}
