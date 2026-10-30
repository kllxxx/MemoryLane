package com.kllxx.memorylane.feature.onboarding.impl

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.core.ui.component.PageIndicator
import com.kllxx.memorylane.core.ui.component.PrimaryButton
import com.kllxx.memorylane.core.ui.component.StatusBarScrim
import com.kllxx.memorylane.core.ui.permission.rememberPermissionRequester
import com.kllxx.memorylane.core.ui.theme.DarkSpringGreen
import com.kllxx.memorylane.core.ui.theme.Spacing
import com.kllxx.memorylane.feature.onboarding.impl.ui.InfoRow
import com.kllxx.memorylane.feature.onboarding.impl.ui.LockIllustration
import com.kllxx.memorylane.feature.onboarding.impl.ui.NotificationCardsIllustration
import com.kllxx.memorylane.feature.onboarding.impl.ui.OnboardingSurface
import com.kllxx.memorylane.feature.onboarding.impl.ui.OnboardingTopBar
import com.kllxx.memorylane.feature.onboarding.impl.ui.SlideText
import com.kllxx.memorylane.feature.onboarding.impl.ui.StackedCardsIllustration
import com.kllxx.memorylane.feature.onboarding.impl.ui.SwipeCardIllustration

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    val photoRequester = rememberPermissionRequester { result ->
        viewModel.onIntent(OnboardingIntent.PhotoPermissionResult(OnboardingPermissions.isMediaAccessGranted(result)))
    }
    val notificationRequester = rememberPermissionRequester { result ->
        val granted = OnboardingPermissions.notification()?.let { result[it] } == true
        viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted))
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.RequestPhotoPermissionLauncher ->
                    photoRequester.request(OnboardingPermissions.photo())
                OnboardingEffect.RequestNotificationPermissionLauncher -> {
                    val permission = OnboardingPermissions.notification()
                    // No runtime permission before API 33, nothing to ask
                    if (permission != null) {
                        notificationRequester.request(listOf(permission))
                    } else {
                        viewModel.onIntent(OnboardingIntent.NotificationPermissionResult(granted = true))
                    }
                }
                OnboardingEffect.OpenAppSettings -> photoRequester.openAppSettings()
                OnboardingEffect.NavigateToSwipe -> onFinished()
            }
        }
    }

    BackHandler(enabled = state.currentPage > 0) { viewModel.onIntent(OnboardingIntent.PreviousPage) }

    OnboardingContent(state = state, onIntent = viewModel::onIntent)
}

@Composable
internal fun OnboardingContent(
    state: OnboardingState,
    onIntent: (OnboardingIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingSurface(modifier = modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            StatusBarScrim(color = DarkSpringGreen)
            OnboardingTopBar(
                showBack = state.currentPage > 0,
                onBack = { onIntent(OnboardingIntent.PreviousPage) },
                onSkip = { onIntent(OnboardingIntent.Skip) },
            )
            AnimatedContent(
                targetState = state.currentPage,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = Spacing.ScreenHorizontal + 8.dp),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "onboardingSlide",
            ) { page ->
                OnboardingSlide(page)
            }
            PageIndicator(
                pageCount = state.totalPages,
                currentPage = state.currentPage,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 20.dp),
            )
            PrimaryButton(
                text = stringResource(
                    if (state.currentPage < OnboardingConstants.PHOTO_PAGE) R.string.onboarding_next else R.string.onboarding_allow,
                ),
                onClick = { onIntent(state.primaryAction()) },
                enabled = !state.isLoading,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.ScreenHorizontal)
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (state.showPermissionRationale) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.onboarding_rationale_title)) },
            text = { Text(stringResource(R.string.onboarding_rationale_text)) },
            confirmButton = {
                TextButton(onClick = { onIntent(OnboardingIntent.OpenSettings) }) {
                    Text(stringResource(R.string.onboarding_rationale_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { onIntent(OnboardingIntent.DismissRationale) }) {
                    Text(stringResource(R.string.onboarding_rationale_continue))
                }
            },
        )
    }
}

private fun OnboardingState.primaryAction(): OnboardingIntent = when (currentPage) {
    OnboardingConstants.PHOTO_PAGE -> OnboardingIntent.RequestPhotoPermission
    OnboardingConstants.NOTIFICATION_PAGE -> OnboardingIntent.RequestNotificationPermission
    else -> OnboardingIntent.NextPage
}

@Composable
private fun OnboardingSlide(page: Int) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when (page) {
                0 -> StackedCardsIllustration()
                1 -> Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SwipeCardIllustration()
                    InfoRow(stringResource(R.string.onboarding_slide2_row_streak), positive = true)
                    InfoRow(stringResource(R.string.onboarding_slide2_row_request), positive = true)
                }
                2 -> Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    LockIllustration()
                    InfoRow(stringResource(R.string.onboarding_slide3_row_access), positive = true)
                    InfoRow(stringResource(R.string.onboarding_slide3_row_local), positive = false)
                }
                else -> NotificationCardsIllustration()
            }
        }
        val (title, subtitle) = when (page) {
            0 -> R.string.onboarding_slide1_title to R.string.onboarding_slide1_subtitle
            1 -> R.string.onboarding_slide2_title to R.string.onboarding_slide2_subtitle
            2 -> R.string.onboarding_slide3_title to R.string.onboarding_slide3_subtitle
            else -> R.string.onboarding_slide4_title to R.string.onboarding_slide4_subtitle
        }
        SlideText(stringResource(title), stringResource(subtitle), Modifier.padding(top = 16.dp))
    }
}
