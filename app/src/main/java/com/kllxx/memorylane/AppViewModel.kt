package com.kllxx.memorylane

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kllxx.memorylane.core.domain.repository.AppSettingsRepository
import com.kllxx.memorylane.core.domain.repository.SwipeRepository
import com.kllxx.memorylane.navigation.StartDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository,
    private val swipeRepository: SwipeRepository,
    private val clock: Clock,
) : ViewModel() {

    private val _startDestination = MutableStateFlow<StartDestination?>(null)
    val startDestination: StateFlow<StartDestination?> = _startDestination.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = appSettingsRepository.getSettings().first()
            _startDestination.value = when {
                !settings.onboardingCompleted -> StartDestination.Onboarding
                swipeRepository.getLastSession()?.date == LocalDate.now(clock) -> StartDestination.Main
                else -> StartDestination.Swipe
            }
        }
    }
}
