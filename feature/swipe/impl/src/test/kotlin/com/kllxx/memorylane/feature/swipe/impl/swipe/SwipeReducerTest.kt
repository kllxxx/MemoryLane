package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.net.Uri
import com.kllxx.memorylane.core.photos.domain.model.Photo
import io.mockk.mockk
import java.time.LocalDate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeReducerTest {

    private val date = LocalDate.of(2026, 10, 15)
    private val uri = mockk<Uri>(relaxed = true)

    private fun photo(id: Long) = Photo(id, uri, LocalDate.of(2024, 10, 15), sizeBytes = id * 100)

    private fun loaded(count: Int) = SwipeState(
        photos = (1..count).map { photo(it.toLong()) }.toImmutableList(),
        date = date,
    )

    private fun reduce(state: SwipeState, intent: SwipeIntent) = SwipeReducer.reduce(state, intent)

    private fun reduce(state: SwipeState, event: SwipeEvent) = SwipeReducer.reduce(state, event)

    @Test
    fun `PhotosLoaded stores photos and stops loading`() {
        val photos = listOf(photo(1), photo(2))
        val result = reduce(SwipeState(date = date, isLoading = true), SwipeEvent.PhotosLoaded(photos))
        assertEquals(photos, result.photos)
        assertEquals(0, result.currentIndex)
        assertFalse(result.isLoading)
        assertFalse(result.isFinished)
    }

    @Test
    fun `PhotosLoaded resumes after the restored index`() {
        val photos = listOf(photo(1), photo(2), photo(3))
        val result = reduce(
            SwipeState(date = date, isLoading = true),
            SwipeEvent.PhotosLoaded(photos, restoredIndex = 1),
        )
        assertEquals(1, result.currentIndex)
    }

    @Test
    fun `a restored index still skips photos marked for deletion`() {
        val photos = listOf(photo(1), photo(2), photo(3))
        val result = reduce(
            SwipeState(date = date, isLoading = true),
            SwipeEvent.PhotosLoaded(photos, pendingDeleteIds = listOf(2L), restoredIndex = 1),
        )
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `a restored index past the end finishes and a negative one is clamped`() {
        val photos = listOf(photo(1), photo(2))
        val past = reduce(SwipeState(date = date, isLoading = true), SwipeEvent.PhotosLoaded(photos, restoredIndex = 9))
        assertTrue(past.isFinished)
        val negative = reduce(SwipeState(date = date, isLoading = true), SwipeEvent.PhotosLoaded(photos, restoredIndex = -4))
        assertEquals(0, negative.currentIndex)
    }

    @Test
    fun `PhotosLoaded with no photos finishes immediately`() {
        val result = reduce(SwipeState(date = date, isLoading = true), SwipeEvent.PhotosLoaded(emptyList()))
        assertTrue(result.isFinished)
    }

    @Test
    fun `PhotosLoaded resumes at the first photo that is not pending`() {
        val photos = listOf(photo(1), photo(2), photo(3))
        val result = reduce(
            SwipeState(date = date, isLoading = true),
            SwipeEvent.PhotosLoaded(photos, pendingDeleteIds = listOf(1L, 2L)),
        )
        assertEquals(2, result.currentIndex)
        assertEquals(listOf(1L, 2L), result.pendingDeletes)
        assertFalse(result.isFinished)
    }

    @Test
    fun `PhotosLoaded ignores pending ids of photos that no longer exist`() {
        val result = reduce(
            SwipeState(date = date, isLoading = true),
            SwipeEvent.PhotosLoaded(listOf(photo(1), photo(2)), pendingDeleteIds = listOf(2L, 99L)),
        )
        assertEquals(listOf(2L), result.pendingDeletes)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `PhotosLoaded with every photo pending finishes`() {
        val result = reduce(
            SwipeState(date = date, isLoading = true),
            SwipeEvent.PhotosLoaded(listOf(photo(1), photo(2)), pendingDeleteIds = listOf(1L, 2L)),
        )
        assertTrue(result.isFinished)
        assertEquals(2, result.currentIndex)
        assertEquals(listOf(1L, 2L), result.pendingDeletes)
    }

    @Test
    fun `swiping skips photos that are already pending`() {
        val state = loaded(4).copy(currentIndex = 0, pendingDeletes = persistentListOf(2L, 3L))
        val result = reduce(state, SwipeIntent.SwipeRight)
        assertEquals(3, result.currentIndex)
        assertFalse(result.isFinished)
    }

    @Test
    fun `swiping the last unmarked photo finishes even when later photos are pending`() {
        val state = loaded(3).copy(currentIndex = 0, pendingDeletes = persistentListOf(2L, 3L))
        val result = reduce(state, SwipeIntent.SwipeRight)
        assertEquals(3, result.currentIndex)
        assertTrue(result.isFinished)
    }

    @Test
    fun `SwipeRight on a pending photo removes it from pending`() {
        val state = loaded(3).copy(pendingDeletes = persistentListOf(1L))
        val result = reduce(state, SwipeIntent.SwipeRight)
        assertTrue(result.pendingDeletes.isEmpty())
    }

    @Test
    fun `SwipeLeft on a pending photo does not duplicate it`() {
        val state = loaded(3).copy(pendingDeletes = persistentListOf(1L))
        val result = reduce(state, SwipeIntent.SwipeLeft)
        assertEquals(listOf(1L), result.pendingDeletes)
    }

    @Test
    fun `SwipeRight advances without marking delete`() {
        val result = reduce(loaded(3), SwipeIntent.SwipeRight)
        assertEquals(1, result.currentIndex)
        assertTrue(result.pendingDeletes.isEmpty())
        assertFalse(result.isFinished)
    }

    @Test
    fun `SwipeLeft advances and records photo id`() {
        val result = reduce(loaded(3), SwipeIntent.SwipeLeft)
        assertEquals(1, result.currentIndex)
        assertEquals(listOf(1L), result.pendingDeletes)
    }

    @Test
    fun `SwipeLeft records the photo at the current index`() {
        val result = reduce(loaded(3).copy(currentIndex = 2), SwipeIntent.SwipeLeft)
        assertEquals(listOf(3L), result.pendingDeletes)
    }

    @Test
    fun `swiping the last photo finishes`() {
        val result = reduce(loaded(2).copy(currentIndex = 1), SwipeIntent.SwipeRight)
        assertEquals(2, result.currentIndex)
        assertTrue(result.isFinished)
    }

    @Test
    fun `swiping when finished changes nothing`() {
        val finished = loaded(1).copy(currentIndex = 1, isFinished = true)
        assertEquals(finished, reduce(finished, SwipeIntent.SwipeLeft))
        assertEquals(finished, reduce(finished, SwipeIntent.SwipeRight))
    }

    @Test
    fun `swiping with no photos changes nothing`() {
        val empty = SwipeState(date = date)
        assertEquals(empty, reduce(empty, SwipeIntent.SwipeLeft))
    }

    @Test
    fun `Close leaves state unchanged`() {
        val state = loaded(2)
        assertEquals(state, reduce(state, SwipeIntent.Close))
    }

    @Test
    fun `LocationLoaded sets the location of that photo only`() {
        val result = reduce(loaded(2), SwipeEvent.LocationLoaded(photoId = 2, location = "Paris"))
        assertEquals(null, result.photos[0].location)
        assertEquals("Paris", result.photos[1].location)
    }

    @Test
    fun `LocationLoaded without a location leaves the state unchanged`() {
        val state = loaded(2)
        assertEquals(state, reduce(state, SwipeEvent.LocationLoaded(photoId = 1, location = null)))
    }

    @Test
    fun `LocationLoaded for an unknown photo leaves the state unchanged`() {
        val state = loaded(2)
        assertEquals(state, reduce(state, SwipeEvent.LocationLoaded(photoId = 99, location = "Paris")))
    }
}
