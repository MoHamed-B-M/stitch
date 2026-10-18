package com.stitch.authenticator.ui.passkeys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import com.stitch.authenticator.data.passkeys.PasskeyRepository
import com.stitch.authenticator.passkeys.AndroidPasskeyCryptoManager
import com.stitch.authenticator.passkeys.PasskeyCryptoManager
import com.stitch.authenticator.passkeys.PasskeyQr
import com.stitch.authenticator.passkeys.PasskeyWebAuthn
import java.security.SecureRandom
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the passkey save sheet shows: two editable fields, a busy flag, and a result. */
data class PasskeySaveUiState(
  val domain: String = "",
  val username: String = "",
  val saving: Boolean = false,
  val saved: Boolean = false,
  val error: String? = null,
)

/**
 * The save half of the scanned-payload flow.
 *
 * A scanned payload only ever contributes the *domain guess*; the user types the account name
 * and can correct the domain, because the payloads people scan (`FIDO:/` handles, `webauthn://`
 * links, request JSONs) do not reliably name their relying party. Saving mints a Keystore key
 * pair through the same crypto seam the credential provider uses and upserts the record, so the
 * provider can immediately offer the passkey on system prompts.
 */
class PasskeySaveViewModel(
  initialPayload: String,
  private val repository: PasskeyRepository,
  private val crypto: PasskeyCryptoManager = AndroidPasskeyCryptoManager(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

  private val _state =
    MutableStateFlow(PasskeySaveUiState(domain = PasskeyQr.parseOrNull(initialPayload)?.rpId.orEmpty()))
  val state: StateFlow<PasskeySaveUiState> = _state.asStateFlow()

  fun onDomainChange(value: String) {
    _state.update { it.copy(domain = value, error = null) }
  }

  fun onUsernameChange(value: String) {
    _state.update { it.copy(username = value, error = null) }
  }

  fun save() {
    val domain = _state.value.domain.trim()
    val username = _state.value.username.trim()
    if (domain.isEmpty() || username.isEmpty() || _state.value.saving) return

    viewModelScope.launch {
      _state.update { it.copy(saving = true, error = null) }
      try {
        val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val credentialIdB64 = PasskeyWebAuthn.b64UrlEncode(credentialId)
        val generated = withContext(ioDispatcher) { crypto.generateKeyPair(credentialIdB64) }
        repository.insert(
          PasskeyEntity(
            credentialId = credentialIdB64,
            rpId = domain,
            username = username,
            // A stable handle derived from the account name: real WebAuthn user handles come
            // from the relying party, and this flow has no party to ask.
            userHandle = PasskeyWebAuthn.b64UrlEncode(username.toByteArray(Charsets.UTF_8)),
            publicKey = generated.publicKeyCose,
            keyAlias = generated.keyAlias,
          ),
        )
        _state.update { it.copy(saving = false, saved = true) }
      } catch (e: Exception) {
        _state.update {
          it.copy(saving = false, error = e.message ?: "Could not save the passkey")
        }
      }
    }
  }
}
