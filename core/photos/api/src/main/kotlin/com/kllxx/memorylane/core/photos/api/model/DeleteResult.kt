package com.kllxx.memorylane.core.photos.api.model

import android.content.IntentSender

sealed interface DeleteResult {
    /** Photos were deleted without needing user interaction. */
    data object Deleted : DeleteResult

    /** The UI must launch [intentSender] to get the user confirmation (API 30+). */
    data class RequiresConfirmation(val intentSender: IntentSender) : DeleteResult
}
