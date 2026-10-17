package com.stitch.authenticator.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The RFC 4648 §10 vectors. Every secret the app ever sees goes through this decoder, so a mistake
 * here silently invalidates every code a user is shown.
 */
class Base32Test {

  @Test
  fun `encode matches the RFC 4648 vectors`() {
    val vectors = mapOf(
      "" to "",
      "f" to "MY======",
      "fo" to "MZXQ====",
      "foo" to "MZXW6===",
      "foob" to "MZXW6YQ=",
      "fooba" to "MZXW6YTB",
      "foobar" to "MZXW6YTBOI======",
    )

    vectors.forEach { (plain, encoded) ->
      assertEquals("encoding \"$plain\"", encoded, Base32.encode(plain.toByteArray()))
    }
  }

  @Test
  fun `decode matches the RFC 4648 vectors`() {
    val vectors = mapOf(
      "" to "",
      "MY======" to "f",
      "MZXQ====" to "fo",
      "MZXW6===" to "foo",
      "MZXW6YQ=" to "foob",
      "MZXW6YTB" to "fooba",
      "MZXW6YTBOI======" to "foobar",
    )

    vectors.forEach { (encoded, plain) ->
      assertEquals("decoding \"$encoded\"", plain, Base32.decode(encoded).decodeToString())
    }
  }

  @Test
  fun `round trips arbitrary bytes`() {
    val bytes = ByteArray(64) { index -> (index * 7 + 3).toByte() }

    assertArrayEquals(bytes, Base32.decode(Base32.encode(bytes)))
  }

  @Test
  fun `decodes the RFC 6238 test secret`() {
    assertEquals(
      "12345678901234567890",
      Base32.decode("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ").decodeToString(),
    )
  }

  @Test
  fun `tolerates the noise pasted secrets carry`() {
    assertEquals("foobar", Base32.decode(" mzxw 6ytb-oi==== ").decodeToString())
    assertEquals(
      "12345678901234567890",
      Base32.decode("gezdgnbvgy3tqojq\nGEZDGNBVGY3TQOJQ").decodeToString(),
    )
  }

  @Test
  fun `rejects characters outside the alphabet`() {
    listOf("0", "1", "8", "9", "!", "MZXW6YTBOI!!!!!!", "sn0wflake", "café").forEach { invalid ->
      assertThrows("decoding \"$invalid\"", IllegalArgumentException::class.java) {
        Base32.decode(invalid)
      }
    }
  }

  @Test
  fun `validity requires a meaningful secret`() {
    assertFalse(Base32.isValid(""))
    assertFalse(Base32.isValid("   "))
    assertFalse(Base32.isValid("===="))
    assertFalse(Base32.isValid("---"))
    assertFalse(Base32.isValid("nope!"))

    assertTrue(Base32.isValid("gezdgnbvgy3tqojq"))
    assertTrue(Base32.isValid("MZXW6YTBOI======"))
  }

  @Test
  fun `rejects a key that decodes to no bytes at all`() {
    // One Base32 character is five bits, which rounds down to nothing. `Base32.decode` accepts it
    // (it is well-formed), so validity is the one place that has to notice: a token built from an
    // empty key would look fine on the list and throw the moment its first code was due.
    assertFalse(Base32.isValid("A"))
    assertFalse(Base32.isValid("2"))
    assertFalse(Base32.isValid("====A===="))

    // Two characters already carry ten bits, which is a byte.
    assertTrue(Base32.isValid("AB"))
  }
}
