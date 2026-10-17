package com.stitch.authenticator.crypto

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values are the published RFC vectors, cross-checked against an independent HMAC
 * implementation before being written down — a regression here means wrong codes for real users.
 */
class TotpEngineTest {

  private val engine = TotpEngine(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC))

  private fun engineAt(epochSecond: Long) =
    TotpEngine(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC))

  private val sha1Secret = "12345678901234567890".toByteArray()
  private val sha256Secret = "12345678901234567890123456789012".toByteArray()
  private val sha512Secret =
    "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

  private data class Vector(
    val time: Long,
    val sha1: String,
    val sha256: String,
    val sha512: String,
  )

  @Test
  fun `matches every RFC 6238 appendix B vector`() {
    val vectors = listOf(
      Vector(59L, "94287082", "46119246", "90693936"),
      Vector(1111111109L, "07081804", "68084774", "25091201"),
      Vector(1111111111L, "14050471", "67062674", "99943326"),
      Vector(1234567890L, "89005924", "91819424", "93441116"),
      Vector(2000000000L, "69279037", "90698825", "38618901"),
      Vector(20000000000L, "65353130", "77737706", "47863826"),
    )

    vectors.forEach { vector ->
      val at = Instant.ofEpochSecond(vector.time)
      assertEquals(
        "SHA1 at ${vector.time}",
        vector.sha1,
        engine.totp(sha1Secret, digits = 8, algorithm = TotpEngine.HMAC_SHA1, at = at),
      )
      assertEquals(
        "SHA256 at ${vector.time}",
        vector.sha256,
        engine.totp(sha256Secret, digits = 8, algorithm = TotpEngine.HMAC_SHA256, at = at),
      )
      assertEquals(
        "SHA512 at ${vector.time}",
        vector.sha512,
        engine.totp(sha512Secret, digits = 8, algorithm = TotpEngine.HMAC_SHA512, at = at),
      )
    }
  }

  @Test
  fun `matches every RFC 4226 appendix D counter`() {
    val expected = listOf(
      "755224", "287082", "359152", "969429", "338314",
      "254676", "287922", "162583", "399871", "520489",
    )

    expected.forEachIndexed { counter, code ->
      assertEquals("counter $counter", code, engine.hotp(sha1Secret, counter.toLong()))
    }
  }

  @Test
  fun `a code is stable for its whole window and rotates at the boundary`() {
    val windowStart = engine.totp(sha1Secret, at = Instant.ofEpochSecond(30))
    val windowEnd = engine.totp(sha1Secret, at = Instant.ofEpochSecond(59))
    val nextWindow = engine.totp(sha1Secret, at = Instant.ofEpochSecond(60))

    assertEquals(windowStart, windowEnd)
    assertNotEquals(windowEnd, nextWindow)
  }

  @Test
  fun `the counter is the number of whole periods since the epoch`() {
    assertEquals(0L, engine.counterFor(Instant.ofEpochSecond(0), period = 30))
    assertEquals(0L, engine.counterFor(Instant.ofEpochSecond(29), period = 30))
    assertEquals(1L, engine.counterFor(Instant.ofEpochSecond(30), period = 30))
    assertEquals(1L, engine.counterFor(Instant.ofEpochSecond(59), period = 30))
    assertEquals(2L, engine.counterFor(Instant.ofEpochSecond(60), period = 30))
    assertEquals(33333333L, engine.counterFor(Instant.ofEpochSecond(2000000000), period = 60))
  }

  @Test
  fun `fewer digits truncate the same number`() {
    val code6 = engine.totp(sha1Secret, digits = 6, at = Instant.ofEpochSecond(59))
    val code8 = engine.totp(sha1Secret, digits = 8, at = Instant.ofEpochSecond(59))

    assertEquals("287082", code6)
    assertEquals("94287082", code8)
    assertTrue(code8, code8.endsWith(code6))
  }

  @Test
  fun `seconds remaining counts down across a window`() {
    assertEquals(30, engine.secondsRemaining(at = Instant.ofEpochSecond(30)))
    assertEquals(30, engine.secondsRemaining(at = Instant.ofEpochSecond(60)))
    assertEquals(29, engine.secondsRemaining(at = Instant.ofEpochSecond(31)))
    assertEquals(15, engine.secondsRemaining(at = Instant.ofEpochSecond(45)))
    assertEquals(1, engine.secondsRemaining(at = Instant.ofEpochSecond(59)))
  }

  @Test
  fun `progress runs from one down to zero`() {
    assertEquals(1f, engine.progress(at = Instant.ofEpochSecond(60)), 0.0001f)
    assertEquals(0.5f, engine.progress(at = Instant.ofEpochSecond(45)), 0.0001f)
    assertEquals(1f / 30f, engine.progress(at = Instant.ofEpochSecond(59)), 0.0001f)
  }

  @Test
  fun `honours the injected clock`() {
    assertEquals("94287082", engineAt(59).totp(sha1Secret, digits = 8))
    assertEquals(1, engineAt(59).secondsRemaining())
  }

  @Test
  fun `rejects parameters it cannot honour`() {
    assertThrows(IllegalArgumentException::class.java) { engine.hotp(sha1Secret, 0L, digits = 5) }
    assertThrows(IllegalArgumentException::class.java) { engine.hotp(sha1Secret, 0L, digits = 9) }
    assertThrows(IllegalArgumentException::class.java) { engine.hotp(ByteArray(0), 0L) }
    assertThrows(IllegalArgumentException::class.java) { engine.counterFor(Instant.EPOCH, period = 0) }
    assertThrows(IllegalArgumentException::class.java) { engine.secondsRemaining(period = -1) }
    assertThrows(IllegalArgumentException::class.java) { engine.progress(period = 0) }
  }
}
