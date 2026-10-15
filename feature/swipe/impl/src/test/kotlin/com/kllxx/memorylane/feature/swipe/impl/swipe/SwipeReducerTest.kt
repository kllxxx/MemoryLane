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
    fun `LoadPhotos resets state and starts loading`() {
        val dirty = loaded(2).copy(
            currentIndex = 1,
            pendingDeletes = persistentListOf(1L),
            isFinished = true,
        )
        val newDate = LocalDate.of(2026, 1, 1)
        val result = reduce(dirty, SwipeIntent.LoadPhotos(newDate))
        assertTrue(result.isLoading)
        assertEquals(newDate, result.date)
        assertEquals(0, result.currentIndex)
        assertTrue(result.photos.isEmpty())
        assertTrue(result.pendingDeletes.isEmpty())
        assertFalse(result.isFinished)
    }

    @Test
    fun `PhotosLoaded stores photos and stops loading`() {
        val photos = listOf(photo(1), photo(2))
        val result = reduce(SwipeState(isLoading = true), SwipeEvent.PhotosLoaded(photos))
        assertEquals(photos, result.photos)
        assertFalse(result.isLoading)
        assertFalse(result.isFinished)
    }

    @Test
    fun `PhotosLoaded with no photos finishes immediately`() {
        val result = reduce(SwipeState(isLoading = true), SwipeEvent.PhotosLoaded(emptyList()))
        assertTrue(result.isFinished)
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
        val empty = SwipeState()
        assertEquals(empty, reduce(empty, SwipeIntent.SwipeLeft))
    }

    @Test
    fun `Close leaves state unchanged`() {
        val state = loaded(2)
        assertEquals(state, reduce(state, SwipeIntent.Close))
    }

    @Test
    fun `ConfirmDelete starts loading`() {
        val state = loaded(2).copy(pendingDeletes = persistentListOf(1L))
        val result = reduce(state, SwipeIntent.ConfirmDelete)
        assertTrue(result.isLoading)
        assertEquals(listOf(1L), result.pendingDeletes)
    }

    @Test
    fun `DeleteResult success clears pending deletes`() {
        val state = loaded(2).copy(pendingDeletes = persistentListOf(1L), isLoading = true)
        val result = reduce(state, SwipeIntent.DeleteResult(success = true))
        assertTrue(result.pendingDeletes.isEmpty())
        assertFalse(result.isLoading)
    }

    @Test
    fun `DeleteResult failure keeps pending deletes`() {
        val state = loaded(2).copy(pendingDeletes = persistentListOf(1L), isLoading = true)
        val result = reduce(state, SwipeIntent.DeleteResult(success = false))
        assertEquals(listOf(1L), result.pendingDeletes)
        assertFalse(result.isLoading)
    }
}
