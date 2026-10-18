package com.stitch.authenticator.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.stitch.authenticator.data.updates.UpdateChannel
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncyContentSize
import com.stitch.authenticator.theme.bouncySpring
import java.io.File

/** Where a release page opens if the system refuses the installer or the user wants to read first. */
private const val RELEASES_PAGE_URL = "https://github.com/MoHamed-B-M/stitch/releases"

/**
 * The updates card: pick a channel, ask it, read what's new, install.
 *
 * Every state of [UpdateUiState] gets an honest rendering — the wavy indicator while the question
 * is in flight or the bytes are streaming, the release notes *before* anything downloads, and a
 * plain sentence when the answer could not be obtained. Springs (never a `tween`) carry every
 * entrance and swap; the primary button's press morph comes from [ButtonDefaults.shapes] itself,
 * so the component animates itself.
 */
@Composable
internal fun UpdatesCard(
  state: UpdateUiState,
  channel: UpdateChannel,
  versionName: String,
  onChannelChange: (UpdateChannel) -> Unit,
  onCheck: () -> Unit,
  onDownload: () -> Unit,
  onOpenPage: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().bouncyContentSize()) {
    Text(
      text = "Updates",
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    // Same grouping language as the other settings sections: `shapes.large` is the 16dp M3
    // baseline corner, and surfaceContainerLow keeps the card a shade off the screen.
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors =
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Text(
          text = "Version $versionName",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChannelRow(selected = channel, onSelect = onChannelChange)
        Text(
          text =
            when (channel) {
              UpdateChannel.STABLE -> "Stable follows tagged releases."
              UpdateChannel.BETA -> "Beta follows the latest dev build, straight from CI."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // The states cross-fade on the house springs — same shape as this screen's biometric
        // support swap, so a card state change never jumps.
        AnimatedContent(
          targetState = state,
          transitionSpec = {
            val enter =
              if (targetState is UpdateUiState.Available && initialState !is UpdateUiState.Available) {
                // "What's new" rises into place — the one flourish the card gets, because the
                // notes are the thing a person came here to read.
                slideInVertically(animationSpec = bouncySpring<IntOffset>()) { it / 4 } +
                  fadeIn(animationSpec = StitchMotion.Smooth)
              } else {
                fadeIn(animationSpec = StitchMotion.BouncyQuick)
              }
            enter togetherWith fadeOut(animationSpec = StitchMotion.Press)
          },
          label = "updateState",
        ) { current ->
          UpdateStateSection(
            state = current,
            onCheck = onCheck,
            onDownload = onDownload,
            onOpenPage = onOpenPage,
          )
        }
      }
    }
  }
}

@Composable
private fun UpdateStateSection(
  state: UpdateUiState,
  onCheck: () -> Unit,
  onDownload: () -> Unit,
  onOpenPage: (String) -> Unit,
) {
  when (state) {
    UpdateUiState.Idle -> CheckButton(label = "Check for updates", onClick = onCheck)

    UpdateUiState.UpToDate ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = "You're up to date.", style = MaterialTheme.typography.bodyMedium)
        CheckButton(label = "Check again", onClick = onCheck)
      }

    // Indeterminate while the question is in flight: the network's own pace is the honest
    // shape until bytes actually stream.
    UpdateUiState.Checking -> LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())

    is UpdateUiState.Downloading ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
          text = "Downloading ${state.versionLabel}…",
          style = MaterialTheme.typography.bodyMedium,
        )
        LinearWavyProgressIndicator(
          progress = { state.progress },
          modifier = Modifier.fillMaxWidth(),
        )
      }

    is UpdateUiState.Available ->
      AvailableSection(state = state, onDownload = onDownload, onOpenPage = onOpenPage)

    is UpdateUiState.Downloaded -> DownloadedSection(state = state, onOpenPage = onOpenPage)

    is UpdateUiState.Unavailable ->
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = state.reason, style = MaterialTheme.typography.bodyMedium)
        CheckButton(label = "Try again", onClick = onCheck)
      }
  }
}

/** The notes arrive before any download starts: nothing is asked of the network to read them. */
@Composable
private fun AvailableSection(
  state: UpdateUiState.Available,
  onDownload: () -> Unit,
  onOpenPage: (String) -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = "${state.versionLabel} is available", style = MaterialTheme.typography.titleSmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
      state.notes.forEach { note ->
        Text(text = "•  $note", style = MaterialTheme.typography.bodyMedium)
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CheckButton(label = "Download update", onClick = onDownload)
      TextButton(onClick = { onOpenPage(state.pageUrl) }) { Text(text = "Release page") }
    }
  }
}

@Composable
private fun DownloadedSection(
  state: UpdateUiState.Downloaded,
  onOpenPage: (String) -> Unit,
) {
  val context = LocalContext.current
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = "${state.versionLabel} is ready to install", style = MaterialTheme.typography.titleSmall)
    Text(
      text = "Android will ask you to confirm. Your accounts are untouched.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      CheckButton(label = "Install update", onClick = { installApk(context, state.apkFile) })
      TextButton(onClick = { onOpenPage(state.pageUrl) }) { Text(text = "Release page") }
    }
  }
}

/**
 * Hands the finished APK to the system installer — the same flow as installing any downloaded
 * package, with the release page in the browser as the honest fallback for OEMs that distrust
 * sideloading rather than a dead tap.
 */
private fun installApk(context: Context, apkFile: File) {
  val installIntent =
    Intent(Intent.ACTION_VIEW).apply {
      setDataAndType(
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile),
        "application/vnd.android.package-archive",
      )
      addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
  try {
    context.startActivity(installIntent)
  } catch (ignored: ActivityNotFoundException) {
    Toast.makeText(context, "No installer available — opening the release page.", Toast.LENGTH_LONG)
      .show()
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE_URL)))
  }
}

/**
 * The one primary action of each state, on the expressive button: [ButtonDefaults.shapes] gives
 * the press its morph, so nothing here authors an animation spec of its own.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CheckButton(label: String, onClick: () -> Unit) {
  Button(onClick = onClick, shapes = ButtonDefaults.shapes()) { Text(text = label) }
}

/**
 * The channel choice, as one segmented row — the same [SingleChoiceSegmentedButtonRow] language
 * the theme selector uses: two co-equal options that are visible at once.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChannelRow(
  selected: UpdateChannel,
  onSelect: (UpdateChannel) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    UpdateChannel.entries.forEachIndexed { index, channel ->
      SegmentedButton(
        selected = selected == channel,
        onClick = { onSelect(channel) },
        shape =
          SegmentedButtonDefaults.itemShape(index = index, count = UpdateChannel.entries.size),
        label = { Text(text = channel.label) },
      )
    }
  }
}

private val UpdateChannel.label: String
  get() =
    when (this) {
      UpdateChannel.STABLE -> "Stable"
      UpdateChannel.BETA -> "Beta"
    }

@Preview(name = "Updates, idle", showBackground = true)
@Composable
private fun UpdatesCardIdlePreview() {
  StitchAuthenticatorTheme {
    UpdatesCard(
      state = UpdateUiState.Idle,
      channel = UpdateChannel.STABLE,
      versionName = "1.0.0",
      onChannelChange = {},
      onCheck = {},
      onDownload = {},
      onOpenPage = {},
      modifier = Modifier.padding(16.dp),
    )
  }
}

@Preview(name = "Updates, beta available", showBackground = true)
@Composable
private fun UpdatesCardAvailablePreview() {
  StitchAuthenticatorTheme {
    UpdatesCard(
      state =
        UpdateUiState.Available(
          versionLabel = "Dev build #19",
          notes =
            listOf(
              "Scan QR codes straight from the add menu",
              "Settings: theme, dynamic color, biometric lock",
            ),
          apkUrl = "",
          pageUrl = "",
        ),
      channel = UpdateChannel.BETA,
      versionName = "1.0-dev.18",
      onChannelChange = {},
      onCheck = {},
      onDownload = {},
      onOpenPage = {},
      modifier = Modifier.padding(16.dp),
    )
  }
}

@Preview(name = "Updates, downloading", showBackground = true)
@Composable
private fun UpdatesCardDownloadingPreview() {
  StitchAuthenticatorTheme {
    UpdatesCard(
      state = UpdateUiState.Downloading(versionLabel = "Dev build #19", progress = 0.42f),
      channel = UpdateChannel.BETA,
      versionName = "1.0-dev.18",
      onChannelChange = {},
      onCheck = {},
      onDownload = {},
      onOpenPage = {},
      modifier = Modifier.padding(16.dp),
    )
  }
}
