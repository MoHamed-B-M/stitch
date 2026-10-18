package com.stitch.authenticator.data.passkeys

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeyCodecTest {

  private val codec = PasskeyCodec()

  private val google = PasskeyEntity(
    credentialId = "cred-google",
    rpId = "google.com",
    username = "jane@example.com",
    userHandle = "dXNlcjE",
    publicKey = "pQECAyY",
    keyAlias = "stitch-passkey-cred-google",
  )

  private val acme = PasskeyEntity(
    credentialId = "cred-acme",
    rpId = "acme.test",
    username = "jane",
    userHandle = "YWJjZA",
    publicKey = "pQECAyYihQ",
    keyAlias = "stitch-passkey-cred-acme",
  )

  @Test
  fun `round trips a list of passkeys`() {
    val stored = listOf(google, acme)

    assertEquals(stored, codec.decode(codec.encode(stored)))
  }

  @Test
  fun `round trips an empty list`() {
    assertEquals(emptyList<PasskeyEntity>(), codec.decode(codec.encode(emptyList())))
  }

  @Test
  fun `keeps the order it was given`() {
    val encoded = codec.encode(listOf(acme, google))

    assertEquals(listOf(acme, google), codec.decode(encoded))
  }

  @Test
  fun `ignores fields written by a newer version`() {
    val raw = """
      [
        {
          "credentialId": "cred-google",
          "rpId": "google.com",
          "username": "jane@example.com",
          "userHandle": "dXNlcjE",
          "publicKey": "pQECAyY",
          "keyAlias": "stitch-passkey-cred-google",
          "someFutureFlag": true
        }
      ]
    """.trimIndent()

    val passkey = codec.decode(raw).single()

    assertEquals("google.com", passkey.rpId)
    assertEquals("jane@example.com", passkey.username)
  }

  @Test
  fun `surfaces corrupt data instead of silently dropping passkeys`() {
    listOf(
      "",
      "not json at all",
      "{}",
      """[{"rpId": "google.com", "username": "jane@example.com"}]""",
      """[{"credentialId": "1", "rpId": "google.com", "username": 7, "userHandle": "dXNlcjE", "publicKey": "pQECAyY", "keyAlias": "k"}]""",
    ).forEach { corrupt ->
      assertThrows("decoding\n$corrupt", SerializationException::class.java) {
        codec.decode(corrupt)
      }
    }
  }

  @Test
  fun `writes every field so the stored format is self-describing`() {
    val encoded = codec.encode(listOf(google))

    listOf(
      "credentialId",
      "rpId",
      "username",
      "userHandle",
      "publicKey",
      "keyAlias",
    ).forEach { field -> assertTrue(encoded, encoded.contains("\"$field\"")) }
  }
}
