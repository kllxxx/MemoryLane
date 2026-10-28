package com.kllxx.memorylane.feature.swipe.impl.common

import androidx.annotation.StringRes
import com.kllxx.memorylane.core.domain.format.SizeUnit
import com.kllxx.memorylane.feature.swipe.impl.R

/** The text of a size unit: «МБ» or «ГБ». */
@StringRes
internal fun SizeUnit.labelRes(): Int = when (this) {
    SizeUnit.MB -> R.string.result_unit_mb
    SizeUnit.GB -> R.string.result_unit_gb
}
