package com.kllxx.memorylane.feature.swipe.impl.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineState())
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private val _effects = Channel<TimelineEffect>(Channel.BUFFERED)
    val effects: Flow<TimelineEffect> = _effects.receiveAsFlow()

    private val loadedDates = mutableSetOf<LocalDate>()

    init {
        // The real permission state decides whether to load, so a revoked permission never leaves a spinner
        onIntent(TimelineIntent.PermissionResult(photoRepository.hasMediaPermission()))
    }

    fun onIntent(intent: TimelineIntent) {
        _state.update { TimelineReducer.reduce(it, intent) }

        when (intent) {
            TimelineIntent.LoadDays -> if (_state.value.hasPermission) loadInitial()
            is TimelineIntent.LoadRange ->
                if (_state.value.hasPermission) loadRange(intent.from, intent.to.plusDays(BUFFER_DAYS))
            TimelineIntent.RequestPermission ->
                _effects.trySend(TimelineEffect.RequestPermissionLauncher)
            is TimelineIntent.PermissionResult -> if (intent.granted) onIntent(TimelineIntent.LoadDays)
            is TimelineIntent.OpenDay ->
                // A day that has not happened yet has no photos to sort
                if (!intent.date.isAfter(LocalDate.now(clock))) {
                    _effects.trySend(TimelineEffect.NavigateToSwipe(intent.date))
                }
            is TimelineIntent.RefreshDay -> if (_state.value.hasPermission) {
                loadedDates.remove(intent.date)
                loadRange(intent.date, intent.date)
            }
        }
    }

    private fun onEvent(event: TimelineEvent) {
        _state.update { TimelineReducer.reduce(it, event) }
    }

    private fun loadInitial() {
        val today = LocalDate.now(clock)
        val yearStart = LocalDate.of(today.year, 1, 1)
        val skeleton = (0 until today.lengthOfYear()).map {
            DayItem(date = yearStart.plusDays(it.toLong()), firstPhoto = null, totalCount = 0)
        }
        loadedDates.clear()
        onEvent(TimelineEvent.DaysInitialized(skeleton))

        val from = today.withDayOfMonth(1)
        loadRange(from, from.plusDays(INITIAL_WINDOW_DAYS - 1))
    }

    private fun loadRange(from: LocalDate, to: LocalDate) {
        val toLoad = _state.value.days.map { it.date }
            .filter { it >= from && it <= to }
            .filter { loadedDates.add(it) }
        if (toLoad.isEmpty()) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            try {
                val items = toLoad.map { date ->
                    val (firstPhoto, totalCount) = photoRepository.getDayInfo(date)
                    DayItem(date = date, firstPhoto = firstPhoto, totalCount = totalCount)
                }
                onEvent(TimelineEvent.DaysLoaded(items))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadedDates.removeAll(toLoad.toSet())
                onEvent(TimelineEvent.LoadFailed(e.message ?: "Failed to load photos"))
            }
        }
    }

    private companion object {
        const val INITIAL_WINDOW_DAYS = 45L
        const val BUFFER_DAYS = 14L
    }
}
