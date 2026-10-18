package com.stitch.authenticator.data.passkeys

import kotlinx.serialization.Serializable

/**
 * One saved passkey, in the shape the system's credential selector needs to offer it back.
 *
 * Everything here is public material or identifiers: [publicKey] is the COSE-encoded EC P-256 key
 * the relying party already knows, [credentialId] and [userHandle] are the WebAuthn handles, and
 * [keyAlias] *points at* the private key — that key itself lives only inside the Android Keystore
 * and never appears in this record, on disk or in memory.
 *
 * [credentialId] is the identity: inserting the same id again replaces the record, because
 * re-attesting the same credential must not produce a second row.
 */
@Serializable
data class PasskeyEntity(
  val credentialId: String,
  /** Relying party id, e.g. "google.com" — the scope a get request is matched against. */
  val rpId: String,
  val username: String,
  val userHandle: String,
  /** COSE public key bytes, Base64-encoded for storage. */
  val publicKey: String,
  /** Android Keystore alias holding the private half of the key pair. */
  val keyAlias: String,
)
