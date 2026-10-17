package com.kllxx.memorylane.feature.achievements.impl

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val swipeRepository: SwipeRepository,
    private val appSettingsRepository: AppSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AchievementsState())
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AchievementsEffect>(extraBufferCapacity = 8)
    val effects: SharedFlow<AchievementsEffect> = _effects.asSharedFlow()

    private var loadJobs: List<Job> = emptyList()

    fun onIntent(intent: AchievementsIntent) {
        _state.update { AchievementsReducer.reduce(it, intent) }

        when (intent) {
            AchievementsIntent.Load -> load()
            is AchievementsIntent.SetStreakGoal ->
                viewModelScope.launch { appSettingsRepository.setStreakGoal(intent.goal) }
        }
    }

    private fun onEvent(event: AchievementsEvent) {
        _state.update { AchievementsReducer.reduce(it, event) }
    }

    private fun load() {
        loadJobs.forEach(Job::cancel)
        loadJobs = listOf(
            viewModelScope.launch {
                swipeRepository.getCompletedSessions().collect { sessions ->
                    val completed = sessions.filter { it.completed }
                    val dates = completed.map { it.date }
                    onEvent(
                        AchievementsEvent.StatsLoaded(
                            currentStreak = StreakCalculator.currentStreak(dates, LocalDate.now()),
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
}
