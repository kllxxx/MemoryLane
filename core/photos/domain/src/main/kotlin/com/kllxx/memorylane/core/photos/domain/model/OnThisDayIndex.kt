package com.kllxx.memorylane.core.photos.domain.model

import android.net.Uri
import java.time.LocalDate
import java.time.MonthDay

/** The oldest photo taken on a month/day, used as its thumbnail. */
data class DayCover(
    val id: Long,
    val uri: Uri,
    val takenOn: LocalDate,
)

/** How many photos were taken on a month/day in earlier years, and the [cover] among them. */
data class DayMemories(
    val count: Int,
    val cover: DayCover,
)

/**
 * Every month/day that has photos from earlier years (up to the look-back limit before [currentYear]),
 * built from one pass over the gallery. A month/day without photos has no entry.
 */
data class OnThisDayIndex(
    val currentYear: Int,
    val entries: Map<MonthDay, DayMemories>,
) {
    /** Photos in the index, i.e. photos that can show up as a memory in the calendar. */
    val totalPhotos: Int get() = entries.values.sumOf { it.count }

    val isEmpty: Boolean get() = entries.isEmpty()
}
