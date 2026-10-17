package com.stitch.authenticator.ui.main

import com.stitch.authenticator.crypto.TotpEngine
import com.stitch.authenticator.data.Token
import com.stitch.authenticator.data.TokenRepository
import com.stitch.authenticator.data.TokenStorage
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
 * The token list is where a TOTP app either works or is quietly wrong, so these tests pin the whole
 * chain — storage, ticker, engine — against the RFC vectors rather than against itself.
 *
 * Time is always injected: the engine sits on a fixed 1970 clock and every instant arrives through
 * the ticker, which proves the screen renders *the ticker's* second. If the engine's own clock were
 * ever read instead, these expectations would be decades off.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

  /** RFC 4226/6238 shared secret: the ASCII bytes of `12345678901234567890`. */
  private val rfcSecret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

  private val rfc = Token(
    id = "id-rfc",
    issuer = "RFC",
    accountName = "6238",
    secret = rfcSecret,
    digits = 8,
  )

  private val github = Token(
    id = "id-github",
    issuer = "GitHub",
    accountName = "octocat",
    secret = rfcSecret,
  )

  private val acme = Token(
    id = "id-acme",
    issuer = "ACME",
    accountName = "jane",
    secret = "MZXW6YTB",
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
  fun `starts in Loading, never in an empty list`() = runTest {
    val viewModel = viewModelWith(FakeTokenStorage(listOf(github)), ticker = MutableStateFlow(59_000L))

    assertEquals(MainUiState.Loading, viewModel.uiState.value)
  }

  @Test
  fun `does not show the empty state while the first read is still in flight`() = runTest {
    val storage = GatedTokenStorage(listOf(github))
    val viewModel = viewModelWith(storage, ticker = MutableStateFlow(59_000L))

    // Subscribe, then let the chain run as far as it can: the read is held open, so this is exactly
    // the window in which the repository still reports an empty list.
    val collection = launch { viewModel.uiState.collect { } }
    runCurrent()

    assertEquals(1, storage.loads)
    assertEquals(MainUiState.Loading, viewModel.uiState.value)

    storage.release()
    val ready = viewModel.awaitReady()
    assertEquals(listOf(github.id), ready.tokens.map { it.id })

    collection.cancelAndJoin()
  }

  @Test
  fun `renders the RFC code for the ticker's second`() = runTest {
    val viewModel = viewModelWith(FakeTokenStorage(listOf(rfc)), ticker = MutableStateFlow(59_000L))

    val ready = viewModel.awaitReady()

    assertEquals(
      listOf(
        TokenUiModel(
          id = rfc.id,
          title = "RFC",
          subtitle = "6238",
          code = "94287082",
          secondsRemaining = 1,
          progress = 1f / 30f,
          isUsable = true,
        )
      ),
      ready.tokens,
    )
  }

  @Test
  fun `rotates the code and restarts the countdown on the next boundary`() = runTest {
    val ticker = MutableStateFlow(59_000L)
    val viewModel = viewModelWith(FakeTokenStorage(listOf(github)), ticker = ticker)

    val firstWindow = viewModel.awaitReady()
    assertEquals("287082", firstWindow.tokens.single().code)
    assertEquals(1, firstWindow.tokens.single().secondsRemaining)

    ticker.value = 60_000L

    val secondWindow = viewModel.awaitReady { ready -> ready.tokens.single().code == "359152" }
    assertEquals("359152", secondWindow.tokens.single().code)
    assertEquals(30, secondWindow.tokens.single().secondsRemaining)
    assertEquals(1f, secondWindow.tokens.single().progress, 0.0001f)
  }

  @Test
  fun `keeps every account in stored order`() = runTest {
    val viewModel =
      viewModelWith(FakeTokenStorage(listOf(github, acme, rfc)), ticker = MutableStateFlow(59_000L))

    val ready = viewModel.awaitReady()

    assertEquals(listOf(github.id, acme.id, rfc.id), ready.tokens.map { it.id })
  }

  @Test
  fun `an empty store is an empty list, not an error`() = runTest {
    val viewModel = viewModelWith(FakeTokenStorage(), ticker = MutableStateFlow(59_000L))

    val ready = viewModel.awaitReady()

    assertTrue(ready.tokens.isEmpty())
  }

  @Test
  fun `titles a token without an issuer by its account name`() = runTest {
    val unnamed = Token(id = "id-unnamed", issuer = "", accountName = "jane@stitch.dev", secret = rfcSecret)
    val viewModel = viewModelWith(FakeTokenStorage(listOf(unnamed)), ticker = MutableStateFlow(59_000L))

    val token = viewModel.awaitReady().tokens.single()

    assertEquals("jane@stitch.dev", token.title)
    assertEquals("", token.subtitle)
  }

  @Test
  fun `flags tokens it cannot use instead of failing the whole list`() = runTest {
    val undecodable =
      Token(id = "id-bad-secret", issuer = "Broken", accountName = "user", secret = "not base32!")
    val zeroPeriod = Token(id = "id-zero-period", issuer = "Frozen", accountName = "user", secret = rfcSecret, period = 0)
    val viewModel =
      viewModelWith(FakeTokenStorage(listOf(undecodable, zeroPeriod, github)), ticker = MutableStateFlow(59_000L))

    val ready = viewModel.awaitReady()

    // The healthy account still works...
    assertEquals("287082", ready.tokens.last().code)
    // ...and the broken ones describe themselves rather than throwing mid-list.
    val broken = ready.tokens.take(2)
    assertFalse(broken.any { it.isUsable })
    assertEquals(listOf("", ""), broken.map { it.code })
    assertEquals(listOf(0, 0), broken.map { it.secondsRemaining })
    assertEquals(listOf(0f, 0f), broken.map { it.progress })
  }

  @Test
  fun `delete removes the account and persists it`() = runTest {
    val storage = FakeTokenStorage(listOf(github, acme))
    val viewModel = viewModelWith(storage, ticker = MutableStateFlow(59_000L))
    viewModel.awaitReady()

    viewModel.delete(github.id)

    val afterDelete = viewModel.awaitReady { ready -> ready.tokens.size == 1 }
    assertEquals(listOf(acme.id), afterDelete.tokens.map { it.id })
    assertEquals(listOf(acme), storage.snapshot())
  }

  @Test
  fun `a failed read becomes an error the user can retry`() = runTest {
    val storage = FailingTokenStorage(listOf(github))
    val viewModel = viewModelWith(storage, ticker = MutableStateFlow(59_000L))

    val error = viewModel.uiState.first { it is MainUiState.Error } as MainUiState.Error
    assertTrue(error.throwable is IllegalStateException)

    storage.failLoad = false
    viewModel.refresh()

    val ready = viewModel.awaitReady()
    assertEquals(listOf(github.id), ready.tokens.map { it.id })
  }

  @Test
  fun `ticker only waits until the next second boundary`() = runTest {
    // 59.25s in: the ring must tick in 750 ms, not a whole second, or it lags the code's rotation.
    val partWay = TotpEngine(Clock.fixed(Instant.ofEpochSecond(59, 250_000_000), ZoneOffset.UTC))
    MainViewModel.tickerFlow(partWay).take(2).toList()
    assertEquals(750L, testScheduler.currentTime)

    // Exactly on a boundary it waits the full second instead of firing twice in a row.
    val exact = TotpEngine(Clock.fixed(Instant.ofEpochSecond(60), ZoneOffset.UTC))
    val before = testScheduler.currentTime
    MainViewModel.tickerFlow(exact).take(2).toList()
    assertEquals(1_000L, testScheduler.currentTime - before)
  }

  private fun TestScope.viewModelWith(storage: TokenStorage, ticker: Flow<Long>): MainViewModel =
    MainViewModel(
      repository = TokenRepository(storage, UnconfinedTestDispatcher(testScheduler)),
      engine = TotpEngine(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)),
      ticker = ticker,
    )

  /** Waits for the loaded list, optionally for a specific one, and fails loudly on an error state. */
  private suspend fun MainViewModel.awaitReady(
    predicate: (MainUiState.Ready) -> Boolean = { true }
  ): MainUiState.Ready {
    val state = uiState.first { it is MainUiState.Ready && predicate(it) }
    return state as MainUiState.Ready
  }

  private class FakeTokenStorage(private var stored: List<Token> = emptyList()) : TokenStorage {

    override suspend fun load(): List<Token> = stored

    override suspend fun save(tokens: List<Token>) {
      stored = tokens
    }

    fun snapshot(): List<Token> = stored
  }

  private class FailingTokenStorage(private val stored: List<Token>) : TokenStorage {

    var failLoad = true

    override suspend fun load(): List<Token> {
      if (failLoad) throw IllegalStateException("Keystore locked")
      return stored
    }

    override suspend fun save(tokens: List<Token>) = Unit
  }

  /** Holds the first read open so a test can inspect the state before storage answers. */
  private class GatedTokenStorage(private val stored: List<Token>) : TokenStorage {

    private val gate = CompletableDeferred<Unit>()

    var loads = 0
      private set

    fun release() {
      gate.complete(Unit)
    }

    override suspend fun load(): List<Token> {
      loads++
      gate.await()
      return stored
    }

    override suspend fun save(tokens: List<Token>) = Unit
  }
}
