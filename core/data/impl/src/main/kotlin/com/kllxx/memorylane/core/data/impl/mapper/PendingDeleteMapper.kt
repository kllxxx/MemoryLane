package com.kllxx.memorylane.core.data.impl.mapper

import com.kllxx.memorylane.core.data.api.model.PendingDelete
import com.kllxx.memorylane.core.data.impl.db.entity.PendingDeleteEntity

fun PendingDeleteEntity.toDomain() = PendingDelete(photoId = photoId, sessionDate = sessionDate)

fun PendingDelete.toEntity() = PendingDeleteEntity(photoId = photoId, sessionDate = sessionDate)
