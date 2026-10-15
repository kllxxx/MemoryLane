package com.kllxx.memorylane.feature.onboarding.impl

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        viewModel.onIntent(
            OnboardingIntent.PermissionResult(OnboardingPermissions.isMediaAccessGranted(result)),
        )
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                OnboardingEffect.RequestPermissionLauncher ->
                    permissionLauncher.launch(OnboardingPermissions.required().toTypedArray())
                OnboardingEffect.NavigateToSwipe -> onFinished()
            }
        }
    }

    // Placeholder UI until the onboarding pages are designed
    Column {
        Text("Onboarding")
        Button(onClick = { viewModel.onIntent(OnboardingIntent.RequestPermissions) }) {
            Text("Grant access")
        }
    }
}
