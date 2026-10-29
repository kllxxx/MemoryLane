package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.net.Uri
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One photo of the grid. A photo that was marked when the screen opened stays in the grid, [marked] or not. */
data class DeleteCell(
    val photoId: Long,
    val uri: Uri,
    val sizeBytes: Long,
    val marked: Boolean,
    /** The day it was decided on. */
    val date: LocalDate,
)

/**
 * [cells] are the photos of all days that were marked for deletion when the screen opened; a tap flips
 * [DeleteCell.marked]. [currentDate] is the day that was swiped just now, its group comes first.
 * While [isDeleting] a delete is on its way and the grid and the button are locked.
 * [showPermanentDialog] is the confirmation below API 30, where there is no trash.
 */
data class BeforeDeleteState(
    val isLoading: Boolean = true,
    val cells: ImmutableList<DeleteCell> = persistentListOf(),
    val isDeleting: Boolean = false,
    val showPermanentDialog: Boolean = false,
    val currentDate: LocalDate = LocalDate.EPOCH,
) {
    val marked: List<DeleteCell> get() = cells.filter { it.marked }

    /** The cells by day: the current day first, then the earlier ones, newest first. Oldest photos first inside a day. */
    val groups: List<DeleteGroup>
        get() = cells.groupBy { it.date }.entries
            .sortedWith(compareByDescending<Map.Entry<LocalDate, List<DeleteCell>>> { it.key == currentDate }.thenByDescending { it.key })
            .map { DeleteGroup(it.key, isCurrent = it.key == currentDate, cells = it.value) }
}

/** The marked photos of one day. */
data class DeleteGroup(val date: LocalDate, val isCurrent: Boolean, val cells: List<DeleteCell>)
