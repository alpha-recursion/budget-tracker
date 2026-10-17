package com.example

import android.app.Application
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import com.example.data.local.AppLockStore
import com.example.ui.screens.LockScreen
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
import com.example.data.reminders.ReminderScheduler
import com.example.data.sms.AutoCaptureNotifier
import com.example.ui.PennywiseApp
import com.example.ui.PennywiseViewModel
import com.example.ui.theme.PennywiseTheme

// FragmentActivity: needed by the fingerprint / screen-lock prompt (BiometricPrompt).
class MainActivity : FragmentActivity() {
    private lateinit var appLock: AppLockStore

    // True while the app lock screen covers the app.
    private var locked by mutableStateOf(false)

    // When the app last went to the background; 0 = not yet.
    private var backgroundedAt = 0L

    // Set when a notification opened the app; cleared once the app has acted on it.
    private var openRequest by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appLock = AppLockStore(this)
        // Locked on a fresh launch; after rotation the unlocked state is kept.
        locked = appLock.isEnabled() && (savedInstanceState?.getBoolean(KEY_UNLOCKED) != true)
        ReminderScheduler.schedule(applicationContext)
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
                    val lockOn by appLock.enabled.collectAsState(initial = appLock.isEnabled())
                    // Hide content from screenshots and the recent-apps preview while the lock is on.
                    LaunchedEffect(lockOn) {
                        if (lockOn) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                    if (locked && lockOn) {
                        LockScreen(onUnlock = ::promptUnlock)
                        // Ask straight away when the lock screen appears.
                        LaunchedEffect(Unit) { promptUnlock() }
                    } else {
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
    }

    override fun onStop() {
        super.onStop()
        // Rotation stops and restarts the activity too; only real trips away count.
        if (!isChangingConfigurations) backgroundedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = backgroundedAt != 0L && SystemClock.elapsedRealtime() - backgroundedAt > AppLockStore.RELOCK_AFTER_MS
        if (away && appLock.isEnabled()) locked = true
        backgroundedAt = 0L
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked)
    }

    /** Shows the phone's own unlock (fingerprint, face, or screen PIN / pattern / password). */
    private fun promptUnlock() {
        if (!appLock.isAvailable()) {
            // The phone's screen lock was removed: nothing to unlock with, so do not trap the owner.
            locked = false
            return
        }
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                locked = false
            }
        })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Pennywise")
                .setSubtitle("Use your fingerprint, face or phone PIN")
                .setAllowedAuthenticators(AppLockStore.AUTHENTICATORS)
                .build()
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openRequest = intent.getStringExtra(AutoCaptureNotifier.EXTRA_OPEN)
    }

    private companion object {
        const val KEY_UNLOCKED = "app_unlocked"

        // Same scrims androidx.activity uses by default for 3-button navigation.
        val LightNavScrim = Color.argb(0xe6, 0xFF, 0xFF, 0xFF)
        val DarkNavScrim = Color.argb(0x80, 0x1b, 0x1b, 0x1b)
    }
}
