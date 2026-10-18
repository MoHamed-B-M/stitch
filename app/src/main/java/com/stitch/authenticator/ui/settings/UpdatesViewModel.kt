package com.stitch.authenticator.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.updates.ApkDownloader
import com.stitch.authenticator.data.updates.InstalledBuild
import com.stitch.authenticator.data.updates.UpdateChannel
import com.stitch.authenticator.data.updates.UpdateCheckResult
import com.stitch.authenticator.data.updates.UpdateChecker
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Everything the system update screen draws, as one immutable snapshot. */
sealed interface UpdateUiState {
  /** Nothing checked yet — the button invites a check. */
  data object Idle : UpdateUiState

  /** The question is in flight; the wavy indicator runs indeterminate. */
  data object Checking : UpdateUiState

  /**
   * The channel's newest build is what is installed, and [checkedAtMillis] is when that verdict
   * was learned — the honest "last checked", never a time the app invented.
   */
  data class UpToDate(val checkedAtMillis: Long) : UpdateUiState

  /** A newer build exists; its notes are on screen and the download is one tap away. */
  data class Available(
    val versionLabel: String,
    val sizeBytes: Long,
    val notes: List<String>,
    val apkUrl: String,
    val pageUrl: String,
  ) : UpdateUiState

  /** The APK is streaming into the cache; the wavy indicator tracks [progress]. */
  data class Downloading(
    val versionLabel: String,
    val sizeBytes: Long,
    val notes: List<String>,
    val progress: Float,
  ) : UpdateUiState

  /** The file is whole in the cache; the system installer is the next tap. */
  data class Installing(
    val versionLabel: String,
    val sizeBytes: Long,
    val notes: List<String>,
    val apkFile: File,
    val pageUrl: String,
  ) : UpdateUiState

  /** Offline, rate-limited, unparsable — said plainly, never worn as "up to date". */
  data class Unavailable(val reason: String) : UpdateUiState
}

/**
 * State holder for the full-screen system update page.
 *
 * Everything here is user-initiated: nothing checks in the background, because an authenticator
 * that phones home on its own schedule is exactly the wrong habit. The channel preference comes
 * from and returns to the same settings repository the rest of the screen reads, so the screen
 * and storage can never disagree about it.
 */
class UpdatesViewModel(
  private val settingsRepository: SettingsRepository,
  private val checker: UpdateChecker,
  private val downloader: ApkDownloader,
  /** What the running app is — exposed so the screen can print version, build and verdicts. */
  val installed: InstalledBuild,
) : ViewModel() {

  private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
  val state: StateFlow<UpdateUiState> = _state.asStateFlow()

  val channel: StateFlow<UpdateChannel> =
    settingsRepository.updateChannel
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = UpdateChannel.STABLE,
      )

  /**
   * The offer the last check produced — what [pause] falls back to and what every download-state
   * snapshot is built from, so mid-download details (size, notes) never go back to the network
   * for data the screen is already showing.
   */
  private var offer: UpdateCheckResult.Available? = null

  /** The stream in flight, if any: [pause] and [setChannel] both cancel through this handle. */
  private var downloadJob: Job? = null

  fun setChannel(channel: UpdateChannel) {
    // Changing channels retires whatever the old one concluded — the notes now on screen are
    // about a different release, and a stream started under the old channel must not land an
    // install offer from it either.
    downloadJob?.cancel()
    downloadJob = null
    offer = null
    _state.value = UpdateUiState.Idle
    viewModelScope.launch { settingsRepository.setUpdateChannel(channel) }
  }

  fun check() {
    val current = _state.value
    // A question already in flight, or bytes already streaming: no second request on top.
    if (current is UpdateUiState.Checking || current is UpdateUiState.Downloading) return
    _state.value = UpdateUiState.Checking
    viewModelScope.launch {
      _state.value =
        try {
          when (val result = checker.check(channel.value, installed)) {
            UpdateCheckResult.UpToDate ->
              UpdateUiState.UpToDate(checkedAtMillis = System.currentTimeMillis())
            is UpdateCheckResult.Available -> {
              offer = result
              result.toUiState()
            }
            is UpdateCheckResult.Unavailable -> UpdateUiState.Unavailable(result.reason)
          }
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (unexpected: Exception) {
          UpdateUiState.Unavailable("Something went wrong. Try again, or open the release page.")
        }
    }
  }

  fun download() {
    // `this.offer` — the local below would otherwise shadow the property inside its own
    // initializer, which Kotlin reads as an uninitialised local.
    val offer = this.offer?.takeIf { _state.value is UpdateUiState.Available } ?: return
    _state.value = UpdateUiState.Downloading(offer.versionLabel, offer.sizeBytes, offer.notes, 0f)
    downloadJob = viewModelScope.launch {
      _state.value =
        try {
          val file =
            downloader.download(offer.apkUrl) { progress ->
              _state.value =
                UpdateUiState.Downloading(
                  versionLabel = offer.versionLabel,
                  sizeBytes = offer.sizeBytes,
                  notes = offer.notes,
                  progress = progress.coerceIn(0f, 1f),
                )
            }
          UpdateUiState.Installing(
            versionLabel = offer.versionLabel,
            sizeBytes = offer.sizeBytes,
            notes = offer.notes,
            apkFile = file,
            pageUrl = offer.pageUrl,
          )
        } catch (cancelled: CancellationException) {
          throw cancelled
        } catch (expected: IOException) {
          UpdateUiState.Unavailable(
            "The download didn't finish. Check your connection and try again."
          )
        } catch (unexpected: Exception) {
          UpdateUiState.Unavailable("Something went wrong. Try again, or open the release page.")
        }
      downloadJob = null
    }
  }

  /**
   * Stops the stream and returns to the offer, bar and all. The half-written file is abandoned
   * rather than resumed: [com.stitch.authenticator.data.updates.CacheApkDownloader] rewrites its
   * one file name from scratch on every attempt, and an update APK is small enough that starting
   * over beats teaching the downloader byte ranges — the next Install simply begins again.
   */
  fun pause() {
    downloadJob?.cancel()
    downloadJob = null
    offer?.let { _state.value = it.toUiState() }
  }

  private fun UpdateCheckResult.Available.toUiState(): UpdateUiState =
    UpdateUiState.Available(
      versionLabel = versionLabel,
      sizeBytes = sizeBytes,
      notes = notes,
      apkUrl = apkUrl,
      pageUrl = pageUrl,
    )

  companion object {
    /** Keep briefly subscribed after the screen leaves, like the other settings view-models. */
    private const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
  }
}
