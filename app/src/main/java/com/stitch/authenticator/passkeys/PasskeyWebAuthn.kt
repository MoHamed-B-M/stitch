package com.stitch.authenticator.passkeys

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * The WebAuthn wire format, hand-rolled and pure: request parsing, authenticator data, client
 * data, the "none" attestation object, and the JSON a relying party reads back.
 *
 * Built on kotlinx-serialization rather than the androidx `webauthn` helpers on purpose — those
 * are `@RestrictTo(LIBRARY)`, and the creation-options parser hardcodes `authenticatorSelection`
 * with a TODO, so this layer owns the bytes end to end and stays JVM-unit-testable.
 *
 * Only the fields an *authenticator* needs are parsed; everything else (extensions, timeouts,
 * mediation) is the client's business and passes through untouched.
 */
internal object PasskeyWebAuthn {

  /** The registration (`navigator.credentials.create`) half of the exchange. */
  data class PasskeyCreationRequest(
    val rpId: String,
    val rpName: String,
    val userId: ByteArray,
    val userName: String,
    val userDisplayName: String,
    val challenge: ByteArray,
    val excludeCredentialIds: List<ByteArray>,
  ) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
  }

  /** The assertion (`navigator.credentials.get`) half of the exchange. */
  data class PasskeyAssertionRequest(
    val rpId: String,
    val challenge: ByteArray,
    val allowCredentialIds: List<ByteArray>,
  ) {
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
  }

  // --------------------------------------------------------------------- request parsing

  fun parseCreationRequest(requestJson: String): PasskeyCreationRequest {
    val root = parseObject(requestJson)
    val rp = root.object_("rp")
    val user = root.object_("user")
    return PasskeyCreationRequest(
      rpId = rp.string("id"),
      rpName = rp.string("name"),
      userId = b64UrlDecode(user.string("id")),
      userName = user.string("name"),
      userDisplayName = user.string("displayName"),
      challenge = b64UrlDecode(root.string("challenge")),
      excludeCredentialIds = root.array("excludeCredentials").mapNotNull { descriptorId(it) },
    )
  }

  fun parseAssertionRequest(requestJson: String): PasskeyAssertionRequest {
    val root = parseObject(requestJson)
    return PasskeyAssertionRequest(
      rpId = root.string("rpId"),
      challenge = b64UrlDecode(root.string("challenge")),
      allowCredentialIds = root.array("allowCredentials").mapNotNull { descriptorId(it) },
    )
  }

  // --------------------------------------------------------------------- base64url

  /** WebAuthn's base64url flavour: URL-safe alphabet, never padded. */
  fun b64UrlEncode(bytes: ByteArray): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

  fun b64UrlDecode(value: String): ByteArray =
    try {
      Base64.getUrlDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
      throw IllegalArgumentException("not valid base64url", e)
    }

  // --------------------------------------------------------------------- client data

  /**
   * `clientDataJSON` (W3C §15.2): the exact bytes whose hash both the RP and the signature
   * commit to. Field order — type, challenge, origin — is fixed by the spec's canonical form.
   */
  fun clientDataJson(type: String, challenge: ByteArray, origin: String): String =
    """{"type":"$type","challenge":"${b64UrlEncode(challenge)}","origin":"$origin","crossOrigin":false}"""

  fun clientDataHash(clientDataJson: String): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(clientDataJson.toByteArray(Charsets.UTF_8))

  // --------------------------------------------------------------------- authenticator data

  // W3C §6.1 flags.
  private const val FLAG_UP = 0x01 // user present
  private const val FLAG_UV = 0x02 // user verified
  private const val FLAG_AT = 0x40 // attested credential data follows

  private const val SIGN_COUNT_LENGTH = 4

  /**
   * §6.1 authenticator data for an *attestation* (create): UP|AT, a zero sign count (platform
   * authenticators have no hardware counter), then the attested credential data — zero AAGUID,
   * the credential id length in network order, the id itself, and the COSE public key.
   */
  fun attestationAuthenticatorData(
    rpId: String,
    credentialId: ByteArray,
    publicKeyCose: ByteArray,
  ): ByteArray {
    val credentialIdLength = byteArrayOf(
      ((credentialId.size shr 8) and 0xFF).toByte(),
      (credentialId.size and 0xFF).toByte(),
    )
    val aaguid = ByteArray(16) // zeros: a software authenticator has no manufacturer to name
    return sha256(rpId) +
      byteArrayOf((FLAG_UP or FLAG_AT).toByte()) +
      ByteArray(SIGN_COUNT_LENGTH) +
      aaguid +
      credentialIdLength +
      credentialId +
      publicKeyCose
  }

  /** §6.1 authenticator data for an *assertion* (get): UP|UV and a zero sign count — 37 bytes. */
  fun assertionAuthenticatorData(rpId: String): ByteArray =
    sha256(rpId) + byteArrayOf((FLAG_UP or FLAG_UV).toByte()) + ByteArray(SIGN_COUNT_LENGTH)

  fun sha256(data: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(data)

  private fun sha256(text: String): ByteArray =
    sha256(text.toByteArray(Charsets.UTF_8))

  // --------------------------------------------------------------------- attestation object

  /**
   * The CBOR attestation object for the `none` statement format — `{fmt: "none", attStmt: {},
   * authData: <bytes>}` — the only format a software authenticator can honestly produce without
   * a manufacturer certificate. WebAuthn clients accept it everywhere attestation isn't
   * demanded.
   */
  fun attestationObject(authenticatorData: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    out.write(0xA2) // map(2)
    writeCborText(out, "fmt")
    writeCborText(out, "none")
    writeCborText(out, "attStmt")
    out.write(0xA0) // map(0)
    writeCborText(out, "authData")
    writeCborBytes(out, authenticatorData)
    return out.toByteArray()
  }

  private fun writeCborText(out: ByteArrayOutputStream, value: String) {
    val bytes = value.toByteArray(Charsets.UTF_8)
    check(bytes.size < 24) { "CBOR short-form text only" }
    out.write(0x60 or bytes.size)
    out.write(bytes)
  }

  private fun writeCborBytes(out: ByteArrayOutputStream, value: ByteArray) {
    if (value.size < 256) {
      out.write(0x58)
      out.write(value.size)
    } else {
      out.write(0x59)
      out.write((value.size shr 8) and 0xFF)
      out.write(value.size and 0xFF)
    }
    out.write(value)
  }

  // --------------------------------------------------------------------- response JSON

  /**
   * The registration response (§7.1) — every credential field is base64url or a fixed token, so
   * the template cannot be broken by user-controlled text.
   */
  fun createResponseJson(
    credentialId: ByteArray,
    clientDataJson: String,
    attestationObject: ByteArray,
  ): String {
    val id = b64UrlEncode(credentialId)
    return """{"id":"$id","rawId":"$id","type":"public-key","clientExtensionResults":{}, """ +
      """"authenticatorAttachment":"platform","response":{"clientDataJSON":""" +
      """"${b64UrlEncode(clientDataJson.toByteArray(Charsets.UTF_8))}","attestationObject": """ +
      """"${b64UrlEncode(attestationObject)}","transports":["internal"]}}"""
  }

  /** The assertion response (§6.2); [userHandle] is included only when the RP sent one. */
  fun assertionResponseJson(
    credentialId: ByteArray,
    clientDataJson: String,
    authenticatorData: ByteArray,
    signature: ByteArray,
    userHandle: ByteArray?,
  ): String {
    val id = b64UrlEncode(credentialId)
    val userHandleField = userHandle?.let { ""","userHandle":"${b64UrlEncode(it)}"""" }.orEmpty()
    return """{"id":"$id","rawId":"$id","type":"public-key","clientExtensionResults":{}, """ +
      """"authenticatorAttachment":"platform","response":{"clientDataJSON":""" +
      """"${b64UrlEncode(clientDataJson.toByteArray(Charsets.UTF_8))}","authenticatorData": """ +
      """"${b64UrlEncode(authenticatorData)}","signature":"${b64UrlEncode(signature)}"""" +
      "$userHandleField}}"
  }

  // --------------------------------------------------------------------- json plumbing

  private fun parseObject(requestJson: String): JsonObject =
    try {
      Json.parseToJsonElement(requestJson).jsonObject
    } catch (e: Exception) {
      throw IllegalArgumentException("request is not a JSON object", e)
    }

  private fun JsonObject.string(key: String): String =
    get(key)?.jsonPrimitive?.content ?: throw IllegalArgumentException("missing \"$key\"")

  private fun JsonObject.object_(key: String): JsonObject =
    get(key)?.jsonObject ?: throw IllegalArgumentException("missing \"$key\"")

  private fun JsonObject.array(key: String): List<JsonElement> =
    (get(key) as? JsonArray)?.toList().orEmpty()

  private fun descriptorId(element: JsonElement): ByteArray? =
    try {
      element.jsonObject["id"]?.jsonPrimitive?.content?.let(::b64UrlDecode)
    } catch (_: IllegalArgumentException) {
      null
    }
}
