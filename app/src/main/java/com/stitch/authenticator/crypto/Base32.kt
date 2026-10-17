package com.stitch.authenticator.crypto

/**
 * RFC 4648 Base32 — the encoding every `otpauth://` secret arrives in.
 *
 * Decoding is deliberately forgiving about the noise real secrets carry (padding, spaces, line
 * breaks, lowercase, the hyphens people paste out of other apps) and strict about everything else: a
 * silently mangled secret produces plausible-looking codes that never validate, which is the single
 * most confusing failure mode an authenticator can have.
 *
 * Pure Kotlin so the RFC 4648 vectors run as plain JVM unit tests.
 */
object Base32 {

  private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
  private const val PAD = '='
  private const val BITS_PER_CHARACTER = 5
  private const val CHARACTER_MASK = 0x1F

  /** `-1` marks a non-Base32 character, `-2` marks padding. Indexed by ASCII code point. */
  private val LOOKUP = IntArray(128) { -1 }.apply {
    ALPHABET.forEachIndexed { index, character -> this[character.code] = index }
    this[PAD.code] = -2
  }

  /**
   * Decodes [encoded], ignoring padding, whitespace and hyphens.
   *
   * @throws IllegalArgumentException when a character outside the alphabet appears.
   */
  fun decode(encoded: String): ByteArray {
    val decoded = java.io.ByteArrayOutputStream(encoded.length * BITS_PER_CHARACTER / 8)
    var buffer = 0
    var bitsInBuffer = 0
    for (character in encoded) {
      if (character == PAD || character.isWhitespace() || character == '-') continue
      // Secrets get pasted in any case; the alphabet itself is uppercase.
      val normalized = character.uppercaseChar()
      val value = if (normalized.code < LOOKUP.size) LOOKUP[normalized.code] else -1
      require(value >= 0) { "Not a Base32 character: '$character'" }
      buffer = (buffer shl BITS_PER_CHARACTER) or value
      bitsInBuffer += BITS_PER_CHARACTER
      if (bitsInBuffer >= 8) {
        bitsInBuffer -= 8
        decoded.write((buffer shr bitsInBuffer) and 0xFF)
      }
    }
    // Leftover bits are padding, not data (RFC 4648 §3.5) — some generators omit the "=" entirely,
    // so a partial trailing group is dropped rather than rejected.
    return decoded.toByteArray()
  }

  /** Encodes [bytes] as padded, uppercase Base32. */
  fun encode(bytes: ByteArray): String {
    val encoded = StringBuilder((bytes.size * 8 + 4) / BITS_PER_CHARACTER)
    var buffer = 0
    var bitsInBuffer = 0
    for (byte in bytes) {
      buffer = (buffer shl 8) or (byte.toInt() and 0xFF)
      bitsInBuffer += 8
      while (bitsInBuffer >= BITS_PER_CHARACTER) {
        bitsInBuffer -= BITS_PER_CHARACTER
        encoded.append(ALPHABET[(buffer shr bitsInBuffer) and CHARACTER_MASK])
      }
    }
    if (bitsInBuffer > 0) {
      encoded.append(ALPHABET[(buffer shl (BITS_PER_CHARACTER - bitsInBuffer)) and CHARACTER_MASK])
    }
    while (encoded.length % 8 != 0) {
      encoded.append(PAD)
    }
    return encoded.toString()
  }

  /**
   * True when [encoded] holds at least one significant character and decodes to actual key material.
   *
   * Decoding cleanly is not sufficient on its own: a single character carries five bits, which round
   * down to nothing, so `"A"` decodes to an empty array. A token built from that would claim to be
   * valid and then throw the moment it tried to produce a code — taking the list down with it.
   */
  fun isValid(encoded: String): Boolean {
    if (encoded.none { it != PAD && !it.isWhitespace() && it != '-' }) return false
    return runCatching { decode(encoded).isNotEmpty() }.getOrDefault(false)
  }
}
