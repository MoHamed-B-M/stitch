package com.stitch.authenticator.data.settings

/**
 * How the app picks its color scheme.
 *
 * Stored as the ordinal in the preferences file. [SYSTEM] is the default, so a fresh install simply
 * follows the device until the user says otherwise.
 */
enum class ThemeMode {
  SYSTEM,
  LIGHT,
  DARK,
}
