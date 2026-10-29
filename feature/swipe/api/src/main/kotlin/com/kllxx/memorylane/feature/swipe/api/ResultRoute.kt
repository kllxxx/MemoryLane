package com.kllxx.memorylane.feature.swipe.api

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * «Итоги» of the deck [deck]; [date] is the ISO-8601 date of the day for [DeckKind.DAY] and null for [DeckKind.UNDATED].
 * The numbers are those of this confirmation: [deletedCount] photos and [freedBytes] bytes were deleted, [keptCount]
 * photos of the deck are kept and [totalCount] is how many photos the deck has. With [deletedCount] 0 nothing was
 * deleted and the screen says so.
 */
@Serializable
data class ResultRoute(
    val deck: DeckKind = DeckKind.DAY,
    val date: String? = null,
    val deletedCount: Int,
    val freedBytes: Long,
    val keptCount: Int,
    val totalCount: Int,
) {
    companion object {
        fun day(date: LocalDate, deletedCount: Int, freedBytes: Long, keptCount: Int, totalCount: Int) =
            ResultRoute(DeckKind.DAY, date.toString(), deletedCount, freedBytes, keptCount, totalCount)

        fun undated(deletedCount: Int, freedBytes: Long, keptCount: Int, totalCount: Int) =
            ResultRoute(DeckKind.UNDATED, null, deletedCount, freedBytes, keptCount, totalCount)
    }
}
