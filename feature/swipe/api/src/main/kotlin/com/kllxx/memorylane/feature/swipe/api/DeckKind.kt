package com.kllxx.memorylane.feature.swipe.api

import kotlinx.serialization.Serializable

/**
 * Which deck a Swipe, «Перед удалением» or «Итоги» route is for. [DAY] is the On This Day deck of a date, which the
 * route's `date` names; [UNDATED] is the deck of photos with no date, and its route has no date.
 */
@Serializable
enum class DeckKind { DAY, UNDATED }
