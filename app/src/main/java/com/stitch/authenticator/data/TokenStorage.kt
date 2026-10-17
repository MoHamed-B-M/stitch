package com.stitch.authenticator.data

/**
 * Persistence seam for the token list.
 *
 * Deliberately tiny and suspension-based: the production implementation talks to encrypted storage
 * on a background dispatcher, unit tests substitute an in-memory fake, and swapping the encryption
 * strategy stays a one-file change.
 */
interface TokenStorage {

  suspend fun load(): List<Token>

  suspend fun save(tokens: List<Token>)
}
