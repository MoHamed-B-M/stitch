package com.stitch.authenticator.data.passkeys

/**
 * Persistence seam for the passkey list: deliberately tiny and suspension-based, mirroring
 * [com.stitch.authenticator.data.TokenStorage].
 *
 * The production implementation talks to encrypted storage on a background dispatcher, unit
 * tests substitute an in-memory fake, and swapping the encryption strategy stays a one-file
 * change. Room was deliberately *not* introduced for this table: it would add an annotation
 * processor to a build that has none, while [EncryptedPasskeyStorage] already keeps the data at
 * rest encrypted under the same Keystore master key as the tokens.
 */
interface PasskeyStorage {

  suspend fun load(): List<PasskeyEntity>

  suspend fun save(passkeys: List<PasskeyEntity>)
}
