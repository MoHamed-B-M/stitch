package com.stitch.authenticator.data

import com.stitch.authenticator.crypto.Base32
import com.stitch.authenticator.crypto.TotpEngine
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * The HMAC hash behind a code. `otpauth://` spells these `SHA1`, `SHA256` and `SHA512`; the enum
 * names are kept identical to the URI values so parsing stays a plain `valueOf`-style lookup.
 */
@Serializable
enum class OtpAlgorithm(val macName: String) {
  SHA1(TotpEngine.HMAC_SHA1),
  SHA256(TotpEngine.HMAC_SHA256),
  SHA512(TotpEngine.HMAC_SHA512),
  ;

  companion object {
    /** Reads the `algorithm` parameter of an `otpauth://` URI; anything unknown falls back to SHA1. */
    fun fromParameter(value: String?): OtpAlgorithm =
      entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: SHA1
  }
}

/**
 * One stored TOTP account.
 *
 * [secret] stays in its canonical Base32 form: that is what the user typed or scanned, it survives a
 * round trip through storage unchanged, and decoding is deferred until a code is actually needed.
 * Instances are immutable — editing means replacing the token.
 */
@Serializable
data class Token(
  val id: String = UUID.randomUUID().toString(),
  val issuer: String,
  val accountName: String,
  val secret: String,
  val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
  val digits: Int = TotpEngine.DEFAULT_DIGITS,
  val period: Int = TotpEngine.DEFAULT_PERIOD,
) {

  /** Primary line of a list row; falls back to the account name when the issuer is unknown. */
  val title: String
    get() = issuer.ifBlank { accountName }

  /** Secondary line, empty when it would only repeat [title]. */
  val subtitle: String
    get() = accountName.takeIf { issuer.isNotBlank() && it.isNotBlank() }.orEmpty()

  /** True when this token can actually produce codes: decodable secret, supported parameters. */
  val isValid: Boolean
    get() = secret.isNotBlank() &&
      digits in TotpEngine.MIN_DIGITS..TotpEngine.MAX_DIGITS &&
      period > 0 &&
      Base32.isValid(secret)
}

/** Raw key material for the token's Base32 [Token.secret]. */
fun Token.secretBytes(): ByteArray = Base32.decode(secret)

/** The code displayed for this token [at] the given instant (default: the engine's clock). */
fun Token.code(engine: TotpEngine, at: Instant = engine.now()): String =
  engine.totp(secretBytes(), period, digits, algorithm.macName, at)
