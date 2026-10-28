package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.data.db.entity.PhotoDecisionEntity
import com.kllxx.memorylane.core.domain.model.PhotoDecision

/** Null for a name this version does not know, e.g. one written by a newer build: such a row is ignored. */
fun PhotoDecisionEntity.toDecisionOrNull(): PhotoDecision? = PhotoDecision.entries.firstOrNull { it.name == decision }
