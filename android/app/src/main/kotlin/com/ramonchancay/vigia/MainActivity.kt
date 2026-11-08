package com.ramonchancay.vigia

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ramonchancay.vigia.core.designsystem.theme.VigiaTheme
import com.ramonchancay.vigia.core.server.ScreenCapture
import com.ramonchancay.vigia.core.server.VigiaServerService
import com.ramonchancay.vigia.navigation.VigiaNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts the panel: a full-screen, always-on status display. The screen stays on while the panel is visible
 * (with the screen on, Doze does not kick in), and the system bars stay hidden.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Inject
    lateinit var screenCapture: ScreenCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash until we know whether to open setup or the panel.
        splash.setKeepOnScreenCondition { viewModel.startDestination.value == null }
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Draw under the notch too; in landscape it sits on a short edge.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        hideSystemBars()
        setContent {
            val startDestination by viewModel.startDestination.collectAsStateWithLifecycle()
            VigiaTheme {
                startDestination?.let { VigiaNavHost(startDestination = it) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The remote-control server runs as a foreground service; starting it again is a no-op.
        VigiaServerService.start(this)
        screenCapture.register(window)
    }

    override fun onStop() {
        screenCapture.unregister(window)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs (USB, permissions) and some OEM skins bring the bars back; hide them again on return.
        if (hasFocus) hideSystemBars()
    }

    /** Bars stay hidden and only come back briefly on an edge swipe. */
    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
