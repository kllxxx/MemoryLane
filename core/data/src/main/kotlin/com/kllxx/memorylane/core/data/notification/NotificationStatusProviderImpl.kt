package com.kllxx.memorylane.core.data.notification

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.kllxx.memorylane.core.domain.notification.NotificationStatusProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

internal class NotificationStatusProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : NotificationStatusProvider {

    override fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()
}
