package com.stitch.authenticator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the settings screen draws, as one immutable snapshot. */
data class SettingsUiState(
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val dynamicColorEnabled: Boolean = true,
  val biometricLockEnabled: Boolean = false,
)

/**
 * State holder for the settings screen.
 *
 * The three preference flows are [combine]d rather than collected one by one: the screen must never
 * show half of a change — a new theme next to the old toggle. The initial value doubles as the
 * loading state: preferences are one local file away, so a dedicated "loading" state would be a
 * flash of nothing for no honest reason.
 *
 * Writes go straight to the repository and come back through the same flows, so what the screen
 * shows is always what storage holds — never an optimistic guess.
 */
class SettingsViewModel(
  private val settingsRepository: SettingsRepository,
) : ViewModel() {

  val uiState: StateFlow<SettingsUiState> =
    combine(
      settingsRepository.themeMode,
      settingsRepository.dynamicColorEnabled,
      settingsRepository.biometricLockEnabled,
    ) { themeMode, dynamicColor, biometricLock ->
        SettingsUiState(
          themeMode = themeMode,
          dynamicColorEnabled = dynamicColor,
          biometricLockEnabled = biometricLock,
        )
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
      )

  fun setThemeMode(themeMode: ThemeMode) {
    viewModelScope.launch { settingsRepository.setThemeMode(themeMode) }
  }

  fun setDynamicColorEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setDynamicColorEnabled(enabled) }
  }

  fun setBiometricLockEnabled(enabled: Boolean) {
    viewModelScope.launch { settingsRepository.setBiometricLockEnabled(enabled) }
  }

  companion object {
    /** Keep briefly subscribed after the screen leaves, like [com.stitch.authenticator.ui.main.MainViewModel]. */
    private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}
