package com.kllxx.memorylane.core.domain.model

import java.time.LocalDate

data class PendingDelete(
    val photoId: Long,
    val sessionDate: LocalDate,
)
