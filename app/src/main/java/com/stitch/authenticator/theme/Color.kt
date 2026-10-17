package com.stitch.authenticator.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Stitch brand palette.
 *
 * Seeded from the launcher-icon blue (`#2F71C1`) and expanded into a Material 3 tonal palette: a
 * 40-tone family for light themes, an 80-tone family for dark themes, each with matching container
 * / on-container pairs plus neutrals. This is the fallback used whenever dynamic color is not
 * available (API < 31, or the user disabled it), so the app always reads as "Stitch".
 */

// --- Light ---------------------------------------------------------------------------------------

val LightPrimary = Color(0xFF1F5FA9)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFD5E3FF)
val LightOnPrimaryContainer = Color(0xFF001B3D)

val LightSecondary = Color(0xFF565F71)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFDAE2F9)
val LightOnSecondaryContainer = Color(0xFF131C2B)

val LightTertiary = Color(0xFF705575)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFFAD8FD)
val LightOnTertiaryContainer = Color(0xFF28132E)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFDFBFF)
val LightOnBackground = Color(0xFF1A1B1F)

val LightSurface = Color(0xFFFDFBFF)
val LightOnSurface = Color(0xFF1A1B1F)
val LightSurfaceVariant = Color(0xFFE1E2EC)
val LightOnSurfaceVariant = Color(0xFF44474F)

val LightOutline = Color(0xFF74777F)
val LightOutlineVariant = Color(0xFFC4C6D0)

val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
val LightSurfaceContainerLow = Color(0xFFF7F5FA)
val LightSurfaceContainer = Color(0xFFF1EFF4)
val LightSurfaceContainerHigh = Color(0xFFEBEAF0)
val LightSurfaceContainerHighest = Color(0xFFE5E4EA)

// --- Dark ----------------------------------------------------------------------------------------

val DarkPrimary = Color(0xFFA8C8FF)
val DarkOnPrimary = Color(0xFF003063)
val DarkPrimaryContainer = Color(0xFF00458C)
val DarkOnPrimaryContainer = Color(0xFFD5E3FF)

val DarkSecondary = Color(0xFFBEC6DC)
val DarkOnSecondary = Color(0xFF283041)
val DarkSecondaryContainer = Color(0xFF3E4759)
val DarkOnSecondaryContainer = Color(0xFFDAE2F9)

val DarkTertiary = Color(0xFFDDBCE0)
val DarkOnTertiary = Color(0xFF3E2844)
val DarkTertiaryContainer = Color(0xFF563C5C)
val DarkOnTertiaryContainer = Color(0xFFFAD8FD)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF1A1B1F)
val DarkOnBackground = Color(0xFFE3E2E6)

val DarkSurface = Color(0xFF1A1B1F)
val DarkOnSurface = Color(0xFFE3E2E6)
val DarkSurfaceVariant = Color(0xFF44474F)
val DarkOnSurfaceVariant = Color(0xFFC4C6D0)

val DarkOutline = Color(0xFF8E9099)
val DarkOutlineVariant = Color(0xFF44474F)

val DarkSurfaceContainerLowest = Color(0xFF0F0F13)
val DarkSurfaceContainerLow = Color(0xFF1A1B1F)
val DarkSurfaceContainer = Color(0xFF1E1F23)
val DarkSurfaceContainerHigh = Color(0xFF292A2E)
val DarkSurfaceContainerHighest = Color(0xFF343539)

// --- Semantic tokens -----------------------------------------------------------------------------

/**
 * Colors that Material 3 has no role for, but an authenticator needs: the state of a rotating
 * one-time code. Kept separate from [androidx.compose.material3.ColorScheme] (which cannot be
 * extended) and provided through [LocalStitchSemanticColors].
 */
@Immutable
data class StitchSemanticColors(
  /** Code still has plenty of life left. */
  val codeValid: Color,
  /** Code is about to roll over — warn before it happens. */
  val codeExpiring: Color,
  /** Code has expired / the account cannot produce a code. */
  val codeExpired: Color,
)

val LightStitchSemanticColors =
  StitchSemanticColors(
    codeValid = Color(0xFF1B7F4B),
    codeExpiring = Color(0xFFB26A00),
    codeExpired = Color(0xFFBA1A1A),
  )

val DarkStitchSemanticColors =
  StitchSemanticColors(
    codeValid = Color(0xFF6FDBA0),
    codeExpiring = Color(0xFFFFC46B),
    codeExpired = Color(0xFFFFB4AB),
  )

val LocalStitchSemanticColors = staticCompositionLocalOf { LightStitchSemanticColors }
