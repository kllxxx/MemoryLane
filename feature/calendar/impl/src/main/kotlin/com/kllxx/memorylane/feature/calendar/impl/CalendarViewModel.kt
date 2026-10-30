package com.kllxx.memorylane.feature.calendar.impl

import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.permission.PermissionRules
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The first visible list item and how far it is scrolled off, in pixels. */
data class ScrollAnchor(val index: Int, val offset: Int)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val swipeRepository: SwipeRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val statsRepository: StatsRepository,
    private val photosToReview: PhotosToReview,
    private val adoptDatedDecisions: AdoptDatedDecisions,
    private val todayProvider: TodayProvider,
    errorHandler: ErrorHandler,
) : MviViewModel<CalendarState, CalendarIntent, CalendarEvent, CalendarEffect>(
    // The grid depends on dates only, so it is there from the start; the photos are added when they are known
    initialState = todayProvider.now().let { today -> CalendarState(months = buildYearGrid(today.year), today = today) },
    reducer = CalendarReducer::reduce,
    errorHandler = errorHandler,
) {

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
        get() = currentState.let { listIndexOfMonth(it.months, YearMonth.from(it.today)) }

    private var lastIndex: OnThisDayIndex? = null
    // What the screen last saw of the photo permission: whether the system can still show its prompt
    private var shouldShowRationale = false
    private var partialAccess = false
    private var todayJob: Job? = null
    private var undatedJob: Job? = null

    init {
        CalendarPerf.onViewModelCreated()
        // The real permission state decides whether to load, so a revoked permission never leaves a blank screen
        update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(photoRepository.hasMediaPermission())) }
        observeToday()
        observePhotoIndex()
        observeSortedDays()
        observeStreak()
    }

    override fun onIntent(intent: CalendarIntent) {
        update { CalendarReducer.reduce(it, intent) }

        when (intent) {
            CalendarIntent.ScreenEntered -> {
                // The permission may have been revoked while the app was away
                val granted = photoRepository.hasMediaPermission()
                update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(granted)) }
                // Photos may have been decided in a day or in the undated deck that was open meanwhile
                refreshUndatedBanner(photoRepository.onThisDayIndex.value)
                refreshTodayBanner()
                if (granted) refresh()
            }
            CalendarIntent.LoadDays -> if (currentState.hasPermission) refresh()
            CalendarIntent.RequestPermission -> requestPermission()
            is CalendarIntent.PermissionChecked -> onPermissionChecked(intent)
            is CalendarIntent.PermissionResult -> if (intent.granted) refresh()
            CalendarIntent.OpenUndated -> sendEffect(CalendarEffect.NavigateToUndated)
            is CalendarIntent.OpenDay ->
                // A day that has not happened yet has no photos to sort
                if (!intent.date.isAfter(todayProvider.now())) {
                    sendEffect(CalendarEffect.NavigateToSwipe(intent.date))
                }
        }
    }

    /**
     * The system prompt while it can still be shown, otherwise the settings of the app. It can be shown when the access
     * was never asked for, when the system wants a rationale, and after "select photos", which is no access.
     */
    private fun requestPermission() {
        launchSafe("ask for the photo permission") {
            val asked = runSafe("read the asked permissions", fallback = { emptySet() }) {
                appSettingsRepository.getSettings().first().requestedPermissions
            }
            val canRequest = PermissionRules.canRequest(
                granted = false,
                requestedBefore = AppPermission.Photos in asked,
                shouldShowRationale = shouldShowRationale,
                partial = partialAccess,
            )
            if (canRequest) {
                runSafe("store the asked permissions", fallback = {}) {
                    appSettingsRepository.markPermissionsRequested(setOf(AppPermission.Photos, AppPermission.Location))
                }
                sendEffect(CalendarEffect.RequestPermissionLauncher)
            } else {
                sendEffect(CalendarEffect.OpenAppSettings)
            }
        }
    }

    /**
     * Back from a dialog or the system settings the photos may be readable, or not any more: the screen follows, and a
     * grant given outside the app loads the photos.
     */
    private fun onPermissionChecked(intent: CalendarIntent.PermissionChecked) {
        shouldShowRationale = intent.shouldShowRationale
        partialAccess = intent.partial
        val granted = photoRepository.hasMediaPermission()
        if (granted == currentState.hasPermission) return
        update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(granted)) }
        if (granted) refresh()
    }

    /**
     * The day changed under a screen that stayed open: the highlight, the days that count as past, the banner and,
     * in a new year, the whole grid follow it.
     */
    private fun observeToday() {
        viewModelScope.launch {
            todayProvider.today.collect { today ->
                val current = currentState
                if (today == current.today) return@collect
                val newYear = today.year != current.today.year
                val base = if (newYear) buildYearGrid(today.year) else current.months
                reduce(CalendarEvent.TodayChanged(today, base.withMemories(lastIndex, today)))
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
                val months = currentState.let { it.months.withMemories(index, it.today) }
                reduce(CalendarEvent.IndexLoaded(months, isEmpty = index.isEmpty))
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
        launchSafe("move the decisions of the photos that got a date") {
            if (adoptDatedDecisions(index.undatedIds) > 0) refreshTodayBanner()
        }
    }

    private fun observeSortedDays() {
        launchSafe("load sorted days") {
            swipeRepository.getCompletedSessions().collect { sessions ->
                reduce(CalendarEvent.SortedDatesLoaded(sessions.map { it.date }.toSet()))
                // A day may have just been completed, which takes today's banner away
                refreshTodayBanner()
            }
        }
    }

    /** The streak is the same for every screen: it comes from the [StatsRepository]. */
    private fun observeStreak() {
        launchSafe("load the streak") {
            statsRepository.streaks.collect { reduce(CalendarEvent.StreakLoaded(it.current)) }
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
        todayJob = launchSafe("refresh the banner of today") {
            // Without the photos of today there is nothing to point at
            val banner = runSafe("work out the banner of today", fallback = { null }) { loadTodayBanner() }
            reduce(CalendarEvent.TodayBannerChanged(banner))
        }
    }

    private suspend fun loadTodayBanner(): TodayBanner? {
        val startedAt = PerfLog.nanoTime()
        val today = currentState.today
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
        undatedJob = launchSafe("refresh the banner of the undated photos") {
            val banner = runSafe("work out the banner of the undated photos", fallback = { null }) {
                loadUndatedBanner(index)
            }
            reduce(CalendarEvent.UndatedBannerChanged(banner))
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
        launchSafe("load the photos of the calendar", onError = { reduce(CalendarEvent.LoadFailed) }) {
            CalendarPerf.onRefreshStarted()
            try {
                photoRepository.refreshOnThisDayIndex()
                reduce(CalendarEvent.LoadSucceeded)
            } catch (e: PermissionDeniedException) {
                // Not an empty gallery: the prompt takes over
                update { CalendarReducer.reduce(it, CalendarIntent.PermissionResult(false)) }
            }
        }
    }
}
