package com.kllxx.memorylane.core.domain.model

import java.time.LocalDate

data class SwipeSession(
    val date: LocalDate,
    val completed: Boolean,
    val savedBytes: Long,
)
