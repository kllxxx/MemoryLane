package com.kllxx.memorylane.core.data.api.model

import java.time.LocalDate

data class PendingDelete(
    val photoId: Long,
    val sessionDate: LocalDate,
)
