package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.net.Uri
import com.kllxx.memorylane.core.domain.model.DeckKey
import java.time.LocalDate
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/** One photo of the grid. A photo that was marked when the screen opened stays in the grid, [marked] or not. */
data class DeleteCell(
    val photoId: Long,
    val uri: Uri,
    val sizeBytes: Long,
    val marked: Boolean,
    /** The deck it was decided in. */
    val deck: DeckKey,
)

/**
 * [cells] are the photos of all days that were marked for deletion when the screen opened; a tap flips
 * [DeleteCell.marked]. [currentDeck] is the deck that was swiped just now, its group comes first.
 * While [isDeleting] a delete is on its way and the grid and the button are locked.
 * [showPermanentDialog] is the confirmation below API 30, where there is no trash.
 */
data class BeforeDeleteState(
    val isLoading: Boolean = true,
    val cells: ImmutableList<DeleteCell> = persistentListOf(),
    val isDeleting: Boolean = false,
    val showPermanentDialog: Boolean = false,
    val currentDeck: DeckKey = DeckKey.Day(LocalDate.EPOCH),
) {
    val marked: List<DeleteCell> get() = cells.filter { it.marked }

    /** The cells by deck: the current deck first, then the earlier days, newest first, and the undated deck last. */
    val groups: List<DeleteGroup>
        get() = cells.groupBy { it.deck }.entries
            .sortedWith(
                compareByDescending<Map.Entry<DeckKey, List<DeleteCell>>> { it.key == currentDeck }
                    .thenBy { it.key is DeckKey.Undated }
                    .thenByDescending { (it.key as? DeckKey.Day)?.date },
            )
            .map { DeleteGroup(it.key, isCurrent = it.key == currentDeck, cells = it.value) }
}

/** The marked photos of one deck. */
data class DeleteGroup(val deck: DeckKey, val isCurrent: Boolean, val cells: List<DeleteCell>)
