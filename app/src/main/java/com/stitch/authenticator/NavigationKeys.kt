package com.stitch.authenticator

import androidx.navigation3.runtime.NavKey
import com.stitch.authenticator.ui.add.AddAccountMode
import kotlinx.serialization.Serializable

/** The token list. Every path in the app starts here. */
@Serializable data object Main : NavKey

/**
 * Adding one account, pushed by the list's speed dial — and by the scanner, for a scanned code.
 *
 * [mode] picks the initial input surface ([AddAccountMode.Manual] for typed values,
 * [AddAccountMode.Link] for a link) and [initialLink] pre-fills it with the `otpauth://` code the
 * camera read, so a scan lands on the exact same draft and validation pipeline as a paste.
 */
@Serializable
data class AddAccount(
  val mode: AddAccountMode = AddAccountMode.Manual,
  val initialLink: String = "",
) : NavKey

/** The QR scanner: pushed by the speed dial, popped the moment a code is accepted or refused. */
@Serializable data object ScanQr : NavKey

/**
 * The passkey save sheet, pushed by the scanner when it reads a passkey-shaped payload
 * (`FIDO:/` handle, `webauthn://` link, or WebAuthn request JSON) instead of an otpauth code.
 *
 * [payload] is the raw text as scanned — the sheet re-classifies it for the domain guess, so
 * routing and display always agree on one source.
 */
@Serializable data class PasskeySave(val payload: String) : NavKey

/** The full-screen system update page, opened from Settings' "System update" row. */
@Serializable data object SystemUpdate : NavKey

/** Settings screen. */
@Serializable data object Settings : NavKey

/**
 * The password vault tab: one of the two destinations the bottom bar switches between.
 *
 * Entering it *is* the biometric gate — the screen prompts, and the database opens only after a
 * success. A cancelled or failed prompt leaves the tab in its locked state; the vault never
 * opens in the background and never survives the tab being left, so every return re-prompts.
 */
@Serializable data object Passwords : NavKey
