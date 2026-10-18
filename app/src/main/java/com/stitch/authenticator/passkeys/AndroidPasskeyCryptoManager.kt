package com.stitch.authenticator.passkeys

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PasskeyCryptoManager] backed by the Android Keystore: EC P-256 key pairs whose private halves
 * are generated on, and never leave, secure hardware.
 *
 * Every key demands user authentication for *each* use — `setUserAuthenticationValidityDurationSeconds(-1)`
 * below API 30, `setUserAuthenticationParameters(0, …)` on 30+ — so a [prepareSignature] result
 * only ever completes inside a BiometricPrompt CryptoObject handshake. On API 30+ the key also
 * dies when a new biometric is enrolled ([KeyGenParameterSpec.Builder.setInvalidatedByBiometricEnrollment]):
 * the biometrics that protected the passkey are the ones that keep protecting it.
 *
 * The credential id never leaves the alias: [aliasFor] namespaces every key as
 * `stitch-passkey-<credentialId>`, so the settings screen can map a stored [com.stitch.authenticator
 * .data.passkeys.PasskeyEntity] straight onto its Keystore entry.
 *
 * Not unit-testable by design: Keystore keys cannot exist on a JVM. [PasskeyCryptoManager] is
 * the seam, exercised by the in-memory fake in tests.
 */
class AndroidPasskeyCryptoManager(
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PasskeyCryptoManager {

  override suspend fun generateKeyPair(credentialId: String): GeneratedPasskeyKey =
    withContext(ioDispatcher) {
      val alias = aliasFor(credentialId)
      val generator =
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, ANDROID_KEY_STORE)
      generator.initialize(
        KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_SIGN)
          .setAlgorithmParameterSpec(ECGenParameterSpec(EC_P256))
          .setDigests(KeyProperties.DIGEST_SHA256)
          .setUserAuthenticationRequired(true)
          .apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
              // 0 seconds == authenticated for every use; a strong biometric or the device
              // credential (PIN/pattern/password) may satisfy it.
              setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL,
              )
              setInvalidatedByBiometricEnrollment(true)
            } else {
              @Suppress("DEPRECATION")
              setUserAuthenticationValidityDurationSeconds(-1)
            }
          }
          .build(),
      )
      generator.generateKeyPair()
      // A PURPOSE_SIGN Keystore key comes with a self-signed certificate holding the public half.
      val publicKey = keyStore().getCertificate(alias)?.publicKey as? ECPublicKey
        ?: error("Keystore returned no EC certificate for $alias")
      GeneratedPasskeyKey(
        keyAlias = alias,
        publicKeyCose =
          Base64.getUrlEncoder().withoutPadding().encodeToString(
            PasskeyCose.encodeP256(publicKey.w.affineX, publicKey.w.affineY),
          ),
      )
    }

  override fun prepareSignature(keyAlias: String): Signature? {
    val store = keyStore()
    if (!store.containsAlias(keyAlias)) return null
    val privateKey = store.getKey(keyAlias, null) as? PrivateKey ?: return null
    return Signature.getInstance(SHA256_WITH_ECDSA).apply { initSign(privateKey) }
  }

  override fun deleteKey(keyAlias: String) {
    val store = keyStore()
    if (store.containsAlias(keyAlias)) store.deleteEntry(keyAlias)
  }

  private fun keyStore(): KeyStore =
    KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }

  companion object {
    private const val ANDROID_KEY_STORE = "AndroidKeyStore"
    private const val SHA256_WITH_ECDSA = "SHA256withECDSA"
    private const val EC_P256 = "secp256r1"
    private const val KEY_ALIAS_PREFIX = "stitch-passkey-"

    /** Keystore alias for a credential — one key pair per WebAuthn credential id. */
    fun aliasFor(credentialId: String): String = KEY_ALIAS_PREFIX + credentialId
  }
}
