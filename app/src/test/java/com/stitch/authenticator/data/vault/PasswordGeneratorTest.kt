package com.stitch.authenticator.data.vault

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the shape of the generated passwords — the pure part of the vault, the only piece of
 * Phase 10's storage layer that runs on the JVM without a device (Keystore, SQLCipher and Room
 * are platform-bound by design; see the honest boundaries in each of those files).
 */
class PasswordGeneratorTest {

  private val generator = PasswordGenerator()

  @Test
  fun `the default length is sixteen`() {
    assertEquals(16, generator.generate().length)
    assertEquals(PasswordGenerator.DefaultLength, generator.generate().length)
  }

  @Test
  fun `every generated password carries all four character classes`() {
    // 200 draws: the probability of a class ever going missing is effectively zero by design,
    // and a generator that regressed to "random from the full alphabet" fails this immediately.
    repeat(200) {
      val password = generator.generate()
      assertTrue(password.any { it in 'A'..'Z' })
      assertTrue(password.any { it in 'a'..'z' })
      assertTrue(password.any { it in '0'..'9' })
      assertTrue(password.any { it in SYMBOLS })
    }
  }

  @Test
  fun `generated passwords never leave the declared alphabet`() {
    repeat(200) {
      generator.generate().forEach { assertTrue(it in ALPHABET) }
    }
  }

  @Test
  fun `two generated passwords differ`() {
    // 16 chars over 80 symbols: a collision would not be bad luck, it would be a broken RNG.
    assertNotEquals(generator.generate(), generator.generate())
  }

  @Test
  fun `a requested length is honoured exactly`() {
    listOf(8, 16, 32, 64).forEach { length ->
      assertEquals(length, generator.generate(length = length).length)
    }
  }

  @Test
  fun `a length below the class count is rejected`() {
    assertThrows(IllegalArgumentException::class.java) { generator.generate(length = 3) }
  }

  private companion object {
    val SYMBOLS = "!@#\$%^&*()-_=+[]{}".toSet()
    val ALPHABET = ("ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
        "abcdefghijklmnopqrstuvwxyz" +
        "0123456789" +
        "!@#\$%^&*()-_=+[]{}")
      .toSet()
  }
}
