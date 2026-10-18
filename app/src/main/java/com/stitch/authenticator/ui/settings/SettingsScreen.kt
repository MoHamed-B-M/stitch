package com.stitch.authenticator.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.data.updates.CacheApkDownloader
import com.stitch.authenticator.data.updates.GitHubUpdateChecker
import com.stitch.authenticator.data.updates.InstalledBuild
import com.stitch.authenticator.data.updates.UpdateChannel
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncyContentSize
import com.stitch.authenticator.theme.springClickable
import com.stitch.authenticator.ui.passkeys.PasskeysViewModel
import com.stitch.authenticator.util.BiometricAuthResult
import com.stitch.authenticator.util.BiometricAuthenticator
import com.stitch.authenticator.util.rememberBiometricAuthState

private val ScreenPadding = 16.dp

/** Where the source lives; the GitHub row opens it in the browser. */
private const val GitHubRepositoryUrl = "https://github.com/MoHamed-B-M/stitch"

/** Which way a pending biometric prompt is heading: enabling asks, disabling confirms. */
private enum class BiometricPromptIntent {
  ENABLE,
  DISABLE,
}

/**
 * Settings: appearance, the biometric lock, saved passkeys, the updater, and the about rows.
 *
 * State comes from [SettingsViewModel], [UpdatesViewModel] and [PasskeysViewModel] in production
 * and is passed in through [SettingsContent] for previews and future UI tests, so the whole screen
 * renders without storage, a Keystore, a biometric sensor or the network.
 */
@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = settingsViewModel(),
  updatesViewModel: UpdatesViewModel = updatesViewModel(),
  passkeysViewModel: PasskeysViewModel = passkeysViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val updatesState by updatesViewModel.state.collectAsStateWithLifecycle()
  val updateChannel by updatesViewModel.channel.collectAsStateWithLifecycle()
  val passkeys by passkeysViewModel.passkeys.collectAsStateWithLifecycle()
  val authState = rememberBiometricAuthState()

  SettingsContent(
    state = state,
    onBackClick = onBackClick,
    onThemeModeSelected = viewModel::setThemeMode,
    onDynamicColorToggle = viewModel::setDynamicColorEnabled,
    onBiometricLockChange = viewModel::setBiometricLockEnabled,
    canUseBiometric = authState.canAuthenticate,
    updatesState = updatesState,
    updateChannel = updateChannel,
    onUpdateChannelChange = updatesViewModel::setChannel,
    onCheckUpdates = updatesViewModel::check,
    onDownloadUpdate = updatesViewModel::download,
    passkeys = passkeys,
    onDeletePasskey = passkeysViewModel::delete,
    modifier = modifier,
  )
}

/** Production wiring: the same repository instance [com.stitch.authenticator.MainActivity] reads. */
@Composable
private fun settingsViewModel(): SettingsViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { SettingsViewModel(AppGraph.settingsRepository(context)) }
}

/** Production wiring: the same passkey repository the credential provider service reads. */
@Composable
private fun passkeysViewModel(): PasskeysViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { PasskeysViewModel(AppGraph.passkeyRepository(context)) }
}

/**
 * Production wiring for the updater: the shared settings repository (for the channel), the GitHub
 * checker, and a cache-backed downloader. Every dependency is cheap and stateless, but they meet
 * here exactly once so the card never constructs its own.
 */
@Composable
private fun updatesViewModel(): UpdatesViewModel {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
  state: SettingsUiState,
  onBackClick: () -> Unit,
  onThemeModeSelected: (ThemeMode) -> Unit,
  onDynamicColorToggle: (Boolean) -> Unit,
  onBiometricLockChange: (Boolean) -> Unit,
  canUseBiometric: Boolean,
  updatesState: UpdateUiState,
  updateChannel: UpdateChannel,
  onUpdateChannelChange: (UpdateChannel) -> Unit,
  onCheckUpdates: () -> Unit,
  onDownloadUpdate: () -> Unit,
  passkeys: List<PasskeyEntity>,
  onDeletePasskey: (PasskeyEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Neither direction writes on tap: turning the lock on or off is confirmed with the very
  // biometrics it governs, so a stray tap can neither lock the app nor unlock it by itself.
  var pendingPrompt by remember { mutableStateOf<BiometricPromptIntent?>(null) }

  val context = LocalContext.current
  val versionName = rememberInstalledVersionName()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Settings") },
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
  ) { innerPadding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize().padding(innerPadding),
      contentPadding =
        PaddingValues(start = ScreenPadding, end = ScreenPadding, top = 8.dp, bottom = ScreenPadding),
      verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
      item {
        AppearanceSection(
          themeMode = state.themeMode,
          dynamicColorEnabled = state.dynamicColorEnabled,
          onThemeModeSelected = onThemeModeSelected,
          onDynamicColorToggle = onDynamicColorToggle,
        )
      }
      item {
        SecuritySection(
          biometricLockEnabled = state.biometricLockEnabled,
          canUseBiometric = canUseBiometric,
          onBiometricLockChange = onBiometricLockChange,
          onRequestPrompt = { intent -> pendingPrompt = intent },
        )
      }
      item {
        PasskeysSection(
          passkeys = passkeys,
          onDeletePasskey = onDeletePasskey,
        )
      }
      item {
        UpdatesCard(
          state = updatesState,
          channel = updateChannel,
          versionName = versionName,
          onChannelChange = onUpdateChannelChange,
          onCheck = onCheckUpdates,
          onDownload = onDownloadUpdate,
          onOpenPage = { url ->
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
          },
        )
      }
      item { AboutSection(versionName = versionName) }
    }
  }

  pendingPrompt?.let { intent ->
    BiometricAuthenticator(
      title =
        if (intent == BiometricPromptIntent.ENABLE) "Enable biometric lock" else "Disable biometric lock",
      subtitle = "Verify it's you",
      description =
        if (intent == BiometricPromptIntent.ENABLE) {
          "Confirm to turn the lock on."
        } else {
          "Confirm to turn the lock off."
        },
      onResult = { result ->
        // Only a success flips the switch. Anything else — including the user backing out —
        // leaves the setting exactly as it was.
        if (result == BiometricAuthResult.SUCCESS) {
          onBiometricLockChange(intent == BiometricPromptIntent.ENABLE)
        }
        pendingPrompt = null
      },
    ) { onAuthenticate ->
      LaunchedEffect(intent) { onAuthenticate() }
    }
  }
}

/**
 * One group of related settings: a label over a card of [ListItem] rows.
 *
 * The card is the grouping — separators would be redundant when rows already share one surface.
 * [bouncyContentSize] keeps any future row-size change from jumping: springs, never a fixed curve.
 */
@Composable
private fun SettingsSection(
  title: String,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Column(modifier = modifier.fillMaxWidth().bouncyContentSize()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    // `shapes.large` is 16dp in the M3 baseline: the same corner language the token cards use.
    Card(
      modifier = Modifier.fillMaxWidth(),
      shape = MaterialTheme.shapes.large,
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
      content()
    }
  }
}

/**
 * The passkeys this device can offer through the system's sign-in prompts.
 *
 * One row per stored credential — relying party over account name — with a trailing delete that
 * removes the record *and* its Keystore key (the view model owns that pairing). The empty state
 * is a row rather than a shrug, so the section's place on the screen never depends on how many
 * passkeys exist, and [bouncyContentSize] (inherited from [SettingsSection]) springs the card
 * closed when the last one goes.
 */
@Composable
private fun PasskeysSection(
  passkeys: List<PasskeyEntity>,
  onDeletePasskey: (PasskeyEntity) -> Unit,
) {
  SettingsSection(title = "Passkeys") {
    if (passkeys.isEmpty()) {
      ListItem(
        headlineContent = { Text("No passkeys yet") },
        supportingContent = {
          Text("Saved passkeys appear here and in the system's sign-in prompts")
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    } else {
      passkeys.forEach { passkey ->
        ListItem(
          headlineContent = { Text(passkey.rpId) },
          supportingContent = { Text(passkey.username) },
          trailingContent = {
            IconButton(onClick = { onDeletePasskey(passkey) }) {
              Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete passkey for ${passkey.rpId}",
              )
            }
          },
          colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
      }
    }
  }
}

@Composable
private fun AppearanceSection(
  themeMode: ThemeMode,
  dynamicColorEnabled: Boolean,
  onThemeModeSelected: (ThemeMode) -> Unit,
  onDynamicColorToggle: (Boolean) -> Unit,
) {
  SettingsSection(title = "Appearance") {
    Column(modifier = Modifier.padding(16.dp)) {
      ThemeModeSelector(selected = themeMode, onSelect = onThemeModeSelected)
      Spacer(Modifier.height(4.dp))
      DynamicColorToggle(enabled = dynamicColorEnabled, onToggle = onDynamicColorToggle)
    }
  }
}

/**
 * The theme choice, as one segmented row.
 *
 * The same [SingleChoiceSegmentedButtonRow] the add screen uses for its mode and algorithm picks:
 * three co-equal options that are visible at once read as a segmented control, not as a dropdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeModeSelector(
  selected: ThemeMode,
  onSelect: (ThemeMode) -> Unit,
) {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    ThemeMode.entries.forEachIndexed { index, mode ->
      SegmentedButton(
        selected = selected == mode,
        onClick = { onSelect(mode) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
        label = { Text(mode.label) },
      )
    }
  }
}

private val ThemeMode.label: String
  get() =
    when (this) {
      ThemeMode.SYSTEM -> "System"
      ThemeMode.LIGHT -> "Light"
      ThemeMode.DARK -> "Dark"
    }

@Composable
private fun DynamicColorToggle(
  enabled: Boolean,
  onToggle: (Boolean) -> Unit,
) {
  ListItem(
    headlineContent = { Text("Dynamic color") },
    supportingContent = { Text("Follow the system wallpaper palette") },
    trailingContent = { Switch(checked = enabled, onCheckedChange = onToggle) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
  )
}

@Composable
private fun SecuritySection(
  biometricLockEnabled: Boolean,
  canUseBiometric: Boolean,
  onBiometricLockChange: (Boolean) -> Unit,
  onRequestPrompt: (BiometricPromptIntent) -> Unit,
) {
  SettingsSection(title = "Security") {
    ListItem(
      headlineContent = { Text("Biometric lock") },
      supportingContent = {
        // The explanation swaps with the house spring instead of jumping — same idea as the
        // token card's code reveal, and no `tween` in sight.
        AnimatedContent(
          targetState = canUseBiometric,
          transitionSpec = {
            fadeIn(animationSpec = StitchMotion.BouncyQuick) togetherWith
              fadeOut(animationSpec = StitchMotion.Press)
          },
          label = "biometricSupport",
        ) { available ->
          Text(if (available) "Ask for biometrics before opening the app" else "No biometrics enrolled")
        }
      },
      trailingContent = {
        Switch(
          checked = biometricLockEnabled,
          // The locked-on switch stays usable: someone who removed their fingerprints must still
          // be able to turn the lock off — otherwise the app is a locked door with no key.
          enabled = canUseBiometric || biometricLockEnabled,
          onCheckedChange = { requested -> onRequestPrompt(if (requested) BiometricPromptIntent.ENABLE else BiometricPromptIntent.DISABLE) },
        )
      },
      colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      modifier =
        Modifier.springClickable(
          enabled = canUseBiometric || biometricLockEnabled,
          onClick = { onRequestPrompt(if (biometricLockEnabled) BiometricPromptIntent.DISABLE else BiometricPromptIntent.ENABLE) },
        ),
    )
    AboutLockNote()
  }
}

/** A plain sentence under the switch, so the group does not pretend to be one more control. */
@Composable
private fun AboutLockNote() {
  Text(
    text = "The lock is asked once per launch. Codes never leave this device either way.",
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
  )
}

/** The CI-supplied version name, read once — the header of both the about row and the updates card. */
@Composable
private fun rememberInstalledVersionName(): String {
  val context = LocalContext.current
  return remember(context) {
    @Suppress("DEPRECATION")
    runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
      .getOrNull()
      .orEmpty()
  }
}

@Composable
private fun AboutSection(versionName: String) {
  val context = LocalContext.current

  var showAboutDialog by remember { mutableStateOf(false) }
  var showPrivacyDialog by remember { mutableStateOf(false) }
  var showLicensesDialog by remember { mutableStateOf(false) }
  var showDeveloperDialog by remember { mutableStateOf(false) }

  SettingsSection(title = "About") {
    AboutRow(title = "About Stitch", supportingText = "Version $versionName") { showAboutDialog = true }
    AboutRow(title = "Privacy policy") { showPrivacyDialog = true }
    AboutRow(title = "Open source licenses", supportingText = "GNU GPL v3") { showLicensesDialog = true }
    AboutRow(title = "Developer") { showDeveloperDialog = true }
    AboutRow(
      title = "GitHub",
      supportingText = "Source code and issues",
      onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubRepositoryUrl))) },
    )
  }

  if (showAboutDialog) {
    InfoDialog(
      title = "About Stitch",
      message =
        "Stitch Authenticator $versionName\n\n" +
          "A privacy-first TOTP authenticator. Everything stays on this device: no cloud, no " +
          "accounts, no tracking.\n\n" +
          "Free software, licensed under the GNU General Public License version 3.",
      onDismiss = { showAboutDialog = false },
    )
  }

  if (showPrivacyDialog) {
    InfoDialog(
      title = "Privacy policy",
      message =
        "Stitch collects no data and needs no account. Codes are generated and stored only on " +
          "this device, encrypted with a key that never leaves the Android Keystore. " +
          "Uninstalling the app deletes them — there is no server to restore from, and none to " +
          "hand anything over from.\n\n" +
          "Update checks happen only when you tap the button in Settings: the app asks GitHub " +
          "directly, for a release, and nothing is checked in the background.",
      onDismiss = { showPrivacyDialog = false },
    )
  }

  if (showLicensesDialog) {
    InfoDialog(
      title = "Open source licenses",
      message =
        "Stitch is free software licensed under the GNU General Public License version 3.0. " +
          "Its source code, including every dependency's attribution, is available on GitHub.",
      onDismiss = { showLicensesDialog = false },
    )
  }

  if (showDeveloperDialog) {
    InfoDialog(
      title = "Developer",
      message =
        "Stitch is designed, built and maintained by @MoHamed-B-M — an independent Android " +
          "developer focused on privacy-first software.\n\n" +
          "Built in Kotlin with Jetpack Compose and Material 3. Bug reports and pull requests " +
          "are welcome on GitHub.",
      onDismiss = { showDeveloperDialog = false },
    )
  }
}

@Composable
private fun AboutRow(
  title: String,
  supportingText: String? = null,
  onClick: () -> Unit,
) {
  ListItem(
    headlineContent = { Text(title) },
    supportingContent = supportingText?.let { text -> { Text(text) } },
    trailingContent = { Text("›", style = MaterialTheme.typography.titleLarge) },
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier = Modifier.springClickable(onClick = onClick),
  )
}

/** One dismissible dialog; the "Close" button is the same tap as tapping outside. */
@Composable
private fun InfoDialog(
  title: String,
  message: String,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(title) },
    text = { Text(message) },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text("Close") }
    },
  )
}

@Preview(name = "Settings", showBackground = true)
@Composable
private fun SettingsContentPreview() {
  StitchAuthenticatorTheme {
    SettingsContent(
      state =
        SettingsUiState(
          themeMode = ThemeMode.SYSTEM,
          dynamicColorEnabled = true,
          biometricLockEnabled = false,
        ),
      onBackClick = {},
      onThemeModeSelected = {},
      onDynamicColorToggle = {},
      onBiometricLockChange = {},
      canUseBiometric = true,
      updatesState = UpdateUiState.Idle,
      updateChannel = UpdateChannel.STABLE,
      onUpdateChannelChange = {},
      onCheckUpdates = {},
      onDownloadUpdate = {},
      passkeys =
        listOf(
          PasskeyEntity(
            credentialId = "preview-credential",
            rpId = "example.com",
            username = "ada@example.com",
            userHandle = "YWRh",
            publicKey = "",
            keyAlias = "stitch-passkey-preview",
          ),
        ),
      onDeletePasskey = {},
    )
  }
}

@Preview(name = "Settings, dark, locked", showBackground = true)
@Composable
private fun SettingsContentDarkPreview() {
  StitchAuthenticatorTheme {
    SettingsContent(
      state =
        SettingsUiState(
          themeMode = ThemeMode.DARK,
          dynamicColorEnabled = false,
          biometricLockEnabled = true,
        ),
      onBackClick = {},
      onThemeModeSelected = {},
      onDynamicColorToggle = {},
      onBiometricLockChange = {},
      canUseBiometric = false,
      updatesState = UpdateUiState.UpToDate,
      updateChannel = UpdateChannel.BETA,
      onUpdateChannelChange = {},
      onCheckUpdates = {},
      onDownloadUpdate = {},
      passkeys = emptyList(),
      onDeletePasskey = {},
    )
  }
}
