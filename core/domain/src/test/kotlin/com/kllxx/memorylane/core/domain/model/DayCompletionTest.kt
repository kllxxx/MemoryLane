package com.kllxx.memorylane.core.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DayCompletionTest {

    @Test
    fun `a day without photos is not completed, there is nothing to complete`() {
        assertFalse(DayCompletion.isCompleted(emptyList(), emptyMap()))
    }

    @Test
    fun `a day is completed when every photo is kept, trashed or deleted`() {
        val decisions = mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.TRASHED, 3L to PhotoDecision.DELETED)

        assertTrue(DayCompletion.isCompleted(listOf(1L, 2L, 3L), decisions))
    }

    @Test
    fun `a photo without a decision keeps the day open`() {
        assertFalse(DayCompletion.isCompleted(listOf(1L, 2L), mapOf(1L to PhotoDecision.KEEP)))
    }

    @Test
    fun `a photo marked for deletion is decided, so a cancelled dialog leaves the day completed`() {
        assertTrue(DayCompletion.isCompleted(listOf(1L, 2L), mapOf(1L to PhotoDecision.KEEP, 2L to PhotoDecision.DELETE)))
    }

    @Test
    fun `decisions of photos that are not in the day do not matter`() {
        assertTrue(DayCompletion.isCompleted(listOf(1L), mapOf(1L to PhotoDecision.KEEP, 9L to PhotoDecision.DELETE)))
    }
}
