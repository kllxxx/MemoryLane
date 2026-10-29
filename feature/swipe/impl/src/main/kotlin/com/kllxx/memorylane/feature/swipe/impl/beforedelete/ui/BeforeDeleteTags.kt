package com.kllxx.memorylane.feature.swipe.impl.beforedelete.ui

/** Test tags of «Перед удалением». */
object BeforeDeleteTags {
    const val BACK = "before_delete_back"
    const val INTRO = "before_delete_intro"
    const val GRID = "before_delete_grid"
    const val BUTTON = "before_delete_button"
    const val DIALOG_CONFIRM = "before_delete_dialog_confirm"
    const val DIALOG_DISMISS = "before_delete_dialog_dismiss"

    fun cell(photoId: Long) = "before_delete_cell_$photoId"

    fun groupHeader(date: java.time.LocalDate) = "before_delete_group_$date"
}
