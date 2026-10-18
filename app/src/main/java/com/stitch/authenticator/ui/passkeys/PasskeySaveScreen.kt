package com.stitch.authenticator.ui.passkeys

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.theme.StitchMotion
import kotlinx.coroutines.launch

/**
 * The stand-in between a scanned passkey payload and the vault: an M3 bottom sheet that shows
 * what the camera read (domain guess, editable) and asks for the account name before minting
 * anything.
 *
 * The sheet — not the navigation — is what the user dismisses, so dismissal springs the sheet
 * down *first* and only then pops this entry; the destination never yanks a visible sheet away.
 * A `FIDO:/` handle, a `webauthn://` link, or a WebAuthn request JSON all land here (the scanner
 * routes them instead of calling them invalid codes), and an unparseable payload simply starts
 * with an empty domain the user types into.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasskeySaveScreen(
  payload: String,
  onDone: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: PasskeySaveViewModel = passkeySaveViewModel(payload),
) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  val scope = rememberCoroutineScope()

  // onDone must fire exactly once: the saved-state effect and the sheet's own dismissal both
  // funnel through [finish], and a second pop would take the entry below this one too.
  var finished by remember { mutableStateOf(false) }
  val currentOnDone by rememberUpdatedState(onDone)
  val finish = {
    if (!finished) {
      finished = true
      currentOnDone()
    }
  }
  // Spring the sheet closed, then leave — one path for swipe-dismiss and for a completed save.
  val dismiss = {
    scope.launch {
      sheetState.hide()
      finish()
    }
  }

  LaunchedEffect(state.saved) {
    if (state.saved) dismiss()
  }

  ModalBottomSheet(
    onDismissRequest = { dismiss() },
    sheetState = sheetState,
    modifier = modifier,
  ) {
    PasskeySaveContent(
      state = state,
      onDomainChange = viewModel::onDomainChange,
      onUsernameChange = viewModel::onUsernameChange,
      onSave = viewModel::save,
    )
  }
}

/**
 * The sheet body: identity of what is being saved, the two fields, and one full-width action
 * that swaps itself for the confirmation with a bouncy fade once the write lands.
 */
@Composable
private fun PasskeySaveContent(
  state: PasskeySaveUiState,
  onDomainChange: (String) -> Unit,
  onUsernameChange: (String) -> Unit,
  onSave: () -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp),
  ) {
    Text("Save passkey", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
      text = "Stores a passkey for this website on your device. It only works with sites that " +
        "trust this app as a passkey provider.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(24.dp))
    OutlinedTextField(
      value = state.domain,
      onValueChange = onDomainChange,
      label = { Text("Website") },
      singleLine = true,
      enabled = !state.saving,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
      value = state.username,
      onValueChange = onUsernameChange,
      label = { Text("Account name") },
      singleLine = true,
      enabled = !state.saving,
      modifier = Modifier.fillMaxWidth(),
    )
    state.error?.let { message ->
      Spacer(Modifier.height(12.dp))
      Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    Spacer(Modifier.height(24.dp))
    AnimatedContent(
      targetState = state.saved,
      transitionSpec = {
        fadeIn(animationSpec = StitchMotion.BouncyQuick) togetherWith
          fadeOut(animationSpec = StitchMotion.Press)
      },
      label = "passkeySaveAction",
    ) { saved ->
      if (saved) {
        Text(
          text = "Passkey saved",
          style = MaterialTheme.typography.titleMedium,
          color = MaterialTheme.colorScheme.primary,
          textAlign = TextAlign.Center,
          modifier = Modifier.fillMaxWidth(),
        )
      } else {
        Button(
          onClick = onSave,
          enabled = !state.saving && state.domain.isNotBlank() && state.username.isNotBlank(),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(if (state.saving) "Saving…" else "Save passkey")
        }
      }
    }
  }
}

/** Production wiring: the same repository instance the credential provider service reads. */
@Composable
private fun passkeySaveViewModel(payload: String): PasskeySaveViewModel {
  val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
  return viewModel {
    PasskeySaveViewModel(
      initialPayload = payload,
      repository = AppGraph.passkeyRepository(context),
    )
  }
}
