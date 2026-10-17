package com.stitch.authenticator.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val SETTINGS_DATASTORE_NAME = "settings_preferences"

val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = SETTINGS_DATASTORE_NAME)

/** The keys, named once so reads and writes cannot drift apart. */
object SettingsKeys {
  val THEME_MODE = intPreferencesKey("theme_mode")
  val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
  val BIOMETRIC_LOCK_ENABLED = booleanPreferencesKey("biometric_lock_enabled")
}

/**
 * The one file the settings live in.
 *
 * No property touches disk synchronously: every read is a cold [Flow] over DataStore's own access,
 * which already runs off the main thread. Defaults are applied at read time, so a fresh install
 * needs no migration and an unknown stored ordinal falls back to [ThemeMode.SYSTEM] instead of
 * crashing.
 *
 * Token secrets deliberately do not live here — they stay in the encrypted storage behind
 * [com.stitch.authenticator.data.TokenRepository]. A preference file is not worth a Keystore round
 * trip; a theme choice is not worth one either.
 */
class SettingsDataStore(private val context: Context) {

  val themeMode: Flow<ThemeMode> = context.settingsDataStore.data.map { preferences ->
    val mode = preferences[SettingsKeys.THEME_MODE] ?: ThemeMode.SYSTEM.ordinal
    ThemeMode.entries.getOrNull(mode) ?: ThemeMode.SYSTEM
  }

  val dynamicColorEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
    preferences[SettingsKeys.DYNAMIC_COLOR_ENABLED] ?: true
  }

  val biometricLockEnabled: Flow<Boolean> = context.settingsDataStore.data.map { preferences ->
    preferences[SettingsKeys.BIOMETRIC_LOCK_ENABLED] ?: false
  }

  suspend fun setThemeMode(themeMode: ThemeMode) {
    context.settingsDataStore.edit { preferences ->
      preferences[SettingsKeys.THEME_MODE] = themeMode.ordinal
    }
  }

  suspend fun setDynamicColorEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { preferences ->
      preferences[SettingsKeys.DYNAMIC_COLOR_ENABLED] = enabled
    }
  }

  suspend fun setBiometricLockEnabled(enabled: Boolean) {
    context.settingsDataStore.edit { preferences ->
      preferences[SettingsKeys.BIOMETRIC_LOCK_ENABLED] = enabled
    }
  }
}
