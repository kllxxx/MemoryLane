package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import com.kllxx.memorylane.core.domain.model.DeckKey
import android.net.Uri
import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.SizeUnit
import io.mockk.mockk
import kotlinx.collections.immutable.toImmutableList
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BeforeDeleteReducerTest {

    private fun cell(id: Long, marked: Boolean = true, size: Long = 100_000_000) =
        DeleteCell(id, mockk<Uri>(relaxed = true), size, marked, DeckKey.Day(LocalDate.of(2015, 1, 15)))

    private fun state(vararg cells: DeleteCell, deleting: Boolean = false) =
        BeforeDeleteState(isLoading = false, cells = cells.toList().toImmutableList(), isDeleting = deleting)

    private fun BeforeDeleteState.marks() = cells.map { it.marked }

    // --- toggling ---

    @Test
    fun `a tap flips the mark of that photo only`() {
        val result = BeforeDeleteReducer.reduce(state(cell(1), cell(2)), BeforeDeleteIntent.Toggle(2))

        assertEquals(listOf(true, false), result.marks())
    }

    @Test
    fun `a second tap marks it again`() {
        val once = BeforeDeleteReducer.reduce(state(cell(1)), BeforeDeleteIntent.Toggle(1))

        assertEquals(listOf(true), BeforeDeleteReducer.reduce(once, BeforeDeleteIntent.Toggle(1)).marks())
    }

    @Test
    fun `a photo that is not in the grid changes nothing`() {
        val before = state(cell(1))

        assertSame(before, BeforeDeleteReducer.reduce(before, BeforeDeleteIntent.Toggle(9)))
    }

    @Test
    fun `the grid is locked while deleting`() {
        val before = state(cell(1), deleting = true)

        assertSame(before, BeforeDeleteReducer.reduce(before, BeforeDeleteIntent.Toggle(1)))
    }

    // --- flags ---

    @Test
    fun `dismissing the dialog hides it`() {
        val shown = BeforeDeleteState(showPermanentDialog = true)

        assertFalse(BeforeDeleteReducer.reduce(shown, BeforeDeleteIntent.PermanentDeleteDismissed).showPermanentDialog)
    }

    @Test
    fun `intents the view model handles leave the state alone`() {
        val before = state(cell(1))

        listOf(
            BeforeDeleteIntent.DeleteClicked,
            BeforeDeleteIntent.PermanentDeleteConfirmed,
            BeforeDeleteIntent.WritePermissionResult(true),
            BeforeDeleteIntent.SystemDeleteResult(true),
        ).forEach { assertSame(it.toString(), before, BeforeDeleteReducer.reduce(before, it)) }
    }

    @Test
    fun `events load the cells and set the flags`() {
        val loaded = BeforeDeleteReducer.reduce(BeforeDeleteState(), BeforeDeleteEvent.CellsLoaded(listOf(cell(1), cell(2))))
        assertFalse(loaded.isLoading)
        assertEquals(listOf(1L, 2L), loaded.cells.map { it.photoId })

        assertTrue(BeforeDeleteReducer.reduce(loaded, BeforeDeleteEvent.DeletingChanged(true)).isDeleting)
        assertTrue(BeforeDeleteReducer.reduce(loaded, BeforeDeleteEvent.PermanentDialogChanged(true)).showPermanentDialog)
    }

    // --- the mapper ---

    @Test
    fun `the button of marked photos has their count and their total size`() {
        val button = state(cell(1, size = 150_000_000), cell(2, size = 250_000_000), cell(3, marked = false)).toButton()

        assertEquals(DeleteButton.Delete(2, FormattedSize("400", SizeUnit.MB)), button)
    }

    @Test
    fun `a size from a gigabyte up is in gigabytes`() {
        val button = state(cell(1, size = 1_500_000_000), cell(2, size = 1_000_000_000)).toButton()

        assertEquals(DeleteButton.Delete(2, FormattedSize("2,5", SizeUnit.GB)), button)
    }

    @Test
    fun `with nothing marked the button is done`() {
        assertEquals(DeleteButton.Done, state(cell(1, marked = false)).toButton())
        assertEquals(DeleteButton.Done, state().toButton())
    }

    @Test
    fun `the intro counts the marked photos and says so when there are none`() {
        assertEquals(DeleteIntro.Marked(2), state(cell(1), cell(2), cell(3, marked = false)).toIntro())
        assertEquals(DeleteIntro.NothingMarked, state(cell(1, marked = false)).toIntro())
    }
}
