package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.kllxx.memorylane.core.photos.domain.repository.DeleteCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class AndroidDeleteCapabilities @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeleteCapabilities {

    override val canTrash: Boolean
        @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.R)
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    override fun hasWritePermission(): Boolean =
        canTrash || context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
}
