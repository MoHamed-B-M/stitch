package com.stitch.authenticator.passkeys

import java.math.BigInteger
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The COSE bytes are the one place where a silently wrong offset still produces a *decodable*
 * credential that every relying party rejects — so every test here compares exact bytes.
 */
class PasskeyCoseTest {

  @Test
  fun `encodes the fixed five-entry COSE map byte for byte`() {
    val xBytes = ByteArray(32) { (it + 1).toByte() } // 01 02 ... 20
    val yBytes = ByteArray(32) { (0x21 + it).toByte() } // 21 22 ... 40

    val encoded = PasskeyCose.encodeP256(BigInteger(1, xBytes), BigInteger(1, yBytes))

    val expected = byteArrayOf(0xA5.toByte(), 0x01, 0x02, 0x03, 0x26, 0x20, 0x01, 0x58, 0x20) +
      xBytes + byteArrayOf(0x58, 0x20) + yBytes
    assertArrayEquals(expected, encoded)
  }

  @Test
  fun `pads a coordinate that DER would have trimmed`() {
    // A small coordinate arrives from BigInteger in its shortest form; CBOR's byte string must
    // still be the curve's full 32 bytes, zero-padded on the left.
    val encoded = PasskeyCose.encodeP256(BigInteger.valueOf(0x7B), BigInteger.valueOf(2))

    val x = encoded.copyOfRange(9, 41)
    assertEquals(32, x.size)
    assertTrue(x.copyOfRange(0, 31).all { it == 0.toByte() })
    assertEquals(0x7B.toByte(), x[31])
  }

  @Test
  fun `drops the sign byte a high coordinate adds`() {
    // A coordinate with its top bit set makes BigInteger prepend a 0x00 sign byte; the curve's
    // fixed width wins — 32 bytes, sign byte gone.
    val high = BigInteger("FF" + "11".repeat(31), 16)
    assertEquals(33, high.toByteArray().size)

    val encoded = PasskeyCose.encodeP256(high, high)

    val x = encoded.copyOfRange(9, 41)
    assertEquals(32, x.size)
    assertEquals(0xFF.toByte(), x[0])
    assertEquals(0x11.toByte(), x[1])
  }

  @Test
  fun `always produces the 75-byte map`() {
    val encoded = PasskeyCose.encodeP256(BigInteger.ZERO, BigInteger.ONE)
    assertEquals(75, encoded.size)
    assertEquals(0xA5.toByte(), encoded[0])
  }
}
