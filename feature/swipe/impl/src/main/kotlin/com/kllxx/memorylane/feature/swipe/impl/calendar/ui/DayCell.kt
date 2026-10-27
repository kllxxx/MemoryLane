package com.kllxx.memorylane.feature.swipe.impl.calendar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.component.PhotoThumbnail
import com.kllxx.memorylane.core.ui.component.ThumbnailLoad
import com.kllxx.memorylane.core.ui.theme.EmptyTile
import com.kllxx.memorylane.core.ui.theme.Emerald
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.calendar.CalendarPerf
import com.kllxx.memorylane.feature.swipe.impl.calendar.DayItem
import com.kllxx.memorylane.feature.swipe.impl.calendar.DayLoadState
import java.time.LocalDate

private val TileShape = RoundedCornerShape(10.dp)
private const val TILE_ASPECT = 1f / 1.1f

internal fun dayTestTag(item: DayItem) = "day-${item.date}"
internal fun sortedTestTag(item: DayItem) = "sorted-${item.date}"

@Composable
internal fun DayCell(
    item: DayItem,
    isToday: Boolean,
    isFuture: Boolean,
    isSorted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loadState = item.loadState
    val photo = loadState as? DayLoadState.Loaded
    val hasPhoto = !isFuture && photo != null
    val primary = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier.testTag(dayTestTag(item)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(TILE_ASPECT)
                .then(if (isFuture) Modifier.alpha(0.4f) else Modifier)
                .clip(TileShape)
                .then(if (hasPhoto) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            when {
                photo != null && hasPhoto -> PhotoTile(photo, item.date)
                isFuture || loadState is DayLoadState.NotLoaded ->
                    Box(Modifier.fillMaxSize().background(emptyTileColor().copy(alpha = 0.6f)))
                else -> EmptyTileContent()
            }
            if (isToday) {
                Box(Modifier.fillMaxSize().border(BorderStroke(2.dp, primary), TileShape))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = item.date.dayOfMonth.toString(),
                color = if (isToday) primary else MaterialTheme.colorScheme.onSurface.copy(alpha = if (isFuture) 0.4f else 0.8f),
                fontSize = 11.sp,
                fontWeight = if (isToday) FontWeight.ExtraBold else FontWeight.SemiBold,
            )
            if (isSorted) {
                Box(
                    Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(primary)
                        .testTag(sortedTestTag(item)),
                )
            }
        }
    }
}

@Composable
private fun PhotoTile(loaded: DayLoadState.Loaded, date: LocalDate) {
    val description = stringResource(R.string.calendar_photo_count, loaded.totalCount)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Emerald.copy(alpha = 0.6f), Emerald)))
            .semantics { contentDescription = description },
    ) {
        // Remembered: PhotoThumbnail rebuilds its request when the callback changes
        val onLoad = remember(loaded.firstPhoto.id, date) { { load: ThumbnailLoad -> CalendarPerf.onThumbnail(date, load) } }
        PhotoThumbnail(uri = loaded.firstPhoto.uri, modifier = Modifier.fillMaxSize(), onLoad = onLoad)
        Text(
            text = loaded.totalCount.toString(),
            color = Color.White,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )
    }
}

@Composable
private fun EmptyTileContent() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(emptyTileColor()),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = stringResource(R.string.calendar_empty_day),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** The light-theme grey from the design; the dark scheme uses its own surface variant. */
@Composable
private fun emptyTileColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) EmptyTile else MaterialTheme.colorScheme.surfaceVariant
