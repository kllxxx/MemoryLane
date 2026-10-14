package com.kllxx.memorylane.core.data.impl.mapper

import com.kllxx.memorylane.core.data.api.model.SwipeSession
import com.kllxx.memorylane.core.data.impl.db.entity.SwipeSessionEntity

fun SwipeSessionEntity.toDomain() =
    SwipeSession(date = date, completed = completed, savedBytes = savedBytes)

fun SwipeSession.toEntity() =
    SwipeSessionEntity(date = date, completed = completed, savedBytes = savedBytes)
