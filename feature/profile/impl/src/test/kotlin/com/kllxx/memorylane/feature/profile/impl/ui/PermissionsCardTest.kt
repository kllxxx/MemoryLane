package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.kllxx.memorylane.core.domain.permission.AppPermission
import com.kllxx.memorylane.core.domain.permission.PermissionsState
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The rows of «Разрешения»: the status each shows and the intent a tap sends, per API level. */
@RunWith(RobolectricTestRunner::class)
class PermissionsCardTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val intents = mutableListOf<ProfileIntent>()

    private fun setContent(permissions: PermissionsState) {
        composeRule.setContent {
            MemoryLaneTheme {
                ProfileContent(
                    state = ProfileState.Content(permissions = permissions),
                    onIntent = { intents += it },
                    snackbarHostState = SnackbarHostState(),
                    is24Hour = true,
                )
            }
        }
    }

    private fun row(permission: AppPermission) = composeRule.onNodeWithTag(permissionRowTag(permission))

    private fun assertStatus(permission: AppPermission, status: String) {
        row(permission).performScrollTo()
        row(permission).assertIsDisplayed()
        row(permission).assert(hasText(status))
    }

    @Test
    fun `API 34 has the photo, location and notification rows`() {
        setContent(PermissionsState(sdkInt = 34))

        row(AppPermission.Photos).performScrollTo().assertIsDisplayed()
        row(AppPermission.Location).performScrollTo().assertIsDisplayed()
        row(AppPermission.Notifications).performScrollTo().assertIsDisplayed()
        row(AppPermission.Delete).assertDoesNotExist()
    }

    @Test
    fun `API 29 has the delete row and no notification row`() {
        setContent(PermissionsState(sdkInt = 29))

        row(AppPermission.Photos).performScrollTo().assertIsDisplayed()
        row(AppPermission.Location).performScrollTo().assertIsDisplayed()
        row(AppPermission.Delete).performScrollTo().assertIsDisplayed()
        row(AppPermission.Notifications).assertDoesNotExist()
    }

    @Test
    fun `API 28 has no location row`() {
        setContent(PermissionsState(sdkInt = 28))

        row(AppPermission.Photos).performScrollTo().assertIsDisplayed()
        row(AppPermission.Delete).performScrollTo().assertIsDisplayed()
        row(AppPermission.Location).assertDoesNotExist()
        row(AppPermission.Notifications).assertDoesNotExist()
    }

    @Test
    fun `a granted permission reads Включено`() {
        setContent(PermissionsState(photos = true, location = true, notifications = true, sdkInt = 34))

        assertStatus(AppPermission.Photos, "Включено")
        assertStatus(AppPermission.Location, "Включено")
        assertStatus(AppPermission.Notifications, "Включено")
    }

    @Test
    fun `a missing permission reads Выключено`() {
        setContent(PermissionsState(photos = false, location = false, notifications = false, sdkInt = 34))

        assertStatus(AppPermission.Photos, "Выключено")
        assertStatus(AppPermission.Location, "Выключено")
        assertStatus(AppPermission.Notifications, "Выключено")
    }

    @Test
    fun `the delete permission reads Разрешено or Нет`() {
        setContent(PermissionsState(delete = true, sdkInt = 29))
        assertStatus(AppPermission.Delete, "Разрешено")
    }

    @Test
    fun `a denied delete permission reads Нет`() {
        setContent(PermissionsState(delete = false, sdkInt = 29))

        assertStatus(AppPermission.Delete, "Нет")
    }

    @Test
    fun `tapping a row sends the intent of that permission`() {
        setContent(PermissionsState(photos = false, sdkInt = 34))

        row(AppPermission.Photos).performScrollTo().performClick()
        row(AppPermission.Location).performScrollTo().performClick()

        assertEquals(
            listOf(
                ProfileIntent.PermissionRowClicked(AppPermission.Photos),
                ProfileIntent.PermissionRowClicked(AppPermission.Location),
            ),
            intents,
        )
    }
}
