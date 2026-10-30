package com.kllxx.memorylane.feature.profile.impl

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.core.domain.permission.PermissionRules
import com.kllxx.memorylane.core.ui.permission.canRequest
import com.kllxx.memorylane.core.ui.permission.rememberPermissionRequester
import com.kllxx.memorylane.feature.profile.impl.ui.ProfileContent

@Composable
fun ProfileScreen(
    onNavigateToOnboarding: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionRequested by rememberUpdatedState((state as? ProfileState.Content)?.permissionRequested ?: false)
    val requestedPermissions by rememberUpdatedState((state as? ProfileState.Content)?.requestedPermissions.orEmpty())
    // The result is not needed: a dialog gives the screen back, which is an ON_RESUME that reads the permissions again
    val permissionRequester = rememberPermissionRequester { }

    fun checkSystemNotifications() {
        viewModel.onIntent(
            ProfileIntent.SystemNotificationsChecked(
                enabled = NotificationSystem.isEnabled(context),
                canRequestPermission = context.canRequestNotificationPermission(permissionRequested),
            ),
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onIntent(ProfileIntent.PermissionResult(granted))
        // A denial can make the permission permanently denied, which only the system can tell
        checkSystemNotifications()
    }

    // The user may have changed the notification settings or a permission while the app was in the background
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        checkSystemNotifications()
        viewModel.onIntent(
            ProfileIntent.PermissionsChecked(
                PermissionRules.relevant(Build.VERSION.SDK_INT)
                    .filterTo(HashSet()) { permissionRequester.canRequest(it, Build.VERSION.SDK_INT, it in requestedPermissions) },
            ),
        )
    }

    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.RequestNotificationPermission ->
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                ProfileEffect.OpenNotificationSettings ->
                    context.startActivity(NotificationSystem.settingsIntent(context.packageName))
                is ProfileEffect.RequestPermissions -> permissionRequester.request(effect.permissions)
                ProfileEffect.OpenAppSettings -> permissionRequester.openAppSettings()
                ProfileEffect.NavigateToOnboarding -> onNavigateToOnboarding()
                is ProfileEffect.ContactSupport -> {
                    val intent = supportMailIntent(
                        email = SUPPORT_EMAIL,
                        subject = resources.getString(R.string.profile_support_subject),
                        body = resources.getString(R.string.profile_support_body, effect.versionName, Build.VERSION.RELEASE, Build.MODEL),
                    )
                    try {
                        context.startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        snackbarHostState.showSnackbar(resources.getString(R.string.profile_no_mail_app))
                    }
                }
                is ProfileEffect.ShowError ->
                    snackbarHostState.showSnackbar(resources.getString(effect.error.message))
            }
        }
    }

    ProfileContent(
        state = state,
        onIntent = viewModel::onIntent,
        snackbarHostState = snackbarHostState,
        is24Hour = DateFormat.is24HourFormat(context),
    )
}

private fun Context.canRequestNotificationPermission(permissionRequested: Boolean): Boolean {
    val granted = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.POST_NOTIFICATIONS,
    ) == PackageManager.PERMISSION_GRANTED
    val activity = findActivity()
    return NotificationSystem.canRequestPermission(
        sdkInt = Build.VERSION.SDK_INT,
        granted = granted,
        permissionRequested = permissionRequested,
        shouldShowRationale = activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS),
    )
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
