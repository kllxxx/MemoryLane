package com.kllxx.memorylane.core.domain.model

/** What a confirmed deletion changed: the photos that moved on from DELETE and the bytes they freed. Zero when nothing did. */
data class DeletionOutcome(val deletedCount: Int, val freedBytes: Long)
