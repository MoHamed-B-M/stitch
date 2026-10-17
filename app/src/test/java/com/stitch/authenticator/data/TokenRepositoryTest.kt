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
    val repository = repositoryWith(FakeTokenStorage(listOf(github)))

    assertEquals(emptyList<Token>(), repository.tokens.first())
  }

  @Test
  fun `refresh publishes what storage holds`() = runTest {
    val repository = repositoryWith(FakeTokenStorage(listOf(github, acme)))

    repository.refresh()

    assertEquals(listOf(github, acme), repository.tokens.first())
  }

  @Test
  fun `add persists before it publishes`() = runTest {
    val storage = FakeTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.add(acme)

    assertEquals(listOf(github, acme), repository.tokens.first())
    assertEquals(listOf(github, acme), storage.snapshot())
    assertEquals(1, storage.saves)
  }

  @Test
  fun `delete removes by id and persists`() = runTest {
    val storage = FakeTokenStorage(listOf(github, acme))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.delete(github.id)

    assertEquals(listOf(acme), repository.tokens.first())
    assertEquals(listOf(acme), storage.snapshot())
  }

  @Test
  fun `deleting something that is not there changes nothing`() = runTest {
    val storage = FakeTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.delete("missing-id")

    assertEquals(listOf(github), repository.tokens.first())
    assertEquals(listOf(github), storage.snapshot())
  }

  @Test
  fun `update replaces the token with the same id in place`() = runTest {
    val storage = FakeTokenStorage(listOf(github, acme))
    val repository = repositoryWith(storage)
    repository.refresh()

    val renamed = github.copy(accountName = "octocat-2")
    repository.update(renamed)

    assertEquals(listOf(renamed, acme), repository.tokens.first())
    assertEquals(listOf(renamed, acme), storage.snapshot())
  }

  @Test
  fun `update appends an unknown id rather than losing it`() = runTest {
    val storage = FakeTokenStorage(listOf(github))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.update(acme)

    assertEquals(listOf(github, acme), storage.snapshot())
  }

  @Test
  fun `tokens survive a restart`() = runTest {
    val storage = FakeTokenStorage()
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

  private class FakeTokenStorage(private var stored: List<Token> = emptyList()) : TokenStorage {

    var saves = 0
      private set

    override suspend fun load(): List<Token> = stored

    override suspend fun save(tokens: List<Token>) {
      stored = tokens
      saves++
    }

    fun snapshot(): List<Token> = stored
  }
}
