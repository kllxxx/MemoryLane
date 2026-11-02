package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kllxx.memorylane.core.domain.language.AppLanguage
import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.core.ui.component.SettingsRow
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal fun languageSegmentTag(language: AppLanguage) = "language-segment-${language.name}"

/** The language of the app, the same segmented control as the theme. */
@Composable
internal fun LanguageCard(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        Column {
            SettingsRow(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.profile_language_title),
            )
            SegmentedRow {
                AppLanguage.entries.forEach { language ->
                    Segment(
                        label = stringResource(language.labelRes()),
                        selected = language == state.language,
                        onClick = { onIntent(ProfileIntent.SelectLanguage(language)) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag(languageSegmentTag(language)),
                    )
                }
            }
        }
    }
}

private fun AppLanguage.labelRes(): Int = when (this) {
    AppLanguage.System -> R.string.profile_language_system
    AppLanguage.Russian -> R.string.profile_language_russian
    AppLanguage.English -> R.string.profile_language_english
}
