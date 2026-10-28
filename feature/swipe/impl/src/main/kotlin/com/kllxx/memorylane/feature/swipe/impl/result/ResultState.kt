package com.kllxx.memorylane.feature.swipe.impl.result

import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.formatSize
import com.kllxx.memorylane.feature.swipe.api.ResultRoute
import java.time.LocalDate

/**
 * «Итоги» of a day. The numbers are those of this confirmation. [streakDays] is the current streak, shown in a chip,
 * and null when the chip is not shown: only a visit that completed today's day today shows it.
 */
data class ResultState(
    val date: LocalDate,
    val deletedCount: Int,
    val keptCount: Int,
    val totalCount: Int,
    val freed: FormattedSize,
    val streakDays: Int? = null,
) {
    /** Nothing was deleted: «ДЕНЬ РАЗОБРАН», «Все фото оставлены», and no card for the deleted photos. */
    val nothingDeleted: Boolean get() = deletedCount == 0
}

internal fun ResultRoute.toState(): ResultState = ResultState(
    date = LocalDate.parse(date),
    deletedCount = deletedCount,
    keptCount = keptCount,
    totalCount = totalCount,
    freed = formatSize(freedBytes),
)
