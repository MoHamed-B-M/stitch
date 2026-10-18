package com.stitch.authenticator.ui.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.vault.PasswordEntry
import com.stitch.authenticator.data.vault.VaultStorage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** What the passwords tab shows. */
sealed interface PasswordsUiState {

  /**
   * The biometric gate: the database has not been opened, and never will be without a prompt.
   * This is a *state*, not a one-shot event — cancelling the prompt lands right back here.
   */
  data object Locked : PasswordsUiState

  /** The prompt succeeded; the database is opening and the first listing has not arrived. */
  data object Loading : PasswordsUiState

  /** Unlocked: [entries] is everything [query] currently matches. */
  data class Ready(val query: String, val entries: List<PasswordEntry>) : PasswordsUiState

  /** The database refused to open — the vault stays closed and the prompt can be tried again. */
  data class Error(val throwable: Throwable) : PasswordsUiState
}

/**
 * State machine for the passwords tab: locked until [unlock] runs after a successful biometric
 * prompt, then a live listing that re-emits per query.
 *
 * The gate is this view model's invariant, not the storage's: [VaultStorage.unlock] *can* be
 * called at any time, and the JVM tests prove what the UI must uphold — nothing queries the
 * vault before [PasswordsUiState.Ready] (the fake refuses to answer, so a violation would land
 * in [PasswordsUiState.Error]), and a failed unlock keeps the vault closed rather than leaving
 * a half-open database behind a red screen.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordsViewModel(private val vault: VaultStorage) : ViewModel() {

  private val open = MutableStateFlow(false)
  private val failure = MutableStateFlow<Throwable?>(null)
  private val query = MutableStateFlow("")

  val uiState: StateFlow<PasswordsUiState> =
    combine(open, failure) { isOpen, error -> isOpen to error }
      .flatMapLatest { (isOpen, error) ->
        when {
          error != null -> flowOf(PasswordsUiState.Error(error))
          !isOpen -> flowOf(PasswordsUiState.Locked)
          else -> {
            // Typed one level up so the Loading seed below can widen the stream to the whole
            // state type — [kotlinx.coroutines.flow.onStart] checks against what the flow it
            // decorates already emits, which is the listing's [PasswordsUiState.Ready].
            val listings: Flow<PasswordsUiState> =
              query.flatMapLatest { text ->
                vault.passwords(text).map { entries -> PasswordsUiState.Ready(text, entries) }
              }
            listings.onStart { emit(PasswordsUiState.Loading) }
          }
        }
      }
      // Nothing queries the vault while nobody watches the tab — the same battery reasoning as
      // the token list's ticker, and the same 5s grace so a rotation does not re-open anything.
      .catch { throwable -> emit(PasswordsUiState.Error(throwable)) }
      .stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        PasswordsUiState.Locked,
      )

  /** Opens the vault. The screen calls this only from a successful biometric prompt. */
  fun unlock() {
    viewModelScope.launch {
      try {
        vault.unlock()
        failure.value = null
        open.value = true
      } catch (throwable: Throwable) {
        failure.value = throwable
      }
    }
  }

  fun setQuery(text: String) {
    query.value = text
  }

  private companion object {
    const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}
