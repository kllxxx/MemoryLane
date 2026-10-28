package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentList

object BeforeDeleteReducer {

    fun reduce(state: BeforeDeleteState, intent: BeforeDeleteIntent): BeforeDeleteState = when (intent) {
        is BeforeDeleteIntent.Toggle -> toggle(state, intent.photoId)
        BeforeDeleteIntent.PermanentDeleteDismissed -> state.copy(showPermanentDialog = false)
        // Handled by the ViewModel, which does the work and reports it with an event
        BeforeDeleteIntent.DeleteClicked,
        BeforeDeleteIntent.PermanentDeleteConfirmed,
        is BeforeDeleteIntent.WritePermissionResult,
        is BeforeDeleteIntent.SystemDeleteResult,
        -> state
    }

    internal fun reduce(state: BeforeDeleteState, event: BeforeDeleteEvent): BeforeDeleteState = when (event) {
        is BeforeDeleteEvent.CellsLoaded -> state.copy(cells = event.cells.toImmutableList(), isLoading = false)
        is BeforeDeleteEvent.DeletingChanged -> state.copy(isDeleting = event.deleting)
        is BeforeDeleteEvent.PermanentDialogChanged -> state.copy(showPermanentDialog = event.shown)
    }

    private fun toggle(state: BeforeDeleteState, photoId: Long): BeforeDeleteState {
        // The grid is locked while a delete is on its way
        if (state.isDeleting) return state
        val index = state.cells.indexOfFirst { it.photoId == photoId }
        if (index < 0) return state
        val cell = state.cells[index]
        return state.copy(cells = state.cells.toPersistentList().set(index, cell.copy(marked = !cell.marked)))
    }
}

/** Results produced by [BeforeDeleteViewModel] itself. The UI never sends these. */
internal sealed interface BeforeDeleteEvent {
    data class CellsLoaded(val cells: List<DeleteCell>) : BeforeDeleteEvent
    data class DeletingChanged(val deleting: Boolean) : BeforeDeleteEvent
    data class PermanentDialogChanged(val shown: Boolean) : BeforeDeleteEvent
}
