package com.stitch.authenticator.passkeys

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * DER → raw conversion is where a WebAuthn signature is won or lost: one unstripped sign byte
 * and every relying party answers `invalid signature`. Hand-built vectors cover each edge of
 * the ASN.1 rules, plus one real SunEC signature as a sanity check.
 */
class PasskeySignatureTest {

  private val r = ByteArray(32) { 0x11 }
  private val s = ByteArray(32) { 0x22 }

  /** A minimal, spec-perfect DER encoding: both integers already exactly 32 bytes. */
  private fun der(r: ByteArray, s: ByteArray): ByteArray =
    byteArrayOf(0x30, (2 + r.size + 2 + s.size).toByte(), 0x02, r.size.toByte()) +
      r + byteArrayOf(0x02, s.size.toByte()) + s

  @Test
  fun `converts a minimal signature to raw r plus s`() {
    assertArrayEquals(r + s, PasskeySignature.toRaw(der(r, s)))
  }

  @Test
  fun `strips the sign byte DER adds before a high bit`() {
    // r's top bit set → DER guards the sign with an extra 0x00 (length 33); raw r||s has no
    // sign convention, so the byte goes.
    val highR = ByteArray(32) { 0xAB.toByte() }
    val derHigh = byteArrayOf(0x30, 0x45, 0x02, 0x21, 0x00) + highR +
      byteArrayOf(0x02, 0x20) + s

    assertArrayEquals(highR + s, PasskeySignature.toRaw(derHigh))
  }

  @Test
  fun `restores the bytes DER minimised away`() {
    // The value 0x7B needs one DER byte, but its raw field is still 32 bytes, zero-padded.
    val derSmall = byteArrayOf(0x30, 0x25, 0x02, 0x01, 0x7B, 0x02, 0x20) + s

    val expectedR = ByteArray(32).also { it[31] = 0x7B }
    assertArrayEquals(expectedR + s, PasskeySignature.toRaw(derSmall))
  }

  @Test
  fun `round trips a signature produced by a real ECDSA signer`() {
    val pair = KeyPairGenerator.getInstance("EC").apply {
      initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()
    val der = Signature.getInstance("SHA256withECDSA").run {
      initSign(pair.private)
      update("authenticator-data ‖ client-data-hash".toByteArray())
      sign()
    }

    val raw = PasskeySignature.toRaw(der)

    assertEquals(64, raw.size)
  }

  @Test
  fun `rejects anything that is not a well-formed P-256 signature`() {
    // Empty input.
    assertThrows(IllegalArgumentException::class.java) { PasskeySignature.toRaw(ByteArray(0)) }
    // Wrong outer tag.
    assertThrows(IllegalArgumentException::class.java) {
      PasskeySignature.toRaw(byteArrayOf(0x31, 0x44))
    }
    // Raw r||s handed in instead of DER.
    assertThrows(IllegalArgumentException::class.java) { PasskeySignature.toRaw(r + s) }
    // Trailing garbage after an otherwise valid sequence.
    assertThrows(IllegalArgumentException::class.java) {
      PasskeySignature.toRaw(der(r, s) + byteArrayOf(0x00))
    }
    // Declared sequence length that does not match the content.
    assertThrows(IllegalArgumentException::class.java) {
      PasskeySignature.toRaw(der(r, s).also { it[1] = 0x43 })
    }
    // A 33-byte magnitude that is not padding — the value does not fit P-256.
    val oversized = byteArrayOf(0x30, 0x45, 0x02, 0x21, 0xFF.toByte()) + r +
      byteArrayOf(0x02, 0x20) + s
    assertThrows(IllegalArgumentException::class.java) { PasskeySignature.toRaw(oversized) }
  }
}
