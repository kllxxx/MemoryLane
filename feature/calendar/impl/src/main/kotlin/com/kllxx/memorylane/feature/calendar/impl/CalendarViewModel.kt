package com.kllxx.memorylane.feature.calendar.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.usecase.PhotosToReview
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The first visible list item and how far it is scrolled off, in pixels. */
data class ScrollAnchor(val index: Int, val offset: Int)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    private val statsRepository: StatsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val today = LocalDate.now(clock)

    // The grid depends on dates only, so it is there from the start; the photos are added when they are known
    private val yearGrid = buildYearGrid(today.year)

    private val _state = MutableStateFlow(CalendarState(months = yearGrid, today = today))
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private val _effects = Channel<CalendarEffect>(Channel.BUFFERED)
    val effects: Flow<CalendarEffect> = _effects.receiveAsFlow()

    /**
     * Where the list is scrolled to. It starts on the current month and then follows the user, so a list that is
     * created again (back from a day, from another tab, after a rotation) opens where it was left. It is not part of
     * [state] because scrolling must not emit states.
     */
    var scrollAnchor = ScrollAnchor(listIndexOfMonth(yearGrid, YearMonth.from(today)), offset = 0)
        private set

    fun onScrolled(index: Int, offset: Int) {
        scrollAnchor = ScrollAnchor(index, offset)
    }

    /** The list position of the current month, for scrolling back to it. */
    val currentMonthIndex: Int = scrollAnchor.index

    // The rule of the banner of today, shared with the reminder
    private val photosToReview = PhotosToReview(photoRepository, swipeRepository)
    private var todayJob: Job? = null

    init {
        CalendarPerf.onViewModelCreated()
        // The real permission state decides whether to load, so a revoked permission never leaves a blank screen
        _state.update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(photoRepository.hasMediaPermission())) }
        observePhotoIndex()
        observeSortedDays()
        observeStreak()
    }

    fun onIntent(intent: CalendarIntent) {
        _state.update { CalendarReducer.reduce(it, intent) }

        when (intent) {
            CalendarIntent.ScreenEntered -> {
                // The permission may have been revoked while the app was away
                val granted = photoRepository.hasMediaPermission()
                _state.update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(granted)) }
                // Photos may have been decided in a day that was open meanwhile
                refreshTodayBanner()
                if (granted) refresh()
            }
            CalendarIntent.LoadDays -> if (_state.value.hasPermission) refresh()
            CalendarIntent.RequestPermission ->
                _effects.trySend(CalendarEffect.RequestPermissionLauncher)
            is CalendarIntent.PermissionResult -> if (intent.granted) refresh()
            is CalendarIntent.OpenDay ->
                // A day that has not happened yet has no photos to sort
                if (!intent.date.isAfter(LocalDate.now(clock))) {
                    _effects.trySend(CalendarEffect.NavigateToSwipe(intent.date))
                }
        }
    }

    private fun onEvent(event: CalendarEvent) {
        _state.update { CalendarReducer.reduce(it, event) }
    }

    /** The last loaded index is shown at once; every new one replaces it in a single state update. */
    private fun observePhotoIndex() {
        viewModelScope.launch {
            photoRepository.onThisDayIndex.filterNotNull().collect { index ->
                refreshTodayBanner()
                val months = yearGrid.withMemories(index, today)
                onEvent(CalendarEvent.IndexLoaded(months, isEmpty = index.isEmpty))
                CalendarPerf.onIndexShown(
                    isEmpty = index.isEmpty,
                    photos = index.totalPhotos,
                    days = index.entries.size,
                )
            }
        }
    }

    private fun observeSortedDays() {
        viewModelScope.launch {
            try {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    onEvent(CalendarEvent.SortedDatesLoaded(sessions.map { it.date }.toSet()))
                    // A day may have just been completed, which takes today's banner away
                    refreshTodayBanner()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load sorted days", e)
            }
        }
    }

    /** The streak is the same for every screen: it comes from the [StatsRepository]. */
    private fun observeStreak() {
        viewModelScope.launch {
            try {
                statsRepository.streaks.collect { onEvent(CalendarEvent.StreakLoaded(it.current)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the streak", e)
            }
        }
    }

    /**
     * The banner of today: there when today has photos that still exist and the day is not completed, which is when
     * some photo has no decision (a mark for deletion is a decision). The count is the number of those photos, as
     * [PhotosToReview] reads it from the gallery, not the one of the photo index. It is worked out again whenever the
     * index, the completed days or the screen change.
     */
    private fun refreshTodayBanner() {
        todayJob?.cancel()
        todayJob = viewModelScope.launch {
            val banner = try {
                loadTodayBanner()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without the photos of today there is nothing to point at
                Log.e(TAG, "Could not work out the banner of today", e)
                null
            }
            onEvent(CalendarEvent.TodayBannerChanged(banner))
        }
    }

    private suspend fun loadTodayBanner(): TodayBanner? {
        val startedAt = PerfLog.nanoTime()
        val count = photosToReview(today)
        PerfLog.d { "todayBanner date=$today photosToReview=$count query=${PerfLog.sinceMs(startedAt)}ms" }
        return if (count > 0) TodayBanner(count) else null
    }

    /** One gallery query; the result arrives through [observePhotoIndex]. */
    private fun refresh() {
        viewModelScope.launch {
            CalendarPerf.onRefreshStarted()
            try {
                photoRepository.refreshOnThisDayIndex()
                onEvent(CalendarEvent.LoadSucceeded)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PermissionDeniedException) {
                // Not an empty gallery: the prompt takes over
                _state.update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(false)) }
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the photos of the calendar", e)
                onEvent(CalendarEvent.LoadFailed)
            }
        }
    }

    private companion object {
        const val TAG = "CalendarViewModel"
    }
}
