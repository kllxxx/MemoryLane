package com.kllxx.memorylane.core.photos.domain.model

import android.net.Uri
import java.time.LocalDate

data class Photo(
    val id: Long,
    val uri: Uri,
    val dateTaken: LocalDate,
    val sizeBytes: Long,
    val location: String? = null,
)
