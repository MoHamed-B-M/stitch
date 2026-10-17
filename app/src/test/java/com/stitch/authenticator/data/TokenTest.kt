package com.stitch.authenticator.data

import com.stitch.authenticator.crypto.Base32
import com.stitch.authenticator.crypto.TotpEngine
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenTest {

  private val engine = TotpEngine(Clock.fixed(Instant.ofEpochSecond(59), ZoneOffset.UTC))

  @Test
  fun `title prefers the issuer and falls back to the account`() {
    val named = token()
    assertEquals("GitHub", named.title)
    assertEquals("octocat", named.subtitle)

    val anonymous = token(issuer = "")
    assertEquals("octocat", anonymous.title)
    assertEquals("", anonymous.subtitle)
  }

  @Test
  fun `subtitle is empty when it would only repeat the title`() {
    assertEquals("", token(accountName = "").subtitle)
    assertEquals("", token(issuer = "", accountName = "").subtitle)
  }

  @Test
  fun `generates the code for its stored base32 secret`() {
    val token = token(secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ", digits = 8)

    assertEquals("94287082", token.code(engine))
  }

  @Test
  fun `dispatches to the configured hash algorithm`() {
    val sha256Secret = "12345678901234567890123456789012".toByteArray()
    val sha512Secret =
      "1234567890123456789012345678901234567890123456789012345678901234".toByteArray()

    val sha256 = token(secret = Base32.encode(sha256Secret), algorithm = OtpAlgorithm.SHA256, digits = 8)
    val sha512 = token(secret = Base32.encode(sha512Secret), algorithm = OtpAlgorithm.SHA512, digits = 8)

    assertEquals("46119246", sha256.code(engine))
    assertEquals("90693936", sha512.code(engine))
  }

  @Test
  fun `validity rejects tokens that could never produce a code`() {
    assertTrue(token().isValid)

    assertFalse(token(secret = "").isValid)
    assertFalse(token(secret = "not-base32!").isValid)
    assertFalse(token(digits = 5).isValid)
    assertFalse(token(digits = 9).isValid)
    assertFalse(token(period = 0).isValid)
  }

  @Test
  fun `reads the hash algorithm from an otpauth parameter`() {
    assertEquals(OtpAlgorithm.SHA1, OtpAlgorithm.fromParameter(null))
    assertEquals(OtpAlgorithm.SHA1, OtpAlgorithm.fromParameter("MD5"))
    assertEquals(OtpAlgorithm.SHA256, OtpAlgorithm.fromParameter("sha256"))
    assertEquals(OtpAlgorithm.SHA512, OtpAlgorithm.fromParameter(" SHA512 "))
    assertEquals("HmacSHA1", OtpAlgorithm.SHA1.macName)
    assertEquals("HmacSHA256", OtpAlgorithm.SHA256.macName)
    assertEquals("HmacSHA512", OtpAlgorithm.SHA512.macName)
  }

  @Test
  fun `defaults every token to a fresh identity`() {
    val first = Token(issuer = "GitHub", accountName = "octocat", secret = "GEZDGNBVGY3TQOJQ")
    val second = Token(issuer = "GitHub", accountName = "octocat", secret = "GEZDGNBVGY3TQOJQ")

    assertTrue(first.id != second.id)
    assertEquals(OtpAlgorithm.SHA1, first.algorithm)
    assertEquals(6, first.digits)
    assertEquals(30, first.period)
  }

  private fun token(
    issuer: String = "GitHub",
    accountName: String = "octocat",
    secret: String = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
    algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
    digits: Int = 6,
    period: Int = 30,
  ) = Token(
    id = "token-id",
    issuer = issuer,
    accountName = accountName,
    secret = secret,
    algorithm = algorithm,
    digits = digits,
    period = period,
  )
}
