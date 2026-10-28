package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

/** How far the small labels of the bottom panel grow with the user's font size: they have a fixed column to fit in. */
const val MAX_LABEL_FONT_SCALE = 1.3f

/**
 * The size in sp to give a text whose base size is [baseSp] so that, once the system multiplies it by [fontScale], it
 * is shown at the font scale but no more than [maxScale] times the base size. At a font scale up to [maxScale] this is
 * [baseSp] itself, so the text follows the user's setting; above it the text stops growing.
 */
fun limitedFontSp(baseSp: Float, fontScale: Float, maxScale: Float = MAX_LABEL_FONT_SCALE): Float {
    if (fontScale <= 0f) return baseSp
    return baseSp * minOf(fontScale, maxScale) / fontScale
}
