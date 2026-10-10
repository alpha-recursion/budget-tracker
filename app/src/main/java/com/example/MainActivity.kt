package com.example

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.sms.AutoCaptureNotifier
import com.example.ui.PennywiseApp
import com.example.ui.PennywiseViewModel
import com.example.ui.theme.PennywiseTheme

class MainActivity : ComponentActivity() {
    // Set when a notification opened the app; cleared once the app has acted on it.
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh launch: after rotation the request was already handled.
        if (savedInstanceState == null) openRequest = intent?.getStringExtra(AutoCaptureNotifier.EXTRA_OPEN)
        // The app always renders the light violet theme, so the system bars must use dark icons
        // regardless of the device's dark-mode setting. The default (auto) style follows the
        // system theme and would draw white status bar icons on our white top bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(LightNavScrim, DarkNavScrim)
        )
        setContent {
            PennywiseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val app = LocalContext.current.applicationContext as Application
                    val viewModel: PennywiseViewModel = viewModel(
                        factory = PennywiseViewModel.Factory(app)
                    )
                    PennywiseApp(
                        viewModel = viewModel,
                        openRequest = openRequest,
                        onOpenRequestHandled = {
                            openRequest = null
                            intent?.removeExtra(AutoCaptureNotifier.EXTRA_OPEN)
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = intent.getStringExtra(AutoCaptureNotifier.EXTRA_OPEN)
    }

    private companion object {
        // Same scrims androidx.activity uses by default for 3-button navigation.
        val LightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
