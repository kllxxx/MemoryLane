package com.kllxx.memorylane.feature.swipe.api

import kotlinx.serialization.Serializable

/**
 * «Итоги» of a day. [date] is the ISO-8601 date of the day. The numbers are those of this confirmation:
 * [deletedCount] photos and [freedBytes] bytes were deleted, [keptCount] photos of the day are kept and [totalCount]
 * is how many photos the day has. With [deletedCount] 0 nothing was deleted and the screen says so.
 */
@Serializable
data class ResultRoute(
    val date: String,
    val deletedCount: Int,
    val freedBytes: Long,
    val keptCount: Int,
    val totalCount: Int,
)
