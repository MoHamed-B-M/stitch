package com.stitch.authenticator.ui.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.data.vault.PasswordEntry
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.ui.settings.SettingsGroup
import com.stitch.authenticator.util.BiometricAuthResult
import com.stitch.authenticator.util.BiometricAuthenticator

/**
 * The password vault tab: a biometric gate, then the list.
 *
 * [BiometricAuthenticator] hosts the prompt and hands the content its trigger; entering the tab
 * in the locked state *is* the prompt (the [LaunchedEffect] below), and a cancelled or failed
 * attempt simply leaves the locked screen standing — the database is untouched either way.
 * Leaving the tab clears this entry and its view model, so every return re-prompts: the vault
 * is as locked as the tab is closed.
 */
@Composable
internal fun PasswordsScreen(
  modifier: Modifier = Modifier,
  viewModel: PasswordsViewModel = passwordsViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  PasswordsContent(
    state = state,
    onQueryChange = viewModel::setQuery,
    onUnlock = viewModel::unlock,
    modifier = modifier,
  )
}

/** Production wiring: the same repository instance the graph keeps for the whole process. */
@Composable
private fun passwordsViewModel(): PasswordsViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { PasswordsViewModel(AppGraph.vaultRepository(context)) }
}

@Composable
internal fun PasswordsContent(
  state: PasswordsUiState,
  onQueryChange: (String) -> Unit,
  onUnlock: () -> Unit,
  modifier: Modifier = Modifier,
) {
  BiometricAuthenticator(
    title = "Unlock passwords",
    subtitle = "Your passwords are locked",
    onResult = { result -> if (result == BiometricAuthResult.SUCCESS) onUnlock() },
  ) { requestUnlock ->
    // Prompt the moment the tab is entered in its locked state. Cancellation leaves the state
    // *Locked* — the same object — so this effect does not re-fire; the button is the retry.
    LaunchedEffect(Unit) {
      if (state is PasswordsUiState.Locked) requestUnlock()
    }

    when (val current = state) {
      is PasswordsUiState.Locked -> VaultLockedContent(requestUnlock, modifier)
      is PasswordsUiState.Loading -> VaultLoadingContent(modifier)
      is PasswordsUiState.Ready -> PasswordListContent(current, onQueryChange, modifier)
      // Retrying re-runs the *prompt*, not the unlock: the biometric gate stays in front of
      // every attempt to open the database.
      is PasswordsUiState.Error -> VaultErrorContent(requestUnlock, modifier)
    }
  }
}

@Composable
private fun VaultLockedContent(onUnlockClick: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Icon(
      imageVector = Icons.Filled.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(48.dp),
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text("Vault locked", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "Use your fingerprint, face, or screen lock to open your passwords.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onUnlockClick) { Text("Unlock") }
  }
}

@Composable
private fun VaultLoadingContent(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun VaultErrorContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text("Couldn't open the vault", style = MaterialTheme.typography.headlineSmall)
    Spacer(modifier = Modifier.height(8.dp))
    Text(
      text = "The password database failed to open. Everything in it stays encrypted.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
    Spacer(modifier = Modifier.height(24.dp))
    Button(onClick = onRetry) { Text("Try again") }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PasswordListContent(
  state: PasswordsUiState.Ready,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier,
    topBar = { TopAppBar(title = { Text("Passwords") }) },
    contentWindowInsets = WindowInsets.safeDrawing,
  ) { padding ->
    LazyColumn(
      modifier = Modifier.fillMaxSize(),
      contentPadding = padding + PaddingValues(horizontal = 16.dp, vertical = 8.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
      item(key = "search") {
        PasswordSearchField(query = state.query, onQueryChange = onQueryChange)
      }
      if (state.entries.isEmpty()) {
        item(key = "empty") {
          EmptyPasswords(hasQuery = state.query.isNotEmpty(), query = state.query)
        }
      } else {
        // The Phase 9 idiom, reused outright: one rounded group, dividers between rows only.
        item(key = "saved") {
          SettingsGroup(
            title = "Saved",
            items = state.entries.map { entry -> { PasswordRow(entry) } },
          )
        }
      }
    }
  }
}

@Composable
private fun PasswordSearchField(query: String, onQueryChange: (String) -> Unit) {
  OutlinedTextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = Modifier.fillMaxWidth(),
    singleLine = true,
    // The pill is the same rounded language as the grouped cards — expressive, not the default
    // box — and the label rather than a bare placeholder so TalkBack always names the field.
    shape = RoundedCornerShape(28.dp),
    label = { Text("Search passwords") },
    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Search, contentDescription = null) },
    trailingIcon =
      if (query.isEmpty()) {
        null
      } else {
        {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(Icons.Filled.Close, contentDescription = "Clear search")
          }
        }
      },
  )
}

/**
 * One saved login as an informational row — no tap affordance, deliberately: the detail screen
 * does not exist yet, and a ripple that opens nothing is a lie about what the app can do. The
 * row gains its chevron the day the form lands.
 */
@Composable
private fun PasswordRow(entry: PasswordEntry) {
  Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
    Text(entry.title, style = MaterialTheme.typography.titleMedium)
    if (entry.username.isNotBlank()) {
      Text(
        entry.username,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
  }
}

@Composable
private fun EmptyPasswords(hasQuery: Boolean, query: String) {
  Column(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(
      text = if (hasQuery) "No matches" else "No passwords yet",
      style = MaterialTheme.typography.titleMedium,
    )
    Spacer(modifier = Modifier.height(4.dp))
    Text(
      text =
        if (hasQuery) {
          "Nothing matches \u201C$query\u201D — try another title, username, or website."
        } else {
          "Passwords you save will appear here, encrypted on this device."
        },
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

@Preview(name = "Vault locked", showBackground = true)
@Composable
private fun VaultLockedPreview() {
  StitchAuthenticatorTheme { VaultLockedContent(onUnlockClick = {}) }
}

@Preview(name = "Passwords list", showBackground = true)
@Composable
private fun PasswordListPreview() {
  StitchAuthenticatorTheme {
    PasswordListContent(
      state =
        PasswordsUiState.Ready(
          query = "git",
          entries =
            listOf(
              previewEntry(title = "GitHub", username = "octocat"),
              previewEntry(title = "Vercel", username = "mohamed"),
            ),
        ),
      onQueryChange = {},
    )
  }
}

private fun previewEntry(title: String, username: String) =
  PasswordEntry(title = title, username = username, password = "hunter2", createdAt = 0L)
