package com.kllxx.memorylane.core.ui.permission

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Asks for permissions and opens the system settings of the app: the one place the screens do it, so a request is the
 * same on every screen. What to ask, and whether to ask or to send the person to the settings, is decided by the
 * ViewModels with the rules of `PermissionRules`.
 */
class PermissionRequester internal constructor(
    private val context: Context,
    private val launcher: (Array<String>) -> Unit,
) {
    /** Shows the system prompt for all of [permissions] at once; the result goes to the callback of [rememberPermissionRequester]. */
    fun request(permissions: List<String>) = launcher(permissions.toTypedArray())

    fun openAppSettings() = context.startActivity(appSettingsIntent(context.packageName))

    fun isGranted(permission: String): Boolean = context.isPermissionGranted(permission)

    /** True while the system still wants a rationale: the permission was denied, but not for good. */
    fun shouldShowRationale(permission: String): Boolean = context.shouldShowRationale(permission)
}

/** [onResult] gets, per permission, whether it was granted. */
@Composable
fun rememberPermissionRequester(onResult: (Map<String, Boolean>) -> Unit): PermissionRequester {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        currentOnResult(it)
    }
    return remember(context, launcher) { PermissionRequester(context) { launcher.launch(it) } }
}

fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

fun Context.isPermissionGranted(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.shouldShowRationale(permission: String): Boolean {
    val activity = findActivity() ?: return false
    return ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
