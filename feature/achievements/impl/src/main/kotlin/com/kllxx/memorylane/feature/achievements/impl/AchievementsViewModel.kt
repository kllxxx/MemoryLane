package com.kllxx.memorylane.feature.achievements.impl

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<AchievementsState>(AchievementsState.Loading)
    val state: StateFlow<AchievementsState> = _state.asStateFlow()

    private val _effects = Channel<AchievementsEffect>(Channel.BUFFERED)
    val effects: Flow<AchievementsEffect> = _effects.receiveAsFlow()

    init {
        observeStats()
    }

    fun onIntent(intent: AchievementsIntent) {
        _state.update { AchievementsReducer.reduce(it, intent) }

        when (intent) {
            AchievementsIntent.GoToCalendarClicked -> _effects.trySend(AchievementsEffect.NavigateToTimeline)
        }
    }

    private fun onEvent(event: AchievementsEvent) {
        _state.update { AchievementsReducer.reduce(it, event) }
    }

    private fun observeStats() {
        viewModelScope.launch {
            try {
                statsRepository.stats.collect { onEvent(AchievementsEvent.StatsLoaded(it)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Could not load the achievements", e)
            }
        }
    }

    private companion object {
        const val TAG = "AchievementsViewModel"
    }
}
