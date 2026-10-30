package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.RowDivider
import com.kllxx.memorylane.core.ui.component.RowValue
import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal fun permissionRowTag(permission: AppPermission) = "permission-row-${permission.name}"

/**
 * «Разрешения»: one row per permission that exists on this API level, showing what the system says. A row has no
 * switch, an app cannot take its own permission back: a tap asks for the permission or opens the settings of the app.
 */
@Composable
internal fun PermissionsCard(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        state.permissionRows.forEachIndexed { index, permission ->
            if (index > 0) RowDivider()
            SettingsRow(
                icon = permissionIcon(permission),
                title = stringResource(permissionTitle(permission)),
                onClick = { onIntent(ProfileIntent.PermissionRowClicked(permission)) },
                modifier = Modifier.testTag(permissionRowTag(permission)),
                trailing = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        RowValue(stringResource(permissionStatus(permission, state.permissions.isGranted(permission))))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun permissionIcon(permission: AppPermission): Painter = when (permission) {
    AppPermission.Photos -> rememberVectorPainter(Icons.Filled.PhotoLibrary)
    AppPermission.Location -> painterResource(UiR.drawable.ic_location_on)
    AppPermission.Notifications -> rememberVectorPainter(Icons.Filled.Notifications)
    AppPermission.Delete -> painterResource(UiR.drawable.ic_delete)
}

private fun permissionTitle(permission: AppPermission): Int = when (permission) {
    AppPermission.Photos -> R.string.profile_permission_photos
    AppPermission.Location -> R.string.profile_permission_location
    AppPermission.Notifications -> R.string.profile_permission_notifications
    AppPermission.Delete -> R.string.profile_permission_delete
}

/** The delete permission is a permission to do something, the others are switched on or off. */
private fun permissionStatus(permission: AppPermission, granted: Boolean): Int = when {
    permission == AppPermission.Delete && granted -> R.string.profile_permission_allowed
    permission == AppPermission.Delete -> R.string.profile_permission_denied
    granted -> R.string.profile_permission_on
    else -> R.string.profile_permission_off
}
