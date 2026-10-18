package com.stitch.authenticator.passkeys

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.exceptions.ClearCredentialUnsupportedException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.CreateCredentialUnsupportedException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.GetCredentialUnsupportedException
import androidx.credentials.provider.BeginCreateCredentialRequest
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.ProviderClearCredentialStateRequest

/**
 * The app's side of Credential Manager: the system binds this service when a relying party asks a
 * client app for a passkey, and every request must be answered with exactly one callback — a
 * service that stays silent makes the selector UI sit until it times out.
 *
 * Phase 7.1 registers the service against the framework contract (see the manifest and
 * `res/xml/credential_provider`) and answers each request with the matching *unsupported*
 * exception, which the system treats as this provider declining gracefully rather than a hang or
 * a crash. The real create/get flows replace these bodies in Phase 7.3.
 *
 * The superclass only exists on API 34+, which is fine at minSdk 26: no older system knows the
 * provider action, so nothing ever loads or binds this class below 34.
 */
class PasskeyProviderService : CredentialProviderService() {

  override fun onBeginCreateCredentialRequest(
    request: BeginCreateCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginCreateCredentialResponse, CreateCredentialException>,
  ) {
    callback.onError(CreateCredentialUnsupportedException("Passkey creation arrives with Phase 7.3"))
  }

  override fun onBeginGetCredentialRequest(
    request: BeginGetCredentialRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<BeginGetCredentialResponse, GetCredentialException>,
  ) {
    callback.onError(GetCredentialUnsupportedException("Passkey retrieval arrives with Phase 7.3"))
  }

  override fun onClearCredentialStateRequest(
    request: ProviderClearCredentialStateRequest,
    cancellationSignal: CancellationSignal,
    callback: OutcomeReceiver<Void?, ClearCredentialException>,
  ) {
    callback.onError(ClearCredentialUnsupportedException("No stored state to clear yet"))
  }
}
