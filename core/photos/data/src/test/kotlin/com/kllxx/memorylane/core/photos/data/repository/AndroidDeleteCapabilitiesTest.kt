package com.kllxx.memorylane.core.photos.data.repository

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class AndroidDeleteCapabilitiesTest {

    private val application: Application = RuntimeEnvironment.getApplication()
    private val capabilities = AndroidDeleteCapabilities(application)

    @Test
    @Config(sdk = [30])
    fun `from API 30 the photos go to the trash and no permission is needed`() {
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        assertTrue(capabilities.canTrash)
        assertTrue(capabilities.hasWritePermission())
    }

    @Test
    @Config(sdk = [29])
    fun `below API 30 there is no trash`() {
        assertFalse(capabilities.canTrash)
    }

    @Test
    @Config(sdk = [29])
    fun `below API 30 the write permission decides`() {
        shadowOf(application).denyPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertFalse(capabilities.hasWritePermission())

        shadowOf(application).grantPermissions(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        assertTrue(capabilities.hasWritePermission())
    }
}
