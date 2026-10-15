package com.kllxx.memorylane.feature.swipe.impl.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SwipeState())
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<SwipeEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<SwipeEffect> = _effects.asSharedFlow()

    fun onIntent(intent: SwipeIntent) {
        val before = _state.value
        _state.update { SwipeReducer.reduce(it, intent) }
        val after = _state.value

        when (intent) {
            is SwipeIntent.LoadPhotos -> loadPhotos(intent)
            SwipeIntent.SwipeLeft, SwipeIntent.SwipeRight -> {
                if (after.pendingDeletes.size > before.pendingDeletes.size) {
                    savePendingDelete(after.pendingDeletes.last(), after)
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
            val photos = try {
                photoRepository.getPhotosForDate(intent.date, includeLocation = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                emptyList()
            }
            onEvent(SwipeEvent.PhotosLoaded(photos))
        }
    }

    private fun savePendingDelete(photoId: Long, state: SwipeState) {
        viewModelScope.launch {
            swipeRepository.savePendingDelete(PendingDelete(photoId, state.date))
        }
    }

    private fun onAllSwiped(state: SwipeState) {
        viewModelScope.launch {
            // Bytes are counted by the Review screen once the photos are actually deleted
            swipeRepository.saveSession(SwipeSession(state.date, completed = true, savedBytes = 0))
            _effects.emit(
                if (state.pendingDeletes.isEmpty()) {
                    SwipeEffect.NavigateToTimeline
                } else {
                    SwipeEffect.NavigateToReview(state.date)
                },
            )
        }
    }
}
