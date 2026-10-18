package com.stitch.authenticator.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.passkeys.PasskeyEntity
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncyContentSize
import com.stitch.authenticator.theme.pressScale
import com.stitch.authenticator.theme.springClickable
import com.stitch.authenticator.ui.passkeys.PasskeysViewModel
import com.stitch.authenticator.ui.update.SystemUpdateIcon
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
 * Settings: appearance, the biometric lock, saved passkeys, the system-update entry, and the
 * about rows.
 *
 * State comes from [SettingsViewModel] and [PasskeysViewModel] in production and is passed in
 * through [SettingsContent] for previews and future UI tests, so the whole screen renders
 * without storage, a Keystore, a biometric sensor or the network. Updates graduated to their
 * own screen — [onOpenSystemUpdate] is the door.
 */
@Composable
fun SettingsScreen(
  onBackClick: () -> Unit,
  onOpenSystemUpdate: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = settingsViewModel(),
  passkeysViewModel: PasskeysViewModel = passkeysViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val passkeys by passkeysViewModel.passkeys.collectAsStateWithLifecycle()
  val authState = rememberBiometricAuthState()

  SettingsContent(
    state = state,
    onBackClick = onBackClick,
    onOpenSystemUpdate = onOpenSystemUpdate,
    onThemeModeSelected = viewModel::setThemeMode,
    onDynamicColorToggle = viewModel::setDynamicColorEnabled,
    onBiometricLockChange = viewModel::setBiometricLockEnabled,
    canUseBiometric = authState.canAuthenticate,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsContent(
  state: SettingsUiState,
  onBackClick: () -> Unit,
  onOpenSystemUpdate: () -> Unit,
  onThemeModeSelected: (ThemeMode) -> Unit,
  onDynamicColorToggle: (Boolean) -> Unit,
  onBiometricLockChange: (Boolean) -> Unit,
  canUseBiometric: Boolean,
  passkeys: List<PasskeyEntity>,
  onDeletePasskey: (PasskeyEntity) -> Unit,
  modifier: Modifier = Modifier,
) {
  // Neither direction writes on tap: turning the lock on or off is confirmed with the very
  // biometrics it governs, so a stray tap can neither lock the app nor unlock it by itself.
  var pendingPrompt by remember { mutableStateOf<BiometricPromptIntent?>(null) }

  val versionName = rememberInstalledVersionName()

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        // The title moved into the list as the large expressive header below; the bar keeps
        // only the back arrow, so "Settings" is never printed twice.
        title = {},
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
        // The large, bold header: display-scale and heaviest weight, the first row of the list
        // so every group scrolls beneath it like a section of one document.
        Text(
          text = "Settings",
          style = MaterialTheme.typography.displaySmall,
          fontWeight = FontWeight.Bold,
          modifier = Modifier.padding(start = 4.dp),
        )
      }
      item {
        // Updates graduated to their own screen; Settings keeps only the door to it.
        SettingsGroup(
          title = "System update",
          items =
            listOf({
              SettingsItem(
                icon = SystemUpdateIcon,
                headline = "Check for updates",
                supporting = { Text("GitHub releases for a newer build") },
                onClick = onOpenSystemUpdate,
              )
            }),
        )
      }
      item {
        AppearanceSection(
          themeMode = state.themeMode,
          dynamicColorEnabled = state.dynamicColorEnabled,
          onThemeModeSelected = onThemeModeSelected,
          onDynamicColorToggle = onDynamicColorToggle,
        )
      }
      item {
        PrivacySection(
          biometricLockEnabled = state.biometricLockEnabled,
          canUseBiometric = canUseBiometric,
          onBiometricLockChange = onBiometricLockChange,
          onRequestPrompt = { intent -> pendingPrompt = intent },
        )
      }
      item {
        // The Passkeys group folds into this layout: same rows, same delete, new surface.
        PasskeysSection(
          passkeys = passkeys,
          onDeletePasskey = onDeletePasskey,
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
 * One group of related settings: a label over a single rounded [Surface] of [SettingsItem] rows.
 *
 * The group *is* the surface — `surfaceContainerHighest` against the screen's `surface`, 24dp
 * corners — so a screen of groups reads like Google Play's segmented lists instead of a stack of
 * per-item cards. Dividers are drawn by the group *between* items only: never under the first
 * row or over the last, so no line ever touches the rounded corners. [bouncyContentSize] keeps a
 * row arriving or leaving (the last passkey going, for example) from jumping: springs, never a
 * fixed curve.
 */
@Composable
internal fun SettingsGroup(
  title: String,
  items: List<@Composable () -> Unit>,
  modifier: Modifier = Modifier,
) {
  Column(modifier = modifier.fillMaxWidth().bouncyContentSize()) {
    Text(
      text = title,
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
      modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
    Surface(
      shape = RoundedCornerShape(24.dp),
      color = MaterialTheme.colorScheme.surfaceContainerHighest,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Column {
        items.forEachIndexed { index, row ->
          if (index > 0) HorizontalDivider()
          row()
        }
      }
    }
  }
}

/**
 * The passkeys this device can offer through the system's sign-in prompts.
 *
 * One row per stored credential — relying party over account name — with a trailing delete that
 * removes the record *and* its Keystore key (the view model owns that pairing). The empty state
 * is a row rather than a shrug, so the group's place on the screen never depends on how many
 * passkeys exist, and [bouncyContentSize] (inherited from [SettingsGroup]) springs it closed
 * when the last one goes. The rows are built as an explicit list so the group owns the dividers
 * between them.
 */
@Composable
private fun PasskeysSection(
  passkeys: List<PasskeyEntity>,
  onDeletePasskey: (PasskeyEntity) -> Unit,
) {
  val rows: List<@Composable () -> Unit> =
    if (passkeys.isEmpty()) {
      listOf({
        SettingsItem(
          icon = Icons.Filled.Lock,
          headline = "No passkeys yet",
          supporting = { Text("Saved passkeys appear here and in the system's sign-in prompts") },
        )
      })
    } else {
      passkeys.map { passkey ->
        {
          val interactionSource = remember { MutableInteractionSource() }
          SettingsItem(
            icon = Icons.Filled.Lock,
            headline = passkey.rpId,
            supporting = { Text(passkey.username) },
            trailing = {
              IconButton(
                onClick = { onDeletePasskey(passkey) },
                interactionSource = interactionSource,
                // The house press feel even on the delete: it scales down and springs back
                // with the rest of the group instead of merely rippling.
                modifier = Modifier.pressScale(interactionSource),
              ) {
                Icon(
                  imageVector = Icons.Filled.Delete,
                  contentDescription = "Delete passkey for ${passkey.rpId}",
                )
              }
            },
          )
        }
      }
    }

  SettingsGroup(title = "Passkeys", items = rows)
}

@Composable
private fun AppearanceSection(
  themeMode: ThemeMode,
  dynamicColorEnabled: Boolean,
  onThemeModeSelected: (ThemeMode) -> Unit,
  onDynamicColorToggle: (Boolean) -> Unit,
) {
  SettingsGroup(
    title = "Appearance",
    items =
      listOf(
        {
          // The segmented selector is wider than any trailing slot — squeezed beside an icon
          // it would starve the headline at 360dp — so it takes the row's full width under the
          // text instead. One item, no divider between a label and the control it names.
          Column(Modifier.fillMaxWidth()) {
            SettingsItem(
              icon = SunIcon,
              headline = "Theme",
              supporting = { Text(themeMode.label) },
            )
            ThemeModeSelector(
              selected = themeMode,
              onSelect = onThemeModeSelected,
              modifier =
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
          }
        },
        {
          SettingsItem(
            icon = PaletteIcon,
            headline = "Dynamic color",
            supporting = { Text("Follow the system wallpaper palette") },
            onClick = { onDynamicColorToggle(!dynamicColorEnabled) },
            trailing = {
              Switch(checked = dynamicColorEnabled, onCheckedChange = onDynamicColorToggle)
            },
          )
        },
      ),
  )
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
  modifier: Modifier = Modifier,
) {
  SingleChoiceSegmentedButtonRow(modifier = modifier) {
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
private fun PrivacySection(
  biometricLockEnabled: Boolean,
  canUseBiometric: Boolean,
  onBiometricLockChange: (Boolean) -> Unit,
  onRequestPrompt: (BiometricPromptIntent) -> Unit,
) {
  SettingsGroup(
    title = "Privacy",
    items =
      listOf({
        // Row and footnote are a single item: no divider ever sits between a setting and the
        // sentence that explains it, and with one item the group draws no dividers at all.
        Column(Modifier.fillMaxWidth()) {
          SettingsItem(
            icon = ShieldIcon,
            headline = "Biometric lock",
            supporting = {
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
            onClick = {
              onRequestPrompt(
                if (biometricLockEnabled) BiometricPromptIntent.DISABLE else BiometricPromptIntent.ENABLE
              )
            },
            // The locked-off row stays tappable only while the sensor can actually answer;
            // the locked-on switch below is the escape hatch when fingerprints are gone.
            enabled = canUseBiometric || biometricLockEnabled,
            trailing = {
              Switch(
                checked = biometricLockEnabled,
                // The locked-on switch stays usable: someone who removed their fingerprints must
                // still be able to turn the lock off — otherwise the app is a locked door with
                // no key.
                enabled = canUseBiometric || biometricLockEnabled,
                onCheckedChange = { requested ->
                  onRequestPrompt(
                    if (requested) BiometricPromptIntent.ENABLE else BiometricPromptIntent.DISABLE
                  )
                },
              )
            },
          )
          AboutLockNote()
        }
      }),
  )
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

/** The CI-supplied version name, read once — the supporting text of the About Stitch row. */
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

  SettingsGroup(
    title = "About",
    items =
      listOf(
        {
          SettingsItem(
            icon = Icons.Filled.Info,
            headline = "About Stitch",
            supporting = { Text("Version $versionName") },
            onClick = { showAboutDialog = true },
          )
        },
        {
          SettingsItem(
            icon = Icons.Filled.Person,
            headline = "About Me",
            onClick = { showDeveloperDialog = true },
          )
        },
        {
          SettingsItem(
            icon = CodeIcon,
            headline = "GitHub",
            supporting = { Text("Source code and issues") },
            onClick = {
              context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GitHubRepositoryUrl)))
            },
          )
        },
        {
          SettingsItem(
            icon = Icons.Filled.Lock,
            headline = "Privacy policy",
            onClick = { showPrivacyDialog = true },
          )
        },
        {
          SettingsItem(
            icon = LinesIcon,
            headline = "Open source licenses",
            supporting = { Text("GNU GPL v3") },
            onClick = { showLicensesDialog = true },
          )
        },
      ),
  )

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
      title = "About Me",
      message =
        "Stitch is designed, built and maintained by @MoHamed-B-M — an independent Android " +
          "developer focused on privacy-first software.\n\n" +
          "Built in Kotlin with Jetpack Compose and Material 3. Bug reports and pull requests " +
          "are welcome on GitHub.",
      onDismiss = { showDeveloperDialog = false },
    )
  }
}

/**
 * One row inside a [SettingsGroup]: a leading icon, a headline, an optional supporting slot, and
 * a trailing control — a chevron when the row opens something, a switch when it *is* the setting.
 *
 * Tapping the row scales it down and springs it back: [springClickable] wires the house
 * [pressScale] token through its own [MutableInteractionSource], exactly like the main screen's
 * cards. A trailing control keeps its own gesture — the child consumes the tap, so flipping a
 * switch never also fires the row it sits in.
 */
@Composable
internal fun SettingsItem(
  icon: ImageVector,
  headline: String,
  modifier: Modifier = Modifier,
  supporting: (@Composable () -> Unit)? = null,
  onClick: (() -> Unit)? = null,
  enabled: Boolean = true,
  trailing: (@Composable () -> Unit)? = null,
) {
  // The row's own trailing, when it opens something and no control was handed in.
  val chevron: (@Composable () -> Unit)? =
    if (onClick == null) {
      null
    } else {
      { Text("›", style = MaterialTheme.typography.titleLarge) }
    }

  ListItem(
    headlineContent = { Text(headline) },
    supportingContent = supporting,
    leadingContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
      )
    },
    trailingContent = trailing ?: chevron,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    modifier =
      if (onClick != null) modifier.springClickable(enabled = enabled, onClick = onClick)
      else modifier,
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
      onOpenSystemUpdate = {},
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
      onOpenSystemUpdate = {},
      passkeys = emptyList(),
      onDeletePasskey = {},
    )
  }
}
