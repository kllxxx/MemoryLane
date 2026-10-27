package com.kllxx.memorylane.core.ui.component

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult

/** Where a thumbnail request is: reported to the optional `onLoad` callback of [PhotoThumbnail]. */
enum class ThumbnailPhase { Started, Success, Error, Cancelled }

/**
 * One step of a thumbnail request, for measurements. [durationMs] is the time since the request started,
 * [source] is where the pixels came from (memory cache, disk, ...) and the bitmap size is what was decoded,
 * to compare with the size of the cell it was decoded for.
 */
data class ThumbnailLoad(
    val phase: ThumbnailPhase,
    val durationMs: Long = 0,
    val source: String? = null,
    val bitmapWidth: Int = 0,
    val bitmapHeight: Int = 0,
    val cellWidthPx: Int = 0,
    val cellHeightPx: Int = 0,
    val error: String? = null,
)

/**
 * Loads a photo from a MediaStore [uri], cropped to fill its bounds. [onLoad] only reports the progress of the
 * request (used for performance measurements); without it the image is loaded exactly the same way.
 */
@Composable
fun PhotoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onLoad: ((ThumbnailLoad) -> Unit)? = null,
) {
    if (onLoad == null) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }

    val context = LocalContext.current
    val cellSize = remember { IntArray(2) }
    val request = remember(uri, onLoad) {
        var startedAt = 0L
        fun elapsedMs() = (System.nanoTime() - startedAt) / NANOS_PER_MILLI
        ImageRequest.Builder(context)
            .data(uri)
            .listener(
                onStart = {
                    startedAt = System.nanoTime()
                    onLoad(ThumbnailLoad(ThumbnailPhase.Started))
                },
                onCancel = {
                    onLoad(ThumbnailLoad(ThumbnailPhase.Cancelled, durationMs = elapsedMs()))
                },
                onError = { _, result: ErrorResult ->
                    onLoad(
                        ThumbnailLoad(
                            ThumbnailPhase.Error,
                            durationMs = elapsedMs(),
                            cellWidthPx = cellSize[0],
                            cellHeightPx = cellSize[1],
                            error = result.throwable.toString(),
                        ),
                    )
                },
                onSuccess = { _, result: SuccessResult ->
                    onLoad(
                        ThumbnailLoad(
                            ThumbnailPhase.Success,
                            durationMs = elapsedMs(),
                            source = result.dataSource.name,
                            bitmapWidth = result.image.width,
                            bitmapHeight = result.image.height,
                            cellWidthPx = cellSize[0],
                            cellHeightPx = cellSize[1],
                        ),
                    )
                },
            )
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier.onSizeChanged {
            cellSize[0] = it.width
            cellSize[1] = it.height
        },
        contentScale = ContentScale.Crop,
    )
}

private const val NANOS_PER_MILLI = 1_000_000L
