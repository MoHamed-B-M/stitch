package com.stitch.authenticator.ui.update

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.updates.CacheApkDownloader
import com.stitch.authenticator.data.updates.GitHubUpdateChecker
import com.stitch.authenticator.data.updates.InstalledBuild
import com.stitch.authenticator.data.updates.UpdateChannel
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.ui.settings.UpdateUiState
import com.stitch.authenticator.ui.settings.UpdatesViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Where a release page opens if the system refuses the installer or the user wants to read first. */
private const val RELEASES_PAGE_URL = "https://github.com/MoHamed-B-M/stitch/releases"

/**
 * The full-screen system update page: pick a channel, ask it, read what's new, download, install.
 *
 * The old settings card promoted to a page of its own — three centre states (checking, up to
 * date, offer/installing) under one full-width bottom action, with [UpdateUiState.Idle] and
 * [UpdateUiState.Unavailable] kept honest beside them. Every state is rendered from data the
 * view model already holds: the offer carries its own size and notes through the whole
 * download, so the screen never re-asks the network for what it is displaying. Springs carry
 * every swap (never a `tween`), and the button's press morph comes from [ButtonDefaults.shapes]
 * itself, so the component animates itself.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpdateScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: UpdatesViewModel = updateViewModel(),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val channel by viewModel.channel.collectAsStateWithLifecycle()
  val context = LocalContext.current

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = {
          // The bar's title *is* the glyph: centred, over whatever the content says below.
          Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Icon(
              imageVector = SystemUpdateIcon,
              contentDescription = "System update",
              tint = MaterialTheme.colorScheme.primary,
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBackClick) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back",
            )
          }
        },
      )
    },
    bottomBar = {
      UpdateActionButton(
        state = state,
        onCheck = viewModel::check,
        onDownload = viewModel::download,
        onPause = viewModel::pause,
        onInstall = { apkFile -> installApk(context, apkFile) },
      )
    },
  ) { innerPadding ->
    // The box centres the column while it fits and lets it fill-and-scroll once the notes run
    // long, so a centred state never pins its top above the fold.
    Box(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        modifier =
          Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
      ) {
        Text(
          text =
            "Version ${viewModel.installed.versionName} (build ${viewModel.installed.versionCode})",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        ChannelRow(selected = channel, onSelect = viewModel::setChannel)
        Text(
          text =
            when (channel) {
              UpdateChannel.STABLE -> "Stable follows tagged releases."
              UpdateChannel.BETA -> "Beta follows the latest dev build, straight from CI."
            },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
        // The states cross-fade on the house springs: the offer rises into place because the
        // notes are the thing a person came here to read; everything else simply swaps.
        // Keyed on the state's kind: a download tick mints a fresh Downloading(...) every
        // percent, and re-running the cross-fade over an identical block is what flickered the
        // headline and the bar. A new kind is still the only thing that transitions.
        AnimatedContent(
          targetState = state,
          contentKey = { it::class },
          transitionSpec = {
            val enter =
              if (targetState is UpdateUiState.Available &&
                initialState !is UpdateUiState.Available) {
                slideInVertically(animationSpec = bouncySpring<IntOffset>()) { it / 4 } +
                  fadeIn(animationSpec = StitchMotion.Smooth)
              } else {
                fadeIn(animationSpec = StitchMotion.BouncyQuick)
              }
            enter togetherWith fadeOut(animationSpec = StitchMotion.Press)
          },
          label = "updateCenter",
        ) { current ->
          UpdateCenter(
            state = current,
            installed = viewModel.installed,
            onOpenPage = { url ->
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            },
          )
        }
      }
    }
  }
}

/**
 * One centre state, headlined. The three the spec names — checking, up to date, and the
 * offer/installing state — plus the idle invitation and the honest failure, all centre-aligned.
 */
@Composable
private fun UpdateCenter(
  state: UpdateUiState,
  installed: InstalledBuild,
  onOpenPage: (String) -> Unit,
) {
  when (state) {
    UpdateUiState.Idle ->
      CenterBlock(
        headline = "Check for updates",
        body = "Nothing is checked until you ask — no background polling, ever.",
      )

    UpdateUiState.Checking ->
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Checking for update…",
          style = MaterialTheme.typography.headlineSmall,
          textAlign = TextAlign.Center,
        )
        // Indeterminate directly under the headline: the network's own pace is the honest shape
        // until bytes actually stream.
        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
          text = "Please wait while we check for the latest features.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
        )
      }

    is UpdateUiState.UpToDate ->
      CenterBlock(
        headline = "Your app is up to date",
        body =
          "Version ${installed.versionName} (build ${installed.versionCode})\n" +
            "Last checked ${formatCheckedAt(state.checkedAtMillis)}",
      )

    is UpdateUiState.Available ->
      OfferBlock(
        headline = "Update available",
        versionLabel = state.versionLabel,
        sizeBytes = state.sizeBytes,
        notes = state.notes,
        progress = null,
        installHint = false,
        pageUrl = state.pageUrl,
        onOpenPage = onOpenPage,
      )

    is UpdateUiState.Downloading ->
      OfferBlock(
        headline = "Installing system update…",
        versionLabel = state.versionLabel,
        sizeBytes = state.sizeBytes,
        notes = state.notes,
        progress = state.progress,
        installHint = false,
        pageUrl = null,
        onOpenPage = onOpenPage,
      )

    is UpdateUiState.Installing ->
      OfferBlock(
        headline = "Installing system update…",
        versionLabel = state.versionLabel,
        sizeBytes = state.sizeBytes,
        notes = state.notes,
        progress = 1f,
        installHint = true,
        pageUrl = state.pageUrl,
        onOpenPage = onOpenPage,
      )

    is UpdateUiState.Unavailable ->
      CenterBlock(headline = "Couldn't check for updates", body = state.reason)
  }
}

/** Headline + body, centre-aligned: the shape every non-offer state shares. */
@Composable
private fun CenterBlock(headline: String, body: String) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Text(
      text = headline,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      text = body,
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/**
 * The offer/installing state: headline, version over size, the determinate bar while bytes move
 * (absent before the download starts — a bar bound to no progress is a lie), the `•` notes, and
 * the release page as the way out.
 */
@Composable
private fun OfferBlock(
  headline: String,
  versionLabel: String,
  sizeBytes: Long,
  notes: List<String>,
  progress: Float?,
  installHint: Boolean,
  pageUrl: String?,
  onOpenPage: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    Text(
      text = headline,
      style = MaterialTheme.typography.headlineSmall,
      textAlign = TextAlign.Center,
    )
    Text(
      text =
        listOfNotNull(versionLabel, sizeLabel(sizeBytes)).joinToString("  ·  "),
      style = MaterialTheme.typography.bodyLarge,
      color = MaterialTheme.colorScheme.primary,
      textAlign = TextAlign.Center,
    )
    progress?.let { fraction ->
      LinearWavyProgressIndicator(
        progress = { fraction },
        modifier = Modifier.fillMaxWidth(),
      )
    }
    if (notes.isNotEmpty()) {
      Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        notes.forEach { note ->
          Text(
            text = "•  $note",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
          )
        }
      }
    }
    if (installHint) {
      Text(
        text = "Android will ask you to confirm. Your accounts are untouched.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
    pageUrl?.let { url ->
      TextButton(onClick = { onOpenPage(url) }) { Text(text = "Release page") }
    }
  }
}

/**
 * The one action of each state, full width in the bottom bar: "Check for update" to ask (or ask
 * again from any answer), "Pause" while the stream runs, "Install" once the file is whole — the
 * same expressive [Button] shape morph on every one, so nothing authors an animation spec.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpdateActionButton(
  state: UpdateUiState,
  onCheck: () -> Unit,
  onDownload: () -> Unit,
  onPause: () -> Unit,
  onInstall: (File) -> Unit,
) {
  val label: String
  val enabled: Boolean
  val onClick: () -> Unit
  when (state) {
    is UpdateUiState.Downloading -> {
      label = "Pause"
      enabled = true
      onClick = onPause
    }
    is UpdateUiState.Available -> {
      // The offer's Install begins the delivery: one action per moment, never two buttons.
      label = "Install"
      enabled = true
      onClick = onDownload
    }
    is UpdateUiState.Installing -> {
      label = "Install"
      enabled = true
      onClick = { onInstall(state.apkFile) }
    }
    UpdateUiState.Checking -> {
      label = "Check for update"
      enabled = false
      onClick = {}
    }
    else -> {
      label = "Check for update"
      enabled = true
      onClick = onCheck
    }
  }
  Button(
    onClick = onClick,
    enabled = enabled,
    shapes = ButtonDefaults.shapes(),
    modifier =
      Modifier.fillMaxWidth()
        .padding(horizontal = 16.dp)
        .navigationBarsPadding()
        .padding(top = 4.dp, bottom = 8.dp),
  ) {
    Text(text = label)
  }
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

/** "8.4 MB" — or nothing when GitHub (or a fixture) reported no size. */
private fun sizeLabel(sizeBytes: Long): String? =
  if (sizeBytes <= 0L) null
  else String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))

/** The verdict's timestamp, in the user's own locale and zone — not a UTC string from the app. */
private fun formatCheckedAt(epochMillis: Long): String =
  DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(epochMillis))

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
 * Production wiring for the updater: the shared settings repository (for the channel), the GitHub
 * checker, and a cache-backed downloader. Every dependency is cheap and stateless, but they meet
 * here exactly once so the screen never constructs its own.
 */
@Composable
private fun updateViewModel(): UpdatesViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel {
    UpdatesViewModel(
      settingsRepository = AppGraph.settingsRepository(context),
      checker = GitHubUpdateChecker(),
      downloader = CacheApkDownloader(context.cacheDir),
      installed = context.installedBuild(),
    )
  }
}

/**
 * What the running app knows about itself.
 *
 * The version code is the CI run number, and it is the one number that grows on every build —
 * which is exactly what the beta channel compares.
 */
private fun Context.installedBuild(): InstalledBuild {
  // `getPackageInfo(String, Int)` is the version-agnostic call; the typed overload needs API 33
  // while minSdk is 26.
  @Suppress("DEPRECATION")
  val info = packageManager.getPackageInfo(packageName, 0)
  return InstalledBuild(
    versionName = info.versionName.orEmpty(),
    versionCode = PackageInfoCompat.getLongVersionCode(info),
  )
}

@Preview(name = "Update, checking", showBackground = true)
@Composable
private fun UpdateCheckingPreview() {
  StitchAuthenticatorTheme {
    UpdatePreviewBox {
      UpdateCenter(
        state = UpdateUiState.Checking,
        installed = InstalledBuild("1.0-dev.36", 36),
        onOpenPage = {},
      )
    }
  }
}

@Preview(name = "Update, up to date", showBackground = true)
@Composable
private fun UpdateUpToDatePreview() {
  StitchAuthenticatorTheme {
    UpdatePreviewBox {
      UpdateCenter(
        state = UpdateUiState.UpToDate(checkedAtMillis = System.currentTimeMillis()),
        installed = InstalledBuild("1.0-dev.36", 36),
        onOpenPage = {},
      )
    }
  }
}

@Preview(name = "Update, available", showBackground = true)
@Composable
private fun UpdateAvailablePreview() {
  StitchAuthenticatorTheme {
    UpdatePreviewBox {
      UpdateCenter(
        state =
          UpdateUiState.Available(
            versionLabel = "Dev build #37",
            sizeBytes = 14_680_064L,
            notes =
              listOf(
                "Passkeys: create, save from a scanned QR, manage in Settings",
                "System update page replaces the Settings card",
              ),
            apkUrl = "",
            pageUrl = "",
          ),
        installed = InstalledBuild("1.0-dev.36", 36),
        onOpenPage = {},
      )
    }
  }
}

@Preview(name = "Update, installing", showBackground = true)
@Composable
private fun UpdateInstallingPreview() {
  StitchAuthenticatorTheme {
    UpdatePreviewBox {
      UpdateCenter(
        state =
          UpdateUiState.Installing(
            versionLabel = "Dev build #37",
            sizeBytes = 14_680_064L,
            notes = listOf("Passkeys: create, save from a scanned QR, manage in Settings"),
            apkFile = File("stitch-update.apk"),
            pageUrl = "",
          ),
        installed = InstalledBuild("1.0-dev.36", 36),
        onOpenPage = {},
      )
    }
  }
}

@Composable
private fun UpdatePreviewBox(content: @Composable () -> Unit) {
  Box(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    contentAlignment = Alignment.Center,
    content = { content() },
  )
}
