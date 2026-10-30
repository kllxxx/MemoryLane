package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.model.PhotoDecision
import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.SessionCompletion
import com.kllxx.memorylane.core.photos.domain.exception.PermissionDeniedException
import com.kllxx.memorylane.core.photos.domain.model.Photo
import com.kllxx.memorylane.core.photos.domain.repository.PhotoRepository
import com.kllxx.memorylane.core.photos.domain.usecase.AdoptDatedDecisions
import com.kllxx.memorylane.core.photos.domain.repository.getPhotos
import com.kllxx.memorylane.feature.swipe.api.SwipeRoute
import com.kllxx.memorylane.feature.swipe.impl.swipe.mapper.toDeckPhoto
import com.kllxx.memorylane.feature.swipe.impl.toDeckKey
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class SwipeViewModel @Inject constructor(
    private val photoRepository: PhotoRepository,
    private val adoptDatedDecisions: AdoptDatedDecisions,
    private val swipeRepository: SwipeRepository,
    private val statsRepository: StatsRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val clock: Clock,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val deckKey: DeckKey = savedStateHandle.toRoute<SwipeRoute>().toDeckKey()

    private val _state = MutableStateFlow(SwipeState(deckKey = deckKey, isLoading = true))
    val state: StateFlow<SwipeState> = _state.asStateFlow()

    private val _effects = Channel<SwipeEffect>(Channel.BUFFERED)
    val effects: Flow<SwipeEffect> = _effects.receiveAsFlow()

    // Geocoding is slow, so the places are looked up in the background, the photo on top first, and a lookup that
    // is started again (the user jumped) skips what has already arrived
    private var locationJob: Job? = null
    private val resolvedLocations = mutableSetOf<Long>()

    // Read before anything is saved, so the loading state can never overwrite them
    private val restoredIndex: Int? = savedStateHandle[CURRENT_INDEX_KEY]
    private val restoredUndo: List<UndoEntry> =
        savedStateHandle.get<ArrayList<String>>(UNDO_STACK_KEY).orEmpty().mapNotNull { decodeUndoEntry(it) }

    init {
        observeButtonsMode()
        loadPhotos()
    }

    fun onIntent(intent: SwipeIntent) {
        val before = _state.value
        _state.update { SwipeReducer.reduce(it, intent) }
        val after = _state.value
        persistState(after)

        when (intent) {
            is SwipeIntent.Swipe -> onSwiped(before, after)
            SwipeIntent.Undo -> onUndone(before, after)
            is SwipeIntent.JumpTo -> if (after.currentIndex != before.currentIndex) restartLocationLookup()
            SwipeIntent.ToggleButtonsMode -> setButtonsMode(!before.buttonsMode)
            SwipeIntent.Share -> before.currentPhoto?.let { _effects.trySend(SwipeEffect.ShareImage(it.photo.uri)) }
            SwipeIntent.OpenInGallery ->
                before.currentPhoto?.let { _effects.trySend(SwipeEffect.OpenInGallery(it.photo.uri)) }
            SwipeIntent.Close -> _effects.trySend(SwipeEffect.NavigateToCalendar)
            SwipeIntent.ReturnedToDeck -> reloadDecisions()
        }
    }

    private fun onEvent(event: SwipeEvent) {
        _state.update { SwipeReducer.reduce(it, event) }
        persistState(_state.value)
    }

    /** Survives process death; not written while loading, when the index is still the reset 0. */
    private fun persistState(state: SwipeState) {
        if (state.isLoading) return
        savedStateHandle[CURRENT_INDEX_KEY] = state.currentIndex
        savedStateHandle[UNDO_STACK_KEY] = ArrayList(state.undoStack.map { encodeUndoEntry(it) })
    }

    private fun onSwiped(before: SwipeState, after: SwipeState) {
        // A swipe that changed nothing (another direction, nothing on top) is not a decision
        if (after == before) return
        val swiped = before.currentPhoto ?: return
        val decision = after.deck.firstOrNull { it.id == swiped.id }?.decision ?: return
        markReviewed(swiped.id)
        saveDecision(swiped.id, decision)
        if (after.isFinished && !before.isFinished) onAllSwiped(after)
    }

    private fun onUndone(before: SwipeState, after: SwipeState) {
        if (after == before) return
        val entry = before.undoStack.lastOrNull() ?: return
        // The photo is gone from the deck, so there is nothing stored to put back
        if (before.deck.none { it.id == entry.photoId }) return
        if (entry.previous == null) removeDecision(entry.photoId) else saveDecision(entry.photoId, entry.previous)
        restartLocationLookup()
    }

    /** Only called once, from init: the screen has no way to start a second load. */
    private fun loadPhotos() {
        viewModelScope.launch {
            val event = try {
                val photos = photoRepository.getPhotos(deckKey)
                // Photos that got a date since they were decided in the undated deck keep their decision on their day
                if (deckKey is DeckKey.Day) adoptDatedDecisions.forDay(deckKey.date, photos.map { it.id })
                val decisions = swipeRepository.getDecisions(deckKey)
                val trashed = loadTrashed(photos, decisions)
                val locale = Locale.getDefault()
                SwipeEvent.PhotosLoaded(
                    // A photo that is back in the library, e.g. restored from the trash, is a photo to decide on again
                    deck = photos.map { it.toDeckPhoto(decisions[it.id]?.takeIf(::isLive), locale) },
                    trashed = trashed.map { it.toDeckPhoto(PhotoDecision.TRASHED, locale) },
                    restoredIndex = restoredIndex,
                    restoredUndo = restoredUndo,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: PermissionDeniedException) {
                // Not an empty day: nothing is recorded and the user stays to grant access
                _effects.trySend(SwipeEffect.PermissionRequired)
                return@launch
            } catch (e: Exception) {
                // A failed load is not an empty day, so no session is recorded for it
                Log.e(TAG, "Could not load the photos for $deckKey", e)
                _effects.trySend(SwipeEffect.NavigateToCalendar)
                return@launch
            }
            onEvent(event)
            // Nothing to sort: an empty day has no activity, so no session is recorded for it
            if (_state.value.isFinished) {
                _effects.trySend(SwipeEffect.NavigateToCalendar)
                return@launch
            }
            restartLocationLookup()
        }
    }

    private fun reloadDecisions() = launchLogged("Could not read the decisions of $deckKey again") {
        onEvent(SwipeEvent.DecisionsReloaded(swipeRepository.getDecisions(deckKey)))
    }

    private fun isLive(decision: PhotoDecision) = decision != PhotoDecision.TRASHED && decision != PhotoDecision.DELETED

    /** The trashed photos of the day for the strip. Without them the deck still works, so a failure only logs. */
    private suspend fun loadTrashed(photos: List<Photo>, decisions: Map<Long, PhotoDecision>): List<Photo> {
        val inLibrary = photos.mapTo(mutableSetOf()) { it.id }
        val ids = decisions.filter { (id, decision) -> decision == PhotoDecision.TRASHED && id !in inLibrary }.keys
        if (ids.isEmpty()) return emptyList()
        return try {
            photoRepository.getTrashedPhotos(ids)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Could not load the trashed photos of $deckKey", e)
            emptyList()
        }
    }

    private fun restartLocationLookup() {
        val state = _state.value
        if (state.deck.isEmpty()) return
        val start = state.currentIndex.coerceIn(0, state.deck.lastIndex)
        // The photo on top first, then the ones after it, then those before it
        val unresolved = (state.deck.drop(start) + state.deck.take(start))
            .map { it.photo }
            .filter { it.location == null && it.id !in resolvedLocations }
        if (unresolved.isEmpty()) return
        locationJob?.cancel()
        locationJob = viewModelScope.launch {
            try {
                photoRepository.resolveLocations(unresolved).collect { found ->
                    resolvedLocations += found.photoId
                    onEvent(SwipeEvent.LocationLoaded(found.photoId, found.location))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not look up the locations of $deckKey", e)
            }
        }
    }

    private fun observeButtonsMode() {
        launchLogged("Could not read the buttons mode") {
            appSettingsRepository.getSettings()
                .map { it.swipeButtonsEnabled }
                .distinctUntilChanged()
                .collect { onEvent(SwipeEvent.ButtonsModeChanged(it)) }
        }
    }

    private fun setButtonsMode(enabled: Boolean) =
        launchLogged("Could not save the buttons mode") { appSettingsRepository.setSwipeButtonsEnabled(enabled) }

    private fun markReviewed(photoId: Long) =
        launchLogged("Could not mark photo $photoId as reviewed") { statsRepository.markReviewed(photoId) }

    private fun saveDecision(photoId: Long, decision: PhotoDecision) =
        launchLogged("Could not save the $decision decision of photo $photoId") {
            swipeRepository.saveDecision(photoId, deckKey, decision)
        }

    private fun removeDecision(photoId: Long) =
        launchLogged("Could not remove the decision of photo $photoId") { swipeRepository.removeDecision(photoId) }

    private fun launchLogged(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, failure, e)
            }
        }
    }

    /**
     * Every photo of the day has a decision, a mark for deletion included, so the day is completed now. Marks left
     * in any day lead to «Перед удалением»; without any there is nothing to delete.
     */
    private fun onAllSwiped(state: SwipeState) {
        viewModelScope.launch {
            // The marks of this day are in the deck already; the stored ones add those of the days before
            var hasDeletes = state.deck.any { it.decision == PhotoDecision.DELETE }
            // The user has finished swiping, so a failed session write must not keep them on this screen.
            // The undated deck is not a day: it has no session to complete and never counts for a streak.
            val day = (state.deckKey as? DeckKey.Day)?.date
            if (day != null) {
                try {
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
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not save the session of $day", e)
                }
            }
            if (!hasDeletes) {
                hasDeletes = try {
                    swipeRepository.getPendingDeletes().isNotEmpty()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Could not read the pending deletions", e)
                    false
                }
            }
            _effects.trySend(
                if (hasDeletes) {
                    SwipeEffect.NavigateToBeforeDelete(state.deckKey, totalPhotosCount = state.deck.size)
                } else {
                    SwipeEffect.NavigateToResult(
                        state.deckKey,
                        keptCount = state.deck.count { it.decision == PhotoDecision.KEEP },
                        totalCount = state.deck.size + state.trashed.size,
                    )
                },
            )
        }
    }

    internal companion object {
        const val TAG = "SwipeViewModel"
        const val CURRENT_INDEX_KEY = "current_index"
        const val UNDO_STACK_KEY = "undo_stack"
        private const val NO_DECISION = "-"

        /** "photoId:DECISION", with "-" for a photo that had no decision. */
        fun encodeUndoEntry(entry: UndoEntry): String = "${entry.photoId}:${entry.previous?.name ?: NO_DECISION}"

        fun decodeUndoEntry(value: String): UndoEntry? {
            val (id, name) = value.split(':', limit = 2).takeIf { it.size == 2 } ?: return null
            val photoId = id.toLongOrNull() ?: return null
            if (name == NO_DECISION) return UndoEntry(photoId, null)
            val decision = PhotoDecision.entries.firstOrNull { it.name == name } ?: return null
            return UndoEntry(photoId, decision)
        }
    }
}
