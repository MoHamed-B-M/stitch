package com.stitch.authenticator.passkeys

import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialUnknownException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialUnknownException
import androidx.credentials.provider.PendingIntentHandler
import androidx.credentials.provider.ProviderCreateCredentialRequest
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The half of the passkey flow the *user* sees: the system fires the PendingIntent carried by
 * one of the provider's entries, this activity confirms intent behind a biometric prompt, does
 * the Keystore work, and hands the WebAuthn JSON back through [PendingIntentHandler].
 *
 * Create needs a confirmation prompt (nothing signs yet — the key is minted *after* the user is
 * verified, because every later use demands authentication again). Get hands the prepared
 * [java.security.Signature] to the prompt as its CryptoObject, so the system releases the key
 * only for a verified user — the signature literally cannot be produced any other way.
 *
 * Only API 34+ ever fires these PendingIntents; below that the activity finishes immediately.
 */
class PasskeyCredentialActivity : FragmentActivity() {

  private val crypto by lazy { AndroidPasskeyCryptoManager() }
  private val repository by lazy { AppGraph.passkeyRepository(this) }
  private val working = mutableStateOf(false)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      finish()
      return
    }

    val createRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
    val isCreate = createRequest != null
    val state: PromptState? = if (createRequest != null) {
      createRequest.creationOrNull()?.let { PromptState.Create(it.rpName.ifEmpty { it.rpId }, it.userName.ifEmpty { it.userDisplayName }) }
    } else {
      if (PendingIntentHandler.retrieveProviderGetCredentialRequest(intent) == null) null
      else {
        val rpId = intent.getStringExtra(EXTRA_RP_ID)
        val userName = intent.getStringExtra(EXTRA_USER_NAME)
        if (rpId != null && userName != null) PromptState.Get(rpId, userName) else null
      }
    }
    if (state == null) {
      failAndFinish(isCreate, "the passkey request could not be read")
      return
    }

    setContent {
      StitchAuthenticatorTheme(themeMode = ThemeMode.SYSTEM, dynamicColor = true) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          PasskeyPromptUi(
            state = state,
            working = working.value,
            onCancel = { if (!working.value) cancelAndFinish(isCreate) },
            onConfirm = { if (!working.value) beginFlow() },
          )
        }
      }
    }
  }

  // --------------------------------------------------------------------- flow orchestration

  private fun beginFlow() {
    val createRequest = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
    if (createRequest != null) {
      runCreate(createRequest)
    } else {
      runGet()
    }
  }

  private fun runCreate(request: ProviderCreateCredentialRequest) {
    val creation = request.creationOrNull()
    if (creation == null) {
      failAndFinish(isCreate = true, message = "the passkey request is malformed")
      return
    }
    promptBiometric(
      title = "Save passkey for ${creation.rpName.ifEmpty { creation.rpId }}",
      subtitle = creation.userName,
      cryptoObject = null,
    ) { result ->
      if (result == null) {
        cancelAndFinish(isCreate = true)
        return@promptBiometric
      }
      working.value = true
      lifecycleScope.launch {
        try {
          val response = withContext(Dispatchers.IO) { createPasskey(creation) }
          PendingIntentHandler.setCreateCredentialResponse(intent, response)
          setResult(Activity.RESULT_OK, intent)
        } catch (e: Exception) {
          PendingIntentHandler.setCreateCredentialException(
            intent,
            CreateCredentialUnknownException("could not save the passkey: ${e.message}"),
          )
          setResult(Activity.RESULT_OK, intent)
        }
        finish()
      }
    }
  }

  private suspend fun createPasskey(
    creation: PasskeyWebAuthn.PasskeyCreationRequest,
  ): CreatePublicKeyCredentialResponse {
    val credentialId = ByteArray(32).also { SecureRandom().nextBytes(it) }
    val generated = crypto.generateKeyPair(PasskeyWebAuthn.b64UrlEncode(credentialId))
    val clientData = PasskeyWebAuthn.clientDataJson("webauthn.create", creation.challenge, origin())
    val authData = PasskeyWebAuthn.attestationAuthenticatorData(
      creation.rpId,
      credentialId,
      PasskeyWebAuthn.b64UrlDecode(generated.publicKeyCose),
    )
    repository.insert(
      PasskeyEntity(
        credentialId = PasskeyWebAuthn.b64UrlEncode(credentialId),
        rpId = creation.rpId,
        username = creation.userName.ifEmpty { creation.userDisplayName },
        userHandle = PasskeyWebAuthn.b64UrlEncode(creation.userId),
        publicKey = generated.publicKeyCose,
        keyAlias = generated.keyAlias,
      ),
    )
    return CreatePublicKeyCredentialResponse(
      PasskeyWebAuthn.createResponseJson(credentialId, clientData, PasskeyWebAuthn.attestationObject(authData)),
    )
  }

  private fun runGet() {
    val credentialId = intent.getStringExtra(EXTRA_CREDENTIAL_ID)
    val rpId = intent.getStringExtra(EXTRA_RP_ID)
    val challenge = intent.getByteArrayExtra(EXTRA_CHALLENGE)
    if (credentialId == null || rpId == null || challenge == null) {
      failAndFinish(isCreate = false, message = "the passkey request is incomplete")
      return
    }
    working.value = true
    lifecycleScope.launch {
      val entity = withContext(Dispatchers.IO) {
        repository.getByRp(rpId).firstOrNull { it.credentialId == credentialId }
      }
      val signature = entity?.let {
        withContext(Dispatchers.IO) { crypto.prepareSignature(it.keyAlias) }
      }
      if (entity == null || signature == null) {
        working.value = false
        failAndFinish(isCreate = false, message = "this passkey is no longer available on the device")
        return@launch
      }
      working.value = false
      promptBiometric(
        title = "Use passkey on $rpId",
        subtitle = entity.username,
        cryptoObject = BiometricPrompt.CryptoObject(signature),
      ) { result ->
        if (result == null) {
          cancelAndFinish(isCreate = false)
          return@promptBiometric
        }
        // The prompt hands back the very object it authenticated; the ?: covers the
        // device-credential path, where the system verifies without a crypto object and the
        // per-use key then refuses to sign — which the catch below turns into a clean error.
        val active = result.cryptoObject?.signature ?: signature
        working.value = true
        lifecycleScope.launch {
          try {
            val response = withContext(Dispatchers.IO) { signAssertion(entity, challenge, active) }
            PendingIntentHandler.setGetCredentialResponse(intent, response)
            setResult(Activity.RESULT_OK, intent)
          } catch (e: Exception) {
            PendingIntentHandler.setGetCredentialException(
              intent,
              GetCredentialUnknownException("could not sign with the passkey: ${e.message}"),
            )
            setResult(Activity.RESULT_OK, intent)
          }
          finish()
        }
      }
    }
  }

  private suspend fun signAssertion(
    entity: PasskeyEntity,
    challenge: ByteArray,
    signature: java.security.Signature,
  ): GetCredentialResponse {
    val clientData = PasskeyWebAuthn.clientDataJson("webauthn.get", challenge, origin())
    // A privileged caller (browser on behalf of a web app) supplies the clientDataHash to sign
    // over instead of the assembled clientDataJSON; direct app callers always take the else.
    val callerHash = intent.getByteArrayExtra(EXTRA_CLIENT_DATA_HASH)
    val authData = PasskeyWebAuthn.assertionAuthenticatorData(entity.rpId)
    signature.update(authData + (callerHash ?: PasskeyWebAuthn.clientDataHash(clientData)))
    val raw = PasskeySignature.toRaw(signature.sign())
    val userHandle = entity.userHandle.ifEmpty { null }?.let(PasskeyWebAuthn::b64UrlDecode)
    return GetCredentialResponse(
      PublicKeyCredential(
        PasskeyWebAuthn.assertionResponseJson(
          PasskeyWebAuthn.b64UrlDecode(entity.credentialId),
          clientData,
          authData,
          raw,
          userHandle,
        ),
      ),
    )
  }

  // --------------------------------------------------------------------- prompt + results

  private fun promptBiometric(
    title: String,
    subtitle: String,
    cryptoObject: BiometricPrompt.CryptoObject?,
    onResult: (BiometricPrompt.AuthenticationResult?) -> Unit,
  ) {
    val prompt = BiometricPrompt(
      this,
      ContextCompat.getMainExecutor(this),
      object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
          onResult(result)
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
          onResult(null) // backing out or an error both mean "not authenticated" — never a crash
        }
      },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
      .setTitle(title)
      .setSubtitle(subtitle)
      .setConfirmationRequired(true)
      .setAllowedAuthenticators(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
      )
      .build()
    if (cryptoObject != null) {
      prompt.authenticate(promptInfo, cryptoObject)
    } else {
      prompt.authenticate(promptInfo)
    }
  }

  private fun cancelAndFinish(isCreate: Boolean) {
    if (isCreate) {
      PendingIntentHandler.setCreateCredentialException(
        intent,
        CreateCredentialCancellationException("cancelled"),
      )
    } else {
      PendingIntentHandler.setGetCredentialException(
        intent,
        GetCredentialCancellationException("cancelled"),
      )
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  private fun failAndFinish(isCreate: Boolean, message: String) {
    if (isCreate) {
      PendingIntentHandler.setCreateCredentialException(intent, CreateCredentialUnknownException(message))
    } else {
      PendingIntentHandler.setGetCredentialException(intent, GetCredentialUnknownException(message))
    }
    setResult(Activity.RESULT_OK, intent)
    finish()
  }

  private fun origin(): String =
    intent.getStringExtra(EXTRA_ORIGIN) ?: "android:apk-key-hash:unknown"

  private fun ProviderCreateCredentialRequest.creationOrNull(): PasskeyWebAuthn.PasskeyCreationRequest? =
    try {
      PasskeyWebAuthn.parseCreationRequest(callingRequest.data)
    } catch (_: IllegalArgumentException) {
      null
    }

  companion object {
    const val EXTRA_RP_ID = "com.stitch.authenticator.passkeys.RP_ID"
    const val EXTRA_RP_NAME = "com.stitch.authenticator.passkeys.RP_NAME"
    const val EXTRA_USER_NAME = "com.stitch.authenticator.passkeys.USER_NAME"
    const val EXTRA_CREDENTIAL_ID = "com.stitch.authenticator.passkeys.CREDENTIAL_ID"
    const val EXTRA_CHALLENGE = "com.stitch.authenticator.passkeys.CHALLENGE"
    const val EXTRA_ORIGIN = "com.stitch.authenticator.passkeys.ORIGIN"
    const val EXTRA_CLIENT_DATA_HASH = "com.stitch.authenticator.passkeys.CLIENT_DATA_HASH"
  }
}

private sealed interface PromptState {
  data class Create(val rpName: String, val userName: String) : PromptState
  data class Get(val rpId: String, val userName: String) : PromptState
}

/** Confirmation UI: one headline, one body line, a spring-scaling primary button, a cancel. */
@Composable
private fun PasskeyPromptUi(
  state: PromptState,
  working: Boolean,
  onCancel: () -> Unit,
  onConfirm: () -> Unit,
) {
  val headline = when (state) {
    is PromptState.Create -> "Save passkey for ${state.rpName}?"
    is PromptState.Get -> "Use passkey on ${state.rpId}?"
  }
  val body = when (state) {
    is PromptState.Create -> "${state.userName} will be able to sign in to ${state.rpName} with this device."
    is PromptState.Get -> "Signing in as ${state.userName}."
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(horizontal = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(
      text = headline,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(12.dp))
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(32.dp))
    SpringConfirmButton(
      text = if (working) "Working…" else "Continue",
      enabled = !working,
      onClick = onConfirm,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onCancel, enabled = !working, modifier = Modifier.fillMaxWidth()) {
      Text("Cancel")
    }
  }
}

/** The same press-scale spring the rest of the app uses, kept local to this flow. */
@Composable
private fun SpringConfirmButton(
  text: String,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val pressed by interactionSource.collectIsPressedAsState()
  val scale by animateFloatAsState(
    targetValue = if (pressed) 0.94f else 1f,
    animationSpec = spring(
      dampingRatio = Spring.DampingRatioMediumBouncy,
      stiffness = Spring.StiffnessMedium,
    ),
    label = "pressScale",
  )
  Button(
    onClick = onClick,
    enabled = enabled,
    interactionSource = interactionSource,
    modifier = modifier.scale(scale),
  ) {
    Text(text)
  }
}
