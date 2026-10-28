package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

/** The shape a card has until its photo's real one is known: a portrait photo. */
const val PROVISIONAL_ASPECT_RATIO = 3f / 4f

/**
 * The card behind is only a hint of the deck: at rest it has the size and shape of the top card, [BEHIND_SCALE] of it,
 * and its top edge is [BEHIND_PEEK] (dp) above the top edge of the top card, so a thin edge shows above it.
 */
const val BEHIND_SCALE = 0.94f
const val BEHIND_PEEK = 10f

/** The library scales the card behind from this to 1 as the top card is dragged away. */
const val LIBRARY_REST_SCALE = 0.8f

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
 * How far the top card is dragged, from 0 (at rest) to 1 (far enough to be swiped), as the library reports it with the
 * scale of the card behind, which it takes from [LIBRARY_REST_SCALE] to 1.
 */
fun behindProgress(libraryScale: Float): Float =
    ((libraryScale - LIBRARY_REST_SCALE) / (1f - LIBRARY_REST_SCALE)).coerceIn(0f, 1f)

/**
 * The size of the card behind at drag [progress]: at rest the [top] card's size times [BEHIND_SCALE], so it never
 * exceeds the top card, and at 1 its [own] size; in between each side is half way along, so the card grows steadily.
 */
fun behindSize(top: CardSize, own: CardSize, progress: Float): CardSize = CardSize(
    width = lerp(top.width * BEHIND_SCALE, own.width, progress),
    height = lerp(top.height * BEHIND_SCALE, own.height, progress),
)

/**
 * How far the centre of the card behind sits above the centre of the top card at drag [progress]. At rest the card is
 * [BEHIND_SCALE] of the top card, so its top edge is already half of what it shrank below the top edge of the top card;
 * the rest brings it to [BEHIND_PEEK] above. It goes to 0 as the card takes the place of the top card.
 */
fun behindShift(top: CardSize, progress: Float): Float =
    lerp(BEHIND_PEEK + (1f - BEHIND_SCALE) / 2f * top.height, 0f, progress)

private fun lerp(start: Float, stop: Float, fraction: Float) = start + (stop - start) * fraction.coerceIn(0f, 1f)
