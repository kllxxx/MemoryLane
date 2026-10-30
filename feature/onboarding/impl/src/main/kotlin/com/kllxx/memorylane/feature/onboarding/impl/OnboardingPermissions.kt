package com.kllxx.memorylane.feature.onboarding.impl

import android.os.Build
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.permission.PermissionRules

internal object OnboardingPermissions {

    /** Requested together on the gallery slide: the photos and, to show where a photo was taken, their location. */
    fun photo(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = PermissionRules.photoRequest(sdkInt)

    /** Runtime permission only since API 33; before that notifications are always allowed, so null. */
    fun notification(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        PermissionRules.permissionOf(AppPermission.Notifications, sdkInt)

    /**
     * Access to the photos is what the app needs to work. The location permission is optional:
     * denying it only means no photo locations.
     */
    fun isMediaAccessGranted(
        result: Map<String, Boolean>,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = result[PermissionRules.mediaRead(sdkInt)] == true
}
