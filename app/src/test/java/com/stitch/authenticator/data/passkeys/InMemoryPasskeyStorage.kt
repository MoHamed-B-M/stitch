package com.stitch.authenticator.data.passkeys

/**
 * A [PasskeyStorage] that is its own file.
 *
 * Shared by the repository tests, which care about what was *actually* written rather than what
 * was asked for: [snapshot] plays the role of the file on disk, and [loads] / [saves] count the
 * traffic over it.
 */
internal class InMemoryPasskeyStorage(
  initial: List<PasskeyEntity> = emptyList(),
) : PasskeyStorage {

  private var stored: List<PasskeyEntity> = initial

  var loads = 0
    private set

  var saves = 0
    private set

  override suspend fun load(): List<PasskeyEntity> {
    loads++
    return stored
  }

  override suspend fun save(passkeys: List<PasskeyEntity>) {
    stored = passkeys
    saves++
  }

  fun snapshot(): List<PasskeyEntity> = stored
}
