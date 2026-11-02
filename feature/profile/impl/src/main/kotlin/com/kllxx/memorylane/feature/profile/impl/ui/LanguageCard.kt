package com.kllxx.memorylane.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kllxx.memorylane.core.domain.language.AppLanguage
import com.kllxx.memorylane.core.ui.component.RowsCard
import com.kllxx.memorylane.feature.profile.impl.ProfileIntent
import com.kllxx.memorylane.feature.profile.impl.ProfileState
import com.kllxx.memorylane.feature.profile.impl.R

internal fun languageSegmentTag(language: AppLanguage) = "language-segment-${language.name}"

/** The language of the app under its own header, the same segmented control as the theme. */
@Composable
internal fun LanguageCard(
    state: ProfileState.Content,
    onIntent: (ProfileIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    RowsCard(modifier) {
        Column {
            Spacer(Modifier.height(16.dp))
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
