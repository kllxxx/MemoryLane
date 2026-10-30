package com.kllxx.memorylane.core.domain.permission

/** What the system says about the app's permissions right now, read on the API level [sdkInt]. */
data class PermissionsState(
    /** All the photos can be read. A partial grant ("select photos") is not access: the app only works with all of them. */
    val photos: Boolean = true,
    val location: Boolean = true,
    val notifications: Boolean = true,
    val delete: Boolean = true,
    val sdkInt: Int = PermissionRules.LATEST_API,
) {
    fun isGranted(permission: AppPermission): Boolean = when (permission) {
        AppPermission.Photos -> photos
        AppPermission.Location -> location
        AppPermission.Notifications -> notifications
        AppPermission.Delete -> delete
    }

    /** The permissions that exist on this API level, in the order of the list. */
    val relevant: List<AppPermission> get() = PermissionRules.relevant(sdkInt)

    /** What to ask the system for when [permission] is requested. */
    fun requestFor(permission: AppPermission): List<String> = PermissionRules.requestFor(permission, sdkInt)
}
