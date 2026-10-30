package com.kllxx.memorylane.core.domain.permission

/** The permissions the app explains and asks for, one per row of the permission list. */
enum class AppPermission { Photos, Location, Notifications, Delete }

/**
 * Which permissions exist on which API level and when the system still shows its own prompt. Pure rules, so every
 * screen that asks for a permission agrees; the Android calls live next to the code that needs them.
 */
object PermissionRules {
    const val READ_MEDIA_IMAGES = "android.permission.READ_MEDIA_IMAGES"
    const val READ_MEDIA_VISUAL_USER_SELECTED = "android.permission.READ_MEDIA_VISUAL_USER_SELECTED"
    const val READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"
    const val ACCESS_MEDIA_LOCATION = "android.permission.ACCESS_MEDIA_LOCATION"
    const val POST_NOTIFICATIONS = "android.permission.POST_NOTIFICATIONS"
    const val WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE"

    /** The newest API level the rules know; the default of a state that was not read from a device. */
    const val LATEST_API = 36

    private const val API_Q = 29
    private const val API_TIRAMISU = 33

    /** The permission that gives the app all the photos. */
    fun mediaRead(sdkInt: Int): String = if (sdkInt >= API_TIRAMISU) READ_MEDIA_IMAGES else READ_EXTERNAL_STORAGE

    /**
     * What is requested together when the photo access is asked for: the photos, and from API 29 the photo locations,
     * which MediaStore hides from the app otherwise.
     */
    fun photoRequest(sdkInt: Int): List<String> = buildList {
        add(mediaRead(sdkInt))
        if (sdkInt >= API_Q) add(ACCESS_MEDIA_LOCATION)
    }

    /** The runtime permission behind a row, null when the API level has no such permission. */
    fun permissionOf(permission: AppPermission, sdkInt: Int): String? = when (permission) {
        AppPermission.Photos -> mediaRead(sdkInt)
        AppPermission.Location -> ACCESS_MEDIA_LOCATION.takeIf { sdkInt >= API_Q }
        AppPermission.Notifications -> POST_NOTIFICATIONS.takeIf { sdkInt >= API_TIRAMISU }
        // Only up to API 29 an app deletes photos on its own; later the system asks per request
        AppPermission.Delete -> WRITE_EXTERNAL_STORAGE.takeIf { sdkInt <= API_Q }
    }

    /** The permissions to request when a row is tapped: photos and location go together. */
    fun requestFor(permission: AppPermission, sdkInt: Int): List<String> = when (permission) {
        AppPermission.Photos, AppPermission.Location -> photoRequest(sdkInt)
        else -> listOfNotNull(permissionOf(permission, sdkInt))
    }

    /** The rows relevant on this API level, in the order of the list. */
    fun relevant(sdkInt: Int): List<AppPermission> =
        AppPermission.entries.filter { permissionOf(it, sdkInt) != null }

    /**
     * Whether the system can still show its prompt: never asked before, or it still wants a rationale shown. Once the
     * permission was asked for and no rationale is due, it is permanently denied and only the settings can change it.
     */
    fun canRequest(granted: Boolean, requestedBefore: Boolean, shouldShowRationale: Boolean): Boolean =
        !granted && (!requestedBefore || shouldShowRationale)
}
