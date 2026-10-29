package com.kllxx.memorylane.feature.swipe.impl.result

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val statsRepository: StatsRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(savedStateHandle.toRoute<ResultRoute>().toState())
    val state: StateFlow<ResultState> = _state.asStateFlow()

    private val _effects = Channel<ResultEffect>(Channel.BUFFERED)
    val effects: Flow<ResultEffect> = _effects.receiveAsFlow()

    init {
        loadStreak()
    }

    fun onIntent(intent: ResultIntent) {
        when (intent) {
            ResultIntent.Done -> _effects.trySend(ResultEffect.NavigateToCalendar)
        }
    }

    /**
     * The chip is for the visit that completed today's day today, a real day. It shows the current streak, which is the
     * one every screen shows: it comes from the [StatsRepository].
     */
    private fun loadStreak() {
        // The undated deck is not a day: it never has a streak chip
        val date = (_state.value.deck as? DeckKey.Day)?.date ?: return
        val today = LocalDate.now(clock)
        if (date != today) return
        viewModelScope.launch {
            try {
                val sessions = swipeRepository.getCompletedSessions().first()
                if (sessions.none { it.date == date && it.completedAt == today }) return@launch
                val current = statsRepository.streaks.first().current
                _state.update { it.copy(streakDays = current.takeIf { days -> days > 0 }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Without the streak the screen is still complete
                Log.e(TAG, "Could not read the streak", e)
            }
        }
    }

    internal companion object {
        const val TAG = "ResultViewModel"
    }
}

sealed interface ResultIntent {
    data object Done : ResultIntent
}

sealed interface ResultEffect {
    data object NavigateToCalendar : ResultEffect
}
