package com.kllxx.memorylane.feature.swipe.impl.swipe

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Type-safe navigation stores route arguments under their property name
    private val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle.get<String>(DATE_ARG)))

    private val _state = MutableStateFlow(SwipeState(date = date))
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SwipeEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<SwipeEffect> = _effects.asSharedFlow()

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
            SwipeIntent.Close -> _effects.tryEmit(SwipeEffect.NavigateToTimeline)
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
            } catch (e: Exception) {
                // A failed load is not an empty day, so no session is recorded for it
                _effects.emit(SwipeEffect.NavigateToTimeline)
                return@launch
            }
            onEvent(event)
            // Nothing left to show: an empty day, or every photo was already marked on a previous run
            _state.value.takeIf { it.isFinished }?.let(::onAllSwiped)
        }
    }

    private fun savePendingDelete(photoId: Long, state: SwipeState) {
        viewModelScope.launch {
            swipeRepository.savePendingDelete(PendingDelete(photoId, state.date))
        }
    }

    private fun removePendingDelete(photoId: Long) {
        viewModelScope.launch {
            swipeRepository.removePendingDelete(photoId)
        }
    }

    private fun onAllSwiped(state: SwipeState) {
        viewModelScope.launch {
            // Nothing to delete means nothing left to review, so the day is done. Otherwise it only
            // completes once Review has deleted the photos, and an existing result is never reset.
            val existing = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == state.date }
            val completed = state.pendingDeletes.isEmpty() || existing?.completed == true
            swipeRepository.saveSession(
                SwipeSession(state.date, completed = completed, savedBytes = existing?.savedBytes ?: 0),
            )
            _effects.emit(
                if (state.pendingDeletes.isEmpty()) {
                    SwipeEffect.NavigateToTimeline
                } else {
                    SwipeEffect.NavigateToReview(state.date, totalPhotosCount = state.photos.size)
                },
            )
        }
    }

    internal companion object {
        const val DATE_ARG = "date"
    }
}
