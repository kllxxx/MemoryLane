package com.kllxx.memorylane.feature.calendar.impl

import android.net.Uri
import androidx.compose.runtime.Immutable
import java.time.LocalDate

/** Photos taken on this day in earlier years: how many, and the one shown as the thumbnail. */
@Immutable
data class DayMemory(
    val count: Int,
    val coverUri: Uri,
)

/** A day of the calendar. [memory] is null for a day without photos, and until the photos are known. */
@Immutable
data class DayItem(
    val date: LocalDate,
    val memory: DayMemory? = null,
)
