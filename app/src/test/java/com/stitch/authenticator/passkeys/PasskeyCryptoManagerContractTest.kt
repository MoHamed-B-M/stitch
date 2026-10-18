package com.stitch.authenticator.passkeys

import java.util.Base64
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The behavioural contract every [PasskeyCryptoManager] must honour, run against the in-memory
 * fake. Phase 7.3's service tests reuse the fake, so anything asserted here holds for them too.
 */
class PasskeyCryptoManagerContractTest {

  private val crypto = InMemoryPasskeyCryptoManager()

  @Test
  fun `generates a distinct key per credential and a usable signature`() = runTest {
    val first = crypto.generateKeyPair("cred-1")
    val second = crypto.generateKeyPair("cred-2")
    assertNotEquals(first.keyAlias, second.keyAlias)

    val signature = crypto.prepareSignature(first.keyAlias)!!
    signature.update("authenticator-data ‖ client-data-hash".toByteArray())
    val raw = PasskeySignature.toRaw(signature.sign())
    assertEquals(64, raw.size)
  }

  @Test
  fun `the COSE public key is the encoding WebAuthn wants`() = runTest {
    val generated = crypto.generateKeyPair("cred-1")

    val decoded = Base64.getUrlDecoder().decode(generated.publicKeyCose)

    assertEquals(75, decoded.size)
    assertEquals(0xA5.toByte(), decoded[0])
  }

  @Test
  fun `deleting a key removes it for good`() = runTest {
    val generated = crypto.generateKeyPair("cred-1")

    crypto.deleteKey(generated.keyAlias)
    assertNull(crypto.prepareSignature(generated.keyAlias))

    crypto.deleteKey(generated.keyAlias) // deleting an absent key must be a silent no-op
  }

  @Test
  fun `an unknown alias prepares no signature`() {
    assertNull(crypto.prepareSignature("stitch-passkey-nope"))
  }
}
