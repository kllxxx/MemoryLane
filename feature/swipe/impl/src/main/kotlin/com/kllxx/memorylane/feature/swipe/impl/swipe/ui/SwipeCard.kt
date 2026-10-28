package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.haroncode.lazycardstack.LazyCardStackState
import com.haroncode.lazycardstack.swiper.SwipeDirection
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.PhotoFit
import com.kllxx.memorylane.core.ui.theme.semanticColors
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.BEHIND_PEEK
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.BEHIND_SCALE
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.CardSize
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.LIBRARY_REST_SCALE
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.behindProgress
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.behindShift
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.behindSize
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.dragOverlay

// The card behind is a little see-through until it becomes the top card
private const val BEHIND_ALPHA = 0.9f

/**
 * One card of the stack. The library gives it the whole card area, and the card itself is centred in it.
 *
 * The top card has the shape of its photo, [own], and shows all of it with a soft shadow, the tint and the badge of the
 * side it is dragged to. The card behind is only a hint of the deck: at rest it is the size and shape of the top card
 * (from [topCard]) times [BEHIND_SCALE], a thin edge above it, with the photo cropped to that; as the top card is dragged away it
 * grows to [own] in proportion to the drag, so that when the swipe is done it already is the top card.
 *
 * Which of the two this card is, and how far along the drag is, are read in the layout and layer phases, from the
 * stack's own state: the composition lags a frame behind a swipe, an undo or a jump, and a card that is sized from a
 * stale [isTop] would flash at the wrong size. [isTop] only chooses how the photo is fitted and whether the drag overlay
 * is drawn, which cannot show at the wrong moment.
 */
@Composable
internal fun SwipeCard(
    item: DeckPhoto,
    index: Int,
    isTop: Boolean,
    stack: LazyCardStackState,
    own: CardSize,
    topCard: () -> CardSize,
    onImageSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SwipeLayout.CardRadius)
    val density = LocalDensity.current
    var widthPx by remember { mutableFloatStateOf(0f) }
    // The photo is decoded for the card's own size, whatever size the card has now: the same request as the preload and
    // as the card on top, so nothing is requested again while the card grows or when it becomes the top card
    val decodeSize = with(density) { IntSize(own.width.dp.roundToPx(), own.height.dp.roundToPx()) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { if (stack.visibleItemIndex != index) placeBehind(stack.scale, topCard()) },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .layout { measurable, constraints ->
                    val size = if (stack.visibleItemIndex == index) {
                        own
                    } else {
                        behindSize(topCard(), own, behindProgress(stack.scale))
                    }
                    val width = size.width.dp.roundToPx().coerceIn(0, constraints.maxWidth)
                    val height = size.height.dp.roundToPx().coerceIn(0, constraints.maxHeight)
                    val placeable = measurable.measure(Constraints.fixed(width, height))
                    layout(width, height) { placeable.place(0, 0) }
                }
                .testTag(SwipeTags.cardPhoto(item.id))
                .shadow(elevation = 8.dp, shape = shape)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .onSizeChanged { widthPx = it.width.toFloat() },
        ) {
            PhotoFit(
                uri = item.photo.uri,
                modifier = Modifier.fillMaxSize(),
                onImageSize = onImageSize,
                // The top card shows the whole photo; the card behind is a hint and is cropped to the shape it has
                contentScale = if (isTop) ContentScale.Fit else ContentScale.Crop,
                decodeSize = decodeSize,
            )
            if (isTop) DragOverlayLayer(stack = stack, cardWidthPx = { widthPx })
        }
    }
}

/**
 * Puts the card behind where the design wants it, instead of the library's own scaling, which goes from
 * [LIBRARY_REST_SCALE] to 1 around the centre and would hide the card completely. The size of the card is its layout
 * size, so the scale the library has applied to this layer is only divided out. At rest the card is [BEHIND_PEEK] above
 * the top card; the shift goes to 0 and the card to full opacity as the card takes the place of the top card.
 * [BEHIND_ALPHA] is how see-through it is until then.
 */
private fun GraphicsLayerScope.placeBehind(libraryScale: Float, top: CardSize) {
    val progress = behindProgress(libraryScale)
    val library = libraryScale.coerceAtLeast(LIBRARY_REST_SCALE)
    scaleX = 1f / library
    scaleY = 1f / library
    translationY = -behindShift(top, progress).dp.toPx() / library
    alpha = lerp(BEHIND_ALPHA, 1f, progress)
}

/**
 * The tint and the badge of the top card, which follow the drag without recomposing on every pixel. They are
 * decoration for the eyes: a screen reader gets the card's «Удалить» and «Оставить» actions instead, so the badge text
 * is kept out of the accessibility tree.
 */
@Composable
private fun DragOverlayLayer(stack: LazyCardStackState, cardWidthPx: () -> Float) {
    val direction by remember(stack) {
        derivedStateOf { dragOverlay(stack.offset.x, cardWidthPx()).direction }
    }
    val colors = MaterialTheme.semanticColors
    val deleting = direction == SwipeDirection.Left
    val tint = if (deleting) colors.delete else colors.keep

    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = dragOverlay(stack.offset.x, cardWidthPx()).tintAlpha }
            .background(tint),
    )
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clearAndSetSemantics { }
            .graphicsLayer { alpha = dragOverlay(stack.offset.x, cardWidthPx()).badgeAlpha },
        contentAlignment = if (deleting) Alignment.TopEnd else Alignment.TopStart,
    ) {
        if (direction != null) {
            DecisionBadge(
                text = stringResource(if (deleting) R.string.swipe_badge_delete else R.string.swipe_badge_keep),
                iconRes = if (deleting) UiR.drawable.ic_delete else UiR.drawable.ic_check,
                background = tint,
                content = if (deleting) colors.onDelete else colors.onKeep,
            )
        }
    }
}

@Composable
private fun DecisionBadge(
    text: String,
    iconRes: Int,
    background: Color,
    content: Color,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = content,
        )
        Text(text = text, color = content, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    }
}
