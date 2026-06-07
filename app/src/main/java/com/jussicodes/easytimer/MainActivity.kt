package com.jussicodes.easytimer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jussicodes.easytimer.model.Screen
import com.jussicodes.easytimer.ui.screens.ActiveTimerScreen
import com.jussicodes.easytimer.ui.screens.HomeScreen
import com.jussicodes.easytimer.ui.screens.SettingsScreen
import com.jussicodes.easytimer.ui.screens.TimerSetupScreen
import com.jussicodes.easytimer.ui.theme.EasyTimerTheme
import com.jussicodes.easytimer.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or denied — proceed either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        enableEdgeToEdge()
        setContent {
            EasyTimerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent()
                }
            }
        }
    }
}

@Composable
private fun AppContent() {
    val viewModel: MainViewModel = viewModel()
    val screen by viewModel.currentScreen.collectAsState()
    val selectedApp by viewModel.selectedApp.collectAsState()

    Crossfade(targetState = screen, label = "screenTransition") { targetScreen ->
        when (targetScreen) {
            Screen.HOME -> {
                HomeScreen(viewModel = viewModel)
            }
            Screen.TIMER_SETUP -> {
                val app = selectedApp
                if (app != null) {
                    TimerSetupScreen(viewModel = viewModel, app = app)
                } else {
                    HomeScreen(viewModel = viewModel)
                }
            }
            Screen.ACTIVE_TIMER -> {
                ActiveTimerScreen(viewModel = viewModel)
            }
            Screen.SETTINGS -> {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
