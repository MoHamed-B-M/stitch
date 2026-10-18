package com.stitch.authenticator.data.passkeys

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PasskeyRepositoryTest {

  private val google = PasskeyEntity(
    credentialId = "cred-google",
    rpId = "google.com",
    username = "jane@example.com",
    userHandle = "dXNlcjE",
    publicKey = "pQECAyY",
    keyAlias = "stitch-passkey-cred-google",
  )

  private val acme = PasskeyEntity(
    credentialId = "cred-acme",
    rpId = "acme.test",
    username = "jane",
    userHandle = "YWJjZA",
    publicKey = "pQECAyYihQ",
    keyAlias = "stitch-passkey-cred-acme",
  )

  @Test
  fun `starts empty so the list never flickers`() = runTest {
    val repository = repositoryWith(InMemoryPasskeyStorage(listOf(google)))

    assertEquals(emptyList<PasskeyEntity>(), repository.passkeys.first())
  }

  @Test
  fun `refresh publishes what storage holds`() = runTest {
    val repository = repositoryWith(InMemoryPasskeyStorage(listOf(google, acme)))

    repository.refresh()

    assertEquals(listOf(google, acme), repository.passkeys.first())
  }

  @Test
  fun `insert persists before it publishes`() = runTest {
    val storage = InMemoryPasskeyStorage(listOf(google))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.insert(acme)

    assertEquals(listOf(google, acme), repository.getAll())
    assertEquals(listOf(google, acme), storage.snapshot())
    assertEquals(1, storage.saves)
  }

  @Test
  fun `insert with a known credential id replaces instead of duplicating`() = runTest {
    // Re-attesting the same credential must not create a second row the selector would offer
    // twice — and the replaced record must reach disk, not just the cache.
    val storage = InMemoryPasskeyStorage(listOf(google))
    val repository = repositoryWith(storage)
    repository.refresh()
    val renewed = google.copy(username = "jane.new@example.com")

    repository.insert(renewed)

    assertEquals(listOf(renewed), repository.getAll())
    assertEquals(listOf(renewed), storage.snapshot())
  }

  @Test
  fun `getByRp returns only that relying party's passkeys`() = runTest {
    val repository = repositoryWith(InMemoryPasskeyStorage(listOf(google, acme)))

    assertEquals(listOf(google), repository.getByRp("google.com"))
    assertEquals(listOf(acme), repository.getByRp("acme.test"))
  }

  @Test
  fun `getByRp for an unknown relying party returns nothing`() = runTest {
    val repository = repositoryWith(InMemoryPasskeyStorage(listOf(google)))

    assertEquals(emptyList<PasskeyEntity>(), repository.getByRp("example.org"))
  }

  @Test
  fun `reading loads storage without an explicit refresh`() = runTest {
    val storage = InMemoryPasskeyStorage(listOf(google))
    val repository = repositoryWith(storage)

    val found = repository.getByRp("google.com")

    assertEquals(listOf(google), found)
    assertEquals(1, storage.loads)
  }

  @Test
  fun `delete removes by credential id and persists`() = runTest {
    val storage = InMemoryPasskeyStorage(listOf(google, acme))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.deleteByCredentialId(google.credentialId)

    assertEquals(listOf(acme), repository.getAll())
    assertEquals(listOf(acme), storage.snapshot())
  }

  @Test
  fun `deleting something that is not there changes nothing`() = runTest {
    val storage = InMemoryPasskeyStorage(listOf(google))
    val repository = repositoryWith(storage)
    repository.refresh()

    repository.deleteByCredentialId("missing-id")

    assertEquals(listOf(google), repository.getAll())
    assertEquals(listOf(google), storage.snapshot())
  }

  @Test
  fun `a write before the first read keeps what storage already held`() = runTest {
    // The provider service can be asked to insert before anything in this process has read the
    // list. Without reading first, that write would persist one credential as the whole file and
    // quietly delete every passkey the user already had.
    val storage = InMemoryPasskeyStorage(listOf(google))
    val repository = repositoryWith(storage)

    repository.insert(acme)

    assertEquals(listOf(google, acme), storage.snapshot())
    assertEquals(listOf(google, acme), repository.getAll())
    assertEquals(1, storage.loads)
    assertEquals(1, storage.saves)
  }

  @Test
  fun `passkeys survive a restart`() = runTest {
    val storage = InMemoryPasskeyStorage()
    val first = repositoryWith(storage)
    first.insert(google)
    first.insert(acme)

    val afterRestart = repositoryWith(storage)
    afterRestart.refresh()

    assertEquals(listOf(google, acme), afterRestart.getAll())
  }

  private fun TestScope.repositoryWith(storage: PasskeyStorage) =
    PasskeyRepository(storage, UnconfinedTestDispatcher(testScheduler))
}
