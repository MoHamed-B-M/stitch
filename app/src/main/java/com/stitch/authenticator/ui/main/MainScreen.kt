package com.stitch.authenticator.ui.main

import android.content.ClipData
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.R
import com.stitch.authenticator.data.DemoAccountsRepository
import com.stitch.authenticator.model.AuthenticatorAccount
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.ui.components.AccountCard
import com.stitch.authenticator.ui.components.EmptyState
import com.stitch.authenticator.ui.components.ErrorState
import com.stitch.authenticator.ui.components.LoadingState
import com.stitch.authenticator.ui.components.NoResultsState
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
  modifier: Modifier = Modifier,
  viewModel: MainScreenViewModel = viewModel { MainScreenViewModel(DemoAccountsRepository()) },
) {
  val state by viewModel.tickingUiState.collectAsStateWithLifecycle()
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val clipboard = LocalClipboard.current

  MainScreenContent(
    state = state,
    onQueryChange = viewModel::onQueryChange,
    onRetry = viewModel::retry,
    onCopy = { account ->
      scope.launch {
        clipboard.setClipEntry(
          ClipData
            .newPlainText(context.getString(R.string.account_code), account.code)
            .toClipEntry(),
        )
        snackbarHostState.showSnackbar(context.getString(R.string.copied_code, account.issuer))
      }
    },
    onAddOptionSelected = {
      scope.launch {
        snackbarHostState.showSnackbar(context.getString(R.string.placeholder_action_message))
      }
    },
    snackbarHostState = snackbarHostState,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
  state: MainScreenUiState,
  onQueryChange: (String) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  onCopy: (AuthenticatorAccount) -> Unit = {},
  onAddOptionSelected: (String) -> Unit = {},
  snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
  var searchVisible by rememberSaveable { mutableStateOf(false) }
  var addSheetVisible by rememberSaveable { mutableStateOf(false) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    containerColor = MaterialTheme.colorScheme.background,
    snackbarHost = { SnackbarHost(snackbarHostState) },
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.accounts_title)) },
        actions = {
          IconButton(
            onClick = {
              searchVisible = !searchVisible
              if (!searchVisible) onQueryChange("")
            },
          ) {
            Icon(
              imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
              contentDescription =
                if (searchVisible) stringResource(R.string.clear_search)
                else stringResource(R.string.search_accounts),
            )
          }
        },
      )
    },
    floatingActionButton = {
      ExtendedFloatingActionButton(
        onClick = { addSheetVisible = true },
        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
        text = { Text(text = stringResource(R.string.add_account_fab)) },
      )
    },
  ) { paddingValues ->
    Column(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(paddingValues)
          .padding(horizontal = 16.dp),
    ) {
      PrivacyLine()
      AnimatedVisibility(
        visible = searchVisible,
        enter =
          fadeIn(animationSpec = StitchMotion.standard()) +
            slideInVertically(animationSpec = StitchMotion.emphasized()) { -it / 4 },
        exit =
          fadeOut(animationSpec = StitchMotion.standard()) +
            slideOutVertically(animationSpec = StitchMotion.emphasized()) { -it / 4 },
      ) {
        Column {
          Spacer(Modifier.height(8.dp))
          AccountSearchField(query = state.query, onQueryChange = onQueryChange)
        }
      }
      Spacer(Modifier.height(12.dp))

      when {
        state.isLoading -> LoadingState(modifier = Modifier.weight(1f))
        state.errorMessage != null -> ErrorState(onRetry = onRetry, modifier = Modifier.weight(1f))
        state.isEmpty -> EmptyState(modifier = Modifier.weight(1f))
        state.isNoResults -> NoResultsState(modifier = Modifier.weight(1f))
        else -> {
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            state.filteredAccounts.forEach { account ->
              AccountCard(account = account, onCopy = onCopy)
            }
          }
        }
      }
      Spacer(Modifier.height(88.dp))
    }
  }

  if (addSheetVisible) {
    AddAccountSheet(
      onDismiss = { addSheetVisible = false },
      onOptionSelected = { label ->
        addSheetVisible = false
        onAddOptionSelected(label)
      },
    )
  }
}

@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Icon(
      imageVector = Icons.Filled.Lock,
      contentDescription = null,
      tint = MaterialTheme.colorScheme.primary,
      modifier = Modifier.size(16.dp),
    )
    Spacer(Modifier.width(8.dp))
    Text(
      text = stringResource(R.string.accounts_subtitle),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@Composable
private fun AccountSearchField(
  query: String,
  onQueryChange: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current

  LaunchedEffect(Unit) { focusRequester.requestFocus() }

  TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier
      .fillMaxWidth()
      .height(56.dp)
      .focusRequester(focusRequester),
    placeholder = {
      Text(
        text = stringResource(R.string.search_accounts),
        style = MaterialTheme.typography.bodyLarge,
      )
    },
    leadingIcon = {
      Icon(
        imageVector = Icons.Filled.Search,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
    trailingIcon =
      if (query.isNotEmpty()) {
        {
          IconButton(onClick = { onQueryChange("") }) {
            Icon(
              imageVector = Icons.Filled.Close,
              contentDescription = stringResource(R.string.clear_search),
            )
          }
        }
      } else {
        null
      },
    singleLine = true,
    shape = MaterialTheme.shapes.extraLarge,
    colors =
      TextFieldDefaults.colors(
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
      ),
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions =
      KeyboardActions(
        onSearch = {
          keyboardController?.hide()
          focusRequester.freeFocus()
        },
      ),
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddAccountSheet(
  onDismiss: () -> Unit,
  onOptionSelected: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val sheetState =
    rememberBottomSheetState(
      initialValue = SheetValue.Hidden,
      enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
  val scanLabel = stringResource(R.string.scan_qr_code)
  val manualLabel = stringResource(R.string.enter_manually)

  ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = sheetState,
    modifier = modifier,
    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
      Text(
        text = stringResource(R.string.add_account_sheet_title),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(horizontal = 4.dp),
      )
      Spacer(Modifier.height(12.dp))
      SheetOption(
        icon = Icons.Filled.QrCodeScanner,
        label = scanLabel,
        supporting = stringResource(R.string.scan_qr_code_supporting),
        onClick = { onOptionSelected(scanLabel) },
      )
      SheetOption(
        icon = Icons.Filled.Keyboard,
        label = manualLabel,
        supporting = stringResource(R.string.enter_manually_supporting),
        onClick = { onOptionSelected(manualLabel) },
      )
      Spacer(Modifier.height(24.dp))
    }
  }
}

@Composable
private fun SheetOption(
  icon: ImageVector,
  label: String,
  supporting: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  ListItem(
    onClick = onClick,
    modifier = modifier.fillMaxWidth(),
    leadingContent = {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
    },
    supportingContent = {
      Text(
        text = supporting,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    },
  ) {
    Text(text = label, style = MaterialTheme.typography.titleMedium)
  }
}

private val previewAccounts =
  listOf(
    AuthenticatorAccount("github", "GitHub", "maya@stitch.app", "482 913"),
    AuthenticatorAccount("google", "Google", "maya@gmail.com", "119 204", secondsRemaining = 24),
    AuthenticatorAccount("proton", "Proton", "maya@pm.me", "730 558", secondsRemaining = 12),
  )

@Preview(showBackground = true)
@Composable
private fun AccountsPreview() {
  StitchAuthenticatorTheme {
    MainScreenContent(
      state = MainScreenUiState(isLoading = false, accounts = previewAccounts),
      onQueryChange = {},
      onRetry = {},
    )
  }
}

@Preview(showBackground = true)
@Composable
private fun AccountsLoadingPreview() {
  StitchAuthenticatorTheme {
    MainScreenContent(state = MainScreenUiState(isLoading = true), onQueryChange = {}, onRetry = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun AccountsEmptyPreview() {
  StitchAuthenticatorTheme {
    MainScreenContent(state = MainScreenUiState(isLoading = false), onQueryChange = {}, onRetry = {})
  }
}

@Preview(showBackground = true)
@Composable
private fun AccountsErrorPreview() {
  StitchAuthenticatorTheme {
    MainScreenContent(
      state = MainScreenUiState(isLoading = false, errorMessage = "Unable to load"),
      onQueryChange = {},
      onRetry = {},
    )
  }
}
