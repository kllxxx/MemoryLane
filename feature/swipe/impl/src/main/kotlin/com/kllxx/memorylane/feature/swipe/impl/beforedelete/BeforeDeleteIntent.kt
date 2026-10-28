package com.kllxx.memorylane.feature.swipe.impl.beforedelete

sealed interface BeforeDeleteIntent {
    /** A tap on a photo: a marked one is kept, a kept one is marked again. Saved at once. */
    data class Toggle(val photoId: Long) : BeforeDeleteIntent

    /** The big button: deletes the marked photos, or with none marked finishes the day. */
    data object DeleteClicked : BeforeDeleteIntent

    /** «Удалить» in our own dialog (below API 30). */
    data object PermanentDeleteConfirmed : BeforeDeleteIntent
    data object PermanentDeleteDismissed : BeforeDeleteIntent

    /** The answer to the request for the permission to write (API 24–29). */
    data class WritePermissionResult(val granted: Boolean) : BeforeDeleteIntent

    /** The system dialog of the trash request closed: [confirmed] is `RESULT_OK`, anything else is a cancel. */
    data class SystemDeleteResult(val confirmed: Boolean) : BeforeDeleteIntent
}
