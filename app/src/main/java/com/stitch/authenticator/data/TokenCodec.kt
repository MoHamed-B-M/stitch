package com.stitch.authenticator.data

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * The on-disk shape of the token list, kept separate from [EncryptedTokenStorage] so the format can
 * be tested without Android, a Keystore or a filesystem.
 *
 * Decoding never guesses: corrupt data or format drift surfaces as a thrown
 * [kotlinx.serialization.SerializationException] the caller can report, instead of a silently
 * emptied authenticator.
 */
class TokenCodec(private val json: Json = Default) {

  private val serializer = ListSerializer(Token.serializer())

  fun encode(tokens: List<Token>): String = json.encodeToString(serializer, tokens)

  fun decode(encoded: String): List<Token> = json.decodeFromString(serializer, encoded)

  companion object {
    /**
     * Unknown keys are ignored so a blob written by a newer build stays readable, and defaults are
     * written out so the stored format is self-describing.
     */
    val Default: Json = Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }
  }
}
