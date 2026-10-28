package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

/** The shape a card has until its photo's real one is known: a portrait photo. */
const val PROVISIONAL_ASPECT_RATIO = 3f / 4f

/** The card behind is this much smaller than the top card, and its top edge is [BEHIND_TOP_OFFSET] lower. */
const val BEHIND_SCALE = 0.94f
const val BEHIND_TOP_OFFSET = 14f

/** The size of a card, in whatever unit the area it was fitted into was in. */
data class CardSize(val width: Float, val height: Float) {
    companion object {
        val Zero = CardSize(0f, 0f)
    }
}

/**
 * The largest card with [aspectRatio] (width over height) that fits inside an area: a portrait photo gives a tall
 * card, a landscape photo a wide, short one, and a photo is never cropped. A card of no size when there is no room or
 * the ratio is not usable.
 */
fun fitInside(areaWidth: Float, areaHeight: Float, aspectRatio: Float): CardSize {
    if (areaWidth <= 0f || areaHeight <= 0f || aspectRatio.isNaN() || aspectRatio <= 0f) return CardSize.Zero
    val width = minOf(areaWidth, areaHeight * aspectRatio)
    return CardSize(width = width, height = width / aspectRatio)
}

/**
 * How far the centre of the card behind sits below the centre of the top card. The card behind is scaled around its
 * centre, which moves its top edge down by half of what it shrank; the design wants that top edge
 * [BEHIND_TOP_OFFSET] below the top edge of the top card, so only the rest is added.
 */
fun behindCenterShift(cardHeight: Float): Float = BEHIND_TOP_OFFSET - (1f - BEHIND_SCALE) / 2f * cardHeight
