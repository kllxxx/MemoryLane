package com.kllxx.memorylane.core.data.permission

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.permission.PermissionRules
import com.kllxx.memorylane.core.domain.permission.PermissionsState
import com.kllxx.memorylane.core.domain.repository.PermissionsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
internal class PermissionsRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PermissionsRepository {

    private val _state = MutableStateFlow(read())
    override val state: StateFlow<PermissionsState> = _state.asStateFlow()

    override fun refresh() {
        _state.value = read()
    }

    private fun read(): PermissionsState {
        val sdkInt = Build.VERSION.SDK_INT
        fun granted(permission: AppPermission): Boolean =
            // A permission the API level does not have is not missing
            PermissionRules.permissionOf(permission, sdkInt)?.let(::isGranted) ?: true
        return PermissionsState(
            // Only the permission for all photos counts: "select photos" grants READ_MEDIA_VISUAL_USER_SELECTED alone,
            // and the app does not work with a part of the gallery
            photos = granted(AppPermission.Photos),
            location = granted(AppPermission.Location),
            // The permission and the system switch in one: what decides whether the reminder can be shown
            notifications = granted(AppPermission.Notifications) && NotificationManagerCompat.from(context).areNotificationsEnabled(),
            delete = granted(AppPermission.Delete),
        )
    }

    private fun isGranted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
