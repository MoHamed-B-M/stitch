package com.stitch.authenticator.ui.passwords

import com.stitch.authenticator.data.vault.PasswordEntry
import com.stitch.authenticator.data.vault.VaultStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The tab's gate is this state machine, not the biometric prompt itself: these tests pin what
 * the screen must be able to uphold — the vault is never queried before
 * [PasswordsViewModel.unlock], a failed unlock leaves the storage closed, and the query both
 * reaches the storage and comes back out in the state the list renders.
 *
 * The fake's [FakeVault.passwords] refuses to answer while locked, so a gate violation would
 * not go unnoticed: it would surface as [PasswordsUiState.Error] where a listing was expected.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PasswordsViewModelTest {

  private val entry =
    PasswordEntry(
      id = 1,
      title = "GitHub",
      username = "octocat",
      password = "hunter2",
      createdAt = 0L,
    )

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `starts locked and never touches the vault`() =
    runTest {
      val vault = FakeVault()
      val viewModel = PasswordsViewModel(vault)

      assertEquals(PasswordsUiState.Locked, viewModel.uiState.first())
      assertFalse(vault.unlocked)
      assertEquals(emptyList<String>(), vault.queries)
    }

  @Test
  fun `a successful unlock flows from locked to the listing`() =
    runTest {
      val vault = FakeVault(entries = listOf(entry))
      val viewModel = PasswordsViewModel(vault)

      viewModel.unlock()
      runCurrent()

      assertTrue(vault.unlocked)
      val ready = viewModel.uiState.first { it is PasswordsUiState.Ready } as PasswordsUiState.Ready
      assertEquals(listOf(entry), ready.entries)
      assertEquals("", ready.query)
    }

  @Test
  fun `a failed unlock lands in error with the vault still closed`() =
    runTest {
      val vault = FakeVault(failuresRemaining = 1)
      val viewModel = PasswordsViewModel(vault)

      viewModel.unlock()
      runCurrent()

      assertFalse(vault.unlocked)
      assertTrue(viewModel.uiState.first() is PasswordsUiState.Error)
    }

  @Test
  fun `unlocking again after a failure recovers`() =
    runTest {
      val vault = FakeVault(entries = listOf(entry), failuresRemaining = 1)
      val viewModel = PasswordsViewModel(vault)

      viewModel.unlock()
      runCurrent()
      assertTrue(viewModel.uiState.first() is PasswordsUiState.Error)

      viewModel.unlock()
      runCurrent()
      assertTrue(viewModel.uiState.first { it is PasswordsUiState.Ready } is PasswordsUiState.Ready)
    }

  @Test
  fun `the query reaches the vault and comes back in the state`() =
    runTest {
      val vault = FakeVault(entries = listOf(entry))
      val viewModel = PasswordsViewModel(vault)

      viewModel.unlock()
      runCurrent()
      viewModel.setQuery("hub")
      runCurrent()

      val ready = viewModel.uiState.first { it is PasswordsUiState.Ready } as PasswordsUiState.Ready
      assertEquals("hub", ready.query)
      assertEquals(listOf(entry), ready.entries)
      assertEquals(listOf("", "hub"), vault.queries)
    }

  /** In-memory [VaultStorage]: filters like the DAO's LIKE and counts every query it answers. */
  private class FakeVault(
    private val entries: List<PasswordEntry> = emptyList(),
    var failuresRemaining: Int = 0,
  ) : VaultStorage {

    var unlocked = false
      private set

    val queries = mutableListOf<String>()

    override suspend fun unlock() {
      if (failuresRemaining > 0) {
        failuresRemaining--
        error("database failed to open")
      }
      unlocked = true
    }

    override fun passwords(query: String): Flow<List<PasswordEntry>> {
      // The gate, enforced at the seam: a query before unlock is a bug the tests want to hear
      // about, not something to paper over with an empty list.
      check(unlocked) { "the vault was queried before unlock()" }
      queries += query
      return MutableStateFlow(entries).map { list ->
        list.filter { it.title.contains(query, ignoreCase = true) }
      }
    }

    override suspend fun byId(id: Long): PasswordEntry? = entries.firstOrNull { it.id == id }

    override suspend fun save(entry: PasswordEntry): Long = error("unused in these tests")

    override suspend fun delete(id: Long) = error("unused in these tests")
  }
}
