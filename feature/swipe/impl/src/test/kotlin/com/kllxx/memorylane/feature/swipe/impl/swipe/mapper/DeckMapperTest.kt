package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeckMapperTest {

    private val photo = Photo(
        id = 7,
        uri = mockk<Uri>(relaxed = true),
        takenAt = LocalDateTime.of(2016, 1, 7, 21, 15),
        sizeBytes = 2_048,
        dateModified = 123,
        location = "Светлогорск, Россия",
    )

    @Test
    fun `a photo becomes a deck photo with its decision and the formatted time`() {
        val deckPhoto = photo.toDeckPhoto(PhotoDecision.KEEP, Locale.forLanguageTag("ru-RU"))

        assertEquals(photo, deckPhoto.photo)
        assertEquals(7L, deckPhoto.id)
        assertEquals(PhotoDecision.KEEP, deckPhoto.decision)
        assertEquals("7 января 2016, 21:15", deckPhoto.takenAtText)
    }

    @Test
    fun `an undecided photo has no decision`() {
        assertNull(photo.toDeckPhoto(null, Locale.US).decision)
    }

    @Test
    fun `the deck photo has the aspect ratio of its photo with the orientation applied`() {
        val turned = photo.copy(width = 4_000, height = 3_000, orientation = 90)

        assertEquals(3f / 4f, turned.toDeckPhoto(null, Locale.US).aspectRatio!!, 0.0001f)
    }

    @Test
    fun `a photo of unknown size has no aspect ratio`() {
        assertNull(photo.toDeckPhoto(null, Locale.US).aspectRatio)
    }

    @Test
    fun `the place of the photo is kept`() {
        assertEquals("Светлогорск, Россия", photo.toDeckPhoto(null, Locale.US).photo.location)
    }
}
