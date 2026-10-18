package com.stitch.authenticator.passkeys

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Classifying camera payloads for the passkey side of the app.
 *
 * The scanner must not feed a passkey-shaped code into the OTP pipeline ("that QR code isn't an
 * authenticator setup code") — a `FIDO:/` handle, a `webauthn://` link, or a raw WebAuthn
 * request JSON all mean *passkey*, and route to the save sheet instead. [parseOrNull] answers
 * "is this passkey-shaped, and what relying party does it name?" — the rpId is best-effort
 * because the payloads people actually scan vary widely; the save sheet lets the user correct
 * it, so a missing rpId routes with `null` rather than being rejected.
 *
 * Pure and JVM-testable: only strings and kotlinx-serialization, no android.net.Uri.
 */
internal object PasskeyQr {

  /** A passkey-shaped payload: the raw text as scanned, plus the relying party it names. */
  data class PasskeyQrRequest(val rpId: String?, val raw: String)

  private const val FidoPrefix = "fido:/"
  private const val WebauthnPrefix = "webauthn:"

  /** The payload when it is passkey-shaped, or `null` when the scanner should reject it. */
  fun parseOrNull(raw: String): PasskeyQrRequest? {
    val value = raw.trim()
    return when {
      value.startsWith(FidoPrefix, ignoreCase = true) ->
        PasskeyQrRequest(rpFromFido(value), value)
      value.startsWith(WebauthnPrefix, ignoreCase = true) ->
        PasskeyQrRequest(rpFromWebauthn(value), value)
      value.startsWith("{") -> jsonRequest(value)
      else -> null
    }
  }

  /**
   * `FIDO:/<handle>` — the handle has no fixed shape across manufacturers, so the first
   * path/query segment that reads as a hostname (contains a dot) is the best rpId guess.
   */
  private fun rpFromFido(value: String): String? =
    value
      .substringAfter("/") // past "FIDO:"
      .substringAfter("/") // past the "/" that follows the scheme
      .split("/", "?", "#")
      .firstOrNull { "." in it }

  /** `webauthn://host/...` — the host is the relying party; an opaque `webauthn:` has none. */
  private fun rpFromWebauthn(value: String): String? =
    value
      .substringAfter("//", missingDelimiterValue = "")
      .substringBefore("/")
      .substringBefore("?")
      .substringBefore("#")
      .ifEmpty { null }

  /**
   * A raw WebAuthn request JSON. Routes only when it carries a relying party *or* a challenge —
   * otherwise every unrelated JSON code in the world would land on the save sheet.
   */
  private fun jsonRequest(value: String): PasskeyQrRequest? =
    try {
      val root = Json.parseToJsonElement(value).jsonObject
      val rpId =
        (root["rp"] as? JsonObject)?.get("id")?.jsonPrimitive?.content
          ?: root["rpId"]?.jsonPrimitive?.content
      if (rpId != null || "challenge" in root) PasskeyQrRequest(rpId, value) else null
    } catch (_: Exception) {
      null
    }
}
