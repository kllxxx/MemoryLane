package com.kllxx.memorylane.core.domain.time

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow

/**
 * The one answer to "what day is it", for everything that lives longer than a day: a screen that stays open over
 * midnight must not keep showing yesterday as today.
 */
interface TodayProvider {

    /** The current date at once, then again whenever it changes: at midnight, or when the date, time or zone is changed. */
    val today: Flow<LocalDate>

    /** The current date now, for code that acts on a tap and must not rely on the last emission. */
    fun now(): LocalDate
}
