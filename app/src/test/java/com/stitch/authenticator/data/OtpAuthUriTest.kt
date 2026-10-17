package com.stitch.authenticator.data

import com.stitch.authenticator.crypto.TotpEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shapes a pasted link actually arrives in.
 *
 * [OtpAuthUri] is the app's only door for QR codes, so every generator that produced something it
 * refused had to be closed by hand in [com.stitch.authenticator.ui.main.MainScreen]'s neighbours —
 * these tests keep the tolerance it was given: lenient about formatting, unforgiving about values
 * that would produce codes the server could never accept.
 */
class OtpAuthUriTest {

  private val key = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

  @Test
  fun `reads a link with both issuer spellings`() {
    val payload = parseOk("otpauth://totp/GitHub:octocat?secret=$key&issuer=GitHub&period=45&digits=8")

    assertEquals("GitHub", payload.issuer)
    assertEquals("octocat", payload.accountName)
    assertEquals(key, payload.secret)
    assertEquals(OtpAlgorithm.SHA1, payload.algorithm)
    assertEquals(45, payload.period)
    assertEquals(8, payload.digits)
    assertFalse(payload.isDefaultShape)
  }

  @Test
  fun `an issuer parameter outranks the label prefix`() {
    // Google's key-uri format says the parameter is the spelling to trust; generators disagree with
    // their own prefixes constantly.
    val payload = parseOk("otpauth://totp/Email:me@example.com?secret=$key&issuer=WorkEmail")

    assertEquals("WorkEmail", payload.issuer)
    assertEquals("me@example.com", payload.accountName)
  }

  @Test
  fun `falls back to the label prefix when no issuer parameter is present`() {
    val payload = parseOk("otpauth://totp/Discord:someone?secret=$key")

    assertEquals("Discord", payload.issuer)
    assertEquals("someone", payload.accountName)
    assertEquals("Discord", payload.title)
    assertEquals("someone", payload.subtitle)
  }

  @Test
  fun `a label that only repeats the issuer is not shown twice`() {
    // "GitHub / GitHub" is what you get when a generator writes the name into both places.
    val payload = parseOk("otpauth://totp/GitHub:GitHub?secret=$key&issuer=GitHub")

    assertEquals("GitHub", payload.issuer)
    assertEquals("GitHub", payload.accountName)
    assertEquals("GitHub", payload.title)
    assertEquals("", payload.subtitle)
  }

  @Test
  fun `a label with no colon is the account name`() {
    val payload = parseOk("otpauth://totp/octocat?secret=$key")

    assertEquals("", payload.issuer)
    assertEquals("octocat", payload.accountName)
    assertEquals("octocat", payload.title)
    assertEquals("", payload.subtitle)
  }

  @Test
  fun `survives the whitespace line breaks and case real links carry`() {
    val payload =
      parseOk("  OTPAUTH://TOTP/ACME%20Corp:jane@example.com?secret=$key  \n&ISSUER=acme%20corp ")

    assertEquals("acme corp", payload.issuer)
    assertEquals("jane@example.com", payload.accountName)
    assertEquals("acme corp", payload.title)
  }

  @Test
  fun `takes the first value of a repeated parameter`() {
    val payload = parseOk("otpauth://totp/One:two?secret=$key&secret=AAAA&issuer=First&issuer=Second")

    assertEquals(key, payload.secret)
    assertEquals("First", payload.issuer)
  }

  @Test
  fun `accepts both spellings of the hash algorithm`() {
    assertEquals(OtpAlgorithm.SHA256, parseOk("otpauth://totp/A:b?secret=$key&algorithm=SHA-256").algorithm)
    assertEquals(OtpAlgorithm.SHA256, parseOk("otpauth://totp/A:b?secret=$key&algorithm=sha256").algorithm)
    assertEquals(OtpAlgorithm.SHA512, parseOk("otpauth://totp/A:b?secret=$key&algorithm=SHA512").algorithm)
  }

  @Test
  fun `omitted parameters take the shapes most services use`() {
    val payload = parseOk("otpauth://totp/A:b?secret=$key")

    assertEquals(OtpAlgorithm.SHA1, payload.algorithm)
    assertEquals(TotpEngine.DEFAULT_DIGITS, payload.digits)
    assertEquals(TotpEngine.DEFAULT_PERIOD, payload.period)
    assertTrue(payload.isDefaultShape)
  }

  @Test
  fun `turns down anything that is not a link`() {
    assertEquals(OtpAuthUri.Error.NotALink, parseErr("https://github.com"))
    assertEquals(OtpAuthUri.Error.NotALink, parseErr("hello, here is my setup key: $key"))
    assertEquals(OtpAuthUri.Error.NotALink, parseErr(""))
    assertEquals(OtpAuthUri.Error.NotALink, parseErr("$key"))
  }

  @Test
  fun `only time based links can become tokens`() {
    // A counter-based link needs a counter, which is not part of a code the app ever displays.
    assertEquals(OtpAuthUri.Error.NotTimeBased, parseErr("otpauth://hotp/A:b?secret=$key"))
    assertEquals(OtpAuthUri.Error.NotTimeBased, parseErr("otpauth://A:b?secret=$key"))
  }

  @Test
  fun `refuses a key that would not decode`() {
    assertEquals(OtpAuthUri.Error.MissingKey, parseErr("otpauth://totp/A:b"))
    assertEquals(OtpAuthUri.Error.MissingKey, parseErr("otpauth://totp/A:b?secret="))
    assertEquals(OtpAuthUri.Error.InvalidKey, parseErr("otpauth://totp/A:b?secret=not-base32!"))
    assertEquals(OtpAuthUri.Error.InvalidKey, parseErr("otpauth://totp/A:b?secret=A"))
  }

  @Test
  fun `refuses a hash it cannot compute rather than guessing one it can`() {
    // Falling back to SHA1 here would store a token whose codes never validate — accepted-looking,
    // permanently broken.
    assertEquals(OtpAuthUri.Error.UnsupportedAlgorithm, parseErr("otpauth://totp/A:b?secret=$key&algorithm=MD5"))
    assertEquals(OtpAuthUri.Error.UnsupportedAlgorithm, parseErr("otpauth://totp/A:b?secret=$key&algorithm="))
    assertEquals(OtpAuthUri.Error.UnsupportedAlgorithm, parseErr("otpauth://totp/A:b?secret=$key&algorithm=SHA3-512"))
  }

  @Test
  fun `refuses digit counts the app cannot show`() {
    assertEquals(OtpAuthUri.Error.UnsupportedDigits, parseErr("otpauth://totp/A:b?secret=$key&digits=5"))
    assertEquals(OtpAuthUri.Error.UnsupportedDigits, parseErr("otpauth://totp/A:b?secret=$key&digits=9"))
    // The form offers 6 and 8; a count it cannot render would become a permanent mystery.
    assertEquals(OtpAuthUri.Error.UnsupportedDigits, parseErr("otpauth://totp/A:b?secret=$key&digits=7"))
    assertEquals(OtpAuthUri.Error.UnsupportedDigits, parseErr("otpauth://totp/A:b?secret=$key&digits=six"))
  }

  @Test
  fun `refuses a period that is not a run of seconds`() {
    assertEquals(OtpAuthUri.Error.UnsupportedPeriod, parseErr("otpauth://totp/A:b?secret=$key&period=0"))
    assertEquals(OtpAuthUri.Error.UnsupportedPeriod, parseErr("otpauth://totp/A:b?secret=$key&period=-30"))
    assertEquals(OtpAuthUri.Error.UnsupportedPeriod, parseErr("otpauth://totp/A:b?secret=$key&period=30.5"))
    assertEquals(OtpAuthUri.Error.UnsupportedPeriod, parseErr("otpauth://totp/A:b?secret=$key&period="))
    assertEquals(OtpAuthUri.Error.UnsupportedPeriod, parseErr("otpauth://totp/A:b?secret=$key&period=hourly"))
  }

  @Test
  fun `a link that names no account at all is refused`() {
    assertEquals(OtpAuthUri.Error.MissingName, parseErr("otpauth://totp/?secret=$key"))
  }

  @Test
  fun `a rejected link explains itself in one human sentence`() {
    val rejected = OtpAuthUri.parse("otpauth://totp/A:b?secret=nope") as OtpAuthUri.Outcome.Rejected

    assertEquals(OtpAuthUri.Error.InvalidKey, rejected.error)
    assertTrue(rejected.error.message.isNotBlank())
    assertTrue(rejected.error.message.length > 10)
  }

  private fun parseOk(link: String): OtpAuthUri.Payload =
    when (val outcome = OtpAuthUri.parse(link)) {
      is OtpAuthUri.Outcome.Parsed -> outcome.payload
      is OtpAuthUri.Outcome.Rejected ->
        throw AssertionError("Expected a payload, got ${outcome.error}")
    }

  private fun parseErr(link: String): OtpAuthUri.Error =
    when (val outcome = OtpAuthUri.parse(link)) {
      is OtpAuthUri.Outcome.Parsed ->
        throw AssertionError("Expected a rejection, got ${outcome.payload}")
      is OtpAuthUri.Outcome.Rejected -> outcome.error
    }
}
