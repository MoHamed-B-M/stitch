package com.stitch.authenticator.passkeys

import android.app.PendingIntent
import android.content.Intent
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialInterruptedException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialInterruptedException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CallingAppInfo
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import androidx.credentials.provider.PublicKeyCredentialEntry
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The app's side of Credential Manager: the system binds this service when a relying party asks a
 * client app for a passkey, and every request must be answered with exactly one callback — a
 * service that stays silent makes the selector UI sit until it times out.
 *
 * Phase 7.3 replaces the graceful declines: create offers a [CreateEntry] pointing at
 * [PasskeyCredentialActivity] (which does the biometric-gated key generation and answers with the
 * attestation JSON), get lists one [PublicKeyCredentialEntry] per stored passkey whose rpId and
 * allowCredentials match, and clear-state acknowledges without touching the user's vault — a
 * website must never be able to silently delete entries the user manages in Settings.
 *
 * The superclass only exists on API 34+, which is fine at minSdk 26: no older system knows the
 * provider action, so nothing ever loads or binds this class below 34. The callbacks arrive on a
 * binder thread; repository and parsing work hops to [Dispatchers.IO] through [serviceScope].
 */
class PasskeyProviderService : CredentialProviderService() {

  private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  override fun onDestroy() {
    serviceScope.cancel()
    super.onDestroy()
  }

  override fun onBeginCreateCredentialRequest(
    request: BeginCreateCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
  ) {
    val responded = AtomicBoolean(false)
    cancellationSignal.setOnCancelListener {
      if (responded.compareAndSet(false, true)) {
        callback.onError(CreateCredentialInterruptedException("cancelled"))
      }
    }

    if (request !is BeginCreatePublicKeyCredentialRequest) {
      declineCreate(responded, callback, "only passkeys are offered here")
      return
    }
    val creation = try {
      PasskeyWebAuthn.parseCreationRequest(request.requestJson)
    } catch (e: IllegalArgumentException) {
      declineCreate(responded, callback, "malformed passkey request: ${e.message}")
      return
    }

    serviceScope.launch {
      val response = try {
        BeginCreateCredentialResponse(listOf(createEntry(creation, request.clientDataHash)))
      } catch (e: Exception) {
        if (responded.compareAndSet(false, true)) {
          callback.onError(CreateCredentialUnknownException("could not offer a passkey: ${e.message}"))
        }
        return@launch
      }
      if (responded.compareAndSet(false, true)) callback.onResult(response)
    }
  }

  override fun onBeginGetCredentialRequest(
    request: BeginGetCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
  ) {
    val responded = AtomicBoolean(false)
    cancellationSignal.setOnCancelListener {
      if (responded.compareAndSet(false, true)) {
        callback.onError(GetCredentialInterruptedException("cancelled"))
      }
    }

    val options =
      request.beginGetCredentialOptions.filterIsInstance<BeginGetPublicKeyCredentialOption>()
    if (options.isEmpty()) {
      // No passkey option among the request: an empty response keeps this provider off the
      // selector entirely instead of offering entries that could never be fulfilled.
      if (responded.compareAndSet(false, true)) callback.onResult(BeginGetCredentialResponse())
      return
    }

    serviceScope.launch {
      val entries = buildList {
        for (option in options) {
          val assertion = try {
            PasskeyWebAuthn.parseAssertionRequest(option.requestJson)
          } catch (_: IllegalArgumentException) {
            continue
          }
          val stored = AppGraph.passkeyRepository(this@PasskeyProviderService)
            .getByRp(assertion.rpId)
          for (entity in stored) {
            if (assertion.allowCredentialIds.isNotEmpty()) {
              val id = try {
                PasskeyWebAuthn.b64UrlDecode(entity.credentialId)
              } catch (_: IllegalArgumentException) {
                continue
              }
              if (assertion.allowCredentialIds.none { it.contentEquals(id) }) continue
            }
            add(passkeyEntry(entity, option, assertion.challenge, option.clientDataHash))
          }
        }
      }
      if (responded.compareAndSet(false, true)) {
        callback.onResult(BeginGetCredentialResponse(entries))
      }
    }
  }

  override fun onClearCredentialStateRequest(
    request: ProviderClearCredentialStateRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<Void?, ClearCredentialException>,
  ) {
    // The vault is user-managed: a relying party asking the provider to forget its state must not
    // silently delete passkeys the user can see and delete in Settings. Acknowledge, keep them.
    callback.onResult(null)
  }

  // --------------------------------------------------------------------- entries

  private fun createEntry(
    creation: PasskeyWebAuthn.PasskeyCreationRequest,
    clientDataHash: ByteArray?,
  ): CreateEntry {
    val intent = Intent(this, PasskeyCredentialActivity::class.java)
      .putExtra(PasskeyCredentialActivity.EXTRA_RP_NAME, creation.rpName)
      .putExtra(PasskeyCredentialActivity.EXTRA_USER_NAME, creation.userName)
      .putExtra(PasskeyCredentialActivity.EXTRA_ORIGIN, callerOrigin(null))
      .putExtra(PasskeyCredentialActivity.EXTRA_CLIENT_DATA_HASH, clientDataHash)
    val pendingIntent = PendingIntent.getActivity(
      this,
      creation.userName.hashCode(),
      intent,
      PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return CreateEntry(
      accountName = creation.userName.ifEmpty { creation.rpName },
      pendingIntent = pendingIntent,
      description = "Passkey saved in Stitch Authenticator",
      isAutoSelectAllowed = true,
    )
  }

  private fun passkeyEntry(
    entity: PasskeyEntity,
    option: BeginGetPublicKeyCredentialOption,
    challenge: ByteArray,
    clientDataHash: ByteArray?,
  ): PublicKeyCredentialEntry {
    val intent = Intent(this, PasskeyCredentialActivity::class.java)
      .putExtra(PasskeyCredentialActivity.EXTRA_CREDENTIAL_ID, entity.credentialId)
      .putExtra(PasskeyCredentialActivity.EXTRA_RP_ID, entity.rpId)
      .putExtra(PasskeyCredentialActivity.EXTRA_USER_NAME, entity.username)
      .putExtra(PasskeyCredentialActivity.EXTRA_CHALLENGE, challenge)
      .putExtra(PasskeyCredentialActivity.EXTRA_ORIGIN, callerOrigin(null))
      .putExtra(PasskeyCredentialActivity.EXTRA_CLIENT_DATA_HASH, clientDataHash)
    val pendingIntent = PendingIntent.getActivity(
      this,
      entity.credentialId.hashCode(),
      intent,
      PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    return PublicKeyCredentialEntry(
      context = this,
      username = entity.username,
      pendingIntent = pendingIntent,
      beginGetPublicKeyCredentialOption = option,
      displayName = entity.username,
      isAutoSelectAllowed = true,
    )
  }

  /**
   * WebAuthn's origin for an APK caller (W3C §13.2): `android:apk-key-hash:<sha256>` over the
   * signing certificate — the same identity Digital Asset Links would verify server-side.
   */
  private fun callerOrigin(callingAppInfo: CallingAppInfo?): String {
    val signingInfo = callingAppInfo?.signingInfo ?: return "android:apk-key-hash:unknown"
    val signer = if (signingInfo.hasMultipleSigners()) {
      signingInfo.apkContentsSigners?.firstOrNull()
    } else {
      @Suppress("DEPRECATION")
      signingInfo.signingCertificateHistory?.firstOrNull()
    } ?: return "android:apk-key-hash:unknown"
    val digest = MessageDigest.getInstance("SHA-256").digest(signer.toByteArray())
    return "android:apk-key-hash:" + digest.joinToString("") { "%02x".format(it) }
  }

  private fun declineCreate(
    responded: AtomicBoolean,
    callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
    reason: String,
  ) {
    if (responded.compareAndSet(false, true)) {
      callback.onError(CreateCredentialUnsupportedException(reason))
    }
  }
}
