package com.kllxx.memorylane

import android.content.Intent
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
import com.kllxx.memorylane.reminder.ReminderConstants
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        // The first frame is drawn only with the stored theme and start destination, never a default
        splashScreen.setKeepOnScreenCondition { viewModel.state.value is AppUiState.Loading }
        enableEdgeToEdge()
        // A start from the reminder; a recreation (rotation) is not a new tap, the intent was handled the first time
        if (savedInstanceState == null) handleReminderIntent(intent)
        setContent {
            val uiState by viewModel.state.collectAsStateWithLifecycle()

            (uiState as? AppUiState.Ready)?.let { ready ->
                MemoryLaneTheme(themeMode = ready.themeMode) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        AppNavHost(
                            startDestination = ready.startDestination,
                            appEffects = viewModel.effects,
                        )
                    }
                }
            }
        }
    }

    /** The reminder was tapped while the app was open: this activity is brought to the front instead of a second one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleReminderIntent(intent)
    }

    private fun handleReminderIntent(intent: Intent?) {
        if (intent?.action != ReminderConstants.ACTION_OPEN_TODAY) return
        // Taken, so that it is not taken again by a recreation of the activity
        intent.action = null
        viewModel.onIntent(AppIntent.ReminderOpened)
    }
}
