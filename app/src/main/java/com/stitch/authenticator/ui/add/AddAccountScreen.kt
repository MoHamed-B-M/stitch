package com.stitch.authenticator.ui.add

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.crypto.TotpEngine
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.OtpAlgorithm
import com.stitch.authenticator.data.OtpAuthUri
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.theme.settleSpring
import com.stitch.authenticator.util.Clipboard

private val ScreenPadding = 16.dp

/**
 * Adding one account, either by typing the values or by pasting an `otpauth://` link.
 *
 * Dismisses itself once [AddAccountViewModel] has confirmed the write: the row the new account
 * deserves only exists after storage says it does, and the caller's [onDone] is the same pop the
 * system back gesture uses, so the exit animation is the same one either way.
 */
@Composable
fun AddAccountScreen(
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: AddAccountViewModel = addAccountViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  // Flips to true exactly once — when the write landed — so this fires once and never re-fires on
  // a configuration change.
  LaunchedEffect(state.saved) {
    if (state.saved) onDone()
  }

  AddAccountContent(
    state = state,
    onModeChange = viewModel::onModeChange,
    onLinkChange = viewModel::onLinkChange,
    onIssuerChange = viewModel::onIssuerChange,
    onAccountNameChange = viewModel::onAccountNameChange,
    onSecretChange = viewModel::onSecretChange,
    onAlgorithmChange = viewModel::onAlgorithmChange,
    onDigitsChange = viewModel::onDigitsChange,
    onPeriodChange = viewModel::onPeriodChange,
    onToggleAdvanced = viewModel::onAdvancedToggle,
    onSave = viewModel::save,
    onDismiss = onDone,
    modifier = modifier,
  )
}

/** Production wiring: the same repository instance [com.stitch.authenticator.ui.main.MainScreen] reads. */
@Composable
private fun addAccountViewModel(): AddAccountViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { AddAccountViewModel(AppGraph.tokenRepository(context)) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AddAccountContent(
  state: AddAccountUiState,
  onModeChange: (AddAccountMode) -> Unit,
  onLinkChange: (String) -> Unit,
  onIssuerChange: (String) -> Unit,
  onAccountNameChange: (String) -> Unit,
  onSecretChange: (String) -> Unit,
  onAlgorithmChange: (OtpAlgorithm) -> Unit,
  onDigitsChange: (Int) -> Unit,
  onPeriodChange: (String) -> Unit,
  onToggleAdvanced: () -> Unit,
  onSave: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    // The keyboard must never sit on top of the last field, so the whole screen lifts with it.
    modifier = modifier.imePadding(),
    topBar = {
      TopAppBar(
        title = { Text("Add account") },
        navigationIcon = {
          IconButton(onClick = onDismiss) {
            Icon(imageVector = Icons.Filled.Close, contentDescription = "Cancel")
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier =
        Modifier.fillMaxSize()
          .padding(innerPadding)
          .padding(horizontal = ScreenPadding)
          .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      ModeToggle(state = state, onModeChange = onModeChange)

      // Springs the two input surfaces past each other and lets the container's own height follow on
      // a spring (AnimatedContent animates size from ContentTransform, no tween anywhere).
      AnimatedContent(
        targetState = state.mode,
        modifier = Modifier.fillMaxWidth(),
        transitionSpec = {
          (slideInVertically(animationSpec = bouncySpring()) { fullHeight -> fullHeight / 8 } +
              fadeIn(animationSpec = StitchMotion.Smooth))
            .togetherWith(
              slideOutVertically(animationSpec = bouncySpring()) { fullHeight -> -fullHeight / 8 } +
                fadeOut(animationSpec = StitchMotion.Smooth)
            )
        },
        label = "addAccountMode",
      ) { mode ->
        when (mode) {
          AddAccountMode.Manual ->
            ManualForm(
              state = state,
              onIssuerChange = onIssuerChange,
              onAccountNameChange = onAccountNameChange,
              onSecretChange = onSecretChange,
              onAlgorithmChange = onAlgorithmChange,
              onDigitsChange = onDigitsChange,
              onPeriodChange = onPeriodChange,
              onToggleAdvanced = onToggleAdvanced,
            )
          AddAccountMode.Link -> LinkForm(state = state, onLinkChange = onLinkChange)
        }
      }

      SaveSection(state = state, onSave = onSave)

      Spacer(Modifier.height(8.dp))
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ModeToggle(state: AddAccountUiState, onModeChange: (AddAccountMode) -> Unit) {
  SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
    AddAccountMode.entries.forEachIndexed { index, entry ->
      SegmentedButton(
        selected = state.mode == entry,
        onClick = { onModeChange(entry) },
        shape = SegmentedButtonDefaults.itemShape(index = index, count = AddAccountMode.entries.size),
        label = { Text(entry.label) },
      )
    }
  }
}

@Composable
private fun ManualForm(
  state: AddAccountUiState,
  onIssuerChange: (String) -> Unit,
  onAccountNameChange: (String) -> Unit,
  onSecretChange: (String) -> Unit,
  onAlgorithmChange: (OtpAlgorithm) -> Unit,
  onDigitsChange: (Int) -> Unit,
  onPeriodChange: (String) -> Unit,
  onToggleAdvanced: () -> Unit,
) {
  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    OutlinedTextField(
      value = state.accountName,
      onValueChange = onAccountNameChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Account name") },
      placeholder = { Text("you@example.com") },
      singleLine = true,
      isError = state.accountError != null,
      supportingText = { FieldMessage(state.accountError) },
    )

    OutlinedTextField(
      value = state.issuer,
      onValueChange = onIssuerChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Service (optional)") },
      placeholder = { Text("GitHub") },
      singleLine = true,
    )

    OutlinedTextField(
      value = state.secret,
      onValueChange = onSecretChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("Setup key") },
      placeholder = { Text("JBSWY3DPEHPK3PXP") },
      singleLine = true,
      // Base32 is uppercase by definition, so the field might as well agree with what it holds.
      keyboardOptions =
        KeyboardOptions(
          keyboardType = KeyboardType.Ascii,
          capitalization = KeyboardCapitalization.Characters,
        ),
      isError = state.secretError != null,
      supportingText = {
        FieldMessage(state.secretError, hint = "The Base32 key your service gave you")
      },
    )

    AdvancedOptions(
      state = state,
      onAlgorithmChange = onAlgorithmChange,
      onDigitsChange = onDigitsChange,
      onPeriodChange = onPeriodChange,
      onToggleAdvanced = onToggleAdvanced,
    )
  }
}

@Composable
private fun LinkForm(state: AddAccountUiState, onLinkChange: (String) -> Unit) {
  val context = LocalContext.current

  Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
    OutlinedTextField(
      value = state.link,
      onValueChange = onLinkChange,
      modifier = Modifier.fillMaxWidth(),
      label = { Text("otpauth:// link") },
      placeholder = { Text("otpauth://totp/GitHub:you?secret=…") },
      minLines = 2,
      maxLines = 4,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
      isError = state.showLinkError,
      supportingText = {
        FieldMessage(
          message = if (state.showLinkError) state.linkError?.message else null,
          hint = "Paste the setup link your service gave you",
        )
      },
    )

    TextButton(onClick = { Clipboard.readText(context)?.let(onLinkChange) }) {
      Text("Paste from clipboard")
    }

    state.linkPayload?.let { payload -> LinkPreview(payload) }
  }
}

/**
 * What a pasted link is about to add: enough of it to recognise, never the whole key — four
 * characters distinguish one key from another without turning the screen into a readable copy of it.
 */
@Composable
private fun LinkPreview(payload: OtpAuthUri.Payload) {
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(
        text = "Ready to add",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Text(text = payload.title, style = MaterialTheme.typography.titleMedium)
      if (payload.subtitle.isNotEmpty()) {
        Text(
          text = payload.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Key ${payload.secret.masked()}",
        style = MaterialTheme.typography.bodyMedium,
      )
      Text(
        text = "${payload.algorithm.label} · ${payload.digits} digits · ${payload.period}s",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun AdvancedOptions(
  state: AddAccountUiState,
  onAlgorithmChange: (OtpAlgorithm) -> Unit,
  onDigitsChange: (Int) -> Unit,
  onPeriodChange: (String) -> Unit,
  onToggleAdvanced: () -> Unit,
) {
  // Closed by default: SHA-1, 6 digits and 30 seconds cover nearly every account ever issued, so the
  // form stays four fields for the people who never need the rest.
  val rotation by animateFloatAsState(
    targetValue = if (state.advancedExpanded) 180f else 0f,
    animationSpec = bouncySpring(),
    label = "advancedChevron",
  )

  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    TextButton(onClick = onToggleAdvanced) {
      Text(if (state.advancedExpanded) "Hide advanced options" else "Advanced options")
      Spacer(Modifier.width(6.dp))
      Icon(
        imageVector = Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        modifier = Modifier.size(18.dp).graphicsLayer { rotationZ = rotation },
      )
    }

    AnimatedVisibility(
      visible = state.advancedExpanded,
      enter =
        fadeIn(animationSpec = StitchMotion.Smooth) +
          expandVertically(animationSpec = bouncySpring()),
      exit =
        fadeOut(animationSpec = StitchMotion.Smooth) +
          shrinkVertically(animationSpec = settleSpring()),
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        FieldLabel("Hash algorithm")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          OtpAlgorithm.entries.forEachIndexed { index, algorithm ->
            SegmentedButton(
              selected = state.algorithm == algorithm,
              onClick = { onAlgorithmChange(algorithm) },
              shape = SegmentedButtonDefaults.itemShape(index = index, count = OtpAlgorithm.entries.size),
              label = { Text(algorithm.label) },
            )
          }
        }

        FieldLabel("Digits")
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
          // The two counts the engine supports are also the two this row can show, which is why
          // [com.stitch.authenticator.data.OtpAuthUri] turns down anything between them.
          listOf(TotpEngine.MIN_DIGITS, TotpEngine.MAX_DIGITS).forEachIndexed { index, digits ->
            SegmentedButton(
              selected = state.digits == digits,
              onClick = { onDigitsChange(digits) },
              shape =
                SegmentedButtonDefaults.itemShape(index = index, count = 2),
              label = { Text(digits.toString()) },
            )
          }
        }

        OutlinedTextField(
          value = state.period,
          onValueChange = onPeriodChange,
          modifier = Modifier.fillMaxWidth(),
          label = { Text("Period (seconds)") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          isError = state.periodError != null,
          supportingText = {
            FieldMessage(state.periodError, hint = "Almost every service uses 30")
          },
        )
      }
    }
  }
}

@Composable
private fun SaveSection(state: AddAccountUiState, onSave: () -> Unit) {
  Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Button(
      onClick = onSave,
      // Stays enabled while the form is wrong: a button that does nothing tells nobody what is
      // wrong with it. Only an in-flight write suppresses the tap.
      enabled = !state.saving,
      modifier = Modifier.fillMaxWidth(),
    ) {
      if (state.saving) {
        CircularProgressIndicator(
          modifier = Modifier.size(18.dp),
          strokeWidth = 2.dp,
          color = LocalContentColor.current,
        )
      } else {
        Text("Add account")
      }
    }

    FieldMessage(message = state.saveError)
  }
}

/** The small print under a field: the error when there is one, otherwise the hint. */
@Composable
private fun FieldMessage(message: String?, hint: String? = null) {
  AnimatedVisibility(
    visible = message != null || hint != null,
    // Springs in and out so a validation message appears without shoving the form down by itself.
    enter =
      fadeIn(animationSpec = StitchMotion.Smooth) +
        expandVertically(animationSpec = bouncySpring()),
    exit =
      fadeOut(animationSpec = StitchMotion.Smooth) +
        shrinkVertically(animationSpec = settleSpring()),
  ) {
    val isError = message != null
    Text(
      text = message ?: hint.orEmpty(),
      style = MaterialTheme.typography.bodySmall,
      color =
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun FieldLabel(text: String) {
  Text(
    text = text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private val AddAccountMode.label: String
  get() = when (this) {
    AddAccountMode.Manual -> "Manual"
    AddAccountMode.Link -> "Link"
  }

private val OtpAlgorithm.label: String
  get() = when (this) {
    OtpAlgorithm.SHA1 -> "SHA-1"
    OtpAlgorithm.SHA256 -> "SHA-256"
    OtpAlgorithm.SHA512 -> "SHA-512"
  }

/** `JBSW…` rather than the key: enough to recognise it, not enough to use it. */
private fun String.masked(): String =
  if (length <= 4) "••••" else "${take(4)}…"

@Preview(name = "Manual", showBackground = true)
@Composable
private fun ManualPreview() {
  StitchAuthenticatorTheme {
    AddAccountContent(
      state = AddAccountUiState(),
      onModeChange = {},
      onLinkChange = {},
      onIssuerChange = {},
      onAccountNameChange = {},
      onSecretChange = {},
      onAlgorithmChange = {},
      onDigitsChange = {},
      onPeriodChange = {},
      onToggleAdvanced = {},
      onSave = {},
      onDismiss = {},
    )
  }
}

@Preview(name = "Errors", showBackground = true)
@Composable
private fun ManualErrorsPreview() {
  StitchAuthenticatorTheme {
    AddAccountContent(
      state =
        AddAccountUiState(
          accountError = "Enter an account name or service",
          secretError = "That is not a valid Base32 key",
          advancedExpanded = true,
        ),
      onModeChange = {},
      onLinkChange = {},
      onIssuerChange = {},
      onAccountNameChange = {},
      onSecretChange = {},
      onAlgorithmChange = {},
      onDigitsChange = {},
      onPeriodChange = {},
      onToggleAdvanced = {},
      onSave = {},
      onDismiss = {},
    )
  }
}

@Preview(name = "Pasted link", showBackground = true)
@Composable
private fun PastedLinkPreview() {
  StitchAuthenticatorTheme {
    AddAccountContent(
      state =
        AddAccountUiState(
          mode = AddAccountMode.Link,
          link = "otpauth://totp/GitHub:octocat?secret=JBSWY3DPEHPK3PXP&issuer=GitHub",
          linkPayload =
            OtpAuthUri.Payload(
              issuer = "GitHub",
              accountName = "octocat",
              secret = "JBSWY3DPEHPK3PXP",
              algorithm = OtpAlgorithm.SHA1,
              digits = 6,
              period = 30,
            ),
        ),
      onModeChange = {},
      onLinkChange = {},
      onIssuerChange = {},
      onAccountNameChange = {},
      onSecretChange = {},
      onAlgorithmChange = {},
      onDigitsChange = {},
      onPeriodChange = {},
      onToggleAdvanced = {},
      onSave = {},
      onDismiss = {},
    )
  }
}
