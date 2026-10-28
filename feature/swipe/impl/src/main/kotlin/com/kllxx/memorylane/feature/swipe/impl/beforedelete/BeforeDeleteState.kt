package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.net.Uri
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One photo of the grid. A photo that was marked when the screen opened stays in the grid, [marked] or not. */
data class DeleteCell(
    val photoId: Long,
    val uri: Uri,
    val sizeBytes: Long,
    val marked: Boolean,
)

/**
 * [cells] are the photos of the day that were marked for deletion when the screen opened, oldest first; a tap flips
 * [DeleteCell.marked]. While [isDeleting] a delete is on its way and the grid and the button are locked.
 * [showPermanentDialog] is the confirmation below API 30, where there is no trash.
 */
data class BeforeDeleteState(
    val isLoading: Boolean = true,
    val cells: ImmutableList<DeleteCell> = persistentListOf(),
    val isDeleting: Boolean = false,
    val showPermanentDialog: Boolean = false,
) {
    val marked: List<DeleteCell> get() = cells.filter { it.marked }
}
