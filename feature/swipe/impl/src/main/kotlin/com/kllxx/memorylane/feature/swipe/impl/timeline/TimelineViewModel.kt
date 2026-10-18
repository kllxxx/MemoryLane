package com.kllxx.memorylane.feature.swipe.impl.timeline

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.feature.swipe.impl.SwipeConstants
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
    private val swipeRepository: SwipeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(TimelineState(today = LocalDate.now(clock)))
    val state: StateFlow<TimelineState> = _state.asStateFlow()

    private val _effects = Channel<TimelineEffect>(Channel.BUFFERED)
    val effects: Flow<TimelineEffect> = _effects.receiveAsFlow()

    private val loadedDates = mutableSetOf<LocalDate>()

    init {
        // The real permission state decides whether to load, so a revoked permission never leaves a spinner
        onIntent(TimelineIntent.PermissionResult(photoRepository.hasMediaPermission()))
        observeSortedDays()
    }

    fun onIntent(intent: TimelineIntent) {
        _state.update { TimelineReducer.reduce(it, intent) }

        when (intent) {
            TimelineIntent.LoadDays -> if (_state.value.hasPermission) loadInitial()
            is TimelineIntent.LoadRange ->
                if (_state.value.hasPermission) loadRange(intent.from, intent.to.plusDays(SwipeConstants.BUFFER_DAYS))
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

    private fun observeSortedDays() {
        viewModelScope.launch {
            try {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    // Streaks follow the day the work was done; sessions saved before that was tracked carry the epoch marker
                    val doneOn = sessions.map { it.completedAt }.filter { it != LocalDate.EPOCH }
                    onEvent(
                        TimelineEvent.StatsLoaded(
                            sortedDates = sessions.map { it.date }.toSet(),
                            currentStreak = StreakCalculator.currentStreak(doneOn, LocalDate.now(clock)),
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load sorted days", e)
            }
        }
    }

    private fun loadInitial() {
        val today = LocalDate.now(clock)
        val yearStart = LocalDate.of(today.year, 1, 1)
        val skeleton = (0 until today.lengthOfYear()).map {
            DayItem(date = yearStart.plusDays(it.toLong()), loadState = DayLoadState.NotLoaded)
        }
        loadedDates.clear()
        onEvent(TimelineEvent.DaysInitialized(skeleton))

        val from = today.withDayOfMonth(1)
        loadRange(from, from.plusDays(SwipeConstants.INITIAL_WINDOW_DAYS - 1))
        loadRestOfYear(today)
    }

    /**
     * A month is only shown once one of its days is known to have photos, so every month has to be
     * looked up, not only the ones that are scrolled to. The current month goes first (see [loadInitial]).
     */
    private fun loadRestOfYear(today: LocalDate) {
        viewModelScope.launch {
            for (month in 1..MONTHS_IN_YEAR) {
                val start = LocalDate.of(today.year, month, 1)
                val toLoad = claimUnloaded(start, start.withDayOfMonth(start.lengthOfMonth()))
                if (toLoad.isNotEmpty() && !loadDays(toLoad)) return@launch
            }
        }
    }

    private fun loadRange(from: LocalDate, to: LocalDate) {
        val toLoad = claimUnloaded(from, to)
        if (toLoad.isEmpty()) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch { loadDays(toLoad) }
    }

    /** Dates in the range that nobody has asked for yet; they are marked as requested. */
    private fun claimUnloaded(from: LocalDate, to: LocalDate): List<LocalDate> =
        _state.value.days.map { it.date }
            .filter { it >= from && it <= to }
            .filter { loadedDates.add(it) }

    /** Returns false if the days could not be loaded. */
    private suspend fun loadDays(toLoad: List<LocalDate>): Boolean = try {
        val items = toLoad.map { date ->
            val (firstPhoto, totalCount) = photoRepository.getDayInfo(date)
            DayItem(
                date = date,
                loadState = if (firstPhoto == null) {
                    DayLoadState.Empty
                } else {
                    DayLoadState.Loaded(firstPhoto, totalCount)
                },
            )
        }
        onEvent(TimelineEvent.DaysLoaded(items))
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loadedDates.removeAll(toLoad.toSet())
        Log.e(TAG, "Could not load ${toLoad.size} timeline days", e)
        onEvent(TimelineEvent.LoadFailed)
        false
    }

    private companion object {
        const val TAG = "TimelineViewModel"
        const val MONTHS_IN_YEAR = 12
    }
}
