package com.stitch.authenticator.data.vault

import java.security.SecureRandom

/**
 * The vault's "Generate password" button: random characters from a fixed alphabet with every
 * character class guaranteed at least once.
 *
 * [SecureRandom] — not `kotlin.random.Random`, whose default is not a CSPRNG — so the output is
 * cryptographically random. The class guarantee is a seeded-positions-then-shuffle rather than
 * a re-roll loop: one pass over the pools, one Fisher-Yates over the result, odds of needing a
 * second pass nil because there is no second pass. Pure Kotlin over an injected [random], so
 * the JVM tests pin the shape without a device.
 */
class PasswordGenerator(private val random: SecureRandom = SecureRandom()) {

  fun generate(length: Int = DefaultLength): String {
    require(length >= Pools.size) {
      "A password shorter than its ${Pools.size} character classes cannot guarantee them all"
    }
    val chars = CharArray(length)
    // One from each class first, so the guarantee holds before the shuffle even runs.
    Pools.forEachIndexed { index, pool -> chars[index] = pool[random.nextInt(pool.length)] }
    for (index in Pools.size until length) {
      chars[index] = Alphabet[random.nextInt(Alphabet.length)]
    }
    // Fisher-Yates so the guaranteed classes are not parked in the first four positions.
    for (index in chars.indices.reversed()) {
      val swap = random.nextInt(index + 1)
      val held = chars[index]
      chars[index] = chars[swap]
      chars[swap] = held
    }
    return String(chars)
  }

  companion object {
    /** Sixteen is the task's brief; ~101 bits over the 80-character alphabet below. */
    const val DefaultLength = 16

    private const val Uppercase = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val Lowercase = "abcdefghijklmnopqrstuvwxyz"
    private const val Digits = "0123456789"

    /**
     * The shell-safe symbols: no quotes, backslashes, backticks or spaces, so a generated
     * password survives being pasted into a terminal, an export field, or a `:`-delimited file.
     */
    private const val Symbols = "!@#$%^&*()-_=+[]{}"

    private val Pools = listOf(Uppercase, Lowercase, Digits, Symbols)
    private val Alphabet = Uppercase + Lowercase + Digits + Symbols
  }
}
