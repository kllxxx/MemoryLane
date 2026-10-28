package com.kllxx.memorylane.core.domain.model

/** What the user decided for one photo. A photo without a decision is undecided. */
enum class PhotoDecision {
    KEEP,

    /** Marked for deletion, not confirmed yet. */
    DELETE,

    /** Confirmed and moved to the system trash (API 30+). */
    TRASHED,

    /** Confirmed and deleted permanently (below API 30). */
    DELETED,
}
