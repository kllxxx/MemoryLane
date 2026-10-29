package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

/** Test tags of the Swipe screen. */
object SwipeTags {
    const val CARD = "swipe_card"
    const val ACTIONS_CARD = "swipe_actions_card"
    const val TOP_BAR_TEXT = "swipe_top_bar_text"
    const val TOP_BAR_DATE = "swipe_date"
    const val TOP_BAR_LOCATION = "swipe_location"
    const val CLOSE = "swipe_close"
    const val BUTTON_DELETE = "swipe_button_delete"
    const val BUTTON_KEEP = "swipe_button_keep"
    const val STRIP = "swipe_strip"
    const val ACTION_UNDO = "swipe_action_undo"
    const val ACTION_SHARE = "swipe_action_share"
    const val ACTION_GALLERY = "swipe_action_gallery"
    const val ACTION_BUTTONS = "swipe_action_buttons"

    fun stripItem(photoId: Long) = "swipe_strip_item_$photoId"

    /** The text label under the circle of a quick action; [action] is one of the `ACTION_*` tags. */
    fun actionLabel(action: String) = "${action}_label"

    /** The card itself, with the shape of its photo, as opposed to [CARD], which is the whole card area. */
    fun cardPhoto(photoId: Long) = "swipe_card_photo_$photoId"
}
