package com.stitch.authenticator.data

/**
 * A [TokenStorage] that is its own file.
 *
 * Shared by the repository and view-model tests, which care about what was *actually* written rather
 * than what was asked for: [snapshot] plays the role of the file on disk, and [loads] / [saves] count
 * the traffic over it.
 */
internal class InMemoryTokenStorage(
  initial: List<Token> = emptyList(),
  private val failLoad: Boolean = false,
  /** Flipped by a test after it has watched one write fail, to exercise the retry path. */
  var failSave: Boolean = false,
) : TokenStorage {

  private var stored: List<Token> = initial

  var loads = 0
    private set

  var saves = 0
    private set

  override suspend fun load(): List<Token> {
    loads++
    if (failLoad) error("Keystore unavailable")
    return stored
  }

  override suspend fun save(tokens: List<Token>) {
    if (failSave) error("Keystore unavailable")
    stored = tokens
    saves++
  }

  fun snapshot(): List<Token> = stored
}
