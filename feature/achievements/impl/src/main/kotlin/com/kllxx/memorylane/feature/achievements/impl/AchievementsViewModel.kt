package com.kllxx.memorylane.feature.achievements.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.model.StreakGoal
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    private val _effects = Channel<AchievementsEffect>(Channel.BUFFERED)
    val effects: Flow<AchievementsEffect> = _effects.receiveAsFlow()

    private var loadJobs: List<Job> = emptyList()

    fun onIntent(intent: AchievementsIntent) {
        val before = _state.value
        _state.update { AchievementsReducer.reduce(it, intent) }

        when (intent) {
            AchievementsIntent.Load -> load()
            is AchievementsIntent.SetStreakGoal ->
                setStreakGoal(intent.goal, previous = before.streakGoal)
        }
    }

    private fun onEvent(event: AchievementsEvent) {
        _state.update { AchievementsReducer.reduce(it, event) }
    }

    /** The state already shows [goal], so a failed write puts [previous] back and tells the UI. */
    private fun setStreakGoal(goal: StreakGoal, previous: StreakGoal) {
        viewModelScope.launch {
            try {
                appSettingsRepository.setStreakGoal(goal)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not store the streak goal", e)
                onEvent(AchievementsEvent.GoalReverted(previous))
                _effects.trySend(AchievementsEffect.ShowSettingsError)
            }
        }
    }

    private fun load() {
        // Repeated Load taps: the reducer does not guard them, but the earlier collectors are cancelled
        // here, so only one pair of collectors is ever active
        loadJobs.forEach(Job::cancel)
        loadJobs = listOf(
            viewModelScope.launch {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    val completed = sessions.filter { it.completed }
                    // Streaks follow the day the work was done; sessions saved before that was tracked
                    // carry the epoch marker and have no real day to count
                    val dates = completed.map { it.completedAt }.filter { it != LocalDate.EPOCH }
                    onEvent(
                        AchievementsEvent.StatsLoaded(
                            currentStreak = StreakCalculator.currentStreak(dates, LocalDate.now(clock)),
                            bestStreak = StreakCalculator.bestStreak(dates),
                            totalFreedBytes = completed.sumOf { it.savedBytes },
                            totalSortedDays = completed.size,
                        ),
                    )
                }
            },
            viewModelScope.launch {
                appSettingsRepository.getSettings()
                    .map { it.streakGoal }
                    .distinctUntilChanged()
                    .collect { onEvent(AchievementsEvent.GoalLoaded(it)) }
            },
        )
    }

    private companion object {
        const val TAG = "AchievementsViewModel"
    }
}
