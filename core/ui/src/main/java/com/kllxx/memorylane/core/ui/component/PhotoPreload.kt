package com.kllxx.memorylane.core.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size

/** A photo to load ahead of time, and the size of the view it will be shown in. */
data class PreloadPhoto(val uri: Uri, val size: IntSize)

/**
 * Warms the image cache with [photos], each decoded for a view of its own size the way [PhotoFit] decodes, so a card
 * that is about to appear finds its photo already loaded. A photo whose view size is not known yet is skipped.
 */
@Composable
fun PreloadPhotos(photos: List<PreloadPhoto>) {
    val known = photos.filter { it.size != IntSize.Zero }
    if (known.isEmpty()) return
    val context = LocalContext.current
    LaunchedEffect(known) {
        val loader = SingletonImageLoader.get(context)
        known.forEach { photo ->
            loader.enqueue(
                ImageRequest.Builder(context)
                    .data(photo.uri)
                    .size(Size(photo.size.width, photo.size.height))
                    .scale(Scale.FIT)
                    .build(),
            )
        }
    }
}
