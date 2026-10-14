package com.kllxx.memorylane.core.data.api.model

import java.time.LocalDate

data class SwipeSession(
    val date: LocalDate,
    val completed: Boolean,
    val savedBytes: Long,
)
