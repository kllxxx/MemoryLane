package com.kllxx.memorylane.feature.swipe.impl.review

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
            ReviewIntent.ConfirmDelete ->
                if (!before.isDeleting && !before.isFinished && before.pendingDeleteIds.isNotEmpty()) {
                    deletePending(before.pendingDeleteIds)
                }
            ReviewIntent.SystemDeleteConfirmed -> if (!before.isFinished) onDeleted(before.sessionFreedBytes)
            ReviewIntent.SystemDeleteCancelled -> Unit
        }
    }

    private fun onEvent(event: ReviewEvent) {
        _state.update { ReviewReducer.reduce(it, event) }
    }

    private fun loadStats() {
        viewModelScope.launch(exceptionHandler) {
            try {
                val ids = swipeRepository.getPendingDeletes()
                    .filter { it.sessionDate == date }
                    .map { it.photoId }
                if (ids.isEmpty()) {
                    // Nothing to review, so the review screen is not shown at all
                    _effects.trySend(ReviewEffect.NavigateToTimeline)
                    return@launch
                }
                val photos = photoRepository.getPhotosForDate(date)
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
                _effects.trySend(ReviewEffect.NavigateToTimeline)
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

    private fun onDeleted(freedBytes: Long) {
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
                        completedAt = existing?.completedAt ?: LocalDate.now(clock),
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the completed session", e)
            }
            try {
                swipeRepository.clearPendingDeletesByDate(date)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not clear the pending deletes", e)
            }
            _effects.trySend(ReviewEffect.ShowFreedSpaceAnimation)
            // Gives the animation time to play before the screen is left
            delay(ANIMATION_DURATION_MS)
            _effects.trySend(ReviewEffect.NavigateToTimeline)
        }
    }

    internal companion object {
        const val TAG = "ReviewViewModel"
        const val ANIMATION_DURATION_MS = 2_000L
    }
}
