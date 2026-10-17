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
 * that would not survive a restart; the in-memory cache is only an optimisation over [storage].
 * Mutations are serialized with a [Mutex] because a double tap interleaving two `load → modify →
 * save` cycles would otherwise drop one of the changes.
 */
class TokenRepository(
  private val storage: TokenStorage,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

  private val cache = MutableStateFlow<List<Token>>(emptyList())
  private val mutations = Mutex()

  /** Current tokens in display order. Empty until the first [refresh]. */
  val tokens: Flow<List<Token>> = cache.asStateFlow()

  /** Re-reads storage, replacing the cache. Call when the list becomes visible. */
  suspend fun refresh() {
    val stored = withContext(ioDispatcher) { storage.load() }
    cache.value = stored
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
      val updated = transform(cache.value)
      withContext(ioDispatcher) { storage.save(updated) }
      cache.value = updated
    }
  }
}
