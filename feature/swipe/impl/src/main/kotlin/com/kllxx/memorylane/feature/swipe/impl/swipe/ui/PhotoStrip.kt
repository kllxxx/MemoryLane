package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.ui.component.PhotoThumbnail
import com.kllxx.memorylane.core.ui.theme.semanticColors
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.StripDot
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.StripItem
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.alpha
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.dot
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.stripPositionOf

private val Thumb = 28.dp
private val CurrentThumb = 34.dp
private val DotSize = 5.dp
private val DotGap = 4.dp
private val ThumbShape = RoundedCornerShape(7.dp)

/** The height of the strip: 6dp above the largest thumbnail, the thumbnail, the gap, the dot and 3dp below. */
internal val StripHeight = 6.dp + CurrentThumb + DotGap + DotSize + 3.dp

/**
 * Every photo of the day, deck and trashed ones, oldest first, with the photo on the card kept in the centre.
 * A tap on a photo of the deck sends [onJump] with its deck index; a trashed photo only gets highlighted.
 */
@Composable
internal fun PhotoStrip(
    strip: List<StripItem>,
    currentDeckIndex: Int,
    onJump: (deckIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    var highlighted by remember { mutableStateOf<Long?>(null) }
    val currentPosition = stripPositionOf(strip, currentDeckIndex)

    // The first position is set without animating, so the strip does not scroll in from the start
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(currentPosition) {
        highlighted = null
        if (currentPosition < 0) return@LaunchedEffect
        if (first) listState.scrollToItem(currentPosition) else listState.animateScrollToItem(currentPosition)
        first = false
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().height(StripHeight)) {
        // Half the width on both sides lets the first and the last photo reach the centre too
        val sidePadding = (maxWidth / 2 - CurrentThumb / 2).coerceAtLeast(0.dp)
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag(SwipeTags.STRIP),
            contentPadding = PaddingValues(start = sidePadding, end = sidePadding, top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Top,
        ) {
            itemsIndexed(strip, key = { _, item -> item.photoId }) { position, item ->
                StripThumbnail(
                    item = item,
                    number = position + 1,
                    total = strip.size,
                    isCurrent = position == currentPosition,
                    isHighlighted = item.photoId == highlighted,
                    onClick = {
                        val deckIndex = item.deckIndex
                        if (deckIndex != null) onJump(deckIndex) else highlighted = item.photoId
                    },
                )
            }
        }
    }
}

@Composable
private fun StripThumbnail(
    item: StripItem,
    number: Int,
    total: Int,
    isCurrent: Boolean,
    isHighlighted: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val size: Dp = if (isCurrent) CurrentThumb else Thumb
    val stateText = stringResource(
        when (item.decision) {
            PhotoDecision.KEEP -> R.string.swipe_state_keep
            PhotoDecision.DELETE -> R.string.swipe_state_delete
            PhotoDecision.TRASHED, PhotoDecision.DELETED -> R.string.swipe_state_trashed
            null -> R.string.swipe_state_undecided
        },
    )
    val description = stringResource(R.string.swipe_strip_item_description, number, total, stateText)
    Column(
        modifier = Modifier
            .testTag(SwipeTags.stripItem(item.photoId))
            .semantics { contentDescription = description; selected = isCurrent }
            .clip(ThumbShape)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Every thumbnail sits in a row of the height of the largest one, so the dots line up
        Box(modifier = Modifier.height(CurrentThumb), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(size)
                    // The photo on the card is shown in full; the others are faded by what is known about them
                    .alpha(if (isCurrent) 1f else item.alpha())
                    .then(
                        when {
                            isCurrent -> Modifier.border(BorderStroke(2.dp, colors.primary), ThumbShape).padding(2.dp)
                            isHighlighted -> Modifier.border(BorderStroke(2.dp, colors.onSurfaceVariant), ThumbShape)
                            else -> Modifier
                        },
                    )
                    .clip(ThumbShape)
                    .background(colors.surfaceVariant),
            ) {
                PhotoThumbnail(uri = item.uri, modifier = Modifier.fillMaxSize(), decodeToCellSize = true)
            }
        }
        Spacer(Modifier.height(DotGap))
        Dot(item.dot())
    }
}

@Composable
private fun Dot(dot: StripDot) {
    val color = when (dot) {
        StripDot.Keep -> MaterialTheme.semanticColors.keep
        StripDot.Delete -> MaterialTheme.semanticColors.delete
        StripDot.None -> null
    }
    Box(
        Modifier
            .size(DotSize)
            .then(if (color != null) Modifier.background(color, CircleShape) else Modifier),
    )
}
