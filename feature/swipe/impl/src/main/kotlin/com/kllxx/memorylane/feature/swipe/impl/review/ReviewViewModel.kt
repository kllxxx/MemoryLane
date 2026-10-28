package com.kllxx.memorylane.feature.swipe.impl.review

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.feature.swipe.api.ReviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val photoRepository: PhotoRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ReviewRoute>()

    private val date: LocalDate = LocalDate.parse(route.date)

    private val totalPhotosCount: Int = route.totalPhotosCount

    // Safety net only: it logs, so one failed coroutine cannot take the whole app down
    private val exceptionHandler = CoroutineExceptionHandler { _, e -> Log.e(TAG, "Unhandled error in Review", e) }

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _effects = Channel<ReviewEffect>(Channel.BUFFERED)
    val effects: Flow<ReviewEffect> = _effects.receiveAsFlow()

    fun onIntent(intent: ReviewIntent) {
        val before = _state.value
        _state.update { ReviewReducer.reduce(it, intent) }

        when (intent) {
            ReviewIntent.LoadStats -> loadStats()
            // Besides the reducer guard, this stops a second system delete request from being started
            ReviewIntent.ConfirmDelete ->
                if (!before.isDeleting && !before.isFinished && before.pendingDeleteIds.isNotEmpty()) {
                    deletePending(before.pendingDeleteIds)
                }
            ReviewIntent.SystemDeleteConfirmed ->
                if (!before.isFinished) onDeleted(before.sessionFreedBytes, before.pendingDeleteIds)
            ReviewIntent.SystemDeleteCancelled -> Unit
        }
    }

    private fun onEvent(event: ReviewEvent) {
        _state.update { ReviewReducer.reduce(it, event) }
    }

    private fun loadStats() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val markedIds = swipeRepository.getDecisionsByDate(date)
                    .filterValues { it == PhotoDecision.DELETE }.keys.toList()
                if (markedIds.isEmpty()) {
                    // Nothing to review, so the review screen is not shown at all
                    _effects.trySend(ReviewEffect.NavigateToCalendar)
                    return@launch
                }
                val photos = photoRepository.getPhotosForDate(date)
                // A marked photo that is no longer in the library (deleted elsewhere) cannot be deleted again
                val existing = photos.mapTo(mutableSetOf()) { it.id }
                val (ids, staleIds) = markedIds.partition { it in existing }
                staleIds.forEach(::removeStaleDecision)
                if (ids.isEmpty()) {
                    _effects.trySend(ReviewEffect.NavigateToCalendar)
                    return@launch
                }
                val idSet = ids.toSet()
                onEvent(
                    ReviewEvent.StatsLoaded(
                        keptCount = (totalPhotosCount - ids.size).coerceAtLeast(0),
                        deletedCount = ids.size,
                        pendingDeleteIds = ids,
                        totalFreedBytes = swipeRepository.getCompletedSessions().first().sumOf { it.savedBytes },
                        sessionFreedBytes = photos.filter { it.id in idSet }.sumOf { it.sizeBytes },
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the review stats", e)
                _effects.trySend(ReviewEffect.NavigateToCalendar)
            }
        }
    }

    private fun removeStaleDecision(photoId: Long) {
        viewModelScope.launch(exceptionHandler) {
            try {
                swipeRepository.removeDecision(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove the stale decision of photo $photoId", e)
            }
        }
    }

    private fun deletePending(photoIds: List<Long>) {
        viewModelScope.launch(exceptionHandler) {
            try {
                when (val result = photoRepository.deletePhotos(photoIds)) {
                    DeleteResult.Deleted -> onIntent(ReviewIntent.SystemDeleteConfirmed)
                    is DeleteResult.RequiresConfirmation ->
                        _effects.trySend(ReviewEffect.RequestSystemDelete(result.intentSender))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // SecurityException, IllegalArgumentException, IOException and anything unexpected:
                // nothing was deleted, so the pending deletes stay and the user can try again
                Log.e(TAG, "Could not delete the pending photos", e)
                onIntent(ReviewIntent.SystemDeleteCancelled)
                _effects.trySend(ReviewEffect.ShowDeleteError)
            }
        }
    }

    private fun onDeleted(freedBytes: Long, deletedIds: List<Long>) {
        val deletedCount = deletedIds.size
        viewModelScope.launch(exceptionHandler) {
            // The photos are already gone, so a failed write must never keep the user on this screen.
            // The session only counts as completed once the photos are really gone. The two writes
            // are independent: one failing does not skip the other.
            try {
                // A day sorted again adds to what it freed before and keeps the day it was first completed
                val existing = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == date }
                swipeRepository.saveSession(
                    SwipeSession(
                        date = date,
                        completed = true,
                        savedBytes = (existing?.savedBytes ?: 0) + freedBytes,
                        deletedCount = (existing?.deletedCount ?: 0) + deletedCount,
                        completedAt = existing?.completedAt ?: LocalDate.now(clock),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the completed session", e)
            }
            try {
                // createDeleteRequest removes the photos for good; Task 3 moves them to the trash instead
                swipeRepository.setDecisions(deletedIds, PhotoDecision.DELETED)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not record the deleted photos", e)
            }
            // The deleted photos must be gone from the Calendar when it opens: the new index is read while the
            // freed space animation plays, and only a failure is allowed to skip it
            val photoIndexRefresh = async { refreshPhotoIndex() }
            _effects.trySend(ReviewEffect.ShowFreedSpaceAnimation)
            delay(ReviewConstants.FREED_SPACE_ANIMATION_DELAY_MS)
            photoIndexRefresh.await()
            _effects.trySend(ReviewEffect.NavigateToCalendar)
        }
    }

    private suspend fun refreshPhotoIndex() {
        try {
            photoRepository.refreshOnThisDayIndex()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not refresh the photo index after the deletion", e)
        }
    }

    internal companion object {
        const val TAG = "ReviewViewModel"
    }
}
