package com.stitch.authenticator.data.updates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The updater's decision rules, pinned as rules: every ordering that could offer an upgrade, a
 * downgrade, or an honest shrug, decided here rather than against GitHub.
 */
class UpdateVersionsTest {

  @Test
  fun `a tagged version parses and prints back the shape it came in`() {
    assertEquals(AppVersion(1, 0, 2), UpdateVersions.parseInstalled("1.0.2"))
    assertEquals("1.0.2", UpdateVersions.parseInstalled("1.0.2")?.toString())
  }

  @Test
  fun `a dev build parses with its run number and a zero patch`() {
    assertEquals(AppVersion(1, 0, 0, devBuild = 18), UpdateVersions.parseInstalled("1.0-dev.18"))
    assertEquals("1.0-dev.18", UpdateVersions.parseInstalled("1.0-dev.18")?.toString())
  }

  @Test
  fun `a release tag wears its v prefix as decoration`() {
    assertEquals(AppVersion(1, 2, 3), UpdateVersions.parseTag("v1.2.3"))
  }

  @Test
  fun `anything outside the two shapes this repo writes is unreadable, not guessed`() {
    assertNull(UpdateVersions.parseInstalled(""))
    assertNull(UpdateVersions.parseInstalled("nightly"))
    assertNull(UpdateVersions.parseInstalled("1.2"))
    assertNull(UpdateVersions.parseInstalled("1.2.3.4"))
    assertNull(UpdateVersions.parseTag("v-next"))
    assertNull(UpdateVersions.parseTag("v1.2"))
  }

  @Test
  fun `the beta build number comes out of the release title`() {
    assertEquals(18, UpdateVersions.parseDevBuildNumber("Dev build #18"))
    assertNull(UpdateVersions.parseDevBuildNumber("Latest release"))
  }

  @Test
  fun `the stable channel offers a strictly newer line and nothing else`() {
    val installed = AppVersion(1, 0, 2)
    assertEquals(ChannelVerdict.Newer, UpdateVersions.stableVerdict(installed, AppVersion(1, 0, 3)))
    assertEquals(ChannelVerdict.Newer, UpdateVersions.stableVerdict(installed, AppVersion(1, 1, 0)))
    assertEquals(
      ChannelVerdict.UpToDate,
      UpdateVersions.stableVerdict(installed, AppVersion(1, 0, 2)),
    )
    // Older must read as up to date: offering it would be a downgrade Android would refuse.
    assertEquals(
      ChannelVerdict.UpToDate,
      UpdateVersions.stableVerdict(installed, AppVersion(1, 0, 1)),
    )
  }

  @Test
  fun `a dev install compares against the stable line it sits on`() {
    val devInstall = AppVersion(1, 0, 0, devBuild = 18)
    assertEquals(
      ChannelVerdict.UpToDate,
      UpdateVersions.stableVerdict(devInstall, AppVersion(1, 0, 0)),
    )
    assertEquals(ChannelVerdict.Newer, UpdateVersions.stableVerdict(devInstall, AppVersion(1, 0, 1)))
  }

  @Test
  fun `a dev build ranks above the release with the same base`() {
    val devBuild = AppVersion(1, 0, 0, devBuild = 18)
    val release = AppVersion(1, 0, 0)
    assertTrue(devBuild > release)
    assertTrue(release < devBuild)
  }

  @Test
  fun `the beta channel compares run numbers, and an unreadable one is not "up to date"`() {
    assertEquals(ChannelVerdict.Newer, UpdateVersions.betaVerdict(18, 19))
    assertEquals(ChannelVerdict.UpToDate, UpdateVersions.betaVerdict(18, 18))
    assertEquals(ChannelVerdict.UpToDate, UpdateVersions.betaVerdict(18, 17))
    assertEquals(ChannelVerdict.Unknown, UpdateVersions.betaVerdict(18, null))
  }

  @Test
  fun `release notes become bullets when the body has them, plain lines when it does not`() {
    val generated =
      """
      ## What's Changed
      * Fix the countdown ring by @dev in #41
      * Speed dial for the add button by @dev in #42

      **Full Changelog**: https://example.com/compare
      """
        .trimIndent()
    assertEquals(
      listOf("Fix the countdown ring by @dev in #41", "Speed dial for the add button by @dev in #42"),
      UpdateVersions.releaseNotes(generated),
    )

    val ciNote = "Latest pre-release built from dev @ abc123.\n\nCI run: https://example.com/runs/1"
    assertEquals(
      listOf("Latest pre-release built from dev @ abc123.", "CI run: https://example.com/runs/1"),
      UpdateVersions.releaseNotes(ciNote),
    )
  }

  @Test
  fun `a long changelog cannot turn the card into a wall`() {
    val body = (1..20).joinToString("\n") { "* change $it" }
    assertEquals(6, UpdateVersions.releaseNotes(body).size)
  }
}
