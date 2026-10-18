package com.stitch.authenticator.data.updates

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The GitHub checker pinned against fixtures: the same JSON shape the API answers with, so a
 * behaviour change here is a deliberate decision rather than something discovered on a phone.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitHubUpdateCheckerTest {

  private val installedDev = InstalledBuild(versionName = "1.0-dev.18", versionCode = 18)

  @Test
  fun `the stable channel reads a newer tag as available`() =
    runTest {
      val checker =
        checkerWith(stableRelease(tag = "v1.0.1", body = "* Faster codes by @dev in #9"))

      val result = checker.check(UpdateChannel.STABLE, installedDev)

      assertTrue(result is UpdateCheckResult.Available)
      result as UpdateCheckResult.Available
      assertEquals("1.0.1", result.versionLabel)
      assertEquals(listOf("Faster codes by @dev in #9"), result.notes)
      assertEquals("https://example.com/app-release.apk", result.apkUrl)
      assertEquals("https://example.com/releases/tag/v1.0.1", result.pageUrl)
    }

  @Test
  fun `the stable channel says up to date when the installed line is the newest`() = runTest {
    val checker = checkerWith(stableRelease(tag = "v1.0.0"))
    val result = checker.check(UpdateChannel.STABLE, InstalledBuild("1.0.0", 17))
    assertEquals(UpdateCheckResult.UpToDate, result)
  }

  @Test
  fun `a dev install is never offered the stable release it already sits on`() = runTest {
    val checker = checkerWith(stableRelease(tag = "v1.0.0"))
    assertEquals(UpdateCheckResult.UpToDate, checker.check(UpdateChannel.STABLE, installedDev))
  }

  @Test
  fun `the beta channel reads a newer v-next build as available`() =
    runTest {
      val checker = checkerWith(betaRelease(title = "Dev build #19"))

      val result = checker.check(UpdateChannel.BETA, installedDev)

      assertTrue(result is UpdateCheckResult.Available)
      result as UpdateCheckResult.Available
      assertEquals("Dev build #19", result.versionLabel)
      assertEquals("https://example.com/v-next.apk", result.apkUrl)
    }

  @Test
  fun `the beta channel says up to date when the install already is the v-next build`() = runTest {
    val checker = checkerWith(betaRelease(title = "Dev build #18"))
    assertEquals(UpdateCheckResult.UpToDate, checker.check(UpdateChannel.BETA, installedDev))
  }

  @Test
  fun `a stable install newer than the last dev push is up to date on beta`() = runTest {
    // A stable build's version code is its run number too, so the channels compare honestly.
    val checker = checkerWith(betaRelease(title = "Dev build #18"))
    val result = checker.check(UpdateChannel.BETA, InstalledBuild("1.0.2", versionCode = 25))
    assertEquals(UpdateCheckResult.UpToDate, result)
  }

  @Test
  fun `a release without an APK is not an update this app can install`() = runTest {
    val checker = checkerWith(stableRelease(tag = "v1.0.1", withApk = false))
    assertTrue(checker.check(UpdateChannel.STABLE, installedDev) is UpdateCheckResult.Unavailable)
  }

  @Test
  fun `a rate limit is said plainly and never as "up to date"`() = runTest {
    val checker = GitHubUpdateChecker(fetch = { throw HttpStatusException(403) })
    assertTrue(checker.check(UpdateChannel.STABLE, installedDev) is UpdateCheckResult.Unavailable)
  }

  @Test
  fun `an unreachable GitHub is an unavailable, not a crash`() = runTest {
    val checker = GitHubUpdateChecker(fetch = { throw java.io.IOException("no route") })
    assertTrue(checker.check(UpdateChannel.BETA, installedDev) is UpdateCheckResult.Unavailable)
  }

  @Test
  fun `an answer that is not a release is an unavailable, not a crash`() = runTest {
    val checker = checkerWith("<html>oops</html>")
    assertTrue(checker.check(UpdateChannel.BETA, installedDev) is UpdateCheckResult.Unavailable)
  }

  private fun checkerWith(raw: String): UpdateChecker =
    GitHubUpdateChecker(fetch = { raw }, ioDispatcher = UnconfinedTestDispatcher())

  private fun stableRelease(tag: String, body: String = "", withApk: Boolean = true): String =
    """
    {
      "tag_name": "$tag",
      "name": "${tag.removePrefix("v")}",
      "body": "$body",
      "prerelease": false,
      "html_url": "https://example.com/releases/tag/$tag",
      "assets": [
        ${if (withApk) """{"name": "app-release.apk", "browser_download_url": "https://example.com/app-release.apk"}""" else ""}
      ]
    }
    """
      .trimIndent()

  private fun betaRelease(title: String): String =
    """
    {
      "tag_name": "v-next",
      "name": "$title",
      "body": "Latest pre-release built from dev @ abc123.",
      "prerelease": true,
      "html_url": "https://example.com/releases/tag/v-next",
      "assets": [
        {"name": "app-release.apk", "browser_download_url": "https://example.com/v-next.apk"}
      ]
    }
    """
      .trimIndent()
}
