package com.kllxx.memorylane.feature.swipe.impl.result

sealed interface ResultIntent {
    data object Done : ResultIntent
}
