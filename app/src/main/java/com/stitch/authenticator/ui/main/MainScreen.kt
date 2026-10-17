package com.stitch.authenticator.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.theme.pressScale
import com.stitch.authenticator.util.Clipboard
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp

/** Room for the FAB (and the snackbar above it) so the last card is never covered. */
private val ListBottomPadding = 96.dp

/**
 * The token list.
 *
 * State comes from [MainViewModel] in production and is passed in for previews and future UI tests,
 * so the whole screen can be rendered without storage, a Keystore or a clock.
 */
@Composable
fun MainScreen(
  onAddAccount: () -> Unit,
  onSettingsClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  viewModel: MainViewModel = mainViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()

  MainContent(
    state = state,
    onAddAccount = onAddAccount,
    onSettingsClick = onSettingsClick,
    onDelete = viewModel::delete,
    onRetry = viewModel::refresh,
    modifier = modifier,
  )
}

/**
 * Production wiring for the token list.
 *
 * The repository comes from [AppGraph] rather than being built here: the add screen writes through
 * the very same instance, and a second repository would keep a second cache over the same encrypted
 * file. Its storage defers the Keystore and file until the first read, which the repository already
 * runs on its IO dispatcher, so nothing touches disk on the main thread.
 */
@Composable
private fun mainViewModel(): MainViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { MainViewModel(AppGraph.tokenRepository(context)) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MainContent(
  state: MainUiState,
  onAddAccount: () -> Unit,
  onSettingsClick: (() -> Unit)? = null,
  onDelete: (String) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current

  // Which codes the user has chosen to show. Screen-level, not per-card: an account keeps its
  // revealed state while it scrolls out of view and back.
  val revealed = remember { mutableStateMapOf<String, Boolean>() }

  val copyCode: (TokenUiModel) -> Unit = remember(context, snackbarHostState, scope) {
    { token ->
      Clipboard.copySensitive(context = context, label = token.title, text = token.code)
      scope.launch {
        // Only ever one confirmation on screen; the newest copy wins.
        snackbarHostState.currentSnackbarData?.dismiss()
        // The account's name, never the code: a snackbar is a public surface.
        snackbarHostState.showSnackbar("${token.title} code copied")
      }
    }
  }

  Scaffold(
    modifier = modifier,
    topBar = {
      TopAppBar(
        title = { Text("Stitch") },
        actions = {
          if (onSettingsClick != null) {
            IconButton(onClick = onSettingsClick) {
              Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = "Settings"
              )
            }
          }
        }
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = { AddAccountFab(onClick = onAddAccount) },
  ) { innerPadding ->
    when (state) {
      MainUiState.Loading -> LoadingState(Modifier.padding(innerPadding))

      is MainUiState.Error ->
        ErrorState(
          throwable = state.throwable,
          onRetry = onRetry,
          modifier = Modifier.padding(innerPadding),
        )

      is MainUiState.Ready ->
        if (state.tokens.isEmpty()) {
          EmptyState(Modifier.padding(innerPadding))
        } else {
          TokenList(
            tokens = state.tokens,
            revealed = revealed,
            onToggleReveal = { id -> revealed[id] = revealed[id] != true },
            onCopy = copyCode,
            onDelete = onDelete,
            modifier = Modifier.padding(innerPadding),
          )
        }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TokenList(
  tokens: List<TokenUiModel>,
  revealed: Map<String, Boolean>,
  onToggleReveal: (String) -> Unit,
  onCopy: (TokenUiModel) -> Unit,
  onDelete: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  LazyColumn(
    modifier = modifier.fillMaxSize(),
    contentPadding =
      PaddingValues(
        start = ScreenPadding,
        end = ScreenPadding,
        top = 8.dp,
        bottom = ListBottomPadding,
      ),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    items(items = tokens, key = { it.id }) { token ->
      // One transition state per row, keyed by the account: a card drops in the first time it
      // appears and stays put afterwards.
      val entered = remember { MutableTransitionState(false).apply { targetState = true } }

      AnimatedVisibility(
        visibleState = entered,
        // Springs for reorder and removal; no fixed-duration curve to fight an interruption.
        modifier =
          Modifier.animateItem(
            placementSpec = bouncySpring(visibilityThreshold = IntOffset.VisibilityThreshold)
          ),
        enter =
          fadeIn(animationSpec = StitchMotion.Smooth) +
            slideInVertically(
              animationSpec = bouncySpring(),
              initialOffsetY = { fullHeight -> fullHeight / 4 },
            ),
        exit = fadeOut(animationSpec = StitchMotion.Smooth),
      ) {
        SwipeToDeleteRow(onDelete = { onDelete(token.id) }) {
          TokenCard(
            token = token,
            revealed = revealed[token.id] == true,
            onToggleReveal = { onToggleReveal(token.id) },
            onCopy = { onCopy(token) },
          )
        }
      }
    }
  }
}

/**
 * Swipe an account away to delete it.
 *
 * [SwipeToDismissBox] supplies its own spring-based settle and fling physics, so the drag, the
 * threshold snap and the release are all physics-driven; only [SwipeToDismissBoxValue.EndToStart] is
 * enabled, because a one-way gesture is easier to learn than one that means two different things
 * depending on its direction.
 */
@Composable
private fun SwipeToDeleteRow(
  onDelete: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  val dismissState =
    rememberSwipeToDismissBoxState(
      confirmValueChange = { value ->
        if (value == SwipeToDismissBoxValue.EndToStart) {
          onDelete()
          true
        } else {
          false
        }
      }
    )

  SwipeToDismissBox(
    state = dismissState,
    modifier = modifier,
    enableDismissFromStartToEnd = false,
    backgroundContent = {
      Box(
        modifier =
          Modifier.fillMaxSize()
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.errorContainer),
        contentAlignment = Alignment.CenterEnd,
      ) {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = "Delete account",
          tint = MaterialTheme.colorScheme.onErrorContainer,
          modifier = Modifier.padding(end = 24.dp),
        )
      }
    },
  ) {
    content()
  }
}

@Composable
private fun AddAccountFab(onClick: () -> Unit) {
  val interactionSource = remember { MutableInteractionSource() }

  ExtendedFloatingActionButton(
    text = { Text("Add account") },
    icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
    onClick = onClick,
    interactionSource = interactionSource,
    modifier = Modifier.pressScale(interactionSource),
  )
}

@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    CircularProgressIndicator()
  }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
  val entered = remember { MutableTransitionState(false).apply { targetState = true } }

  AnimatedVisibility(
    visibleState = entered,
    modifier = modifier.fillMaxSize(),
    enter =
      fadeIn(animationSpec = StitchMotion.Smooth) +
        slideInVertically(
          animationSpec = bouncySpring(),
          initialOffsetY = { fullHeight -> fullHeight / 3 },
        ),
    exit = fadeOut(animationSpec = StitchMotion.Smooth),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(text = "No accounts yet", style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(8.dp))
      Text(
        text = "Add an account to start generating codes — scan a QR code or type the setup key.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun ErrorState(
  throwable: Throwable,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val entered = remember { MutableTransitionState(false).apply { targetState = true } }

  AnimatedVisibility(
    visibleState = entered,
    modifier = modifier.fillMaxSize(),
    enter = fadeIn(animationSpec = StitchMotion.Smooth),
    exit = fadeOut(animationSpec = StitchMotion.Smooth),
  ) {
    Column(
      modifier = Modifier.fillMaxSize().padding(32.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center,
    ) {
      Text(
        text = "Couldn't read your accounts",
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = throwable.message ?: "Storage did not respond.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Button(onClick = onRetry) { Text("Try again") }
    }
  }
}

@Preview(name = "Accounts", showBackground = true)
@Composable
private fun MainContentPreview() {
  StitchAuthenticatorTheme {
    MainContent(
      state =
        MainUiState.Ready(
          tokens =
            listOf(
              TokenUiModel(
                id = "1",
                title = "GitHub",
                subtitle = "octocat",
                code = "94287082",
                secondsRemaining = 22,
                progress = 22f / 30f,
                isUsable = true,
              ),
              TokenUiModel(
                id = "2",
                title = "Stitch",
                subtitle = "jane@stitch.dev",
                code = "287082",
                secondsRemaining = 4,
                progress = 4f / 30f,
                isUsable = true,
              ),
            )
        ),
      onAddAccount = {},
      onDelete = {},
      onRetry = {},
    )
  }
}

@Preview(name = "No accounts", showBackground = true)
@Composable
private fun MainContentEmptyPreview() {
  StitchAuthenticatorTheme {
    MainContent(
      state = MainUiState.Ready(tokens = emptyList()),
      onAddAccount = {},
      onDelete = {},
      onRetry = {},
    )
  }
}

@Preview(name = "Storage error", showBackground = true)
@Composable
private fun MainContentErrorPreview() {
  StitchAuthenticatorTheme {
    MainContent(
      state = MainUiState.Error(IllegalStateException("Keystore unavailable")),
      onAddAccount = {},
      onDelete = {},
      onRetry = {},
    )
  }
}
