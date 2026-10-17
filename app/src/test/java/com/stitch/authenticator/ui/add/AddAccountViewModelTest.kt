package com.stitch.authenticator.ui.add

import com.stitch.authenticator.data.InMemoryTokenStorage
import com.stitch.authenticator.data.OtpAlgorithm
import com.stitch.authenticator.data.OtpAuthUri
import com.stitch.authenticator.data.TokenRepository
import com.stitch.authenticator.data.TokenStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Everything the add screen decides, decided here.
 *
 * The screen itself only draws the state: what gets written, what gets refused, what an error looks
 * like and when it is allowed to appear are all [AddAccountViewModel] behaviour, so they are pinned
 * against storage rather than against a rendering of themselves.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AddAccountViewModelTest {

  private val key = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a pasted link fills the draft instead of being saved behind the user's back`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)

    viewModel.onLinkChange(
      "otpauth://totp/GitHub:octocat?secret=$key&issuer=GitHub&algorithm=SHA-256&digits=8"
    )
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("GitHub", state.issuer)
    assertEquals("octocat", state.accountName)
    assertEquals(key, state.secret)
    assertEquals(OtpAlgorithm.SHA256, state.algorithm)
    assertEquals(8, state.digits)
    assertNotNull(state.linkPayload)
    assertNull(state.linkError)
    // Pasting is not committing: the user still sees the form and presses the button.
    assertEquals(0, storage.saves)
    assertFalse(state.saved)
  }

  @Test
  fun `a link that changes something non-default opens the settings it changed`() = runTest {
    val viewModel = viewModelWith(InMemoryTokenStorage())

    viewModel.onLinkChange("otpauth://totp/A:b?secret=$key")
    assertFalse(viewModel.uiState.value.advancedExpanded)

    viewModel.onLinkChange("otpauth://totp/A:b?secret=$key&digits=8")
    // Something a person would otherwise have to know to correct, so it is shown rather than filed.
    assertTrue(viewModel.uiState.value.advancedExpanded)
    assertEquals(8, viewModel.uiState.value.digits)
  }

  @Test
  fun `a rejected link leaves the draft alone and drops the payload that was parsed from it`() =
    runTest {
      val viewModel = viewModelWith(InMemoryTokenStorage())
      viewModel.onAccountNameChange("jane")
      viewModel.onSecretChange(key)

      // A zero: Base32 ignores hyphens and case, so this is the shape of an invalid key rather than
      // the obvious junk that would also have been valid.
      viewModel.onLinkChange("otpauth://totp/A:b?secret=n0pe")

      val state = viewModel.uiState.value
      assertEquals("jane", state.accountName)
      assertEquals(key, state.secret)
      assertNull(state.linkPayload)
      assertEquals(OtpAuthUri.Error.InvalidKey, state.linkError)
    }

  @Test
  fun `the link complaint waits until the text claims to be a link`() = runTest {
    val viewModel = viewModelWith(InMemoryTokenStorage())

    viewModel.onLinkChange("just some notes")
    assertFalse(viewModel.uiState.value.showLinkError)

    // From here on the text is claiming to be a link, so saying what is wrong with it is helpful
    // rather than premature.
    viewModel.onLinkChange("otpauth://totp/A:b")
    assertEquals(OtpAuthUri.Error.MissingKey, viewModel.uiState.value.linkError)
    assertTrue(viewModel.uiState.value.showLinkError)
  }

  @Test
  fun `switching back to manual shows exactly what a link put in the draft`() = runTest {
    val viewModel = viewModelWith(InMemoryTokenStorage())
    viewModel.onLinkChange("otpauth://totp/ACME:jane?secret=$key&issuer=ACME&period=45")

    viewModel.onModeChange(AddAccountMode.Manual)

    val state = viewModel.uiState.value
    assertEquals("ACME", state.issuer)
    assertEquals("jane", state.accountName)
    assertEquals("45", state.period)
    // The same state object, so nothing can disagree about what will be saved.
    assertTrue(state.advancedExpanded)
  }

  @Test
  fun `saving stores one account under a canonical key`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)

    viewModel.onIssuerChange("ACME")
    viewModel.onAccountNameChange("jane")
    // Lower case, padded, as it appears on a printout: all of it is the same key.
    viewModel.onSecretChange("jbswy3dpehpk3pxp==")
    viewModel.save()
    advanceUntilIdle()

    val stored = storage.snapshot().single()
    assertEquals("JBSWY3DPEHPK3PXP", stored.secret)
    assertEquals("ACME", stored.issuer)
    assertEquals("jane", stored.accountName)
    assertEquals(30, stored.period)
    assertTrue(stored.isValid)
    assertEquals(1, storage.saves)

    val state = viewModel.uiState.value
    assertTrue(state.saved)
    assertFalse(state.saving)
    assertNull(state.saveError)
  }

  @Test
  fun `an incomplete account is refused with the errors the form would show`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)

    viewModel.save()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals("Enter an account name or service", state.accountError)
    assertEquals("Enter the setup key", state.secretError)
    assertNull(state.periodError)
    assertFalse(state.saved)
    assertFalse(state.saving)
    assertEquals(0, storage.saves)
  }

  @Test
  fun `errors stay hidden until the first save attempt`() = runTest {
    val viewModel = viewModelWith(InMemoryTokenStorage())

    // A person who has not been told they are wrong yet should not be told they are wrong.
    viewModel.onIssuerChange("")
    assertNull(viewModel.uiState.value.accountError)
    assertNull(viewModel.uiState.value.secretError)

    viewModel.save()
    assertNotNull(viewModel.uiState.value.accountError)
  }

  @Test
  fun `a period the form will not accept is refused before storage is touched`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)
    viewModel.onAccountNameChange("jane")
    viewModel.onSecretChange(key)
    viewModel.onPeriodChange("0")

    viewModel.save()
    advanceUntilIdle()

    assertEquals("Enter a period from 1 to 3600 seconds", viewModel.uiState.value.periodError)
    assertEquals(0, storage.saves)
  }

  @Test
  fun `a write that fails stays on the screen and can be retried`() = runTest {
    val storage = InMemoryTokenStorage(failSave = true)
    val viewModel = viewModelWith(storage)
    viewModel.onAccountNameChange("jane")
    viewModel.onSecretChange(key)

    viewModel.save()
    advanceUntilIdle()

    val failed = viewModel.uiState.value
    assertEquals("Keystore unavailable", failed.saveError)
    assertFalse(failed.saved)
    assertFalse(failed.saving)
    assertEquals(0, storage.saves)

    storage.failSave = false
    viewModel.save()
    advanceUntilIdle()

    val retried = viewModel.uiState.value
    assertNull(retried.saveError)
    assertTrue(retried.saved)
    assertEquals(1, storage.saves)
  }

  @Test
  fun `double tapping save writes exactly once`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)
    viewModel.onAccountNameChange("jane")
    viewModel.onSecretChange(key)

    viewModel.save()
    viewModel.save()
    viewModel.save()
    advanceUntilIdle()

    assertEquals(1, storage.saves)
    assertEquals(1, storage.snapshot().size)
  }

  @Test
  fun `link mode with nothing parsed writes nothing`() = runTest {
    val storage = InMemoryTokenStorage()
    val viewModel = viewModelWith(storage)
    // A perfectly good manual draft underneath — but the screen is on Link mode and the link is
    // rubbish, so saving must not quietly fall back to the other surface.
    viewModel.onAccountNameChange("jane")
    viewModel.onSecretChange(key)
    viewModel.onModeChange(AddAccountMode.Link)
    viewModel.onLinkChange("otpauth://totp/A:b")

    viewModel.save()
    advanceUntilIdle()

    assertEquals(0, storage.saves)
    assertEquals(OtpAuthUri.Error.MissingKey, viewModel.uiState.value.linkError)
    assertTrue(viewModel.uiState.value.showLinkError)
  }

  @Test
  fun `the digits row only offers counts the engine can produce`() = runTest {
    val viewModel = viewModelWith(InMemoryTokenStorage())

    viewModel.onDigitsChange(6)
    assertEquals(6, viewModel.uiState.value.digits)

    viewModel.onDigitsChange(8)
    assertEquals(8, viewModel.uiState.value.digits)

    // Neither of the two the row can show, so it never becomes part of what would be saved.
    viewModel.onDigitsChange(7)
    assertEquals(8, viewModel.uiState.value.digits)
  }

  private fun TestScope.viewModelWith(storage: TokenStorage): AddAccountViewModel =
    AddAccountViewModel(TokenRepository(storage, UnconfinedTestDispatcher(testScheduler)))
}
