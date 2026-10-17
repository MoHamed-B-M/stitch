package com.stitch.authenticator.data

import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenCodecTest {

  private val codec = TokenCodec()

  private val github = Token(
    id = "id-github",
    issuer = "GitHub",
    accountName = "octocat",
    secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ",
  )

  private val acme = Token(
    id = "id-acme",
    issuer = "ACME",
    accountName = "jane@acme.test",
    secret = "MZXW6YTBOI======",
    algorithm = OtpAlgorithm.SHA512,
    digits = 8,
    period = 60,
  )

  @Test
  fun `round trips a list of tokens`() {
    val stored = listOf(github, acme)

    assertEquals(stored, codec.decode(codec.encode(stored)))
  }

  @Test
  fun `round trips an empty list`() {
    assertEquals(emptyList<Token>(), codec.decode(codec.encode(emptyList())))
  }

  @Test
  fun `keeps the order it was given`() {
    val encoded = codec.encode(listOf(acme, github))

    assertEquals(listOf(acme, github), codec.decode(encoded))
  }

  @Test
  fun `ignores fields written by a newer version`() {
    val raw = """
      [
        {
          "id": "id-github",
          "issuer": "GitHub",
          "accountName": "octocat",
          "secret": "GEZDGNBVGY3TQOJQ",
          "someFutureFlag": true
        }
      ]
    """.trimIndent()

    val token = codec.decode(raw).single()

    assertEquals("GitHub", token.issuer)
    assertEquals("octocat", token.accountName)
  }

  @Test
  fun `fills in defaults for fields an older version never wrote`() {
    val raw = """
      [{"id": "id-github", "issuer": "GitHub", "accountName": "octocat", "secret": "GEZDGNBVGY3TQOJQ"}]
    """.trimIndent()

    val token = codec.decode(raw).single()

    assertEquals(OtpAlgorithm.SHA1, token.algorithm)
    assertEquals(6, token.digits)
    assertEquals(30, token.period)
  }

  @Test
  fun `surfaces corrupt data instead of silently dropping tokens`() {
    listOf(
      "",
      "not json at all",
      "{}",
      """[{"issuer": "GitHub", "accountName": "octocat"}]""",
      """[{"id": "1", "issuer": "GitHub", "accountName": "octocat", "secret": "GEZDGNBVGY3TQOJQ", "digits": "six"}]""",
    ).forEach { corrupt ->
      assertThrows("decoding\n$corrupt", SerializationException::class.java) {
        codec.decode(corrupt)
      }
    }
  }

  @Test
  fun `writes defaults so the stored format is self-describing`() {
    val encoded = codec.encode(listOf(github))

    assertTrue(encoded, encoded.contains("\"algorithm\":\"SHA1\""))
    assertTrue(encoded, encoded.contains("\"digits\":6"))
    assertTrue(encoded, encoded.contains("\"period\":30"))
  }
}
