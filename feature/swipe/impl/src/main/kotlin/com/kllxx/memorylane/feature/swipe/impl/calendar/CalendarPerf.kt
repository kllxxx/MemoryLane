package com.kllxx.memorylane.feature.swipe.impl.calendar

import com.kllxx.memorylane.core.common.PerfLog
import com.kllxx.memorylane.core.ui.component.ThumbnailLoad
import com.kllxx.memorylane.core.ui.component.ThumbnailPhase
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Measurements of how long the Calendar takes to load, on top of [PerfLog]. Every line has the thread name and the
 * time since t0, the moment the route was entered (or the ViewModel created on a cold open).
 * Nothing runs when [PerfLog] is off, which is always the case in release.
 */
internal object CalendarPerf {
    private const val NANOS_PER_MILLI = 1_000_000L
    private const val SETTLE_MS = 150L

    // Replaceable so that the tests can control the time
    internal var nanoTime: () -> Long = PerfLog::nanoTime

    @Volatile private var t0 = 0L
    @Volatile private var viewModelIsFresh = false
    @Volatile private var indexShownLogged = false
    @Volatile private var summaryLogged = false
    private val firstFrameLogged = MutableStateFlow(false)
    private val images = AtomicInteger()
    private val pendingImages = MutableStateFlow(0)

    /** A new ViewModel means a cold open: t0 is its creation. */
    fun onViewModelCreated() {
        if (!PerfLog.isEnabled()) return
        reset()
        viewModelIsFresh = true
        log("t0: CalendarViewModel created")
    }

    /** The Calendar route was composed. With a ViewModel that is being reused this is a warm open and the new t0. */
    fun onRouteEntered() {
        if (!PerfLog.isEnabled()) return
        if (viewModelIsFresh) {
            viewModelIsFresh = false
            log("route entered (cold)")
        } else {
            reset()
            log("t0: route entered (warm, ViewModel reused)")
        }
    }

    fun onRefreshStarted() {
        if (!PerfLog.isEnabled()) return
        log("photo index refresh started")
    }

    /** The index reached the state: the screen goes from Loading to Empty or Content. */
    fun onIndexShown(isEmpty: Boolean, photos: Int, days: Int) {
        if (!PerfLog.isEnabled()) return
        if (indexShownLogged) {
            log("index replaced: photos=$photos days=$days")
            return
        }
        indexShownLogged = true
        log("Loading -> ${if (isEmpty) "Empty" else "Content"}: photos=$photos days=$days")
    }

    /** The first frame that follows the composition with calendar content, so the content has been drawn. */
    fun onFirstContentFrame() {
        if (!PerfLog.isEnabled() || firstFrameLogged.value) return
        firstFrameLogged.value = true
        log("first frame with calendar content drawn")
    }

    fun onThumbnail(date: LocalDate, load: ThumbnailLoad) {
        if (!PerfLog.isEnabled()) return
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
        if (!PerfLog.isEnabled()) return
        combine(pendingImages, firstFrameLogged) { pending, frameDrawn -> pending == 0 && frameDrawn }.collectLatest { idle ->
            if (!idle || images.get() == 0 || summaryLogged) return@collectLatest
            delay(SETTLE_MS)
            if (!summaryLogged) {
                summaryLogged = true
                log("all visible thumbnails loaded: images=${images.get()}")
            }
        }
    }

    private fun reset() {
        t0 = nanoTime()
        images.set(0)
        pendingImages.value = 0
        indexShownLogged = false
        firstFrameLogged.value = false
        summaryLogged = false
    }

    private fun log(message: String) {
        val sinceT0 = (nanoTime() - t0) / NANOS_PER_MILLI
        PerfLog.d { "+${sinceT0}ms $message" }
    }
}
