package com.kllxx.memorylane.core.domain.permission

/** What the system says about the app's permissions right now. */
data class PermissionsState(
    /** All the photos can be read. A partial grant ("select photos") is not access: the app only works with all of them. */
    val photos: Boolean = true,
    val location: Boolean = true,
    val notifications: Boolean = true,
    val delete: Boolean = true,
) {
    fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.Photos -> photos
        AppPermission.Location -> location
        AppPermission.Notifications -> notifications
        AppPermission.Delete -> delete
    }
}
