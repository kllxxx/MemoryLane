package com.kllxx.memorylane.feature.profile.impl

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/** What the Android system says about notifications; the ViewModel only ever sees the results. */
internal object NotificationSystem {

    fun isEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /**
     * The runtime permission exists since API 33. It can be requested again while it was never requested
     * or the system still wants a rationale; once it was requested and no rationale is due, it is permanently denied.
     */
    fun canRequestPermission(
        sdkInt: Int,
        granted: Boolean,
        permissionRequested: Boolean,
        shouldShowRationale: Boolean,
    ): Boolean = sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted && (!permissionRequested || shouldShowRationale)

    fun settingsIntent(packageName: String, sdkInt: Int = Build.VERSION.SDK_INT): Intent =
        if (sdkInt >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
}
