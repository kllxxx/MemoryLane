package com.kllxx.memorylane.feature.swipe.impl.beforedelete

import com.kllxx.memorylane.core.domain.format.FormattedSize
import com.kllxx.memorylane.core.domain.format.formatSize

/** What the button of «Перед удалением» says: «Готово» when nothing is marked, else the count and the size. */
internal sealed interface DeleteButton {
    data object Done : DeleteButton
    data class Delete(val count: Int, val size: FormattedSize) : DeleteButton
}

/** The text above the grid. */
internal sealed interface DeleteIntro {
    data object NothingMarked : DeleteIntro
    data class Marked(val count: Int) : DeleteIntro
}

internal fun BeforeDeleteState.toButton(): DeleteButton {
    val marked = marked
    return if (marked.isEmpty()) {
        DeleteButton.Done
    } else {
        DeleteButton.Delete(count = marked.size, size = formatSize(marked.sumOf { it.sizeBytes }))
    }
}

internal fun BeforeDeleteState.toIntro(): DeleteIntro {
    val count = marked.size
    return if (count == 0) DeleteIntro.NothingMarked else DeleteIntro.Marked(count)
}
