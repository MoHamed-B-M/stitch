package com.stitch.authenticator.data.passkeys

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for stored passkeys — the DAO surface the feature spec asked for
 * ([insert], [getByRp], [getAll], [deleteByCredentialId]) layered over [PasskeyStorage] exactly
 * the way [com.stitch.authenticator.data.TokenRepository] sits over [com.stitch.authenticator
 * .data.TokenStorage], instead of a Room table: Room would drag an annotation processor into a
 * build that deliberately has none, while the encrypted prefs keep this data at rest either way.
 *
 * Every mutation persists the whole list *before* publishing it, so the credential selector and
 * the settings screen can never offer a passkey that would not survive a restart; the in-memory
 * cache is only an optimisation over [storage], which is why the first write of a session
 * re-reads it rather than overwriting it blind. Mutations are serialized with a [Mutex] because
 * the provider service and the UI run in different threads — an interleaved `load → modify →
 * save` would otherwise drop one of the changes.
 */
class PasskeyRepository(
  private val storage: PasskeyStorage,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val cache = MutableStateFlow<List<PasskeyEntity>>(emptyList())
  private val mutations = Mutex()

  /** False until [storage] has been read once; see [mutate] for why that matters. */
  private var loaded = false

  /** Current passkeys in insertion order. Empty until the first read, be it a read or a write. */
  val passkeys: Flow<List<PasskeyEntity>> = cache.asStateFlow()

  /**
   * Re-reads storage, replacing the cache. Call when the list becomes visible.
   *
   * Takes the same lock as every mutation, so a refresh cannot publish a snapshot taken before a
   * write and drop the credential that write just saved.
   */
  suspend fun refresh() {
    mutations.withLock { reload() }
  }

  /**
   * DAO insert: upsert by [PasskeyEntity.credentialId]. The same credential arriving again (a
   * re-attestation) replaces its record in place; a new credential is appended so display order
   * stays stable.
   */
  suspend fun insert(passkey: PasskeyEntity) {
    mutate { current ->
      if (current.any { it.credentialId == passkey.credentialId }) {
        current.map { if (it.credentialId == passkey.credentialId) passkey else it }
      } else {
        current + passkey
      }
    }
  }

  /**
   * DAO read-by-RP: every stored passkey whose [PasskeyEntity.rpId] matches exactly. Loads
   * storage on first use, so a caller never has to remember to [refresh] first.
   */
  suspend fun getByRp(rpId: String): List<PasskeyEntity> = mutations.withLock {
    if (!loaded) reload()
    cache.value.filter { it.rpId == rpId }
  }

  /** DAO read-all: every stored passkey, in insertion order. Loads storage on first use. */
  suspend fun getAll(): List<PasskeyEntity> = mutations.withLock {
    if (!loaded) reload()
    cache.value
  }

  /** DAO delete: removes exactly the credential with [credentialId]; an unknown id is a no-op. */
  suspend fun deleteByCredentialId(credentialId: String) {
    mutate { current -> current.filterNot { it.credentialId == credentialId } }
  }

  private suspend fun mutate(transform: (List<PasskeyEntity>) -> List<PasskeyEntity>) {
    mutations.withLock {
      // Read-modify-write, never blind-write: the provider service can insert before this process
      // has ever read the list, and that write must not replace everything already on disk.
      if (!loaded) reload()
      val updated = transform(cache.value)
      withContext(ioDispatcher) { storage.save(updated) }
      cache.value = updated
    }
  }

  /** Reads [storage] into the cache. Callers must already hold [mutations]. */
  private suspend fun reload() {
    cache.value = withContext(ioDispatcher) { storage.load() }
    loaded = true
  }
}
