package com.kllxx.memorylane.core.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.size.Scale
import coil3.size.Size
import com.kllxx.memorylane.core.common.PerfLog

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
 * Loads a photo from a MediaStore [uri], cropped to fill its bounds.
 *
 * With [decodeToCellSize] the photo is decoded for the measured size of this view, with the scale that
 * [ContentScale.Crop] needs (the image covers the view), instead of a size derived from the layout constraints.
 * [onLoad] only reports the progress of the request (performance measurements).
 */
@Composable
fun PhotoThumbnail(
    uri: Uri,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    decodeToCellSize: Boolean = false,
    onLoad: ((ThumbnailLoad) -> Unit)? = null,
) {
    if (!decodeToCellSize && onLoad == null) {
        AsyncImage(
            model = uri,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = ContentScale.Crop,
        )
        return
    }

    val context = LocalContext.current
    var cell by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.onSizeChanged { cell = it }) {
        // The cell has no size before its first layout pass; the request waits for it
        if (decodeToCellSize && cell == IntSize.Zero) return@Box
        val request = remember(uri, cell, onLoad, decodeToCellSize) {
            var startedAt = 0L
            val builder = ImageRequest.Builder(context).data(uri)
            if (decodeToCellSize) builder.size(Size(cell.width, cell.height)).scale(Scale.FILL)
            if (onLoad != null) {
                builder.listener(
                    onStart = {
                        startedAt = PerfLog.nanoTime()
                        onLoad(ThumbnailLoad(ThumbnailPhase.Started))
                    },
                    onCancel = {
                        onLoad(ThumbnailLoad(ThumbnailPhase.Cancelled, durationMs = PerfLog.sinceMs(startedAt)))
                    },
                    onError = { _, result: ErrorResult ->
                        onLoad(
                            ThumbnailLoad(
                                ThumbnailPhase.Error,
                                durationMs = PerfLog.sinceMs(startedAt),
                                cellWidthPx = cell.width,
                                cellHeightPx = cell.height,
                                error = result.throwable.toString(),
                            ),
                        )
                    },
                    onSuccess = { _, result: SuccessResult ->
                        onLoad(
                            ThumbnailLoad(
                                ThumbnailPhase.Success,
                                durationMs = PerfLog.sinceMs(startedAt),
                                source = result.dataSource.name,
                                bitmapWidth = result.image.width,
                                bitmapHeight = result.image.height,
                                cellWidthPx = cell.width,
                                cellHeightPx = cell.height,
                            ),
                        )
                    },
                )
            }
            builder.build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
    }
}
