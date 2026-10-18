package com.stitch.authenticator.theme

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.stitch.authenticator.data.settings.ThemeMode

private val StitchDarkColorScheme =
  darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
  )

private val StitchLightColorScheme =
  lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
  )

/**
 * Stitch theme.
 *
 * [MaterialExpressiveTheme] — not `MaterialTheme` — is what makes every Material component move
 * expressively: it installs [MotionScheme.expressive] into the theme, so component motion (FAB,
 * chips, segmented buttons, sheets, swipe-to-dismiss) is physics-driven without any per-call-site
 * animation code.
 *
 * @param dynamicColor Android 12+ wallpaper colors, off-switchable for a branded look.
 * @param motionScheme null (the default) means the expressive scheme — or the calmer standard
 *   scheme when the system's reduced-motion setting is on ("Remove animations"), so honouring
 *   that is a single call here rather than an audit of every animation in the app. Passing an
 *   explicit scheme overrides both choices.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StitchAuthenticatorTheme(
  themeMode: ThemeMode = ThemeMode.SYSTEM,
  darkTheme: Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
  },
  // Dynamic color is available on Android 12+
  dynamicColor: Boolean = true,
  motionScheme: MotionScheme? = null,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }
      darkTheme -> StitchDarkColorScheme
      else -> StitchLightColorScheme
    }

  // null means "the app's choice": the expressive scheme — or the calmer standard scheme when
  // the system asks for reduced motion (accessibility "Remove animations"), so every component
  // that asks the theme for its motion gets flat springs instead of bouncy ones in one place.
  val resolvedMotionScheme =
    motionScheme
      ?: if (rememberSystemReducedMotion()) MotionScheme.standard() else MotionScheme.expressive()

  CompositionLocalProvider(
    LocalStitchSemanticColors provides
      if (darkTheme) DarkStitchSemanticColors else LightStitchSemanticColors
  ) {
    MaterialExpressiveTheme(
      colorScheme = colorScheme,
      motionScheme = resolvedMotionScheme,
      shapes = Shapes,
      typography = Typography,
      content = content,
    )
  }
}

/**
 * Whether the system is asking apps to cut motion: the accessibility "Remove animations" toggles
 * drive `animator_duration_scale` and `transition_animation_scale` to zero.
 *
 * Re-read on every resume rather than trusted from composition time: the only way to flip the
 * setting is with another app in front, and coming back keeps this composition — and its
 * remembered answers — alive. (The app's authored springs are additionally ended mid-flight by
 * the platform itself, which observes the same setting live; the flag here picks the *character*
 * of whatever motion still runs.)
 */
@Composable
private fun rememberSystemReducedMotion(): Boolean {
  val context = LocalContext.current
  val reducedMotion = remember { mutableStateOf(readReducedMotion(context)) }
  val lifecycleOwner = LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        reducedMotion.value = readReducedMotion(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  return reducedMotion.value
}

private fun readReducedMotion(context: Context): Boolean {
  val resolver = context.contentResolver
  return Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f ||
    Settings.Global.getFloat(resolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f) == 0f
}

/** Semantic colors that M3 has no role for (one-time-code state). */
val MaterialTheme.stitchColors: StitchSemanticColors
  @Composable @ReadOnlyComposable get() = LocalStitchSemanticColors.current
