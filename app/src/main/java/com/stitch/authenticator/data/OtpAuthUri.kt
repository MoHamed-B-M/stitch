package com.stitch.authenticator.data

import com.stitch.authenticator.crypto.Base32
import com.stitch.authenticator.crypto.TotpEngine
import java.net.URLDecoder

/**
 * The `otpauth://totp/...` links an authenticator exchanges when an account is set up.
 *
 * Parsed by hand instead of with `java.net.URI`, because these strings arrive by paste and by scan
 * from every generator in the wild: `java.net.URI` rejects the line breaks, bare spaces and missing
 * percent-encoding those links carry. Parsing is therefore tolerant about *shape* and strict about
 * *values*. Anything that would change the codes an account produces — an unsupported hash, a digit
 * count the app cannot show, a period that is not a number of seconds — is rejected with a reason
 * the user can act on rather than quietly replaced with a default: a token that generates codes
 * which never validate is the most confusing failure an authenticator can have.
 */
object OtpAuthUri {

  private const val Scheme = "otpauth://"
  private const val Totp = "totp"

  /**
   * The digit counts this app can both edit and show: the manual form's own two options.
   *
   * Wider than what [Token.isValid] accepts on purpose — a token already in storage may carry 7 and
   * still produce correct codes — but a link naming a count the form cannot render would arrive as
   * a row that contradicts its own settings, so it is refused here instead.
   */
  private val SupportedDigits = setOf(TotpEngine.MIN_DIGITS, TotpEngine.MAX_DIGITS)

  /** Everything a link needs to become a [Token]. */
  data class Payload(
    val issuer: String,
    val accountName: String,
    val secret: String,
    val algorithm: OtpAlgorithm,
    val digits: Int,
    val period: Int,
  ) {
    /** Mirrors [Token.title]: the issuer names the row, the account name is the fallback. */
    val title: String get() = issuer.ifBlank { accountName }

    /** Mirrors [Token.subtitle]: empty when it would only repeat [title]. */
    val subtitle: String
      get() =
        accountName.takeIf { issuer.isNotBlank() && it.isNotBlank() && it != issuer }.orEmpty()

    /** True when the link asked for the shapes most accounts use, so nothing has to be expanded. */
    val isDefaultShape: Boolean
      get() =
        algorithm == OtpAlgorithm.SHA1 &&
          digits == TotpEngine.DEFAULT_DIGITS &&
          period == TotpEngine.DEFAULT_PERIOD
  }

  /** Why a link could not be used, phrased for the person who pasted it. */
  enum class Error(val message: String) {
    NotALink("This is not an otpauth:// link"),
    NotTimeBased("Only time-based (TOTP) links can be added"),
    MissingKey("The link does not include a setup key"),
    InvalidKey("The setup key in this link is not valid Base32"),
    UnsupportedAlgorithm("The link asks for a hash this app cannot compute"),
    UnsupportedDigits("The link asks for a digit count this app cannot show"),
    UnsupportedPeriod("The link's period is not a number of seconds"),
    MissingName("The link does not name the account"),
  }

  /** The outcome of reading a pasted link: usable as [Parsed] or rejected with a reason. */
  sealed interface Outcome {
    data class Parsed(val payload: Payload) : Outcome
    data class Rejected(val error: Error) : Outcome
  }

  /**
   * Reads [raw] into a [Payload], tolerating the whitespace and case noise pasted links carry.
   *
   * Checks run from most fundamental outward — scheme, then type, then key, then parameters — so the
   * message the user sees points at the first thing that is actually wrong.
   */
  fun parse(raw: String): Outcome {
    // Wrapping in an email or a code block is what pasted links look like; a link never needs them.
    val link = raw.filterNot { it == '\n' || it == '\r' || it == '\t' }.trim()
    if (!link.startsWith(Scheme, ignoreCase = true)) return Outcome.Rejected(Error.NotALink)

    val afterScheme = link.substring(Scheme.length)
    val fragment = afterScheme.indexOf('#')
    val withoutFragment = if (fragment >= 0) afterScheme.substring(0, fragment) else afterScheme
    val queryStart = withoutFragment.indexOf('?')
    val path = if (queryStart >= 0) withoutFragment.substring(0, queryStart) else withoutFragment
    val query = if (queryStart >= 0) withoutFragment.substring(queryStart + 1) else ""

    val separator = path.indexOf('/')
    val type = (if (separator >= 0) path.substring(0, separator) else path).trim()
    if (!type.equals(Totp, ignoreCase = true)) return Outcome.Rejected(Error.NotTimeBased)

    val label = decode(if (separator >= 0) path.substring(separator + 1) else "").trim()
    val parameters = parseQuery(query)

    val secret = parameters["secret"].orEmpty().trim()
    if (secret.isEmpty()) return Outcome.Rejected(Error.MissingKey)
    if (!Base32.isValid(secret)) return Outcome.Rejected(Error.InvalidKey)

    val algorithm = OtpAlgorithm.fromParameter(parameters["algorithm"])
      ?: return Outcome.Rejected(Error.UnsupportedAlgorithm)

    val digits = readNumber(parameters["digits"], TotpEngine.DEFAULT_DIGITS)
      ?: return Outcome.Rejected(Error.UnsupportedDigits)
    if (digits !in SupportedDigits) return Outcome.Rejected(Error.UnsupportedDigits)

    val period = readNumber(parameters["period"], TotpEngine.DEFAULT_PERIOD)
      ?: return Outcome.Rejected(Error.UnsupportedPeriod)
    if (period <= 0) return Outcome.Rejected(Error.UnsupportedPeriod)

    // `otpauth` labels the account `Issuer:Account`; the colon is optional and the issuer is
    // often only in the `issuer` parameter, so the two sources are reconciled rather than assumed.
    val colon = label.indexOf(':')
    val labelIssuer = if (colon >= 0) label.substring(0, colon).trim() else ""
    val labelAccount = if (colon >= 0) label.substring(colon + 1).trim() else label
    val issuer = parameters["issuer"].orEmpty().trim().ifEmpty { labelIssuer }

    if (issuer.isBlank() && labelAccount.isBlank()) return Outcome.Rejected(Error.MissingName)

    return Outcome.Parsed(
      Payload(
        // A label that only repeats the issuer would render as "GitHub / GitHub"; dropping the
        // duplicate lets the row show the name once.
        issuer = if (issuer.equals(labelAccount, ignoreCase = true)) "" else issuer,
        accountName = labelAccount,
        secret = secret,
        algorithm = algorithm,
        digits = digits,
        period = period,
      )
    )
  }

  /**
   * A parameter as an [Int]: [default] when the parameter is absent, and `null` — never a default —
   * when it is present but not a number, because silently reinterpreting `period=abc` as `30`
   * seconds would produce codes that cannot validate.
   */
  private fun readNumber(value: String?, default: Int): Int? =
    if (value == null) default else value.trim().toIntOrNull()

  /**
   * The link's parameters, keyed in lower case.
   *
   * A repeated parameter keeps its first value: duplication means a broken generator, and the first
   * value is the one every other authenticator reads.
   */
  private fun parseQuery(query: String): Map<String, String> {
    val parameters = mutableMapOf<String, String>()
    query.split('&').forEach { pair ->
      if (pair.isEmpty()) return@forEach
      val equals = pair.indexOf('=')
      val key = (if (equals >= 0) pair.substring(0, equals) else pair).trim().lowercase()
      val value = if (equals >= 0) decode(pair.substring(equals + 1)) else ""
      // First one wins: a duplicated parameter means a broken generator, and every other
      // authenticator reads the first value it finds.
      if (key !in parameters) parameters[key] = value
    }
    return parameters
  }

  /**
   * Percent-decodes both label and query values, keeping a plus a plus.
   *
   * [URLDecoder] implements *form* decoding, where `+` means space — fine for a query, wrong for a
   * label: `jane+tag@example.com` is a real account name that would arrive as "jane tag" and stop
   * matching the account it belongs to. Escaping the plus first makes both halves percent-only, and
   * anything still malformed is kept raw rather than rejected.
   */
  private fun decode(value: String): String =
    runCatching { URLDecoder.decode(value.replace("+", "%2B"), Charsets.UTF_8.name()) }
      .getOrDefault(value)
}
