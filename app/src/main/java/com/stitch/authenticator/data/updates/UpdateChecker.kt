package com.stitch.authenticator.data.updates

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** What the running app knows about itself, so the decision rules read as rules. */
data class InstalledBuild(
  val versionName: String,
  val versionCode: Long,
)

/** The answer to one channel check. */
sealed interface UpdateCheckResult {
  /** The channel has nothing newer than what is installed. */
  data object UpToDate : UpdateCheckResult

  /**
   * A newer build exists. [versionLabel] is what the screen shows ("1.0.1" or "Dev build #19"),
   * [sizeBytes] the APK's weight as GitHub reports it (0 if the field is absent), [notes] the
   * release body split into readable lines, [apkUrl] the asset to stream, and [pageUrl] where the
   * browser fallback lands.
   */
  data class Available(
    val versionLabel: String,
    val sizeBytes: Long,
    val notes: List<String>,
    val apkUrl: String,
    val pageUrl: String,
  ) : UpdateCheckResult

  /**
   * The check could not answer — offline, rate-limited, or a release that could not be read.
   * Never folded into [UpToDate]: "we don't know" and "there is nothing" are different answers.
   */
  data class Unavailable(val reason: String) : UpdateCheckResult
}

/** A non-200 from the API; carries the status so callers can say something honest about it. */
class HttpStatusException(val status: Int) : IOException("HTTP $status")

/**
 * One question, asked of a release channel: is there anything newer than what is installed?
 *
 * Implementations answer with [UpdateCheckResult.Unavailable] rather than throwing for expected
 * failures (no connection, a malformed reply), so the view-model never has to guess which
 * exceptions a user deserves to see.
 */
interface UpdateChecker {
  suspend fun check(channel: UpdateChannel, installed: InstalledBuild): UpdateCheckResult
}

/**
 * The GitHub Releases implementation, and the only place in the app that talks to the network.
 *
 * Both channels are plain reads of the same public API this app is distributed from: [STABLE]
 * asks for the latest tagged release, [BETA] asks for the `v-next` pre-release this repository
 * replaces on every push to `dev`. No API token, no telemetry, nothing that could identify the
 * caller beyond the request GitHub already sees.
 */
class GitHubUpdateChecker(
  /** Reads a URL to text; injectable so tests pin behaviour against fixtures, not the network. */
  private val fetch: suspend (String) -> String = ::httpGet,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UpdateChecker {

  private val json = Json { ignoreUnknownKeys = true }

  override suspend fun check(
    channel: UpdateChannel,
    installed: InstalledBuild,
  ): UpdateCheckResult =
    withContext(ioDispatcher) {
      try {
        when (channel) {
          UpdateChannel.STABLE -> checkStable(installed)
          UpdateChannel.BETA -> checkBeta(installed)
        }
      } catch (expected: HttpStatusException) {
        UpdateCheckResult.Unavailable(statusReason(expected))
      } catch (expected: SerializationException) {
        UpdateCheckResult.Unavailable("GitHub's answer didn't make sense. Try again later.")
      } catch (expected: IOException) {
        UpdateCheckResult.Unavailable("Couldn't reach GitHub. Check your connection and try again.")
      }
    }

  private suspend fun checkStable(installed: InstalledBuild): UpdateCheckResult {
    val release = decodeRelease(fetch(STABLE_RELEASES_URL))
    val releaseVersion =
      UpdateVersions.parseTag(release.tagName)
        ?: return UpdateCheckResult.Unavailable("Couldn't read the release version GitHub sent.")
    val installedVersion =
      UpdateVersions.parseInstalled(installed.versionName)
        ?: return UpdateCheckResult.Unavailable("Couldn't read this build's own version.")
    return when (UpdateVersions.stableVerdict(installedVersion, releaseVersion)) {
      ChannelVerdict.Newer -> available(release, releaseVersion.toString())
      ChannelVerdict.UpToDate -> UpdateCheckResult.UpToDate
      ChannelVerdict.Unknown -> UpdateCheckResult.Unavailable("Couldn't compare the versions.")
    }
  }

  private suspend fun checkBeta(installed: InstalledBuild): UpdateCheckResult {
    val release = decodeRelease(fetch(BETA_RELEASE_URL))
    val buildNumber = UpdateVersions.parseDevBuildNumber(release.name)
    return when (UpdateVersions.betaVerdict(installed.versionCode, buildNumber)) {
      // `Newer` implies a parsed build number, so the fallback can only fire on an oddly
      // titled release — where the tag still tells the truth.
      ChannelVerdict.Newer ->
        available(release, release.name.ifEmpty { "Dev build #${buildNumber ?: 0}" })
      ChannelVerdict.UpToDate -> UpdateCheckResult.UpToDate
      ChannelVerdict.Unknown ->
        UpdateCheckResult.Unavailable("Couldn't read the beta build number GitHub sent.")
    }
  }

  /** Turns a parsed release into an offer, refusing one this app cannot actually install. */
  private fun available(release: GitHubRelease, versionLabel: String): UpdateCheckResult {
    val apk =
      release.apkAsset
        ?: return UpdateCheckResult.Unavailable("That release has no APK attached.")
    return UpdateCheckResult.Available(
      versionLabel = versionLabel,
      sizeBytes = apk.sizeBytes,
      notes = UpdateVersions.releaseNotes(release.body),
      apkUrl = apk.browserDownloadUrl,
      pageUrl = release.htmlUrl,
    )
  }

  private fun decodeRelease(raw: String): GitHubRelease =
    json.decodeFromString(GitHubRelease.serializer(), raw)

  private fun statusReason(error: HttpStatusException): String =
    when (error.status) {
      // Only reachable on the beta channel, before the first push to `dev` has published v-next.
      404 -> "That channel has no release yet."
      403, 429 -> "GitHub is limiting requests right now. Try again in a minute."
      else -> "GitHub returned an error (${error.status}). Try again later."
    }
}

private const val REPOSITORY = "MoHamed-B-M/stitch"

/** GitHub answers with the newest non-prerelease here — the stable channel, by definition. */
private const val STABLE_RELEASES_URL = "https://api.github.com/repos/$REPOSITORY/releases/latest"

/** The pre-release this repository replaces on every push to `dev` — the beta channel. */
private const val BETA_RELEASE_URL = "https://api.github.com/repos/$REPOSITORY/releases/tags/v-next"

/**
 * One GET, to text. GitHub rejects requests without a User-Agent, and release JSON is small
 * enough that buffering the whole answer is simpler than streaming it.
 */
private fun httpGet(url: String): String {
  val connection = URL(url).openConnection() as HttpURLConnection
  try {
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    connection.setRequestProperty("Accept", "application/vnd.github+json")
    connection.setRequestProperty("User-Agent", "StitchAuthenticator")
    val status = connection.responseCode
    if (status != HttpURLConnection.HTTP_OK) throw HttpStatusException(status)
    return connection.inputStream.bufferedReader().use { it.readText() }
  } finally {
    connection.disconnect()
  }
}
