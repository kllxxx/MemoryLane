package com.kllxx.memorylane.feature.swipe.impl.calendar

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
class CalendarViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(CalendarState(today = LocalDate.now(clock)))
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private val _effects = Channel<CalendarEffect>(Channel.BUFFERED)
    val effects: Flow<CalendarEffect> = _effects.receiveAsFlow()

    private val loadedDates = mutableSetOf<LocalDate>()

    init {
        CalendarPerf.onViewModelCreated()
        // The real permission state decides whether to load, so a revoked permission never leaves a spinner
        onIntent(CalendarIntent.PermissionResult(photoRepository.hasMediaPermission()))
        observeSortedDays()
    }

    fun onIntent(intent: CalendarIntent) {
        _state.update { CalendarReducer.reduce(it, intent) }

        when (intent) {
            CalendarIntent.LoadDays -> if (_state.value.hasPermission) loadInitial()
            is CalendarIntent.LoadRange ->
                if (_state.value.hasPermission) loadRange(intent.from, intent.to.plusDays(SwipeConstants.BUFFER_DAYS))
            CalendarIntent.RequestPermission ->
                _effects.trySend(CalendarEffect.RequestPermissionLauncher)
            is CalendarIntent.PermissionResult -> if (intent.granted) onIntent(CalendarIntent.LoadDays)
            is CalendarIntent.OpenDay ->
                // A day that has not happened yet has no photos to sort
                if (!intent.date.isAfter(LocalDate.now(clock))) {
                    _effects.trySend(CalendarEffect.NavigateToSwipe(intent.date))
                }
            is CalendarIntent.RefreshDay -> if (_state.value.hasPermission) {
                loadedDates.remove(intent.date)
                loadRange(intent.date, intent.date)
            }
        }
    }

    private fun onEvent(event: CalendarEvent) {
        _state.update { CalendarReducer.reduce(it, event) }
    }

    private fun observeSortedDays() {
        viewModelScope.launch {
            try {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    // Streaks follow the day the work was done; sessions saved before that was tracked carry the epoch marker
                    val doneOn = sessions.map { it.completedAt }.filter { it != LocalDate.EPOCH }
                    onEvent(
                        CalendarEvent.StatsLoaded(
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
        onEvent(CalendarEvent.DaysInitialized(skeleton))

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
            CalendarPerf.onYearLoaded()
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
        val startedAt = System.nanoTime()
        val items = toLoad.map { date ->
            CalendarPerf.onQuery()
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
        onEvent(CalendarEvent.DaysLoaded(items))
        CalendarPerf.onDaysLoaded(
            days = items.size,
            batchMs = (System.nanoTime() - startedAt) / NANOS_PER_MILLI,
            anyPhotos = items.any { it.loadState is DayLoadState.Loaded },
        )
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        loadedDates.removeAll(toLoad.toSet())
        Log.e(TAG, "Could not load ${toLoad.size} calendar days", e)
        onEvent(CalendarEvent.LoadFailed)
        false
    }

    private companion object {
        const val TAG = "CalendarViewModel"
        const val MONTHS_IN_YEAR = 12
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
