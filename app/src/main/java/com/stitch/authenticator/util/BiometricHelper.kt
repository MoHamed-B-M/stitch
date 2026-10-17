package com.stitch.authenticator.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** How one [BiometricAuthenticator] attempt ended. */
enum class BiometricAuthResult {
  SUCCESS,
  ERROR,
  FAILED,
  CANCELLED,
}

/** Whether the device can show a biometric prompt right now. */
data class BiometricAuthState(
  val isAvailable: Boolean = false,
  val canAuthenticate: Boolean = false,
)

/** Strong or weak biometrics. Device credentials are not biometrics and are treated separately. */
private val BiometricOnly =
  BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK

/**
 * Answers "can this device show a biometric prompt today?".
 *
 * [BiometricAuthState.canAuthenticate] is strictly [BiometricManager.BIOMETRIC_SUCCESS] — nothing
 * enrolled means offering the lock would offer something that cannot work. The check is cheap (no
 * I/O) but still remembered on its result so recomposition does not repeat it.
 */
@Composable
fun rememberBiometricAuthState(context: Context = LocalContext.current): BiometricAuthState {
  val biometricManager = remember(context) { BiometricManager.from(context) }
  val status = biometricManager.canAuthenticate(BiometricOnly)

  return remember(status) {
    BiometricAuthState(
      isAvailable = status != BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
      canAuthenticate = status == BiometricManager.BIOMETRIC_SUCCESS,
    )
  }
}

/**
 * Hosts one [BiometricPrompt] and hands [content] the function that starts it.
 *
 * The prompt needs a [FragmentActivity] to attach to, so [content] receives the trigger instead of
 * the prompt firing on composition by itself — the caller decides *when*: a
 * [androidx.compose.runtime.LaunchedEffect] for "prompt as soon as this state exists", a click for
 * "prompt to confirm".
 *
 * Everything is remembered on the activity and the callback so the prompt survives recomposition.
 * [onResult] maps errors: the user backing out is [BiometricAuthResult.CANCELLED], not a failure —
 * declining is a valid choice, not an error to report.
 */
@Composable
fun BiometricAuthenticator(
  title: String,
  subtitle: String? = null,
  description: String? = null,
  negativeButtonText: String = "Cancel",
  onResult: (BiometricAuthResult) -> Unit,
  content: @Composable (onAuthenticate: () -> Unit) -> Unit,
) {
  val context = LocalContext.current
  val activity = context as? FragmentActivity
  val authState = rememberBiometricAuthState(context)
  val executor = remember(context) { ContextCompat.getMainExecutor(context) }

  val promptInfo =
    remember(title, subtitle, description, negativeButtonText, authState.canAuthenticate) {
      BiometricPrompt.PromptInfo.Builder()
        .setTitle(title)
        .setSubtitle(subtitle)
        .setDescription(description)
        .setConfirmationRequired(true)
        .apply {
          if (authState.canAuthenticate) {
            setAllowedAuthenticators(BiometricOnly)
            setNegativeButtonText(negativeButtonText)
          } else {
            // Defensive fallback: with no biometrics enrolled the prompt can fall back to the
            // device PIN/pattern, which cannot carry a negative button — the button *is* the
            // credential — so it is simply left unset.
            setAllowedAuthenticators(BiometricManager.Authenticators.DEVICE_CREDENTIAL)
          }
        }
        .build()
    }

  val biometricPrompt =
    remember(activity, executor, onResult) {
      activity?.let { host ->
        BiometricPrompt(host, executor, object : BiometricPrompt.AuthenticationCallback() {
          override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            onResult(BiometricAuthResult.SUCCESS)
          }

          override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            onResult(
              when (errorCode) {
                BiometricPrompt.ERROR_USER_CANCELED,
                BiometricPrompt.ERROR_NEGATIVE_BUTTON,
                ->
                  BiometricAuthResult.CANCELLED

                else -> BiometricAuthResult.ERROR
              },
            )
          }

          override fun onAuthenticationFailed() {
            onResult(BiometricAuthResult.FAILED)
          }
        })
      }
    }

  content {
    biometricPrompt?.authenticate(promptInfo)
  }
}
