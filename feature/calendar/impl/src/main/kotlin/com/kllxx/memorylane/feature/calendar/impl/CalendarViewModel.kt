package com.kllxx.memorylane.feature.calendar.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.time.TodayProvider
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.OnThisDayIndex
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.usecase.AdoptDatedDecisions
import com.kllxx.memorylane.core.photos.domain.usecase.PhotosToReview
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val photosToReview: PhotosToReview,
    private val adoptDatedDecisions: AdoptDatedDecisions,
    private val todayProvider: TodayProvider,
) : ViewModel() {

    // The grid depends on dates only, so it is there from the start; the photos are added when they are known
    private val _state = MutableStateFlow(
        todayProvider.now().let { today -> CalendarState(months = buildYearGrid(today.year), today = today) },
    )
    val state: StateFlow<CalendarState> = _state.asStateFlow()

    private val _effects = Channel<CalendarEffect>(Channel.BUFFERED)
    val effects: Flow<CalendarEffect> = _effects.receiveAsFlow()

    /**
     * Where the list is scrolled to. It starts on the current month and then follows the user, so a list that is
     * created again (back from a day, from another tab, after a rotation) opens where it was left. It is not part of
     * [state] because scrolling must not emit states. A new year is a new list, so it goes back to the current month.
     */
    var scrollAnchor = ScrollAnchor(currentMonthIndex, offset = 0)
        private set

    fun onScrolled(index: Int, offset: Int) {
        scrollAnchor = ScrollAnchor(index, offset)
    }

    /** The list position of the current month, for scrolling back to it. */
    val currentMonthIndex: Int
        get() = _state.value.let { listIndexOfMonth(it.months, YearMonth.from(it.today)) }

    private var lastIndex: OnThisDayIndex? = null
    private var todayJob: Job? = null
    private var undatedJob: Job? = null

    init {
        CalendarPerf.onViewModelCreated()
        // The real permission state decides whether to load, so a revoked permission never leaves a blank screen
        _state.update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(photoRepository.hasMediaPermission())) }
        observeToday()
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
                // Photos may have been decided in a day or in the undated deck that was open meanwhile
                refreshUndatedBanner(photoRepository.onThisDayIndex.value)
                refreshTodayBanner()
                if (granted) refresh()
            }
            CalendarIntent.LoadDays -> if (_state.value.hasPermission) refresh()
            CalendarIntent.RequestPermission ->
                _effects.trySend(CalendarEffect.RequestPermissionLauncher)
            is CalendarIntent.PermissionResult -> if (intent.granted) refresh()
            CalendarIntent.OpenUndated -> _effects.trySend(CalendarEffect.NavigateToUndated)
            is CalendarIntent.OpenDay ->
                // A day that has not happened yet has no photos to sort
                if (!intent.date.isAfter(todayProvider.now())) {
                    _effects.trySend(CalendarEffect.NavigateToSwipe(intent.date))
                }
        }
    }

    private fun onEvent(event: CalendarEvent) {
        _state.update { CalendarReducer.reduce(it, event) }
    }

    /**
     * The day changed under a screen that stayed open: the highlight, the days that count as past, the banner and,
     * in a new year, the whole grid follow it.
     */
    private fun observeToday() {
        viewModelScope.launch {
            todayProvider.today.collect { today ->
                val current = _state.value
                if (today == current.today) return@collect
                val newYear = today.year != current.today.year
                val base = if (newYear) buildYearGrid(today.year) else current.months
                onEvent(CalendarEvent.TodayChanged(today, base.withMemories(lastIndex, today)))
                if (newYear) scrollAnchor = ScrollAnchor(currentMonthIndex, offset = 0)
                refreshTodayBanner()
            }
        }
    }

    /** The last loaded index is shown at once; every new one replaces it in a single state update. */
    private fun observePhotoIndex() {
        viewModelScope.launch {
            photoRepository.onThisDayIndex.filterNotNull().collect { index ->
                lastIndex = index
                refreshUndatedBanner(index)
                adoptDatedDecisions(index)
                refreshTodayBanner()
                val months = _state.value.let { it.months.withMemories(index, it.today) }
                onEvent(CalendarEvent.IndexLoaded(months, isEmpty = index.isEmpty))
                CalendarPerf.onIndexShown(
                    isEmpty = index.isEmpty,
                    photos = index.totalPhotos,
                    days = index.entries.size,
                )
            }
        }
    }

    /**
     * A photo that was undated and has a date now takes its decision to its day, so a mark for deletion is a pending
     * delete of that day at once. A day that is completed by it takes the banner of today away.
     */
    private fun adoptDatedDecisions(index: OnThisDayIndex) {
        viewModelScope.launch {
            try {
                if (adoptDatedDecisions(index.undatedIds) > 0) refreshTodayBanner()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not move the decisions of the photos that got a date", e)
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
        val today = _state.value.today
        val count = photosToReview(today)
        PerfLog.d { "todayBanner date=$today photosToReview=$count query=${PerfLog.sinceMs(startedAt)}ms" }
        return if (count > 0) TodayBanner(count) else null
    }

    /**
     * The banner of the photos without a date: there when at least one of them has no decision yet. The photos are those
     * of the index, so the banner appears once the date chain has looked at every file and never counts a photo that
     * may still get a date; a photo that does leaves the count at once. It is worked out again whenever the index or the
     * screen change.
     */
    private fun refreshUndatedBanner(index: OnThisDayIndex?) {
        undatedJob?.cancel()
        undatedJob = viewModelScope.launch {
            val banner = try {
                loadUndatedBanner(index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not work out the banner of the undated photos", e)
                null
            }
            onEvent(CalendarEvent.UndatedBannerChanged(banner))
        }
    }

    private suspend fun loadUndatedBanner(index: OnThisDayIndex?): UndatedBanner? {
        val undated = index?.undatedIds.orEmpty()
        if (undated.isEmpty() || !photoRepository.hasMediaPermission()) return null
        val decisions = swipeRepository.getDecisions(DeckKey.Undated)
        val count = undated.count { it !in decisions }
        return if (count > 0) UndatedBanner(count) else null
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
