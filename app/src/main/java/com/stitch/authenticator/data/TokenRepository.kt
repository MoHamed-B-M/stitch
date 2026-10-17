package com.stitch.authenticator.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Single source of truth for the stored tokens.
 *
 * Every mutation persists the whole list *before* publishing it, so the UI can never show a token
 * that would not survive a restart; the in-memory cache is only an optimisation over [storage],
 * which is why the first write of a session re-reads it rather than overwriting it blind.
 * Mutations are serialized with a [Mutex] because a double tap interleaving two `load → modify →
 * save` cycles would otherwise drop one of the changes.
 */
class TokenRepository(
  private val storage: TokenStorage,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val cache = MutableStateFlow<List<Token>>(emptyList())
  private val mutations = Mutex()

  /** False until [storage] has been read once; see [mutate] for why that matters. */
  private var loaded = false

  /** Current tokens in display order. Empty until the first read, be it a [refresh] or a write. */
  val tokens: Flow<List<Token>> = cache.asStateFlow()

  /**
   * Re-reads storage, replacing the cache. Call when the list becomes visible.
   *
   * Takes the same lock as every mutation, so a refresh cannot publish a snapshot taken before a
   * write and drop the token that write just saved.
   */
  suspend fun refresh() {
    mutations.withLock { reload() }
  }

  suspend fun add(token: Token) {
    mutate { current -> current + token }
  }

  /** Replaces the token carrying the same [Token.id]; an unknown id is appended so nothing is lost. */
  suspend fun update(token: Token) {
    mutate { current ->
      if (current.any { it.id == token.id }) {
        current.map { if (it.id == token.id) token else it }
      } else {
        current + token
      }
    }
  }

  suspend fun delete(id: String) {
    mutate { current -> current.filterNot { it.id == id } }
  }

  private suspend fun mutate(transform: (List<Token>) -> List<Token>) {
    mutations.withLock {
      // Read-modify-write, never blind-write. A caller that never refreshed — the add screen is the
      // first one that can write before anything has been read — would otherwise persist this one
      // token *over* everything already in storage, so the first write of a session reads first.
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
