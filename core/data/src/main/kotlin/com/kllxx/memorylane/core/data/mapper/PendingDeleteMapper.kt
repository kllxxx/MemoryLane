package com.kllxx.memorylane.core.data.mapper

import com.kllxx.memorylane.core.domain.model.PendingDelete
import com.kllxx.memorylane.core.data.db.entity.PendingDeleteEntity

fun PendingDeleteEntity.toDomain() = PendingDelete(photoId = photoId, sessionDate = sessionDate)

fun PendingDelete.toEntity() = PendingDeleteEntity(photoId = photoId, sessionDate = sessionDate)
