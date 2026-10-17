package com.stitch.authenticator.data.settings

import kotlinx.coroutines.flow.Flow

/**
 * The app's user-facing settings.
 *
 * A seam over [SettingsDataStore] so screens and tests depend on three flows and three suspend
 * functions rather than on DataStore itself.
 */
interface SettingsRepository {
  val themeMode: Flow<ThemeMode>
  val dynamicColorEnabled: Flow<Boolean>
  val biometricLockEnabled: Flow<Boolean>

  suspend fun setThemeMode(themeMode: ThemeMode)
  suspend fun setDynamicColorEnabled(enabled: Boolean)
  suspend fun setBiometricLockEnabled(enabled: Boolean)
}

/** Pass-through implementation; [settingsDataStore] owns all the behaviour. */
class SettingsRepositoryImpl(
  private val settingsDataStore: SettingsDataStore,
) : SettingsRepository {

  override val themeMode: Flow<ThemeMode> = settingsDataStore.themeMode
  override val dynamicColorEnabled: Flow<Boolean> = settingsDataStore.dynamicColorEnabled
  override val biometricLockEnabled: Flow<Boolean> = settingsDataStore.biometricLockEnabled

  override suspend fun setThemeMode(themeMode: ThemeMode) {
    settingsDataStore.setThemeMode(themeMode)
  }

  override suspend fun setDynamicColorEnabled(enabled: Boolean) {
    settingsDataStore.setDynamicColorEnabled(enabled)
  }

  override suspend fun setBiometricLockEnabled(enabled: Boolean) {
    settingsDataStore.setBiometricLockEnabled(enabled)
  }
}
