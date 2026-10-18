package com.stitch.authenticator.passkeys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PasskeyQrTest {

  @Test
  fun `fido handle with a hostname segment names its relying party`() {
    val parsed = PasskeyQr.parseOrNull("FIDO:/example.com/abc123")

    assertNotNull(parsed)
    assertEquals("example.com", parsed!!.rpId)
    assertEquals("FIDO:/example.com/abc123", parsed.raw)
  }

  @Test
  fun `fido handle with a manufacturer prefix still finds the hostname`() {
    val parsed = PasskeyQr.parseOrNull("fido:/FIDO/example.com/handle?x=1")

    assertNotNull(parsed)
    assertEquals("example.com", parsed!!.rpId)
  }

  @Test
  fun `fido handle without any hostname routes with a null rp id`() {
    val parsed = PasskeyQr.parseOrNull("FIDO:/just-a-handle")

    assertNotNull(parsed)
    assertNull(parsed!!.rpId)
  }

  @Test
  fun `webauthn link takes the relying party from the host`() {
    val parsed = PasskeyQr.parseOrNull("webauthn://example.com/register?ch=1")

    assertNotNull(parsed)
    assertEquals("example.com", parsed!!.rpId)
  }

  @Test
  fun `opaque webauthn code routes without a relying party`() {
    val parsed = PasskeyQr.parseOrNull("webauthn:create:something")

    assertNotNull(parsed)
    assertNull(parsed!!.rpId)
  }

  @Test
  fun `creation request json names the relying party from its rp id`() {
    val parsed =
      PasskeyQr.parseOrNull(
        """{"challenge":"abc","rp":{"name":"Example","id":"example.com"},"user":{"name":"ada"}}""",
      )

    assertNotNull(parsed)
    assertEquals("example.com", parsed!!.rpId)
  }

  @Test
  fun `assertion request json names the relying party from rpId`() {
    val parsed = PasskeyQr.parseOrNull("""{"challenge":"abc","rpId":"example.com"}""")

    assertNotNull(parsed)
    assertEquals("example.com", parsed!!.rpId)
  }

  @Test
  fun `json without webauthn markers is not a passkey code`() {
    assertNull(PasskeyQr.parseOrNull("""{"foo":1,"bar":2}"""))
    assertNull(PasskeyQr.parseOrNull("{not even json"))
  }

  @Test
  fun `otp and plain text are never passkey codes`() {
    assertNull(PasskeyQr.parseOrNull("otpauth://totp/Example:ada?secret=JBSWY3DPEHPK3PXP"))
    assertNull(PasskeyQr.parseOrNull("https://example.com"))
    assertNull(PasskeyQr.parseOrNull("just some text"))
  }
}
