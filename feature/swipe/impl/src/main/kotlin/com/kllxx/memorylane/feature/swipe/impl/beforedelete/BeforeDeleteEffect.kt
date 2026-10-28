package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.content.IntentSender

sealed interface BeforeDeleteEffect {
    /** The screen launches the system dialog of the trash request (API 30+) and reports the result. */
    data class LaunchTrashRequest(val intentSender: IntentSender) : BeforeDeleteEffect

    /** The screen asks for `WRITE_EXTERNAL_STORAGE` (API 24–29), which deleting needs there. */
    data object RequestWritePermission : BeforeDeleteEffect

    /** The deletion is done, or there was nothing to delete: «Итоги» with these numbers. */
    data class NavigateToResult(
        val date: java.time.LocalDate,
        val deletedCount: Int,
        val freedBytes: Long,
        val keptCount: Int,
        val totalCount: Int,
    ) : BeforeDeleteEffect

    /** Nothing to review (the marked photos are gone, or the library cannot be read). */
    data object NavigateToCalendar : BeforeDeleteEffect

    /** The delete failed with an error, as opposed to the user cancelling. Nothing was deleted. */
    data object ShowDeleteError : BeforeDeleteEffect

    /** The permission to write was refused, so nothing was deleted. */
    data object ShowPermissionDenied : BeforeDeleteEffect
}
