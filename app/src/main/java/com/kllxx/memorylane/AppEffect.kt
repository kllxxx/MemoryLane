package com.kllxx.memorylane

import java.time.LocalDate

/** What the screen is asked to do outside of its own back stack. */
sealed interface AppEffect {
    /** The reminder was tapped and today has photos to review: Swipe for [date], with Calendar below it. */
    data class OpenToday(val date: LocalDate) : AppEffect
}
