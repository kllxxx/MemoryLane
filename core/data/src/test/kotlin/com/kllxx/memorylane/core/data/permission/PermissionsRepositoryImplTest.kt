package com.kllxx.memorylane.core.data.permission

import android.app.NotificationManager
import android.content.Context
import com.kllxx.memorylane.core.domain.permission.PermissionRules
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionsRepositoryImplTest {

    private val application = RuntimeEnvironment.getApplication()

    private fun repository() = PermissionsRepositoryImpl(application)

    private fun grant(vararg permissions: String) = shadowOf(application).grantPermissions(*permissions)

    private fun deny(vararg permissions: String) = shadowOf(application).denyPermissions(*permissions)

    private fun setNotificationsEnabled(enabled: Boolean) {
        val manager = application.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        shadowOf(manager).setNotificationsEnabled(enabled)
    }

    @Test
    fun `the photos are granted with the permission for all of them`() {
        grant(PermissionRules.READ_MEDIA_IMAGES)

        assertTrue(repository().state.value.photos)
    }

    @Test
    fun `without any photo permission there is no access`() {
        deny(PermissionRules.READ_MEDIA_IMAGES, PermissionRules.READ_MEDIA_VISUAL_USER_SELECTED)

        assertFalse(repository().state.value.photos)
    }

    @Test
    fun `selected photos alone are no access`() {
        deny(PermissionRules.READ_MEDIA_IMAGES)
        grant(PermissionRules.READ_MEDIA_VISUAL_USER_SELECTED)

        assertFalse(repository().state.value.photos)
    }

    @Test
    fun `the location follows its own permission`() {
        grant(PermissionRules.ACCESS_MEDIA_LOCATION)
        assertTrue(repository().state.value.location)

        deny(PermissionRules.ACCESS_MEDIA_LOCATION)
        assertFalse(repository().state.value.location)
    }

    @Test
    fun `notifications need both the permission and the system switch`() {
        grant(PermissionRules.POST_NOTIFICATIONS)
        setNotificationsEnabled(true)
        assertTrue(repository().state.value.notifications)

        setNotificationsEnabled(false)
        assertFalse(repository().state.value.notifications)

        setNotificationsEnabled(true)
        deny(PermissionRules.POST_NOTIFICATIONS)
        assertFalse(repository().state.value.notifications)
    }

    @Test
    fun `the delete permission is not missing on an API level without it`() {
        deny(PermissionRules.WRITE_EXTERNAL_STORAGE)

        assertTrue(repository().state.value.delete)
    }

    @Test
    @Config(sdk = [29])
    fun `the delete permission is read where it exists`() {
        deny(PermissionRules.WRITE_EXTERNAL_STORAGE)
        assertFalse(repository().state.value.delete)

        grant(PermissionRules.WRITE_EXTERNAL_STORAGE)
        assertTrue(repository().state.value.delete)
    }

    @Test
    fun `refresh picks up a change made in the system settings`() {
        deny(PermissionRules.ACCESS_MEDIA_LOCATION)
        val repository = repository()
        assertFalse(repository.state.value.location)

        grant(PermissionRules.ACCESS_MEDIA_LOCATION)
        assertFalse(repository.state.value.location)
        repository.refresh()

        assertTrue(repository.state.value.location)
    }
}
