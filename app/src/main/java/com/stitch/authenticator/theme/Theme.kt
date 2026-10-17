package com.stitch.authenticator.theme

import android.os.Build
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
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
 * @param motionScheme defaults to [MotionScheme.expressive]. Swapping in [MotionScheme.standard] is
 *   the reduced-motion escape hatch (Phase 6 wires this to the system animation-scale setting);
 *   because it flows through the theme, honouring it is a single call, not an audit of every
 *   animation in the app.
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
  motionScheme: MotionScheme = MotionScheme.expressive(),
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

  CompositionLocalProvider(
    LocalStitchSemanticColors provides
      if (darkTheme) DarkStitchSemanticColors else LightStitchSemanticColors
  ) {
    MaterialExpressiveTheme(
      colorScheme = colorScheme,
      motionScheme = motionScheme,
      shapes = Shapes,
      typography = Typography,
      content = content,
    )
  }
}

/** Semantic colors that M3 has no role for (one-time-code state). */
val MaterialTheme.stitchColors: StitchSemanticColors
  @Composable @ReadOnlyComposable get() = LocalStitchSemanticColors.current
