package com.stitch.authenticator.passkeys

import java.security.Signature

/**
 * Where passkey private keys are born and used — the seam that lets every layer *above* the
 * Android Keystore run in JVM unit tests.
 *
 * The private half of every key pair never leaves the Keystore: [generateKeyPair] hands back only
 * the key alias and the *public* COSE key, and signing goes through [prepareSignature], whose
 * returned [Signature] is bound to the private key. Because the keys are generated with
 * user-authentication-required, [Signature.sign] succeeds only while the system considers the
 * user verified — in the provider flow the signature object is handed to BiometricPrompt as its
 * CryptoObject and completed only after authentication succeeded.
 */
interface PasskeyCryptoManager {

  /**
   * Generates a hardware-backed EC P-256 key pair for [credentialId] and returns its Keystore
   * alias together with the public key in COSE format, Base64 URL-safe without padding — the
   * exact encoding the WebAuthn JSON response wants.
   */
  suspend fun generateKeyPair(credentialId: String): GeneratedPasskeyKey

  /**
   * A "SHA256withECDSA" [Signature] initialised with the private key behind [keyAlias], or null
   * when no such key exists. Signing is a two-step dance on purpose: init now, [Signature.sign]
   * later, only after the user has authenticated for that specific object.
   */
  fun prepareSignature(keyAlias: String): Signature?

  /** Removes the key pair; deleting a passkey that is already gone is a no-op. */
  fun deleteKey(keyAlias: String)
}

/** The public fallout of [PasskeyCryptoManager.generateKeyPair]. */
data class GeneratedPasskeyKey(
  /** Keystore alias; stored as [com.stitch.authenticator.data.passkeys.PasskeyEntity.keyAlias]. */
  val keyAlias: String,
  /** COSE-encoded EC P-256 public key, Base64 URL-safe without padding. */
  val publicKeyCose: String,
)
