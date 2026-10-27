package com.kllxx.memorylane.feature.swipe.impl.calendar

import com.kllxx.memorylane.core.ui.component.ThumbnailLoad
import com.kllxx.memorylane.core.ui.component.ThumbnailPhase
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Measurements of how long the Calendar takes to load. Logging only: nothing here changes what the screen does.
 * Every line has the tag "CalendarPerf", the thread name and the time since t0, the moment the route was entered.
 */
internal object CalendarPerf {
    private const val TAG = "CalendarPerf"
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val SETTLE_MS = 150L

    // Replaceable so that the tests can read the lines and control the time
    internal var sink: (String) -> Unit = { Timber.tag(TAG).d(it) }
    internal var nanoTime: () -> Long = System::nanoTime
    internal var threadName: () -> String = { Thread.currentThread().name }

    @Volatile private var t0 = 0L
    @Volatile private var viewModelIsFresh = false
    @Volatile private var firstDataLogged = false
    private val firstFrameLogged = MutableStateFlow(false)
    @Volatile private var summaryLogged = false
    private val queries = AtomicInteger()
    private val images = AtomicInteger()
    private val pendingImages = MutableStateFlow(0)

    /** A new ViewModel means a cold open: t0 is its creation. */
    fun onViewModelCreated() {
        reset()
        viewModelIsFresh = true
        log("t0: CalendarViewModel created")
    }

    /** The Calendar route was composed. With a ViewModel that is being reused this is a warm open and the new t0. */
    fun onRouteEntered() {
        if (viewModelIsFresh) {
            viewModelIsFresh = false
            log("route entered (cold)")
        } else {
            reset()
            log("t0: route entered (warm, ViewModel reused)")
        }
    }

    /** Called right before every MediaStore query made for the screen. */
    fun onQuery() {
        queries.incrementAndGet()
    }

    /** A batch of days finished loading: [anyPhotos] tells if the state now holds calendar data. */
    fun onDaysLoaded(days: Int, batchMs: Long, anyPhotos: Boolean) {
        log("loadDays: $days days in ${batchMs}ms, queries so far=${queries.get()}")
        if (anyPhotos && !firstDataLogged) {
            firstDataLogged = true
            log("first state with calendar data emitted")
        }
    }

    fun onYearLoaded() {
        log("rest of the year loaded, queries=${queries.get()}")
    }

    /** The first frame that follows the composition with calendar content, so the content has been drawn. */
    fun onFirstContentFrame() {
        if (firstFrameLogged.value) return
        firstFrameLogged.value = true
        log("first frame with calendar content drawn")
    }

    fun onThumbnail(date: LocalDate, load: ThumbnailLoad) {
        when (load.phase) {
            ThumbnailPhase.Started -> {
                images.incrementAndGet()
                pendingImages.update { it + 1 }
                return
            }
            ThumbnailPhase.Success -> log(
                "thumb ok $date ${load.durationMs}ms source=${load.source} " +
                    "bitmap=${load.bitmapWidth}x${load.bitmapHeight} cell=${load.cellWidthPx}x${load.cellHeightPx}",
            )
            ThumbnailPhase.Error -> log("thumb FAILED $date ${load.durationMs}ms ${load.error}")
            ThumbnailPhase.Cancelled -> log("thumb cancelled $date ${load.durationMs}ms")
        }
        pendingImages.update { (it - 1).coerceAtLeast(0) }
    }

    /** Suspends in the composition; logs the summary once the thumbnails have been quiet for a moment. */
    suspend fun logSummaryWhenThumbnailsLoaded() {
        combine(pendingImages, firstFrameLogged) { pending, frameDrawn -> pending == 0 && frameDrawn }.collectLatest { idle ->
            if (!idle || images.get() == 0 || summaryLogged) return@collectLatest
            delay(SETTLE_MS)
            if (!summaryLogged) {
                summaryLogged = true
                log("all visible thumbnails loaded: images=${images.get()}, queries=${queries.get()}")
            }
        }
    }

    private fun reset() {
        t0 = nanoTime()
        queries.set(0)
        images.set(0)
        pendingImages.value = 0
        firstDataLogged = false
        firstFrameLogged.value = false
        summaryLogged = false
    }

    private fun log(message: String) {
        val sinceT0 = (nanoTime() - t0) / NANOS_PER_MILLI
        sink("[${threadName()}] +${sinceT0}ms $message")
    }
}
