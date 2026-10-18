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

/** Settings screen. */
@Serializable data object Settings : NavKey
