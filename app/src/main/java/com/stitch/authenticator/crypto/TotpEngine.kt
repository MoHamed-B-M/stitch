package com.stitch.authenticator.crypto

import java.time.Clock
import java.time.Instant
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * RFC 4226 (HOTP) and RFC 6238 (TOTP) built directly on `javax.crypto`, so the published RFC test
 * vectors run as ordinary JVM unit tests instead of needing a device or an emulator.
 *
 * The clock is injected and every time-dependent function takes an optional [Instant]: "now" is a
 * dependency, which is what makes code rotation and the countdown ring testable.
 */
class TotpEngine(private val clock: Clock = Clock.systemUTC()) {

  /** The current instant according to the injected clock. */
  fun now(): Instant = clock.instant()

  /** RFC 6238 §4.2 — the counter is the number of whole [period]s elapsed since the Unix epoch. */
  fun counterFor(instant: Instant, period: Int = DEFAULT_PERIOD): Long {
    require(period > 0) { "Period must be positive, was $period" }
    return Math.floorDiv(instant.epochSecond, period.toLong())
  }

  /** RFC 4226 §5.3 — HMAC the counter, truncate dynamically, then keep [digits] digits. */
  fun hotp(
    secret: ByteArray,
    counter: Long,
    digits: Int = DEFAULT_DIGITS,
    algorithm: String = HMAC_SHA1,
  ): String {
    require(secret.isNotEmpty()) { "Secret must not be empty" }
    require(digits in MIN_DIGITS..MAX_DIGITS) { "Unsupported digit count: $digits" }
    val digest = hmac(secret, counter, algorithm)

    // Dynamic truncation (RFC 4226 §5.4): the low nibble of the last byte selects the offset.
    val offset = digest[digest.size - 1].toInt() and 0x0F
    val truncated = ((digest[offset].toInt() and 0x7F) shl 24) or
      ((digest[offset + 1].toInt() and 0xFF) shl 16) or
      ((digest[offset + 2].toInt() and 0xFF) shl 8) or
      (digest[offset + 3].toInt() and 0xFF)

    return (truncated % POWERS_OF_TEN[digits - MIN_DIGITS]).toString().padStart(digits, '0')
  }

  /** RFC 6238 TOTP for [at], defaulting to the injected clock. */
  fun totp(
    secret: ByteArray,
    period: Int = DEFAULT_PERIOD,
    digits: Int = DEFAULT_DIGITS,
    algorithm: String = HMAC_SHA1,
    at: Instant = now(),
  ): String = hotp(secret, counterFor(at, period), digits, algorithm)

  /**
   * Whole seconds until the current code rolls over: [period] at the instant a window opens and `1`
   * on its final second, so a countdown never has to display `0`.
   */
  fun secondsRemaining(period: Int = DEFAULT_PERIOD, at: Instant = now()): Int {
    require(period > 0) { "Period must be positive, was $period" }
    return (period - (at.epochSecond % period)).toInt()
  }

  /**
   * Fraction of the current window still remaining: `1f` the moment a code appears, approaching `0f`
   * as it expires. Drives the countdown ring.
   */
  fun progress(period: Int = DEFAULT_PERIOD, at: Instant = now()): Float {
    require(period > 0) { "Period must be positive, was $period" }
    return secondsRemaining(period, at).toFloat() / period.toFloat()
  }

  private fun hmac(secret: ByteArray, counter: Long, algorithm: String): ByteArray {
    val mac = Mac.getInstance(algorithm)
    mac.init(SecretKeySpec(secret, algorithm))

    // Counter as an unsigned 64-bit big-endian integer (RFC 4226 §5.2).
    val message = ByteArray(Long.SIZE_BYTES)
    var remaining = counter
    for (index in message.indices.reversed()) {
      message[index] = (remaining and 0xFF).toByte()
      remaining = remaining ushr 8
    }
    return mac.doFinal(message)
  }

  companion object {
    const val HMAC_SHA1 = "HmacSHA1"
    const val HMAC_SHA256 = "HmacSHA256"
    const val HMAC_SHA512 = "HmacSHA512"

    const val DEFAULT_DIGITS = 6
    const val MIN_DIGITS = 6
    const val MAX_DIGITS = 8
    const val DEFAULT_PERIOD = 30

    /** `10^n` for every supported digit count, so truncation never touches floating point. */
    private val POWERS_OF_TEN = IntArray(MAX_DIGITS - MIN_DIGITS + 1) { index ->
      var power = 1
      repeat(index + MIN_DIGITS) { power *= 10 }
      power
    }
  }
}
