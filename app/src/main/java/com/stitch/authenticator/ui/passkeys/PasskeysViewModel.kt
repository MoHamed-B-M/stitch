package com.stitch.authenticator.ui.passkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import com.stitch.authenticator.data.passkeys.PasskeyRepository
import com.stitch.authenticator.passkeys.AndroidPasskeyCryptoManager
import com.stitch.authenticator.passkeys.PasskeyCryptoManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The passkey list shown in Settings, and the delete that keeps the Keystore honest.
 *
 * The list is the repository's own flow — the same snapshot the credential provider service
 * reads — so a passkey saved by a website appears here without the screen re-reading storage.
 */
class PasskeysViewModel(
  private val repository: PasskeyRepository,
  private val crypto: PasskeyCryptoManager = AndroidPasskeyCryptoManager(),
) : ViewModel() {

  val passkeys: StateFlow<List<PasskeyEntity>> =
    repository.passkeys.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

  init {
    // The cache starts empty until something reads storage; the settings screen is that reader.
    viewModelScope.launch { repository.refresh() }
  }

  fun delete(passkey: PasskeyEntity) {
    viewModelScope.launch {
      // The private key lives under this passkey's alias. Dropping only the record would leave
      // the Keystore key behind, usable by nothing but visible to anything that enumerates.
      runCatching { crypto.deleteKey(passkey.keyAlias) }
      repository.deleteByCredentialId(passkey.credentialId)
    }
  }
}
