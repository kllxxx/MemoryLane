package com.kllxx.memorylane.feature.swipe.impl.result

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.domain.model.DeckKey
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class ResultViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val statsRepository: StatsRepository,
    private val clock: Clock,
    savedStateHandle: SavedStateHandle,
    errorHandler: ErrorHandler,
) : MviViewModel<ResultState, ResultIntent, ResultEvent, ResultEffect>(
    initialState = savedStateHandle.toRoute<ResultRoute>().toState(),
    reducer = ResultReducer::reduce,
    errorHandler = errorHandler,
) {

    init {
        loadStreak()
    }

    override fun onIntent(intent: ResultIntent) {
        update { ResultReducer.reduce(it, intent) }

        when (intent) {
            ResultIntent.Done -> sendEffect(ResultEffect.NavigateToCalendar)
        }
    }

    /**
     * The chip is for the visit that completed today's day today, a real day. It shows the current streak, which is the
     * one every screen shows: it comes from the [StatsRepository].
     */
    private fun loadStreak() {
        // The undated deck is not a day: it never has a streak chip
        val date = (currentState.deck as? DeckKey.Day)?.date ?: return
        val today = LocalDate.now(clock)
        if (date != today) return
        // Without the streak the screen is still complete, so a failure is only logged
        launchSafe("read the streak") {
            val sessions = swipeRepository.getCompletedSessions().first()
            if (sessions.none { it.date == date && it.completedAt == today }) return@launchSafe
            reduce(ResultEvent.StreakLoaded(statsRepository.streaks.first().current))
        }
    }
}
