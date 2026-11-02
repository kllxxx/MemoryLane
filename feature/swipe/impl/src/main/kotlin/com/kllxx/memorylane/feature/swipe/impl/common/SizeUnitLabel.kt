package com.kllxx.memorylane.feature.swipe.impl.common

import com.kllxx.memorylane.core.ui.R as UiR
import androidx.annotation.StringRes
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.feature.swipe.impl.R

/** The text of a size unit: «КБ», «МБ» or «ГБ». */
@StringRes
internal fun SizeUnit.labelRes(): Int = when (this) {
    SizeUnit.KB -> UiR.string.common_unit_kb
    SizeUnit.MB -> UiR.string.common_unit_mb
    SizeUnit.GB -> UiR.string.common_unit_gb
}
