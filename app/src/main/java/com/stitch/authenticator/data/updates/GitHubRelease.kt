package com.stitch.authenticator.data.updates

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The slice of GitHub's release JSON this app reads.
 *
 * Every field has a default and unknown keys are ignored on decode, so a GitHub-side addition
 * (or a truncated answer) degrades to a missing field instead of a crash.
 */
@Serializable
data class GitHubRelease(
  @SerialName("tag_name") val tagName: String = "",
  val name: String = "",
  val body: String = "",
  val prerelease: Boolean = false,
  @SerialName("html_url") val htmlUrl: String = "",
  val assets: List<GitHubAsset> = emptyList(),
) {
  /** The first attached APK — CI uploads exactly one, named `app-release.apk`. */
  val apkAsset: GitHubAsset?
    get() = assets.firstOrNull { it.isApk }
}

@Serializable
data class GitHubAsset(
  val name: String = "",
  @SerialName("browser_download_url") val browserDownloadUrl: String = "",
  /** Bytes, as the API reports them; 0 when GitHub (or a test fixture) omits the field. */
  @SerialName("size") val sizeBytes: Long = 0L,
) {
  val isApk: Boolean
    get() = name.endsWith(".apk", ignoreCase = true)
}
