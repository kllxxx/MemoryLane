package com.kllxx.memorylane.feature.swipe.impl.swipe.mapper

import android.net.Uri
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.feature.swipe.impl.swipe.DeckPhoto
import io.mockk.mockk
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StripMapperTest {

    private val uri = mockk<Uri>(relaxed = true)

    private fun photo(id: Long, takenAt: LocalDateTime, decision: PhotoDecision? = null) =
        DeckPhoto(Photo(id, uri, takenAt, sizeBytes = 1), decision, takenAtText = "photo $id")

    private val at = LocalDateTime.of(2016, 1, 7, 10, 0)

    @Test
    fun `deck and trashed photos are merged by capture time`() {
        val deck = listOf(photo(1, at), photo(3, at.plusYears(2)))
        val trashed = listOf(photo(2, at.plusYears(1), PhotoDecision.TRASHED))

        assertEquals(listOf(1L, 2L, 3L), buildStrip(deck, trashed).map { it.photoId })
    }

    @Test
    fun `the id breaks a tie in time`() {
        val deck = listOf(photo(9, at), photo(5, at))
        val trashed = listOf(photo(7, at, PhotoDecision.TRASHED))

        assertEquals(listOf(5L, 7L, 9L), buildStrip(deck, trashed).map { it.photoId })
    }

    @Test
    fun `a deck photo keeps its deck index and a trashed photo has none`() {
        val deck = listOf(photo(1, at), photo(3, at.plusYears(2)))
        val trashed = listOf(photo(2, at.plusYears(1), PhotoDecision.TRASHED))

        val strip = buildStrip(deck, trashed)

        assertEquals(listOf(0, null, 1), strip.map { it.deckIndex })
        assertTrue(strip[1].isTrashed)
        assertFalse(strip[0].isTrashed)
    }

    @Test
    fun `a trashed photo is a TRASHED decision whatever it was stored as`() {
        val strip = buildStrip(emptyList(), listOf(photo(2, at, PhotoDecision.DELETED)))

        assertEquals(PhotoDecision.TRASHED, strip.single().decision)
    }

    @Test
    fun `an empty day has an empty strip`() {
        assertTrue(buildStrip(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun `the strip position of a deck photo skips the trashed ones before it`() {
        val deck = listOf(photo(1, at.plusYears(1)), photo(2, at.plusYears(2)))
        val trashed = listOf(photo(3, at, PhotoDecision.TRASHED))
        val strip = buildStrip(deck, trashed)

        assertEquals(1, stripPositionOf(strip, 0))
        assertEquals(2, stripPositionOf(strip, 1))
        assertEquals(-1, stripPositionOf(strip, 5))
    }

    private fun item(decision: PhotoDecision?, deckIndex: Int? = 0) = StripItem(1, uri, decision, deckIndex)

    @Test
    fun `the dot is green for keep, red for every kind of deletion, and none when undecided`() {
        assertEquals(StripDot.Keep, item(PhotoDecision.KEEP).dot())
        assertEquals(StripDot.Delete, item(PhotoDecision.DELETE).dot())
        assertEquals(StripDot.Delete, item(PhotoDecision.TRASHED, deckIndex = null).dot())
        assertEquals(StripDot.Delete, item(PhotoDecision.DELETED).dot())
        assertEquals(StripDot.None, item(null).dot())
    }

    @Test
    fun `undecided photos are faded a little and trashed ones most`() {
        assertEquals(1f, item(PhotoDecision.KEEP).alpha(), 0f)
        assertEquals(1f, item(PhotoDecision.DELETE).alpha(), 0f)
        assertEquals(0.55f, item(null).alpha(), 0f)
        assertEquals(0.4f, item(PhotoDecision.TRASHED, deckIndex = null).alpha(), 0f)
    }

    @Test
    fun `a trashed photo is faded as trashed even though it has a decision`() {
        assertNull(item(PhotoDecision.TRASHED, deckIndex = null).deckIndex)
        assertEquals(0.4f, item(PhotoDecision.TRASHED, deckIndex = null).alpha(), 0f)
    }
}
