package com.kllxx.memorylane.feature.swipe.impl.swipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
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
            SwipeIntent.ConfirmDelete -> deletePending(before.pendingDeletes)
            is SwipeIntent.DeleteResult -> if (intent.success) onDeleted()
        }
    }

    private fun onEvent(event: SwipeEvent) {
        _state.update { SwipeReducer.reduce(it, event) }
    }

    private fun loadPhotos(intent: SwipeIntent.LoadPhotos) {
        viewModelScope.launch {
            val photos = try {
                photoRepository.getPhotosForDate(intent.date)
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
        val savedBytes = state.photos.filter { it.id in state.pendingDeletes }.sumOf { it.sizeBytes }
        viewModelScope.launch {
            swipeRepository.saveSession(SwipeSession(state.date, completed = true, savedBytes = savedBytes))
            if (state.pendingDeletes.isEmpty()) _effects.emit(SwipeEffect.NavigateToTimeline)
        }
    }

    private fun deletePending(photoIds: List<Long>) {
        viewModelScope.launch {
            try {
                when (val result = photoRepository.deletePhotos(photoIds)) {
                    DeleteResult.Deleted -> onIntent(SwipeIntent.DeleteResult(success = true))
                    is DeleteResult.RequiresConfirmation ->
                        _effects.emit(SwipeEffect.RequestSystemDelete(result.intentSender))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                onIntent(SwipeIntent.DeleteResult(success = false))
            }
        }
    }

    private fun onDeleted() {
        viewModelScope.launch {
            swipeRepository.clearPendingDeletes()
            _effects.emit(SwipeEffect.NavigateToTimeline)
        }
    }
}
