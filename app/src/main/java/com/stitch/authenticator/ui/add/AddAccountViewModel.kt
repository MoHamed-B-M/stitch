package com.stitch.authenticator.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.crypto.Base32
import com.stitch.authenticator.crypto.TotpEngine
import com.stitch.authenticator.data.OtpAlgorithm
import com.stitch.authenticator.data.OtpAuthUri
import com.stitch.authenticator.data.Token
import com.stitch.authenticator.data.TokenRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Longest period accepted from a link or the form; anything slower than this is a typo, not a plan. */
private const val MaxPeriodSeconds = 3_600

/** Which input surface the form is showing. */
enum class AddAccountMode { Manual, Link }

/**
 * Everything the add screen renders.
 *
 * Held as one snapshot because the parts have to agree: the draft, the errors describing it and the
 * save state are read together on every recomposition, and a `StateFlow` of one data class cannot
 * hand two of them to a screen while withholding the third.
 */
data class AddAccountUiState(
  val mode: AddAccountMode = AddAccountMode.Manual,
  val link: String = "",
  /** What [link] parsed into; `null` while it reads as anything but an account. */
  val linkPayload: OtpAuthUri.Payload? = null,
  val linkError: OtpAuthUri.Error? = null,
  /** Whether [linkError] should be on screen. */
  val showLinkError: Boolean = false,
  // The draft, filled by the fields in Manual mode and by a pasted link in Link mode. One draft for
  // both is what makes "paste a link" the same act as "fill in this form", rather than a second
  // source of truth that would have to be kept in step with the first.
  val issuer: String = "",
  val accountName: String = "",
  val secret: String = "",
  val algorithm: OtpAlgorithm = OtpAlgorithm.SHA1,
  val digits: Int = TotpEngine.DEFAULT_DIGITS,
  val period: String = TotpEngine.DEFAULT_PERIOD.toString(),
  val advancedExpanded: Boolean = false,
  // Field errors, derived from the draft on every change instead of stored while the user types, so
  // an error can never go on describing a value that has since been corrected.
  val accountError: String? = null,
  val secretError: String? = null,
  val periodError: String? = null,
  val saving: Boolean = false,
  val saved: Boolean = false,
  val saveError: String? = null,
)

/**
 * State holder for adding one account.
 *
 * Two ways in — typing the values, or pasting an `otpauth://` link — and exactly one draft behind
 * them: a pasted link [writes into the fields][onLinkChange], so the user can switch to the manual
 * view and inspect or correct what the link contained instead of having to trust a preview of it.
 *
 * Nothing here touches Android: [repository] arrives from the composition, which keeps the save flow
 * and the validation unit-testable on the JVM.
 */
class AddAccountViewModel(
  private val repository: TokenRepository,
) : ViewModel() {

  private val state = MutableStateFlow(AddAccountUiState())

  val uiState: StateFlow<AddAccountUiState> = state.asStateFlow()

  /**
   * Set on the first [save]. Validation runs on every keystroke either way; this decides whether its
   * results are *shown*, so nobody opens the screen and is immediately told what they got wrong.
   */
  private var attemptedSave = false

  fun onModeChange(value: AddAccountMode) = setState { copy(mode = value) }

  fun onIssuerChange(value: String) = setState { copy(issuer = value) }

  fun onAccountNameChange(value: String) = setState { copy(accountName = value) }

  fun onSecretChange(value: String) = setState { copy(secret = value) }

  fun onAlgorithmChange(value: OtpAlgorithm) = setState { copy(algorithm = value) }

  fun onPeriodChange(value: String) = setState { copy(period = value) }

  fun onAdvancedToggle() = setState { copy(advancedExpanded = !advancedExpanded) }

  /**
   * Offers only the digit counts the row can show — 6 (the default) and 8, which are also the ends
   * of the range [TotpEngine] supports. Anything else cannot arrive from the form, and [OtpAuthUri]
   * refuses it from a link, because a count the UI cannot render would become a permanent mystery.
   */
  fun onDigitsChange(value: Int) {
    if (value == TotpEngine.MIN_DIGITS || value == TotpEngine.MAX_DIGITS) {
      setState { copy(digits = value) }
    }
  }

  /**
   * Reads the pasted link and, when it is a usable account, writes it into the draft.
   *
   * Rejected input leaves the draft alone: half a link must not blank out what was already typed.
   * It also clears [AddAccountUiState.linkPayload], so an account parsed from a *previous* version
   * of the text can never be saved while the box now shows something else.
   */
  fun onLinkChange(value: String) {
    val parsed = OtpAuthUri.parse(value)

    setState {
      val filled =
        when (parsed) {
          is OtpAuthUri.Outcome.Parsed ->
            copy(
              issuer = parsed.payload.issuer,
              accountName = parsed.payload.accountName,
              secret = parsed.payload.secret,
              algorithm = parsed.payload.algorithm,
              digits = parsed.payload.digits,
              period = parsed.payload.period.toString(),
              // Anything the link specified is worth showing rather than hiding behind a disclosure.
              advancedExpanded = advancedExpanded || !parsed.payload.isDefaultShape,
            )
          is OtpAuthUri.Outcome.Rejected -> this
        }
      val rejected = parsed as? OtpAuthUri.Outcome.Rejected

      filled.copy(
        link = value,
        linkPayload = parsed as? OtpAuthUri.Outcome.Parsed,
        // Nothing to complain about while the box is empty: the message waits until the user has
        // written something that claims to be a link, or has tried to save it.
        linkError = if (value.isBlank() || rejected == null) null else rejected.error,
      )
    }
  }

  /**
   * Adds the account and, once storage confirms it, leaves the screen.
   *
   * Deliberately not optimistic: the list is a projection of storage, so a row shown before its write
   * landed would be a row that could still disappear. Waiting costs the length of one encrypted file
   * write, and it puts failures on this screen — the one place that can show them and retry them.
   */
  fun save() {
    if (state.value.saving || state.value.saved) return
    attemptedSave = true
    setState { copy(saveError = null) }

    val token =
      when (state.value.mode) {
        AddAccountMode.Link -> state.value.linkPayload?.asToken()
        AddAccountMode.Manual -> state.value.manualToken()
      }
    if (token == null) return

    setState { copy(saving = true) }

    viewModelScope.launch {
      runCatching { repository.add(token) }
        .onSuccess { setState { copy(saving = false, saved = true) } }
        .onFailure { failure ->
          setState {
            copy(
              saving = false,
              saveError = failure.message?.takeIf { it.isNotBlank() } ?: "Storage did not respond.",
            )
          }
        }
    }
  }

  /**
   * The token the manual draft describes, or `null` when it is not a complete account.
   *
   * The checks [AddAccountUiState.accountProblem], [AddAccountUiState.secretProblem] and
   * [AddAccountUiState.periodProblem] report on the fields run here as well, so what the screen
   * labels an error and what the save refuses can never disagree.
   */
  private fun AddAccountUiState.manualToken(): Token? {
    if (accountProblem() != null || secretProblem() != null || periodProblem() != null) return null

    return Token(
      issuer = issuer.trim(),
      accountName = accountName.trim(),
      secret = canonicalSecret(secret),
      algorithm = algorithm,
      digits = digits,
      period = period.trim().toIntOrNull() ?: return null,
    )
  }

  /** The account a parsed link carries, canonicalised exactly like the manual path. */
  private fun OtpAuthUri.Payload.asToken(): Token =
    Token(
      issuer = issuer,
      accountName = accountName,
      secret = canonicalSecret(secret),
      algorithm = algorithm,
      digits = digits,
      period = period,
    )

  /** Applies [change] to the draft and re-derives every field error from what it leaves behind. */
  private fun setState(change: AddAccountUiState.() -> AddAccountUiState) {
    state.update { current -> current.change().validated() }
  }

  private fun AddAccountUiState.validated(): AddAccountUiState {
    val manual = attemptedSave && mode == AddAccountMode.Manual
    return copy(
      accountError = if (manual) accountProblem() else null,
      secretError = if (manual) secretProblem() else null,
      periodError = if (manual) periodProblem() else null,
      showLinkError = linkError != null && (attemptedSave || looksLikeALink(link)),
    )
  }

  private fun AddAccountUiState.accountProblem(): String? =
    if (issuer.isBlank() && accountName.isBlank()) "Enter an account name or service" else null

  private fun AddAccountUiState.secretProblem(): String? =
    when {
      secret.isBlank() -> "Enter the setup key"
      !Base32.isValid(secret) -> "That is not a valid Base32 key"
      else -> null
    }

  private fun AddAccountUiState.periodProblem(): String? {
    val seconds = period.trim().toIntOrNull() ?: return "Enter a period in seconds"
    return if (seconds in 1..MaxPeriodSeconds) null
    else "Enter a period from 1 to $MaxPeriodSeconds seconds"
  }
}

/** An input this far along claims to be a link, even if it does not parse as one. */
private fun looksLikeALink(link: String): Boolean =
  link.trim().startsWith("otpauth", ignoreCase = true)

/**
 * One spelling of the key, for both entry paths: secrets arrive lowercase, spaced, hyphenated and
 * unpadded, and [Base32] forgives all of that. Round-tripping through it stores the canonical form
 * without touching a single byte the codes are computed from, so what is written and what was typed
 * are provably the same key.
 *
 * Only called once the key has passed validation, which is what makes the decode total.
 */
private fun canonicalSecret(secret: String): String =
  Base32.encode(Base32.decode(secret.trim()))
