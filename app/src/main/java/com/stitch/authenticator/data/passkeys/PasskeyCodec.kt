package com.stitch.authenticator.data.passkeys

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of the passkey list, kept separate from [EncryptedPasskeyStorage] so the
 * format can be tested without Android, a Keystore or a filesystem — the same split [com.stitch
 * .authenticator.data.TokenCodec] has for tokens.
 *
 * Decoding never guesses: corrupt data or format drift surfaces as a thrown
 * [kotlinx.serialization.SerializationException] the caller can report, instead of a silently
 * emptied passkey list.
 */
class PasskeyCodec(private val json: Json = Default) {

  private val serializer = ListSerializer(PasskeyEntity.serializer())

  fun encode(passkeys: List<PasskeyEntity>): String = json.encodeToString(serializer, passkeys)

  fun decode(encoded: String): List<PasskeyEntity> = json.decodeFromString(serializer, encoded)

  companion object {
    /**
     * Unknown keys are ignored so a blob written by a newer build stays readable, and fields are
     * written out even when they carry defaults, so the stored format is self-describing.
     */
    val Default: Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
  }
}
