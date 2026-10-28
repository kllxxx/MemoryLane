package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import androidx.compose.ui.unit.IntSize
import com.kllxx.memorylane.core.photos.domain.model.Photo

/**
 * The width over the height of a photo as it is shown, from the size and orientation MediaStore stores. A file that
 * is turned by 90 or 270 degrees has its sides swapped. Null when MediaStore does not know the size, which is when
 * the card falls back to the size of the decoded image.
 */
fun displayAspectRatio(width: Int, height: Int, orientation: Int): Float? {
    if (width <= 0 || height <= 0) return null
    val degrees = ((orientation % 360) + 360) % 360
    val turned = degrees == 90 || degrees == 270
    return if (turned) height.toFloat() / width else width.toFloat() / height
}

fun Photo.displayAspectRatio(): Float? = displayAspectRatio(width, height, orientation)

/** The aspect ratio of a decoded image, or null when it has no size yet. */
fun IntSize.aspectRatioOrNull(): Float? =
    if (width <= 0 || height <= 0) null else width.toFloat() / height
