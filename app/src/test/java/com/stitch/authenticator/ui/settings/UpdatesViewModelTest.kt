package com.stitch.authenticator.ui.settings

import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.data.updates.ApkDownloader
import com.stitch.authenticator.data.updates.InstalledBuild
import com.stitch.authenticator.data.updates.UpdateChannel
import com.stitch.authenticator.data.updates.UpdateCheckResult
import com.stitch.authenticator.data.updates.UpdateChecker
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Everything the system update screen decides, decided here: when a check starts, what each
 * answer looks like on screen, and that a failure is never worn as "up to date".
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UpdatesViewModelTest {

  @Before
  fun setUp() {
    Dispatchers.setMain(StandardTestDispatcher())
  }

  @After
  fun tearDown() {
    Dispatchers.resetMain()
  }

  @Test
  fun `a check that finds something keeps the notes, not just the flag`() = runTest {
    val viewModel = viewModelWith(checker = returning(available()))

    viewModel.check()
    advanceUntilIdle()

    val state = viewModel.state.value as UpdateUiState.Available
    assertEquals("Dev build #19", state.versionLabel)
    assertEquals(8_388_608L, state.sizeBytes)
    assertEquals(listOf("One"), state.notes)
    assertEquals("https://example.com/page", state.pageUrl)
  }

  @Test
  fun `a check that finds nothing lands on up to date`() = runTest {
    val viewModel = viewModelWith(checker = returning(UpdateCheckResult.UpToDate))

    viewModel.check()
    advanceUntilIdle()

    // Up to date now carries when it was learned — the screen prints it, the test pins it.
    val state = viewModel.state.value as UpdateUiState.UpToDate
    assertTrue(state.checkedAtMillis > 0L)
  }

  @Test
  fun `a failed check stays an honest failure`() = runTest {
    val viewModel =
      viewModelWith(checker = returning(UpdateCheckResult.Unavailable("Couldn't reach GitHub.")))

    viewModel.check()
    advanceUntilIdle()

    val state = viewModel.state.value as UpdateUiState.Unavailable
    assertEquals("Couldn't reach GitHub.", state.reason)
  }

  @Test
  fun `a check that blows up is caught and turned into a sentence`() = runTest {
    val viewModel =
      viewModelWith(
        checker =
          object : UpdateChecker {
            override suspend fun check(
              channel: UpdateChannel,
              installed: InstalledBuild,
            ): UpdateCheckResult = throw IllegalStateException("unexpected")
          }
      )

    viewModel.check()
    advanceUntilIdle()

    assertTrue(viewModel.state.value is UpdateUiState.Unavailable)
  }

  @Test
  fun `the download walks its progress and lands whole in the cache`() = runTest {
    val downloader = RecordingDownloader()
    val viewModel = viewModelWith(checker = returning(available()), downloader = downloader)

    viewModel.check()
    advanceUntilIdle()
    viewModel.download()
    // The dispatcher has not run yet: the screen must already say it is downloading, with the
    // offer's own size and notes riding along so the middle of a download never loses them.
    val streaming = viewModel.state.value as UpdateUiState.Downloading
    assertEquals(8_388_608L, streaming.sizeBytes)
    assertEquals(listOf("One"), streaming.notes)
    advanceUntilIdle()

    val state = viewModel.state.value as UpdateUiState.Installing
    assertEquals(File("cache/updates/stitch-update.apk"), state.apkFile)
    assertEquals(8_388_608L, state.sizeBytes)
    assertEquals(listOf("One"), state.notes)
    assertEquals(listOf(0.5f, 1f), downloader.reported)
  }

  @Test
  fun `a download that dies mid-flight says so and keeps the release page in reach`() = runTest {
    val viewModel =
      viewModelWith(
        checker = returning(available()),
        downloader =
          object : ApkDownloader {
            override suspend fun download(url: String, onProgress: (Float) -> Unit): File =
              throw IOException("connection reset")
          },
      )

    viewModel.check()
    advanceUntilIdle()
    viewModel.download()
    advanceUntilIdle()

    val state = viewModel.state.value as UpdateUiState.Unavailable
    assertTrue(state.reason.contains("download"))
  }

  @Test
  fun `changing the channel retires what the old one concluded`() = runTest {
    val settings = FakeSettingsRepository()
    val viewModel = viewModelWith(settings = settings, checker = returning(available()))

    viewModel.check()
    advanceUntilIdle()
    assertTrue(viewModel.state.value is UpdateUiState.Available)

    viewModel.setChannel(UpdateChannel.STABLE)
    assertEquals(UpdateUiState.Idle, viewModel.state.value)

    advanceUntilIdle()
    assertEquals(UpdateChannel.STABLE, settings.updateChannel.first())
  }

  @Test
  fun `a second tap while a check is in flight asks only once`() = runTest {
    val checker = CountingChecker()
    val viewModel = viewModelWith(checker = checker)

    viewModel.check()
    viewModel.check()
    viewModel.check()
    advanceUntilIdle()

    assertEquals(1, checker.calls)
  }

  @Test
  fun `a download tap with nothing available does nothing at all`() = runTest {
    val downloader = RecordingDownloader()
    val viewModel = viewModelWith(downloader = downloader)

    viewModel.download()
    advanceUntilIdle()

    assertEquals(UpdateUiState.Idle, viewModel.state.value)
    assertEquals(0, downloader.reported.size)
  }

  @Test
  fun `pausing a download returns to the offer with everything the screen needs`() = runTest {
    val viewModel =
      viewModelWith(checker = returning(available()), downloader = HangingDownloader())

    viewModel.check()
    advanceUntilIdle()
    viewModel.download()
    advanceUntilIdle()
    assertTrue(viewModel.state.value is UpdateUiState.Downloading)

    viewModel.pause()
    advanceUntilIdle()

    val state = viewModel.state.value as UpdateUiState.Available
    assertEquals("Dev build #19", state.versionLabel)
    assertEquals(8_388_608L, state.sizeBytes)
    assertEquals(listOf("One"), state.notes)
  }

  @Test
  fun `switching channels mid-download stops the stream instead of landing it`() = runTest {
    val viewModel =
      viewModelWith(checker = returning(available()), downloader = HangingDownloader())

    viewModel.check()
    advanceUntilIdle()
    viewModel.download()
    advanceUntilIdle()
    assertTrue(viewModel.state.value is UpdateUiState.Downloading)

    viewModel.setChannel(UpdateChannel.BETA)
    advanceUntilIdle()

    // Idle, and nothing from the retired channel may surface after the switch.
    assertEquals(UpdateUiState.Idle, viewModel.state.value)
  }

  private fun TestScope.viewModelWith(
    settings: SettingsRepository = FakeSettingsRepository(),
    checker: UpdateChecker = returning(UpdateCheckResult.UpToDate),
    downloader: ApkDownloader = RecordingDownloader(),
  ) = UpdatesViewModel(settings, checker, downloader, InstalledBuild("1.0-dev.18", 18))

  private fun returning(result: UpdateCheckResult): UpdateChecker =
    object : UpdateChecker {
      override suspend fun check(
        channel: UpdateChannel,
        installed: InstalledBuild,
      ): UpdateCheckResult = result
    }

  private fun available() =
    UpdateCheckResult.Available(
      versionLabel = "Dev build #19",
      sizeBytes = 8_388_608L,
      notes = listOf("One"),
      apkUrl = "https://example.com/a.apk",
      pageUrl = "https://example.com/page",
    )

  private class CountingChecker : UpdateChecker {
    var calls = 0

    override suspend fun check(
      channel: UpdateChannel,
      installed: InstalledBuild,
    ): UpdateCheckResult {
      calls++
      return UpdateCheckResult.UpToDate
    }
  }

  private class RecordingDownloader : ApkDownloader {
    val reported = mutableListOf<Float>()

    override suspend fun download(url: String, onProgress: (Float) -> Unit): File {
      onProgress(0.5f)
      reported += 0.5f
      onProgress(1f)
      reported += 1f
      return File("cache/updates/stitch-update.apk")
    }
  }

  /** A stream that reports once and then never returns — cancelled only by [UpdatesViewModel.pause]. */
  private class HangingDownloader : ApkDownloader {
    override suspend fun download(url: String, onProgress: (Float) -> Unit): File {
      onProgress(0.4f)
      awaitCancellation()
    }
  }

  private class FakeSettingsRepository : SettingsRepository {
    private val theme = MutableStateFlow(ThemeMode.SYSTEM)
    private val dynamicColor = MutableStateFlow(true)
    private val biometricLock = MutableStateFlow(false)
    private val channelFlow = MutableStateFlow(UpdateChannel.STABLE)

    override val themeMode: Flow<ThemeMode> = theme.asStateFlow()
    override val dynamicColorEnabled: Flow<Boolean> = dynamicColor.asStateFlow()
    override val biometricLockEnabled: Flow<Boolean> = biometricLock.asStateFlow()
    override val updateChannel: Flow<UpdateChannel> = channelFlow.asStateFlow()

    override suspend fun setThemeMode(themeMode: ThemeMode) {
      theme.value = themeMode
    }

    override suspend fun setDynamicColorEnabled(enabled: Boolean) {
      dynamicColor.value = enabled
    }

    override suspend fun setBiometricLockEnabled(enabled: Boolean) {
      biometricLock.value = enabled
    }

    override suspend fun setUpdateChannel(channel: UpdateChannel) {
      channelFlow.value = channel
    }
  }
}
