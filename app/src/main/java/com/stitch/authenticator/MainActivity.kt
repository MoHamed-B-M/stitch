package com.stitch.authenticator

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.theme.StitchAuthenticatorTheme

/**
 * The one activity.
 *
 * It extends [FragmentActivity] rather than `ComponentActivity` on purpose: androidx.biometric's
 * [androidx.biometric.BiometricPrompt] attaches to a FragmentActivity, so the settings screen's
 * biometric lock needs that host before it can show a prompt.
 */
class MainActivity : FragmentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val settingsRepository = AppGraph.settingsRepository(this)

    enableEdgeToEdge()
    setContent {
      // Lifecycle-aware: the theme stops being observed (and the flow stops being collected) while
      // the activity is stopped, the same way every screen observes its own state.
      val themeMode by settingsRepository.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
      val dynamicColor by settingsRepository.dynamicColorEnabled.collectAsStateWithLifecycle(true)

      StitchAuthenticatorTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor,
      ) {
        Surface(
          modifier = Modifier.fillMaxSize(),
          color = MaterialTheme.colorScheme.background,
        ) {
          MainNavigation()
        }
      }
    }
  }
}
