package com.stitch.authenticator.passkeys

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64

/**
 * A [PasskeyCryptoManager] whose Keystore is the JVM itself: real EC P-256 keys and real ECDSA
 * signatures, none of the hardware, prompting or API-level plumbing. Everything above the
 * Android Keystore — the provider service, response assembly, the UI — tests against this.
 */
internal class InMemoryPasskeyCryptoManager : PasskeyCryptoManager {

  private val keys = LinkedHashMap<String, KeyPair>()

  override suspend fun generateKeyPair(credentialId: String): GeneratedPasskeyKey {
    val alias = "fake-passkey-$credentialId"
    val pair = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    keys[alias] = pair
    val publicKey = pair.public as ECPublicKey
    return GeneratedPasskeyKey(
      keyAlias = alias,
      publicKeyCose =
        Base64.getUrlEncoder().withoutPadding().encodeToString(
          PasskeyCose.encodeP256(publicKey.w.affineX, publicKey.w.affineY),
        ),
    )
  }

  override fun prepareSignature(keyAlias: String): Signature? {
    val pair = keys[keyAlias] ?: return null
    return Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private) }
  }

  override fun deleteKey(keyAlias: String) {
    keys.remove(keyAlias)
  }
}
