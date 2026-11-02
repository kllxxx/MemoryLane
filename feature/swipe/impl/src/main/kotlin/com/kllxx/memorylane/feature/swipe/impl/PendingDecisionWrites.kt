package com.kllxx.memorylane.feature.swipe.impl

import androidx.annotation.VisibleForTesting
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.di.ApplicationScope
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A write of one decision that is stored or waits to be. */
sealed interface DecisionWrite {
    val photoId: Long

    data class Save(
        override val photoId: Long,
        val deck: DeckKey,
        val decision: PhotoDecision,
        val takenAt: LocalDateTime? = null,
    ) : DecisionWrite

    data class Remove(override val photoId: Long) : DecisionWrite
}

/**
 * Writes the decisions of the user so that a failing database does not lose them silently. A write that fails is
 * retried once at once; if it fails again it waits in memory, by photo (the latest write of a photo wins), and is tried
 * again before the next write, before the screen reads what is stored, and when a screen is left.
 *
 * It lives in the application and runs in the [ApplicationScope], so a screen that is closed while a write is on its
 * way does not take the write with it. Writes go one after another, in the order they were asked for.
 */
@Singleton
class PendingDecisionWrites @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val errorHandler: ErrorHandler,
    @ApplicationScope private val appScope: CoroutineScope,
) {

    private class Entry(val write: DecisionWrite, val error: Exception)

    private val mutex = Mutex()
    private val queue = LinkedHashMap<Long, Entry>()

    /** What waits to be stored, by photo id. */
    @get:VisibleForTesting
    internal val pending: Map<Long, DecisionWrite>
        get() = queue.mapValues { it.value.write }

    /**
     * Stores [write]. Returns false when it could not be stored even after a retry; it is queued then and the caller
     * decides what to tell the user. A cancelled caller gets the cancellation, the write goes on without it.
     */
    suspend fun write(write: DecisionWrite): Boolean =
        // Undispatched: the place in the mutex's line is taken in the order of the calls
        appScope.async(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                // An older write of the same photo is replaced by this one
                queue.remove(write.photoId)
                flushQueue()
                val error = attempt(write) ?: return@withLock true
                queue[write.photoId] = Entry(write, error)
                false
            }
        }.await()

    /** Tries everything that waits. True when nothing waits afterwards. */
    suspend fun flush(): Boolean =
        appScope.async(start = CoroutineStart.UNDISPATCHED) { mutex.withLock { flushQueue() } }.await()

    /** For a screen that is left: flushes in the background, and logs what still cannot be stored. No UI. */
    fun flushOnLeave() {
        appScope.launch(start = CoroutineStart.UNDISPATCHED) {
            mutex.withLock {
                if (!flushQueue()) {
                    errorHandler.handle(queue.values.last().error, "store ${queue.size} pending decisions when leaving the screen")
                }
            }
        }
    }

    /** One try for each, without logging: a write that waits was logged when it was queued. */
    private suspend fun flushQueue(): Boolean {
        for (entry in queue.values.toList()) {
            val error = tryOnce(entry.write)
            if (error == null) queue.remove(entry.write.photoId) else queue[entry.write.photoId] = Entry(entry.write, error)
        }
        return queue.isEmpty()
    }

    /** Runs a new write, once more if it failed. Null on success, the last error otherwise (logged). */
    private suspend fun attempt(write: DecisionWrite): Exception? {
        if (tryOnce(write) == null) return null
        val retry = tryOnce(write) ?: return null
        errorHandler.handle(retry, write.describe())
        return retry
    }

    /** Completes "Could not …" in the log. */
    private fun DecisionWrite.describe() = when (this) {
        is DecisionWrite.Save -> "save the $decision decision of photo $photoId"
        is DecisionWrite.Remove -> "remove the decision of photo $photoId"
    }

    private suspend fun tryOnce(write: DecisionWrite): Exception? = try {
        store(write)
        null
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        e
    }

    private suspend fun store(write: DecisionWrite) = when (write) {
        is DecisionWrite.Save -> swipeRepository.saveDecision(write.photoId, write.deck, write.decision, write.takenAt)
        is DecisionWrite.Remove -> swipeRepository.removeDecision(write.photoId)
    }
}
