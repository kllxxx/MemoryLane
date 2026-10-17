package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val date: LocalDate = LocalDate.parse(savedStateHandle.toRoute<SwipeRoute>().date)

    private val _state = MutableStateFlow(SwipeState(date = date))
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private val _effects = Channel<SwipeEffect>(Channel.BUFFERED)
    val effects: Flow<SwipeEffect> = _effects.receiveAsFlow()

    init {
        onIntent(SwipeIntent.LoadPhotos(date))
    }

    fun onIntent(intent: SwipeIntent) {
        val before = _state.value
        _state.update { SwipeReducer.reduce(it, intent) }
        val after = _state.value

        when (intent) {
            is SwipeIntent.LoadPhotos -> loadPhotos(intent)
            SwipeIntent.SwipeLeft, SwipeIntent.SwipeRight -> {
                val photoId = before.photos.getOrNull(before.currentIndex)?.id
                if (photoId != null) {
                    if (after.pendingDeletes.size > before.pendingDeletes.size) {
                        savePendingDelete(photoId, after)
                    } else if (after.pendingDeletes.size < before.pendingDeletes.size) {
                        removePendingDelete(photoId)
                    }
                }
                if (after.isFinished && !before.isFinished) onAllSwiped(after)
            }
            SwipeIntent.Close -> _effects.trySend(SwipeEffect.NavigateToTimeline)
        }
    }

    private fun onEvent(event: SwipeEvent) {
        _state.update { SwipeReducer.reduce(it, event) }
    }

    private fun loadPhotos(intent: SwipeIntent.LoadPhotos) {
        viewModelScope.launch {
            val event = try {
                val photos = photoRepository.getPhotosForDate(intent.date, includeLocation = true)
                val pendingIds = swipeRepository.getPendingDeletes()
                    .filter { it.sessionDate == intent.date }
                    .map { it.photoId }
                SwipeEvent.PhotosLoaded(photos, pendingIds)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PermissionDeniedException) {
                // Not an empty day: nothing is recorded and the user stays to grant access
                _effects.trySend(SwipeEffect.PermissionRequired)
                return@launch
            } catch (e: Exception) {
                // A failed load is not an empty day, so no session is recorded for it
                Log.e(TAG, "Could not load the photos for $date", e)
                _effects.trySend(SwipeEffect.NavigateToTimeline)
                return@launch
            }
            onEvent(event)
            // Nothing left to show: an empty day, or every photo was already marked on a previous run
            _state.value.takeIf { it.isFinished }?.let(::onAllSwiped)
        }
    }

    private fun savePendingDelete(photoId: Long, state: SwipeState) {
        viewModelScope.launch {
            try {
                swipeRepository.savePendingDelete(PendingDelete(photoId, state.date))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the pending delete of photo $photoId", e)
            }
        }
    }

    private fun removePendingDelete(photoId: Long) {
        viewModelScope.launch {
            try {
                swipeRepository.removePendingDelete(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not remove the pending delete of photo $photoId", e)
            }
        }
    }

    private fun onAllSwiped(state: SwipeState) {
        viewModelScope.launch {
            // The user has finished swiping, so a failed session write must not keep them on this screen
            try {
                // Nothing to delete means nothing left to review, so the day is done. Otherwise it only
                // completes once Review has deleted the photos, and an existing result is never reset.
                val existing = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == state.date }
                val completed = state.pendingDeletes.isEmpty() || existing?.completed == true
                swipeRepository.saveSession(
                    SwipeSession(state.date, completed = completed, savedBytes = existing?.savedBytes ?: 0),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not save the session of ${state.date}", e)
            }
            _effects.trySend(
                if (state.pendingDeletes.isEmpty()) {
                    SwipeEffect.NavigateToTimeline
                } else {
                    SwipeEffect.NavigateToReview(state.date, totalPhotosCount = state.photos.size)
                },
            )
        }
    }

    internal companion object {
        const val TAG = "SwipeViewModel"
    }
}
