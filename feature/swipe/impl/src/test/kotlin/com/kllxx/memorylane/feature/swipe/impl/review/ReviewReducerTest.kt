package com.kllxx.memorylane.feature.swipe.impl.review

import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewReducerTest {

    private val loaded = ReviewState(
        keptCount = 5,
        deletedCount = 2,
        pendingDeleteIds = persistentListOf(1L, 2L),
        totalFreedBytes = 1_000,
        sessionFreedBytes = 300,
    )

    private fun reduce(state: ReviewState, intent: ReviewIntent) = ReviewReducer.reduce(state, intent)

    private fun reduce(state: ReviewState, event: ReviewEvent) = ReviewReducer.reduce(state, event)

    @Test
    fun `LoadStats leaves state unchanged`() {
        assertEquals(loaded, reduce(loaded, ReviewIntent.LoadStats))
    }

    @Test
    fun `StatsLoaded sets all loaded fields`() {
        val result = reduce(
            ReviewState(),
            ReviewEvent.StatsLoaded(
                keptCount = 5,
                deletedCount = 2,
                pendingDeleteIds = listOf(1L, 2L),
                totalFreedBytes = 1_000,
                sessionFreedBytes = 300,
            ),
        )
        assertEquals(loaded, result)
    }

    @Test
    fun `ConfirmDelete starts deleting`() {
        val result = reduce(loaded, ReviewIntent.ConfirmDelete)
        assertTrue(result.isDeleting)
        assertEquals(loaded.pendingDeleteIds, result.pendingDeleteIds)
    }

    @Test
    fun `ConfirmDelete is ignored while already deleting`() {
        val deleting = loaded.copy(isDeleting = true)
        assertEquals(deleting, reduce(deleting, ReviewIntent.ConfirmDelete))
    }

    @Test
    fun `ConfirmDelete is ignored once finished`() {
        val finished = loaded.copy(isFinished = true)
        assertEquals(finished, reduce(finished, ReviewIntent.ConfirmDelete))
    }

    @Test
    fun `DeleteResult success finishes and adds freed bytes to the total`() {
        val result = reduce(
            loaded.copy(isDeleting = true),
            ReviewIntent.DeleteResult(success = true, freedBytes = 300),
        )
        assertTrue(result.isFinished)
        assertFalse(result.isDeleting)
        assertTrue(result.pendingDeleteIds.isEmpty())
        assertEquals(300, result.sessionFreedBytes)
        assertEquals(1_300, result.totalFreedBytes)
        assertEquals(2, result.deletedCount)
        assertEquals(5, result.keptCount)
    }

    @Test
    fun `DeleteResult failure keeps pending deletes and totals`() {
        val result = reduce(
            loaded.copy(isDeleting = true),
            ReviewIntent.DeleteResult(success = false, freedBytes = 0),
        )
        assertFalse(result.isDeleting)
        assertFalse(result.isFinished)
        assertEquals(loaded.copy(isDeleting = false), result)
    }
}
