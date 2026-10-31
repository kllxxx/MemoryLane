package com.kllxx.memorylane.core.ui.component

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList

/**
 * [PhotoThumbnail] with an image loader that serves a small bitmap, or fails. The component has no placeholder and no
 * error picture of its own; what it offers is the [ThumbnailLoad] report, which is what these tests read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PhotoThumbnailTest {

    @get:Rule
    val rule = createComposeRule()

    private val uri = Uri.parse("content://media/external/images/media/7")
    private val loads = CopyOnWriteArrayList<ThumbnailLoad>()

    @After
    fun tearDown() = SingletonImageLoader.reset()

    private fun useLoader(fail: Boolean) {
        SingletonImageLoader.setUnsafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(
                        Fetcher.Factory<coil3.Uri> { _, _, _ ->
                            object : Fetcher {
                                override suspend fun fetch(): FetchResult {
                                    if (fail) throw IOException("no file behind the photo")
                                    return ImageFetchResult(
                                        image = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).asImage(),
                                        isSampled = false,
                                        dataSource = DataSource.DISK,
                                    )
                                }
                            }
                        },
                    )
                }
                .build()
        }
    }

    private fun show(decodeToCellSize: Boolean = true) {
        rule.setContent {
            MemoryLaneTheme {
                PhotoThumbnail(
                    uri = uri,
                    modifier = Modifier.size(100.dp),
                    contentDescription = "photo",
                    decodeToCellSize = decodeToCellSize,
                    onLoad = { loads += it },
                )
            }
        }
    }

    private fun phases() = loads.map { it.phase }

    @Test
    fun `a photo that loads is reported as started and then as a success with its size`() {
        useLoader(fail = false)

        show()
        rule.waitUntil(timeoutMillis = 10_000) { ThumbnailPhase.Success in phases() }

        assertEquals(ThumbnailPhase.Started, phases().first())
        val success = loads.first { it.phase == ThumbnailPhase.Success }
        assertEquals(8, success.bitmapWidth)
        assertEquals(8, success.bitmapHeight)
        assertTrue(ThumbnailPhase.Error !in phases())
    }

    @Test
    fun `a photo that cannot be read is reported as an error with its cause`() {
        useLoader(fail = true)

        show()
        rule.waitUntil(timeoutMillis = 10_000) { ThumbnailPhase.Error in phases() }

        val error = loads.first { it.phase == ThumbnailPhase.Error }
        assertNotNull(error.error)
        assertTrue(ThumbnailPhase.Success !in phases())
    }

    @Test
    fun `the plain thumbnail without a report renders and loads the photo`() {
        useLoader(fail = false)

        rule.setContent {
            MemoryLaneTheme { PhotoThumbnail(uri = uri, modifier = Modifier.size(100.dp), contentDescription = "photo") }
        }
        rule.waitForIdle()

        assertTrue(loads.isEmpty())
    }
}
