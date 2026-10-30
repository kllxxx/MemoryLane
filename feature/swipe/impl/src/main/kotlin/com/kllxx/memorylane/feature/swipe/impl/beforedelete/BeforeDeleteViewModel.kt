package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.common.UiError
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.DeletionOutcome
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.DeleteResult
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.repository.getPhotos
import com.kllxx.memorylane.core.photos.domain.usecase.AdoptDatedDecisions
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.toDeckKey
import com.kllxx.memorylane.feature.swipe.api.BeforeDeleteRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * «Перед удалением». The photos marked for deletion, of the current day and of the days before whose dialog was
 * cancelled, are a grid grouped by day; a tap flips a mark and saves it at once.
 *
 * Deleting on API 30+ is a trash request the user confirms in the system dialog. What it is about (the photo ids and
 * their sizes, read before the request) is written to the [SavedStateHandle] before the dialog opens and removed only
 * once the deletion is recorded, so a result that arrives after a rotation or after the process died is still
 * understood. Recording is one database transaction that only touches photos still marked DELETE, so a result that
 * arrives twice counts nothing the second time (see [SwipeRepository.confirmDeletion]).
 */
@HiltViewModel
class BeforeDeleteViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val photoRepository: PhotoRepository,
    private val adoptDatedDecisions: AdoptDatedDecisions,
    private val clock: Clock,
    @ApplicationContext private val context: Context,
    private val savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
) : MviViewModel<BeforeDeleteState, BeforeDeleteIntent, BeforeDeleteEvent, BeforeDeleteEffect>(
    initialState = BeforeDeleteState(currentDeck = savedStateHandle.toRoute<BeforeDeleteRoute>().toDeckKey()),
    reducer = BeforeDeleteReducer::reduce,
    errorHandler = errorHandler,
) {

    private val deck: DeckKey = savedStateHandle.toRoute<BeforeDeleteRoute>().toDeckKey()

    private var loadJob: Job? = null

    // A deletion is recorded once: this guards a result that is delivered twice while the first is still being recorded
    private var confirming = false

    // Set when the screen has handed over to «Итоги», so nothing that arrives later can start it again
    private var finished = false

    init {
        loadJob = load()
    }

    override fun onIntent(intent: BeforeDeleteIntent) {
        val before = currentState
        update { BeforeDeleteReducer.reduce(it, intent) }

        when (intent) {
            is BeforeDeleteIntent.Toggle -> onToggled(before, intent.photoId)
            BeforeDeleteIntent.DeleteClicked -> onDeleteClicked(before)
            BeforeDeleteIntent.PermanentDeleteConfirmed -> onPermanentDeleteConfirmed(before)
            BeforeDeleteIntent.PermanentDeleteDismissed -> Unit
            BeforeDeleteIntent.Retry -> {
                loadJob?.cancel()
                loadJob = load()
            }
            is BeforeDeleteIntent.WritePermissionResult ->
                if (intent.granted) {
                    deletePermanently(before.marked)
                } else {
                    reduce(BeforeDeleteEvent.DeletingChanged(false))
                    sendEffect(BeforeDeleteEffect.ShowPermissionDenied)
                }
            is BeforeDeleteIntent.SystemDeleteResult -> onSystemDeleteResult(intent.confirmed)
        }
    }

    // --- loading ---

    private fun load() = launchSafe(
        what = "load the marked photos",
        onError = { reduce(BeforeDeleteEvent.LoadFailed(UiError(R.string.before_delete_load_error))) },
    ) {
        try {
            // A photo that was marked in the undated deck and has a date now is a mark of its day
            adoptDatedMarks()
            val pending = swipeRepository.getPendingDeletes()
            // A trash request is open: the marked photos may already be in the trash, so none of them is "stale"
            val awaitingResult = savedStateHandle.contains(PENDING_IDS)
            val savedIds = savedStateHandle.get<LongArray>(GRID_IDS)
            val savedDecks = savedStateHandle.get<ArrayList<String>>(GRID_DATES)
            val gridByDeck: Map<DeckKey, Set<Long>> = if (savedIds != null && savedDecks != null && savedDecks.size == savedIds.size) {
                savedIds.indices.groupBy({ decodeDeck(savedDecks[it]) }, { savedIds[it] }).mapValues { it.value.toSet() }
            } else {
                pending
            }
            val cells = gridByDeck.keys.flatMap { deck ->
                val photos = photoRepository.getPhotos(deck)
                val decisions = swipeRepository.getDecisions(deck)
                val inLibrary = photos.mapTo(HashSet()) { it.id }
                // A marked photo that is no longer in the library (deleted elsewhere) cannot be deleted again
                if (!awaitingResult) {
                    val missing = decisions.filter { (id, decision) -> decision == PhotoDecision.DELETE && id !in inLibrary }.keys
                    // A photo of the undated deck that is gone from it may have got a date: it is not deleted, it is on its day now
                    val stale = if (deck == DeckKey.Undated && missing.isNotEmpty()) {
                        missing - photoRepository.getPhotoSizes(missing).keys
                    } else {
                        missing
                    }
                    stale.forEach(::removeStaleDecision)
                }
                photos.filter { it.id in gridByDeck.getValue(deck) }.map {
                    DeleteCell(it.id, it.uri, it.sizeBytes, marked = decisions[it.id] == PhotoDecision.DELETE, deck = deck)
                }
            }
            savedStateHandle[GRID_IDS] = cells.map { it.photoId }.toLongArray()
            savedStateHandle[GRID_DATES] = ArrayList(cells.map { encodeDeck(it.deck) })
            if (cells.isEmpty() && !awaitingResult) {
                // Nothing to review, so the screen is not shown at all
                sendEffect(BeforeDeleteEffect.NavigateToCalendar)
                return@launchSafe
            }
            reduce(BeforeDeleteEvent.CellsLoaded(cells))
            // The system dialog was open when the screen was recreated: the button stays locked until it answers
            if (awaitingResult) reduce(BeforeDeleteEvent.DeletingChanged(true))
        } catch (e: PermissionDeniedException) {
            sendEffect(BeforeDeleteEffect.NavigateToCalendar)
        }
    }

    /**
     * Without it the grid is still right for everything but a photo that got a date lately, so a failure only logs.
     * Having no access to the photos is not a failure to skip: it ends the load.
     */
    private suspend fun adoptDatedMarks() {
        val denied = runSafe("move the decisions of the photos that got a date", fallback = { null }) {
            try {
                adoptDatedDecisions(photoRepository.onThisDayIndex.value?.undatedIds.orEmpty())
                null
            } catch (e: PermissionDeniedException) {
                e
            }
        }
        if (denied != null) throw denied
    }

    private fun removeStaleDecision(photoId: Long) = launchSafe("remove the stale decision of photo $photoId") {
        swipeRepository.removeDecision(photoId)
    }

    // --- marks ---

    private fun onToggled(before: BeforeDeleteState, photoId: Long) {
        if (before.isDeleting) return
        val cell = before.cells.firstOrNull { it.photoId == photoId } ?: return
        val decision = if (cell.marked) PhotoDecision.KEEP else PhotoDecision.DELETE
        launchSafe("save the $decision decision of photo $photoId") {
            swipeRepository.saveDecision(photoId, cell.deck, decision)
        }
    }

    // --- deleting ---

    private fun onDeleteClicked(before: BeforeDeleteState) {
        if (before.isDeleting || before.isLoading || before.loadError != null || finished) return
        val marked = before.marked
        when {
            marked.isEmpty() -> finishWithoutDeleting()
            trashAvailable() -> startTrashRequest(marked)
            // No trash and no system dialog below API 30: our own confirmation comes first
            else -> reduce(BeforeDeleteEvent.PermanentDialogChanged(true))
        }
    }

    private fun startTrashRequest(marked: List<DeleteCell>) {
        reduce(BeforeDeleteEvent.DeletingChanged(true))
        launchSafe(
            what = "ask for the trash request",
            onError = {
                // SecurityException, IllegalArgumentException and the like: nothing was trashed, the marks stay
                clearPending()
                reduce(BeforeDeleteEvent.DeletingChanged(false))
                sendEffect(BeforeDeleteEffect.ShowError(DeleteError))
            },
        ) {
            val ids = marked.map { it.photoId }
            val bytes = sizesBeforeRequest(marked)
            // Before the dialog opens: after a recreation the result is understood from this
            savedStateHandle[PENDING_IDS] = ids.toLongArray()
            savedStateHandle[PENDING_BYTES] = ids.map { bytes.getValue(it) }.toLongArray()
            when (val result = photoRepository.deletePhotos(ids)) {
                is DeleteResult.RequiresConfirmation ->
                    sendEffect(BeforeDeleteEffect.LaunchTrashRequest(result.intentSender))
                // Nothing asked for a confirmation, so the photos are already handled
                DeleteResult.Deleted -> confirm(bytes, PhotoDecision.TRASHED)
            }
        }
    }

    private fun onSystemDeleteResult(confirmed: Boolean) {
        if (!confirmed) {
            // Cancelled in the system dialog: nothing changes and the user can try again
            clearPending()
            reduce(BeforeDeleteEvent.DeletingChanged(false))
            return
        }
        viewModelScope.launch {
            // After a recreation the answer can come before the grid is loaded; the pending request does not need it
            loadJob?.join()
            val ids = savedStateHandle.get<LongArray>(PENDING_IDS)
            val bytes = savedStateHandle.get<LongArray>(PENDING_BYTES)
            // No request open: this answer was already handled
            if (ids == null || bytes == null || ids.size != bytes.size) return@launch
            confirm(ids.zip(bytes.toList()).toMap(), PhotoDecision.TRASHED)
        }
    }

    private fun onPermanentDeleteConfirmed(before: BeforeDeleteState) {
        reduce(BeforeDeleteEvent.PermanentDialogChanged(false))
        if (before.isDeleting) return
        if (!hasWritePermission()) {
            // Deleting other apps' photos below API 30 needs it, and it is asked for only now
            reduce(BeforeDeleteEvent.DeletingChanged(true))
            sendEffect(BeforeDeleteEffect.RequestWritePermission)
        } else {
            deletePermanently(before.marked)
        }
    }

    private fun deletePermanently(marked: List<DeleteCell>) {
        if (marked.isEmpty()) {
            reduce(BeforeDeleteEvent.DeletingChanged(false))
            return
        }
        reduce(BeforeDeleteEvent.DeletingChanged(true))
        launchSafe(
            what = "delete the marked photos",
            onError = {
                reduce(BeforeDeleteEvent.DeletingChanged(false))
                sendEffect(BeforeDeleteEffect.ShowError(DeleteError))
            },
        ) {
            val ids = marked.map { it.photoId }
            val bytes = sizesBeforeRequest(marked)
            photoRepository.deletePhotos(ids)
            confirm(bytes, PhotoDecision.DELETED)
        }
    }

    /** The sizes are read before the request: afterwards trashed photos are no longer in a normal query. */
    private suspend fun sizesBeforeRequest(marked: List<DeleteCell>): Map<Long, Long> {
        val read = photoRepository.getPhotoSizes(marked.map { it.photoId })
        return marked.associate { it.photoId to (read[it.photoId] ?: it.sizeBytes) }
    }

    /**
     * Records the deletion in one transaction, refreshes the index and hands over to «Итоги» with what was really
     * recorded. Photos that are no longer marked are not counted, which also makes a repeated call harmless.
     */
    private suspend fun confirm(bytesById: Map<Long, Long>, decision: PhotoDecision) {
        if (finished || confirming) return
        confirming = true
        try {
            runSafe(
                what = "record the deletion",
                fallback = {
                    // The pending request stays: the photos may be gone already, so the result is not asked for again
                    confirming = false
                    reduce(BeforeDeleteEvent.DeletingChanged(false))
                    sendEffect(BeforeDeleteEffect.ShowError(DeleteError))
                },
            ) {
                val outcome = swipeRepository.confirmDeletion(bytesById, decision)
                clearPending()
                finished = true
                refreshPhotoIndex()
                handOver(outcome)
            }
        } catch (e: CancellationException) {
            confirming = false
            throw e
        }
    }

    /** Nothing is marked any more: the day is finished without deleting anything. */
    private fun finishWithoutDeleting() {
        viewModelScope.launch {
            // The undated deck is not a day: it has no session to complete
            val day = (deck as? DeckKey.Day)?.date
            if (day != null) {
                // The user has finished, so a failed write must not keep them on this screen
                runSafe("save the session of $day", fallback = {}) {
                    val existing = swipeRepository.getCompletedSessions().first().firstOrNull { it.date == day }
                    swipeRepository.saveSession(
                        SwipeSession(
                            date = day,
                            completed = true,
                            savedBytes = existing?.savedBytes ?: 0,
                            deletedCount = existing?.deletedCount ?: 0,
                            // The date the day is really completed on; a day that was completed on its own date keeps it
                            completedAt = SessionCompletion.completedAt(existing, day, LocalDate.now(clock)),
                        ),
                    )
                }
            }
            finished = true
            handOver(DeletionOutcome(deletedCount = 0, freedBytes = 0))
        }
    }

    private suspend fun handOver(outcome: DeletionOutcome) {
        val decisions = runSafe("read the decisions of $deck", fallback = { emptyMap() }) {
            swipeRepository.getDecisions(deck)
        }
        sendEffect(
            BeforeDeleteEffect.NavigateToResult(
                deck = deck,
                deletedCount = outcome.deletedCount,
                freedBytes = outcome.freedBytes,
                keptCount = decisions.count { it.value == PhotoDecision.KEEP },
                totalCount = decisions.size,
            ),
        )
    }

    private suspend fun refreshPhotoIndex() {
        // The deleted photos must be gone from the Calendar when it opens; only a failure is allowed to skip this
        runSafe("refresh the photo index after the deletion", fallback = {}) {
            photoRepository.refreshOnThisDayIndex()
        }
    }

    private fun clearPending() {
        savedStateHandle.remove<LongArray>(PENDING_IDS)
        savedStateHandle.remove<LongArray>(PENDING_BYTES)
    }

    private fun trashAvailable() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private fun hasWritePermission() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ||
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    internal companion object {
        /** The delete failed with an error, as opposed to the user cancelling. */
        private val DeleteError = UiError(R.string.before_delete_error)

        /** The photos of the grid, kept so that a recreated screen shows the unmarked ones as well. */
        const val GRID_IDS = "grid_ids"
        const val GRID_DATES = "grid_dates"
        private const val UNDATED_DECK = "undated"
        private const val DAY_PREFIX = "day:"

        /** "day:2026-10-15" or "undated": a deck as the saved state keeps it. */
        fun encodeDeck(deck: DeckKey): String = when (deck) {
            is DeckKey.Day -> DAY_PREFIX + deck.date
            DeckKey.Undated -> UNDATED_DECK
        }

        fun decodeDeck(value: String): DeckKey =
            if (value == UNDATED_DECK) DeckKey.Undated else DeckKey.Day(LocalDate.parse(value.removePrefix(DAY_PREFIX)))

        /** The trash request that is open: the photo ids and their sizes, in the same order. */
        const val PENDING_IDS = "pending_ids"
        const val PENDING_BYTES = "pending_bytes"
    }
}
