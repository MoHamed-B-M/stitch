package com.stitch.authenticator.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TokenRepositoryTest {

  private val github = Token(
    id = "id-github",
    issuer = "GitHub",
    accountName = "octocat",
    secret = "GEZDGNBVGY3TQOJQ",
  )

  private val acme = Token(
    id = "id-acme",
    issuer = "ACME",
    accountName = "jane",
    secret = "MZXW6YTB",
  )

  @Test
  fun `starts empty so the list never flickers`() = runTest {
    val repository = repositoryWith(InMemoryTokenStorage(listOf(github)))

    assertEquals(emptyList<Token>(), repository.tokens.first())
  }

  @Test
  fun `refresh publishes what storage holds`() = runTest {
    val repository = repositoryWith(InMemoryTokenStorage(listOf(github, acme)))

    repository.refresh()

    assertEquals(listOf(github, acme), repository.tokens.first())
  }

  @Test
  fun `add persists before it publishes`() = runTest {
    val storage = InMemoryTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.add(acme)

    assertEquals(listOf(github, acme), repository.tokens.first())
    assertEquals(listOf(github, acme), storage.snapshot())
    assertEquals(1, storage.saves)
  }

  @Test
  fun `delete removes by id and persists`() = runTest {
    val storage = InMemoryTokenStorage(listOf(github, acme))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.delete(github.id)

    assertEquals(listOf(acme), repository.tokens.first())
    assertEquals(listOf(acme), storage.snapshot())
  }

  @Test
  fun `deleting something that is not there changes nothing`() = runTest {
    val storage = InMemoryTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.delete("missing-id")

    assertEquals(listOf(github), repository.tokens.first())
    assertEquals(listOf(github), storage.snapshot())
  }

  @Test
  fun `update replaces the token with the same id in place`() = runTest {
    val storage = InMemoryTokenStorage(listOf(github, acme))
    val repository = repositoryWith(storage)
    repository.refresh()

    val renamed = github.copy(accountName = "octocat-2")
    repository.update(renamed)

    assertEquals(listOf(renamed, acme), repository.tokens.first())
    assertEquals(listOf(renamed, acme), storage.snapshot())
  }

  @Test
  fun `update appends an unknown id rather than losing it`() = runTest {
    val storage = InMemoryTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.update(acme)

    assertEquals(listOf(github, acme), storage.snapshot())
  }

  @Test
  fun `a write before the first read keeps what storage already held`() = runTest {
    // The add screen can save before anything has been read — it is the first screen in the app that
    // can. Without reading first, that write would persist one token as the whole file and quietly
    // delete every account the user already had.
    val storage = InMemoryTokenStorage(listOf(github))
    val repository = repositoryWith(storage)

    repository.add(acme)

    assertEquals(listOf(github, acme), storage.snapshot())
    assertEquals(listOf(github, acme), repository.tokens.first())
    assertEquals(1, storage.loads)
    assertEquals(1, storage.saves)
  }

  @Test
  fun `refresh during a write never publishes a stale snapshot`() = runTest {
    // A refresh landing while a save is in flight must not replace the token that save just wrote.
    val storage = InMemoryTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.add(acme)
    repository.refresh()

    assertEquals(listOf(github, acme), repository.tokens.first())
    assertEquals(listOf(github, acme), storage.snapshot())
  }

  @Test
  fun `tokens survive a restart`() = runTest {
    val storage = InMemoryTokenStorage()
    val first = repositoryWith(storage)
    first.refresh()
    first.add(github)
    first.add(acme)

    val afterRestart = repositoryWith(storage)
    afterRestart.refresh()

    assertEquals(listOf(github, acme), afterRestart.tokens.first())
  }

  private fun TestScope.repositoryWith(storage: TokenStorage) =
    TokenRepository(storage, UnconfinedTestDispatcher(testScheduler))
}
