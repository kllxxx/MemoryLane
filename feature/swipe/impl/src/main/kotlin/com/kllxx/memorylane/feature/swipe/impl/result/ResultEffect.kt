package com.kllxx.memorylane.feature.swipe.impl.result

sealed interface ResultEffect {
    data object NavigateToCalendar : ResultEffect
}
