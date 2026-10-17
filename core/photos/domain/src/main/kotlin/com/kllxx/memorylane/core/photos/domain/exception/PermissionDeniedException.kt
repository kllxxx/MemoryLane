package com.kllxx.memorylane.core.photos.domain.exception

/** The media read permission is not granted, which is different from a day that simply has no photos. */
class PermissionDeniedException(message: String? = null) : Exception(message)
