package com.stitch.authenticator.passkeys

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wire bytes are the contract with every relying party on earth, so this suite pins the
 * exact layouts: flags, length prefixes, CBOR map order, and the JSON a server parses back.
 */
class PasskeyWebAuthnTest {

  private val challenge = ByteArray(32) { (it * 7).toByte() }
  private val challengeB64 = PasskeyWebAuthn.b64UrlEncode(challenge)

  private val creationJson = """
    {
      "challenge": "$challengeB64",
      "rp": {"name": "Example Corp", "id": "example.com"},
      "user": {
        "id": "${PasskeyWebAuthn.b64UrlEncode(ByteArray(16) { (it + 1).toByte() })}",
        "name": "ada@example.com",
        "displayName": "Ada Lovelace"
      },
      "pubKeyCredParams": [{"type": "public-key", "alg": -7}],
      "excludeCredentials": [
        {"type": "public-key", "id": "${PasskeyWebAuthn.b64UrlEncode(ByteArray(4) { 0x0A })}"},
        {"type": "public-key", "id": "not-base64url!!"}
      ],
      "attestation": "none"
    }
  """.trimIndent()

  // --------------------------------------------------------------------- parsing

  @Test
  fun `parses a creation request into the fields an authenticator needs`() {
    val parsed = PasskeyWebAuthn.parseCreationRequest(creationJson)

    assertEquals("example.com", parsed.rpId)
    assertEquals("Example Corp", parsed.rpName)
    assertEquals("ada@example.com", parsed.userName)
    assertEquals("Ada Lovelace", parsed.userDisplayName)
    assertArrayEquals(challenge, parsed.challenge)
    assertArrayEquals(ByteArray(16) { (it + 1).toByte() }, parsed.userId)
    // The malformed descriptor is skipped, the good one survives.
    assertEquals(1, parsed.excludeCredentialIds.size)
    assertArrayEquals(ByteArray(4) { 0x0A }, parsed.excludeCredentialIds[0])
  }

  @Test
  fun `parses an assertion request and tolerates a missing allowCredentials`() {
    val parsed = PasskeyWebAuthn.parseAssertionRequest(
      """{"challenge":"$challengeB64","rpId":"example.com","userVerification":"required"}""",
    )

    assertEquals("example.com", parsed.rpId)
    assertArrayEquals(challenge, parsed.challenge)
    assertTrue(parsed.allowCredentialIds.isEmpty())
  }

  @Test
  fun `rejects requests that are not usable`() {
    assertThrows(IllegalArgumentException::class.java) {
      PasskeyWebAuthn.parseCreationRequest("not json at all")
    }
    assertThrows(IllegalArgumentException::class.java) {
      PasskeyWebAuthn.parseCreationRequest("""{"challenge":"$challengeB64"}""") // no rp / user
    }
    assertThrows(IllegalArgumentException::class.java) {
      PasskeyWebAuthn.parseAssertionRequest("""{"rpId":"example.com"}""") // no challenge
    }
    assertThrows(IllegalArgumentException::class.java) {
      PasskeyWebAuthn.parseAssertionRequest("""{"challenge":"****","rpId":"x"}""")
    }
  }

  // --------------------------------------------------------------------- base64url

  @Test
  fun `base64url round trips and never pads`() {
    val bytes = ByteArray(64) { (it * 13).toByte() }
    val encoded = PasskeyWebAuthn.b64UrlEncode(bytes)

    assertTrue(!encoded.contains('='))
    assertTrue(!encoded.contains('+') && !encoded.contains('/'))
    assertArrayEquals(bytes, PasskeyWebAuthn.b64UrlDecode(encoded))
    // The decoder also accepts the padded form, for request JSONs that carry it.
    assertArrayEquals(bytes, PasskeyWebAuthn.b64UrlDecode("$encoded=="))
  }

  // --------------------------------------------------------------------- authenticator data

  @Test
  fun `attestation authenticator data carries the exact spec layout`() {
    val credentialId = ByteArray(32) { 0x55 }
    val publicKeyCose = ByteArray(75) { 0x11 }

    val authData = PasskeyWebAuthn.attestationAuthenticatorData(
      "example.com",
      credentialId,
      publicKeyCose,
    )

    assertEquals(32 + 1 + 4 + 16 + 2 + 32 + 75, authData.size)
    assertArrayEquals(PasskeyWebAuthn.sha256("example.com".toByteArray()), authData.copyOfRange(0, 32))
    assertEquals(0x45.toByte(), authData[32]) // UP | UV | AT
    assertTrue(authData.copyOfRange(33, 37).all { it == 0.toByte() }) // sign count
    assertTrue(authData.copyOfRange(37, 53).all { it == 0.toByte() }) // AAGUID
    assertEquals(0, authData[53].toInt()) // credential id length, network order
    assertEquals(32, authData[54].toInt())
    assertArrayEquals(credentialId, authData.copyOfRange(55, 87))
    assertArrayEquals(publicKeyCose, authData.copyOfRange(87, authData.size))
  }

  @Test
  fun `assertion authenticator data is 37 bytes with UP and UV`() {
    val authData = PasskeyWebAuthn.assertionAuthenticatorData("example.com")

    assertEquals(37, authData.size)
    assertEquals(0x05.toByte(), authData[32]) // UP | UV
  }

  // --------------------------------------------------------------------- attestation object

  @Test
  fun `attestation object is the none-format CBOR map, byte for byte`() {
    val authenticatorData = PasskeyWebAuthn.assertionAuthenticatorData("example.com")

    val encoded = PasskeyWebAuthn.attestationObject(authenticatorData)

    val prefix = byteArrayOf(
      0xA2.toByte(), // map(2)
      0x63, 'f'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(),
      0x64, 'n'.code.toByte(), 'o'.code.toByte(), 'n'.code.toByte(), 'e'.code.toByte(),
      0x67, 'a'.code.toByte(), 't'.code.toByte(), 't'.code.toByte(), 'S'.code.toByte(),
      't'.code.toByte(), 'm'.code.toByte(), 't'.code.toByte(),
      0xA0.toByte(), // map(0) — the empty attStmt
      0x68, 'a'.code.toByte(), 'u'.code.toByte(), 't'.code.toByte(), 'h'.code.toByte(),
      'D'.code.toByte(), 'a'.code.toByte(), 't'.code.toByte(), 'a'.code.toByte(),
      0x58, authenticatorData.size.toByte(),
    )
    assertArrayEquals(prefix + authenticatorData, encoded)
  }

  // --------------------------------------------------------------------- response JSON

  @Test
  fun `create response has the fields a relying party parses back`() {
    val credentialId = ByteArray(32) { 0x33 }
    val clientData = PasskeyWebAuthn.clientDataJson("webauthn.create", challenge, "android:apk-key-hash:ab")
    val attestation = PasskeyWebAuthn.attestationObject(
      PasskeyWebAuthn.attestationAuthenticatorData("example.com", credentialId, ByteArray(4)),
    )

    val json = kotlinx.serialization.json.Json.parseToJsonElement(
      PasskeyWebAuthn.createResponseJson(credentialId, clientData, attestation),
    ).jsonObject

    val id = PasskeyWebAuthn.b64UrlEncode(credentialId)
    assertEquals(id, json["id"]!!.jsonPrimitive.content)
    assertEquals(id, json["rawId"]!!.jsonPrimitive.content)
    assertEquals("public-key", json["type"]!!.jsonPrimitive.content)
    val response = json["response"]!!.jsonObject
    assertEquals(
      PasskeyWebAuthn.b64UrlEncode(clientData.toByteArray()),
      response["clientDataJSON"]!!.jsonPrimitive.content,
    )
    assertEquals(
      PasskeyWebAuthn.b64UrlEncode(attestation),
      response["attestationObject"]!!.jsonPrimitive.content,
    )
    assertEquals("internal", response["transports"]!!.jsonArray[0].jsonPrimitive.content)
  }

  @Test
  fun `assertion response carries the signature and the optional user handle`() {
    val credentialId = ByteArray(32) { 0x44 }
    val authData = PasskeyWebAuthn.assertionAuthenticatorData("example.com")
    val clientData = PasskeyWebAuthn.clientDataJson("webauthn.get", challenge, "android:apk-key-hash:ab")
    val signature = ByteArray(64) { 0x66 }
    val userHandle = ByteArray(16) { 0x77 }

    val withHandle = kotlinx.serialization.json.Json.parseToJsonElement(
      PasskeyWebAuthn.assertionResponseJson(credentialId, clientData, authData, signature, userHandle),
    ).jsonObject
    val withoutHandle = kotlinx.serialization.json.Json.parseToJsonElement(
      PasskeyWebAuthn.assertionResponseJson(credentialId, clientData, authData, signature, null),
    ).jsonObject

    val response = withHandle["response"]!!.jsonObject
    assertEquals(
      PasskeyWebAuthn.b64UrlEncode(signature),
      response["signature"]!!.jsonPrimitive.content,
    )
    assertEquals(
      PasskeyWebAuthn.b64UrlEncode(userHandle),
      response["userHandle"]!!.jsonPrimitive.content,
    )
    assertTrue("userHandle" !in withoutHandle["response"]!!.jsonObject)
  }

  // --------------------------------------------------------------------- client data

  @Test
  fun `client data commits to type, challenge and origin and hashes to 32 bytes`() {
    val clientData = PasskeyWebAuthn.clientDataJson("webauthn.create", challenge, "android:apk-key-hash:ab")

    assertTrue(clientData.startsWith("""{"type":"webauthn.create","challenge":"$challengeB64","""))
    assertTrue(clientData.contains(""""origin":"android:apk-key-hash:ab""""))
    assertEquals(32, PasskeyWebAuthn.clientDataHash(clientData).size)
  }
}
