package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import android.content.IntentSender
import com.kllxx.memorylane.core.common.UiError

sealed interface BeforeDeleteEffect {
    /** The screen launches the system dialog of the trash request (API 30+) and reports the result. */
    data class LaunchTrashRequest(val intentSender: IntentSender) : BeforeDeleteEffect

    /** The screen asks for `WRITE_EXTERNAL_STORAGE` (API 24–29), which deleting needs there. */
    data object RequestWritePermission : BeforeDeleteEffect

    /** The deletion is done, or there was nothing to delete: «Итоги» with these numbers. */
    data class NavigateToResult(
        val deck: com.kllxx.memorylane.core.domain.model.DeckKey,
        val deletedCount: Int,
        val freedBytes: Long,
        val keptCount: Int,
        val totalCount: Int,
    ) : BeforeDeleteEffect

    /** Nothing to review (the marked photos are gone, or the library cannot be read). */
    data object NavigateToCalendar : BeforeDeleteEffect

    /** The delete failed with an error, as opposed to the user cancelling. Nothing was deleted. */
    data class ShowError(val error: UiError) : BeforeDeleteEffect

    /** The permission to write was refused, so nothing was deleted. */
    data object ShowPermissionDenied : BeforeDeleteEffect
}
