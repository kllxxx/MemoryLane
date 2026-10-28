package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import com.haroncode.lazycardstack.swiper.SwipeDirection
import kotlin.math.abs

/** The tint over the card never gets stronger than this. */
const val MAX_TINT_ALPHA = 0.22f

/** How far along a horizontal drag has to go, as a share of the card width, to count as a swipe. */
private const val SWIPE_THRESHOLD = 0.4f

/**
 * What the top card shows while it is dragged: [direction] is the side it is dragged to (null when it is at rest),
 * [tintAlpha] grows with the drag up to [MAX_TINT_ALPHA], and [badgeAlpha] goes from 0 to 1 over the same distance.
 */
data class DragOverlay(val direction: SwipeDirection?, val tintAlpha: Float, val badgeAlpha: Float) {
    companion object {
        val None = DragOverlay(null, 0f, 0f)
    }
}

fun dragOverlay(offsetX: Float, cardWidthPx: Float): DragOverlay {
    if (offsetX == 0f || cardWidthPx <= 0f) return DragOverlay.None
    val progress = (abs(offsetX) / (cardWidthPx * SWIPE_THRESHOLD)).coerceIn(0f, 1f)
    return DragOverlay(
        direction = if (offsetX < 0f) SwipeDirection.Left else SwipeDirection.Right,
        tintAlpha = progress * MAX_TINT_ALPHA,
        badgeAlpha = progress,
    )
}
