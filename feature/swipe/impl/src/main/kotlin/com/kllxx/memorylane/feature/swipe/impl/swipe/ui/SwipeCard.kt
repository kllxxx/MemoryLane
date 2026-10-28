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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
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
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.BEHIND_SCALE
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.behindCenterShift
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.dragOverlay

// The library scales the card behind from this size up to full size as the top card is dragged away
private const val LIBRARY_REST_SCALE = 0.8f

// The card behind is a little see-through until it becomes the top card
private const val BEHIND_ALPHA = 0.9f

/**
 * One card of the stack. The library gives it the whole card area; the card itself has the photo's own shape,
 * [cardWidth] by [cardHeight], is centred in the area and shows the whole photo with a soft shadow. The top card also
 * shows the tint and the badge of the side it is dragged to; the card behind peeks out below it.
 */
@Composable
internal fun SwipeCard(
    item: DeckPhoto,
    isTop: Boolean,
    stack: LazyCardStackState,
    cardWidth: Dp,
    cardHeight: Dp,
    onImageSize: (IntSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(SwipeLayout.CardRadius)
    var widthPx by remember { mutableFloatStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isTop) {
                    Modifier
                } else {
                    Modifier.graphicsLayer { placeBehind(stack.scale, behindCenterShift(cardHeight.value)) }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = cardWidth, height = cardHeight)
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
            )
            if (isTop) DragOverlayLayer(stack = stack, cardWidthPx = { widthPx })
        }
    }
}

/**
 * Gives the card behind the look of the design instead of the library's own scaling, which goes from
 * [LIBRARY_REST_SCALE] to 1 around the centre and would hide the card completely: [BEHIND_SCALE] at rest (1 once the
 * top card is gone), with its top edge a little below the top card's, and a little see-through. The library has
 * already scaled and moved this layer by its own factor, so that factor is divided out to land on screen as wanted.
 * [centerShiftDp] is how far the centre moves down at rest, in dp.
 */
private fun GraphicsLayerScope.placeBehind(libraryScale: Float, centerShiftDp: Float) {
    val progress = ((libraryScale - LIBRARY_REST_SCALE) / (1f - LIBRARY_REST_SCALE)).coerceIn(0f, 1f)
    val library = libraryScale.coerceAtLeast(LIBRARY_REST_SCALE)
    val wanted = lerp(BEHIND_SCALE, 1f, progress)
    scaleX = wanted / library
    scaleY = wanted / library
    translationY = lerp(centerShiftDp.dp.toPx(), 0f, progress) / library
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
