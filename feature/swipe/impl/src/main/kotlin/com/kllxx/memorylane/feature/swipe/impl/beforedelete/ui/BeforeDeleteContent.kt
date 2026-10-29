package com.kllxx.memorylane.feature.swipe.impl.beforedelete.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kllxx.memorylane.core.ui.R as UiR
import com.kllxx.memorylane.core.ui.component.PhotoThumbnail
import com.kllxx.memorylane.feature.swipe.impl.R
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteIntent
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.BeforeDeleteState
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.DeleteButton
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.DeleteCell
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.DeleteGroup
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.DeleteIntro
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.toButton
import com.kllxx.memorylane.feature.swipe.impl.beforedelete.toIntro
import com.kllxx.memorylane.feature.swipe.impl.common.ReviewButton
import com.kllxx.memorylane.feature.swipe.impl.common.labelRes
import com.kllxx.memorylane.core.domain.format.formatDayMonth
import java.util.Locale

private val CellShape = RoundedCornerShape(14.dp)
private val GridGap = 8.dp
private val MarkedBorder = 3.dp
private const val MARKED_TINT_ALPHA = 0.12f
private const val UNMARKED_BADGE_ALPHA = 0.35f

/** «Перед удалением» for a [BeforeDeleteState]; every action goes out as an intent. */
@Composable
fun BeforeDeleteContent(
    state: BeforeDeleteState,
    onIntent: (BeforeDeleteIntent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier.fillMaxSize().background(colors.surface)) {
        TopBar(onBack)
        Intro(state)
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (state.isLoading) {
                val loading = stringResource(R.string.before_delete_loading)
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).semantics { contentDescription = loading },
                )
            } else {
                PhotoGrid(state, onIntent)
            }
        }
        DeleteActionButton(state, onIntent)
    }
    if (state.showPermanentDialog) PermanentDeleteDialog(state, onIntent)
}

@Composable
private fun TopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag(BeforeDeleteTags.BACK)) {
            Icon(
                painter = painterResource(UiR.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.before_delete_back),
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.before_delete_title),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun Intro(state: BeforeDeleteState) {
    val text = when (val intro = state.toIntro()) {
        DeleteIntro.NothingMarked -> stringResource(R.string.before_delete_description_none)
        is DeleteIntro.Marked -> pluralStringResource(R.plurals.before_delete_description, intro.count, intro.count)
    }
    Text(
        text = text,
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp).testTag(BeforeDeleteTags.INTRO),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 14.sp,
    )
}

@Composable
private fun PhotoGrid(state: BeforeDeleteState, onIntent: (BeforeDeleteIntent) -> Unit) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().testTag(BeforeDeleteTags.GRID),
        // 3 cells and 2 gaps are as wide as the button below: 104dp cells on a 360dp screen
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(GridGap),
        verticalArrangement = Arrangement.spacedBy(GridGap),
    ) {
        val groups = state.groups
        var number = 0
        groups.forEach { group ->
            // The current day alone needs no header; a day before it always says so
            if (groups.size > 1 || !group.isCurrent) {
                item(key = "group-${group.date}", span = { GridItemSpan(maxLineSpan) }) { GroupHeader(group) }
            }
            val first = number
            number += group.cells.size
            itemsIndexed(group.cells, key = { _, cell -> cell.photoId }) { index, cell ->
                DeleteGridCell(
                    cell = cell,
                    number = first + index + 1,
                    enabled = !state.isDeleting,
                    onToggle = { onIntent(BeforeDeleteIntent.Toggle(cell.photoId)) },
                )
            }
        }
    }
}

/** The day of a group; the days before the current one say they were marked earlier. */
@Composable
private fun GroupHeader(group: DeleteGroup) {
    val day = formatDayMonth(group.date, Locale.getDefault())
    Text(
        text = if (group.isCurrent) day else stringResource(R.string.before_delete_earlier, day),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag(BeforeDeleteTags.groupHeader(group.date)),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = 13.sp,
        fontWeight = FontWeight.ExtraBold,
    )
}

@Composable
private fun DeleteGridCell(cell: DeleteCell, number: Int, enabled: Boolean, onToggle: () -> Unit) {
    val error = MaterialTheme.colorScheme.error
    val description = stringResource(
        if (cell.marked) R.string.before_delete_cell_marked else R.string.before_delete_cell_unmarked,
        number,
    )
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .testTag(BeforeDeleteTags.cell(cell.photoId))
            .clip(CellShape)
            .toggleable(value = cell.marked, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle() })
            .semantics { contentDescription = description },
    ) {
        // The photo is decoded for the size of its cell
        PhotoThumbnail(uri = cell.uri, modifier = Modifier.fillMaxSize(), decodeToCellSize = true)
        if (cell.marked) {
            Box(Modifier.fillMaxSize().background(error.copy(alpha = MARKED_TINT_ALPHA)))
            Box(Modifier.fillMaxSize().border(MarkedBorder, error, CellShape))
        }
        Badge(marked = cell.marked, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
    }
}

/** A red circle with a check on a marked photo, an empty white circle on a kept one. */
@Composable
private fun Badge(marked: Boolean, modifier: Modifier = Modifier) {
    if (marked) {
        Box(
            modifier = modifier.size(24.dp).background(MaterialTheme.colorScheme.error, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(UiR.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onError,
            )
        }
    } else {
        Box(
            modifier = modifier
                .size(24.dp)
                .background(Color.White.copy(alpha = UNMARKED_BADGE_ALPHA), CircleShape)
                .border(2.dp, Color.White, CircleShape),
        )
    }
}

@Composable
private fun DeleteActionButton(state: BeforeDeleteState, onIntent: (BeforeDeleteIntent) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val text: String
    val container: Color
    val content: Color
    when (val button = state.toButton()) {
        DeleteButton.Done -> {
            text = stringResource(R.string.before_delete_done)
            container = colors.primary
            content = colors.onPrimary
        }
        is DeleteButton.Delete -> {
            text = pluralStringResource(
                R.plurals.before_delete_button,
                button.count,
                button.count,
                button.size.value,
                stringResource(button.size.unit.labelRes()),
            )
            container = colors.error
            content = colors.onError
        }
    }
    ReviewButton(
        text = text,
        containerColor = container,
        contentColor = content,
        onClick = { onIntent(BeforeDeleteIntent.DeleteClicked) },
        modifier = Modifier.padding(16.dp).testTag(BeforeDeleteTags.BUTTON),
        enabled = !state.isDeleting && !state.isLoading,
    )
}

@Composable
private fun PermanentDeleteDialog(state: BeforeDeleteState, onIntent: (BeforeDeleteIntent) -> Unit) {
    val count = state.marked.size
    AlertDialog(
        onDismissRequest = { onIntent(BeforeDeleteIntent.PermanentDeleteDismissed) },
        title = { Text(pluralStringResource(R.plurals.before_delete_dialog_title, count, count)) },
        text = { Text(stringResource(R.string.before_delete_dialog_message)) },
        confirmButton = {
            TextButton(
                onClick = { onIntent(BeforeDeleteIntent.PermanentDeleteConfirmed) },
                modifier = Modifier.testTag(BeforeDeleteTags.DIALOG_CONFIRM),
            ) {
                Text(stringResource(R.string.before_delete_dialog_confirm), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(
                onClick = { onIntent(BeforeDeleteIntent.PermanentDeleteDismissed) },
                modifier = Modifier.testTag(BeforeDeleteTags.DIALOG_DISMISS),
            ) {
                Text(stringResource(R.string.before_delete_dialog_dismiss))
            }
        },
    )
}
