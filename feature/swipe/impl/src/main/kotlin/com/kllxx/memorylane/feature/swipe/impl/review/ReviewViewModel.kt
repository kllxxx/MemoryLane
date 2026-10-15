package com.kllxx.memorylane.feature.swipe.impl.review

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
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
class ReviewViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val photoRepository: PhotoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    // Type-safe navigation stores route arguments under their property name
    private val date: LocalDate = LocalDate.parse(checkNotNull(savedStateHandle.get<String>(DATE_ARG)))

    private val _state = MutableStateFlow(ReviewState())
    val state: StateFlow<ReviewState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<ReviewEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<ReviewEffect> = _effects.asSharedFlow()

    fun onIntent(intent: ReviewIntent) {
        val before = _state.value
        _state.update { ReviewReducer.reduce(it, intent) }

        when (intent) {
            ReviewIntent.LoadStats -> loadStats()
            ReviewIntent.ConfirmDelete ->
                if (!before.isDeleting && !before.isFinished && before.pendingDeleteIds.isNotEmpty()) {
                    deletePending(before.pendingDeleteIds, before.sessionFreedBytes)
                }
            is ReviewIntent.DeleteResult -> if (intent.success) onDeleted(intent.freedBytes)
        }
    }

    private fun onEvent(event: ReviewEvent) {
        _state.update { ReviewReducer.reduce(it, event) }
    }

    private fun loadStats() {
        viewModelScope.launch {
            try {
                val ids = swipeRepository.getPendingDeletes()
                    .filter { it.sessionDate == date }
                    .map { it.photoId }
                if (ids.isEmpty()) {
                    // Nothing to review, so the review screen is not shown at all
                    _effects.emit(ReviewEffect.NavigateToTimeline)
                    return@launch
                }
                val photos = photoRepository.getPhotosForDate(date)
                val idSet = ids.toSet()
                onEvent(
                    ReviewEvent.StatsLoaded(
                        keptCount = (photos.size - ids.size).coerceAtLeast(0),
                        deletedCount = ids.size,
                        pendingDeleteIds = ids,
                        totalFreedBytes = swipeRepository.getCompletedSessions().first().sumOf { it.savedBytes },
                        sessionFreedBytes = photos.filter { it.id in idSet }.sumOf { it.sizeBytes },
                    ),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _effects.emit(ReviewEffect.NavigateToTimeline)
            }
        }
    }

    private fun deletePending(photoIds: List<Long>, expectedFreedBytes: Long) {
        viewModelScope.launch {
            try {
                when (val result = photoRepository.deletePhotos(photoIds)) {
                    DeleteResult.Deleted -> onIntent(ReviewIntent.DeleteResult(success = true, freedBytes = expectedFreedBytes))
                    is DeleteResult.RequiresConfirmation ->
                        _effects.emit(ReviewEffect.RequestSystemDelete(result.intentSender))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                onIntent(ReviewIntent.DeleteResult(success = false, freedBytes = 0))
            }
        }
    }

    private fun onDeleted(freedBytes: Long) {
        viewModelScope.launch {
            val session = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == date }
                ?: SwipeSession(date, completed = true, savedBytes = 0)
            swipeRepository.saveSession(session.copy(savedBytes = freedBytes))
            swipeRepository.clearPendingDeletesByDate(date)
            _effects.emit(ReviewEffect.ShowFreedSpaceAnimation)
            // Gives the animation time to play before the screen is left
            delay(ANIMATION_DURATION_MS)
            _effects.emit(ReviewEffect.NavigateToTimeline)
        }
    }

    internal companion object {
        const val DATE_ARG = "date"
        const val ANIMATION_DURATION_MS = 2_000L
    }
}
