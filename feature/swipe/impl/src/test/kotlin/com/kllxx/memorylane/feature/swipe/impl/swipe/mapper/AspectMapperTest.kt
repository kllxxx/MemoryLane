package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import android.net.Uri
import androidx.compose.ui.unit.IntSize
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AspectMapperTest {

    @Test
    fun `a landscape file with no orientation is wide`() {
        assertEquals(4f / 3f, displayAspectRatio(width = 4_000, height = 3_000, orientation = 0)!!, 0.0001f)
    }

    @Test
    fun `a portrait file with no orientation is tall`() {
        assertEquals(3f / 4f, displayAspectRatio(width = 3_000, height = 4_000, orientation = 0)!!, 0.0001f)
    }

    @Test
    fun `a file turned by 90 degrees has its sides swapped`() {
        // The phone was held upright: the file is landscape, the photo is portrait
        assertEquals(3f / 4f, displayAspectRatio(width = 4_000, height = 3_000, orientation = 90)!!, 0.0001f)
    }

    @Test
    fun `a file turned by 270 degrees has its sides swapped`() {
        assertEquals(3f / 4f, displayAspectRatio(width = 4_000, height = 3_000, orientation = 270)!!, 0.0001f)
    }

    @Test
    fun `a file turned by 180 degrees keeps its shape`() {
        assertEquals(4f / 3f, displayAspectRatio(width = 4_000, height = 3_000, orientation = 180)!!, 0.0001f)
    }

    @Test
    fun `an orientation outside 0 to 359 is read modulo a full turn`() {
        assertEquals(3f / 4f, displayAspectRatio(4_000, 3_000, orientation = 450)!!, 0.0001f)
        assertEquals(3f / 4f, displayAspectRatio(4_000, 3_000, orientation = -90)!!, 0.0001f)
    }

    @Test
    fun `an orientation that is not a right angle changes nothing`() {
        assertEquals(4f / 3f, displayAspectRatio(4_000, 3_000, orientation = 45)!!, 0.0001f)
    }

    @Test
    fun `a square photo is square whatever its orientation`() {
        assertEquals(1f, displayAspectRatio(1_000, 1_000, orientation = 90)!!, 0f)
    }

    @Test
    fun `an unknown size is null`() {
        assertNull(displayAspectRatio(width = 0, height = 3_000, orientation = 0))
        assertNull(displayAspectRatio(width = 4_000, height = 0, orientation = 0))
        assertNull(displayAspectRatio(width = 0, height = 0, orientation = 90))
    }

    @Test
    fun `a negative size is null`() {
        assertNull(displayAspectRatio(width = -1, height = 3_000, orientation = 0))
    }

    @Test
    fun `a photo gives its own aspect ratio`() {
        val photo = Photo(
            1, mockk<Uri>(relaxed = true), LocalDateTime.of(2016, 1, 7, 21, 15), 100,
            width = 4_000, height = 3_000, orientation = 90,
        )

        assertEquals(3f / 4f, photo.displayAspectRatio()!!, 0.0001f)
    }

    @Test
    fun `a decoded image gives its aspect ratio`() {
        assertEquals(1.5f, IntSize(1_500, 1_000).aspectRatioOrNull()!!, 0.0001f)
    }

    @Test
    fun `an image with no size yet has no aspect ratio`() {
        assertNull(IntSize.Zero.aspectRatioOrNull())
        assertNull(IntSize(100, 0).aspectRatioOrNull())
    }
}
