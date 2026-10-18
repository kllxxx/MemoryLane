package com.kllxx.memorylane.feature.achievements.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.core.domain.streak.StreakCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
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
        _state.update { AchievementsReducer.reduce(it, intent) }

        when (intent) {
            AchievementsIntent.Load -> load()
        }
    }

    private fun onEvent(event: AchievementsEvent) {
        _state.update { AchievementsReducer.reduce(it, event) }
    }

    private fun load() {
        // Repeated Load taps: the reducer does not guard them, but the earlier collectors are cancelled
        // here, so only one pair of collectors is ever active
        loadJobs.forEach(Job::cancel)
        loadJobs = listOf(
            viewModelScope.launch {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    // The query already returns completed sessions only
                    // Streaks follow the day the work was done; sessions saved before that was tracked
                    // carry the epoch marker and have no real day to count
                    val dates = sessions.map { it.completedAt }.filter { it != LocalDate.EPOCH }
                    onEvent(
                        AchievementsEvent.StatsLoaded(
                            currentStreak = StreakCalculator.currentStreak(dates, LocalDate.now(clock)),
                            bestStreak = StreakCalculator.bestStreak(dates),
                            totalFreedBytes = sessions.sumOf { it.savedBytes },
                            totalSortedDays = sessions.size,
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
}
