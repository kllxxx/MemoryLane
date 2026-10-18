package com.kllxx.memorylane.feature.onboarding.impl

import android.Manifest
import android.os.Build

internal object OnboardingPermissions {

    /** Requested together on the gallery slide. */
    fun photo(sdkInt: Int = Build.VERSION.SDK_INT): List<String> = buildList {
        add(mediaReadPermission(sdkInt))
        // Lets the app read GPS data of photos, used to show where a photo was taken
        if (sdkInt >= Build.VERSION_CODES.Q) add(Manifest.permission.ACCESS_MEDIA_LOCATION)
    }

    /** Runtime permission only since API 33; before that notifications are always allowed, so null. */
    fun notification(sdkInt: Int = Build.VERSION.SDK_INT): String? =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null

    /**
     * Access to the photos is what the app needs to work. The location permission is optional:
     * denying it only means no photo locations.
     */
    fun isMediaAccessGranted(
        result: Map<String, Boolean>,
        sdkInt: Int = Build.VERSION.SDK_INT,
    ): Boolean = result[mediaReadPermission(sdkInt)] == true

    private fun mediaReadPermission(sdkInt: Int): String =
        if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
}
