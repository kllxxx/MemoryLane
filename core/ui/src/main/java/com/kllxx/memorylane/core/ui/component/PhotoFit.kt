package com.kllxx.memorylane.core.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.size.Scale
import coil3.size.Size

/**
 * Loads a photo from a MediaStore [uri] and shows all of it, never cropped. The photo is decoded to fit the measured
 * size of this view, with the scale that [ContentScale.Fit] needs (the whole image inside the view). Give the view the
 * photo's own aspect ratio and it is filled exactly.
 *
 * [onImageSize] reports the size of the decoded image once it is loaded, which is how a caller that does not know the
 * photo's size from elsewhere finds its aspect ratio.
 */
@Composable
fun PhotoFit(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onImageSize: ((IntSize) -> Unit)? = null,
) {
    val context = LocalContext.current
    val latestOnImageSize by rememberUpdatedState(onImageSize)
    var cell by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { cell = it }) {
        // The view has no size before its first layout pass; the request waits for it
        if (cell == IntSize.Zero) return@Box
        val request = remember(uri, cell) {
            ImageRequest.Builder(context)
                .data(uri)
                .size(Size(cell.width, cell.height))
                .scale(Scale.FIT)
                .listener(onSuccess = { _, result ->
                    latestOnImageSize?.invoke(IntSize(result.image.width, result.image.height))
                })
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
    }
}
