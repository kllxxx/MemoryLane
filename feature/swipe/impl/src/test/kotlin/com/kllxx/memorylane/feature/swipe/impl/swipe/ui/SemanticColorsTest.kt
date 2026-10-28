package com.kllxx.memorylane.feature.swipe.impl.swipe.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.kllxx.memorylane.core.domain.model.ThemeMode
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.core.ui.theme.semanticColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private data class Read(val keep: Color, val onKeep: Color, val delete: Color, val onDelete: Color, val error: Color, val secondary: Color)

@RunWith(RobolectricTestRunner::class)
class SemanticColorsTest {

    @get:Rule
    val composeRule = createComposeRule()

    // Both themes are read in one composition, because a test can set its content only once
    private val reads: Map<ThemeMode, Read> by lazy {
        val result = mutableMapOf<ThemeMode, Read>()
        composeRule.setContent {
            listOf(ThemeMode.Light, ThemeMode.Dark).forEach { mode ->
                MemoryLaneTheme(mode) {
                    val semantic = MaterialTheme.semanticColors
                    result[mode] = Read(
                        keep = semantic.keep,
                        onKeep = semantic.onKeep,
                        delete = semantic.delete,
                        onDelete = semantic.onDelete,
                        error = MaterialTheme.colorScheme.error,
                        secondary = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        composeRule.waitForIdle()
        result
    }

    private fun read(mode: ThemeMode): Read = reads.getValue(mode)

    @Test
    fun `keep is the brand emerald`() {
        assertEquals(Color(0xFF5BBA6F), read(ThemeMode.Light).keep)
    }

    @Test
    fun `delete is the error red`() {
        val light = read(ThemeMode.Light)

        assertEquals(light.error, light.delete)
    }

    @Test
    fun `keep and delete are the same in light and dark`() {
        val light = read(ThemeMode.Light)
        val dark = read(ThemeMode.Dark)

        assertEquals(light.keep, dark.keep)
        assertEquals(light.delete, dark.delete)
        assertEquals(light.onKeep, dark.onKeep)
        assertEquals(light.onDelete, dark.onDelete)
    }

    @Test
    fun `delete is the error red in dark too`() {
        val dark = read(ThemeMode.Dark)

        assertEquals(dark.error, dark.delete)
    }

    @Test
    fun `keep is not the secondary role, which is grey-green in the dark theme`() {
        val dark = read(ThemeMode.Dark)

        assertNotEquals(dark.secondary, dark.keep)
    }

    @Test
    fun `keep and delete are different colours`() {
        val light = read(ThemeMode.Light)

        assertNotEquals(light.keep, light.delete)
    }
}
