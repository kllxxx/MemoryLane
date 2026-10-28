package com.kllxx.memorylane.feature.swipe.impl.swipe

import android.net.Uri
import java.time.LocalDate

sealed interface SwipeEffect {
    data object NavigateToCalendar : SwipeEffect

    /** The photo library cannot be read. No session is recorded and the screen stays put. */
    data object PermissionRequired : SwipeEffect

    /**
     * Every photo of the day has a decision and some are marked for deletion on [date].
     * [totalPhotosCount] is how many photos the day had, so the screen can derive the kept count.
     */
    data class NavigateToBeforeDelete(val date: LocalDate, val totalPhotosCount: Int) : SwipeEffect

    /**
     * Every photo of the day has a decision and none is marked for deletion, so there is nothing to delete: «Итоги»
     * for the day, with [keptCount] kept photos out of [totalCount].
     */
    data class NavigateToResult(val date: LocalDate, val keptCount: Int, val totalCount: Int) : SwipeEffect

    /** The screen builds the chooser for [uri] (`ACTION_SEND`). */
    data class ShareImage(val uri: Uri) : SwipeEffect

    /** The screen opens [uri] in another app (`ACTION_VIEW`). */
    data class OpenInGallery(val uri: Uri) : SwipeEffect
}
