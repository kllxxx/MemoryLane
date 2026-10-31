package com.kllxx.memorylane.core.photos.domain.repository

/** What the system lets the app do when it deletes photos; which of it depends on the Android version. */
interface DeleteCapabilities {

    /** Photos can be moved to the trash after one confirmation in the system dialog (Android 11+). Otherwise they are deleted for good. */
    val canTrash: Boolean

    /** The app may delete photos of other apps right now. Below Android 11 that needs a permission the user grants. */
    fun hasWritePermission(): Boolean
}
