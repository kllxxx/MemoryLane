package com.kllxx.memorylane.feature.profile.impl

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProfileScreen(
    onNavigateToOnboarding: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    LaunchedEffect(viewModel) {
        viewModel.onIntent(ProfileIntent.Load)
        viewModel.effects.collect { effect ->
            when (effect) {
                ProfileEffect.NavigateToOnboarding -> onNavigateToOnboarding()
                ProfileEffect.ShowSettingsError -> Unit
            }
        }
    }

    Text("Profile")
}
