package com.kllxx.memorylane.feature.swipe.impl

import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.IOException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The writes of the decisions: one retry, then the queue, and what flushes it. */
@OptIn(ExperimentalCoroutinesApi::class)
class PendingDecisionWritesTest {

    private val dispatcher = StandardTestDispatcher()
    private val appScope = CoroutineScope(dispatcher + SupervisorJob())
    private val deck = DeckKey.Day(LocalDate.of(2026, 10, 15))
    private val errorHandler = mockk<ErrorHandler> { every { handle(any(), any()) } returns UiError.Generic }

    /** Stores into [stored] in call order; every call fails while [failing] is set and waits on [gate] when it is given. */
    private class FakeRepository : SwipeRepository by mockk(relaxed = true) {
        var failing = false
        var gate: CompletableDeferred<Unit>? = null
        val calls = mutableListOf<String>()
        val stored = mutableMapOf<Long, PhotoDecision?>()

        override suspend fun saveDecision(photoId: Long, deck: DeckKey, decision: PhotoDecision, takenAt: LocalDateTime?) {
            calls += "save $photoId $decision"
            gate?.await()
            if (failing) throw IOException("disk")
            stored[photoId] = decision
        }

        override suspend fun removeDecision(photoId: Long) {
            calls += "remove $photoId"
            if (failing) throw IOException("disk")
            stored.remove(photoId)
        }
    }

    private val repository = FakeRepository()
    private val writes = PendingDecisionWrites(repository, errorHandler, appScope)

    private fun save(id: Long, decision: PhotoDecision) = DecisionWrite.Save(id, deck, decision)

    @Test
    fun `a write that fails once is retried and stored, nothing is queued or logged`() = runTest(dispatcher) {
        var attempts = 0
        val flaky = object : SwipeRepository by repository {
            override suspend fun saveDecision(photoId: Long, deck: DeckKey, decision: PhotoDecision, takenAt: LocalDateTime?) {
                if (attempts++ == 0) throw IOException("busy")
                repository.stored[photoId] = decision
            }
        }
        val flakyWrites = PendingDecisionWrites(flaky, errorHandler, appScope)

        val ok = flakyWrites.write(save(1, PhotoDecision.KEEP))

        assertTrue(ok)
        assertEquals(2, attempts)
        assertEquals(PhotoDecision.KEEP, repository.stored[1])
        assertTrue(flakyWrites.pending.isEmpty())
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `a write that fails twice is queued and logged once`() = runTest(dispatcher) {
        repository.failing = true

        val ok = writes.write(save(1, PhotoDecision.DELETE))

        assertFalse(ok)
        assertEquals(listOf("save 1 DELETE", "save 1 DELETE"), repository.calls)
        assertEquals(mapOf(1L to save(1, PhotoDecision.DELETE)), writes.pending)
        verify(exactly = 1) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `the latest write of a photo wins and the older one is dropped from the queue`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))
        repository.failing = false
        repository.calls.clear()

        assertTrue(writes.write(save(1, PhotoDecision.DELETE)))

        assertEquals(listOf("save 1 DELETE"), repository.calls)
        assertEquals(PhotoDecision.DELETE, repository.stored[1])
        assertTrue(writes.pending.isEmpty())
    }

    @Test
    fun `the queue is flushed before the next write, in the order of the queue`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))
        writes.write(DecisionWrite.Remove(2))
        repository.failing = false
        repository.calls.clear()

        assertTrue(writes.write(save(3, PhotoDecision.DELETE)))

        assertEquals(listOf("save 1 KEEP", "remove 2", "save 3 DELETE"), repository.calls)
        assertTrue(writes.pending.isEmpty())
    }

    @Test
    fun `a write that goes on failing keeps the queue and queues the new one behind it`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))

        assertFalse(writes.write(save(2, PhotoDecision.KEEP)))

        assertEquals(listOf(1L, 2L), writes.pending.keys.toList())
    }

    @Test
    fun `flush says whether anything is left`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))

        assertFalse(writes.flush())
        assertEquals(1, writes.pending.size)

        repository.failing = false
        assertTrue(writes.flush())
        assertEquals(PhotoDecision.KEEP, repository.stored[1])
        assertTrue(writes.pending.isEmpty())
    }

    @Test
    fun `leaving the screen stores what waits`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))
        repository.failing = false

        writes.flushOnLeave()
        advanceUntilIdle()

        assertEquals(PhotoDecision.KEEP, repository.stored[1])
        assertTrue(writes.pending.isEmpty())
    }

    @Test
    fun `leaving the screen with a queue that cannot be stored logs it and shows nothing`() = runTest(dispatcher) {
        repository.failing = true
        writes.write(save(1, PhotoDecision.KEEP))

        writes.flushOnLeave()
        advanceUntilIdle()

        // The failure of the write itself, then the one of leaving
        verify(exactly = 2) { errorHandler.handle(any(), any()) }
        assertEquals(1, writes.pending.size)
    }

    @Test
    fun `a caller that is cancelled does not take its write with it`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.gate = gate

        val caller = launch { writes.write(save(1, PhotoDecision.DELETE)) }
        advanceUntilIdle()
        caller.cancel()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(PhotoDecision.DELETE, repository.stored[1])
        verify(exactly = 0) { errorHandler.handle(any(), any()) }
    }

    @Test
    fun `writes are stored in the order they were asked for`() = runTest(dispatcher) {
        launch { writes.write(save(1, PhotoDecision.KEEP)) }
        launch { writes.write(save(2, PhotoDecision.DELETE)) }
        launch { writes.write(DecisionWrite.Remove(1)) }
        advanceUntilIdle()

        assertEquals(listOf("save 1 KEEP", "save 2 DELETE", "remove 1"), repository.calls)
    }
}
