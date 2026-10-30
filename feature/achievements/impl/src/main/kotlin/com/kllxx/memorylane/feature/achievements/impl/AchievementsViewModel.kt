package com.kllxx.memorylane.feature.achievements.impl

import com.kllxx.memorylane.core.common.ErrorHandler
import com.kllxx.memorylane.core.common.MviViewModel
import com.kllxx.memorylane.core.domain.repository.StatsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AchievementsViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    errorHandler: ErrorHandler,
) : MviViewModel<AchievementsState, AchievementsIntent, AchievementsEvent, AchievementsEffect>(
    initialState = AchievementsState.Loading,
    reducer = AchievementsReducer::reduce,
    errorHandler = errorHandler,
) {

    init {
        observeStats()
    }

    override fun onIntent(intent: AchievementsIntent) {
        update { AchievementsReducer.reduce(it, intent) }

        when (intent) {
            AchievementsIntent.GoToCalendarClicked -> sendEffect(AchievementsEffect.NavigateToCalendar)
        }
    }

    private fun observeStats() {
        launchSafe("load the achievements") {
            statsRepository.stats.collect { reduce(AchievementsEvent.StatsLoaded(it)) }
        }
    }
}
