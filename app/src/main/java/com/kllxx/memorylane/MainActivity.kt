package com.kllxx.memorylane

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kllxx.memorylane.core.ui.theme.MemoryLaneTheme
import com.kllxx.memorylane.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The first frame is drawn only with the stored theme and start destination, never a default
        splashScreen.setKeepOnScreenCondition { viewModel.uiState.value is AppUiState.Loading }
        enableEdgeToEdge()
        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            (uiState as? AppUiState.Ready)?.let { ready ->
                MemoryLaneTheme(themeMode = ready.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(startDestination = ready.startDestination, today = ready.today)
                    }
                }
            }
        }
    }
}
