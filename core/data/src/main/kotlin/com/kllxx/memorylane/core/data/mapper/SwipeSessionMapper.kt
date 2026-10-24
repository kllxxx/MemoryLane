package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.domain.model.SwipeSession
import com.kllxx.memorylane.core.data.db.entity.SwipeSessionEntity

fun SwipeSessionEntity.toDomain() =
    SwipeSession(date = date, completed = completed, savedBytes = savedBytes, completedAt = completedAt, deletedCount = deletedCount)

fun SwipeSession.toEntity() =
    SwipeSessionEntity(date = date, completed = completed, savedBytes = savedBytes, completedAt = completedAt, deletedCount = deletedCount)
