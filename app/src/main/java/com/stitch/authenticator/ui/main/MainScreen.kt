package com.stitch.authenticator.ui.main


import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import com.stitch.authenticator.ui.components.ExpressiveActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.stitch.authenticator.R
import com.stitch.authenticator.data.AppGraph
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncyContentSize
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.theme.pressScale
import com.stitch.authenticator.theme.springClickable
import com.stitch.authenticator.ui.add.AddAccountMode
import com.stitch.authenticator.util.Clipboard
import kotlinx.coroutines.launch

private val ScreenPadding = 16.dp

/** Room for the FAB (and the snackbar above it) so the last card is never covered. */
private val ListBottomPadding = 112.dp

/**
 * The token list.
 *
 * State comes from [MainViewModel] in production and is passed in for previews and future UI tests,
 * so the whole screen can be rendered without storage, a Keystore or a clock.
 */
@Composable
fun MainScreen(
  onAddAccount: (AddAccountMode) -> Unit,
  onScanQr: () -> Unit,
  onSettingsClick: (() -> Unit)? = null,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
  viewModel: MainViewModel = mainViewModel(),
) {
  val state by viewModel.uiState.collectAsStateWithLifecycle()
  val restoreViewModel = restoreBackupViewModel()
  val restoreState by restoreViewModel.uiState.collectAsStateWithLifecycle()

  LaunchedEffect(restoreState.restored) {
    if (restoreState.restored) viewModel.refresh()
  }

  if (restoreState.backupFound) {
    AlertDialog(
      onDismissRequest = restoreViewModel::dismiss,
      title = { Text(stringResource(R.string.backup_found_title)) },
      text = { Text(stringResource(R.string.backup_found_message)) },
      confirmButton = {
        TextButton(onClick = restoreViewModel::restore, enabled = !restoreState.restoring) {
          Text(stringResource(R.string.restore_backup))
        }
      },
      dismissButton = {
        TextButton(onClick = restoreViewModel::dismiss, enabled = !restoreState.restoring) {
          Text(stringResource(R.string.skip_restore))
        }
      },
    )
  }

  MainContent(
    state = state,
    onAddAccount = onAddAccount,
    onScanQr = onScanQr,
    onSettingsClick = onSettingsClick,
    onDelete = viewModel::delete,
    onRetry = viewModel::refresh,
    contentPadding = contentPadding,
    modifier = modifier,
  )
}

@Composable
private fun restoreBackupViewModel(): RestoreBackupViewModel {
  val context = LocalContext.current.applicationContext
  return viewModel { RestoreBackupViewModel(AppGraph.backupManager(context)) }
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
  onAddAccount: (AddAccountMode) -> Unit,
  onScanQr: () -> Unit,
  onSettingsClick: (() -> Unit)? = null,
  onDelete: (String) -> Unit,
  onRetry: () -> Unit,
  modifier: Modifier = Modifier,
  contentPadding: PaddingValues = PaddingValues(0.dp),
) {
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()
  val context = LocalContext.current
  val revealed = remember { mutableStateMapOf<String, Boolean>() }
  var dialOpen by rememberSaveable { mutableStateOf(false) }
  var searchVisible by rememberSaveable { mutableStateOf(false) }
  var query by rememberSaveable { mutableStateOf("") }
  BackHandler(enabled = dialOpen) { dialOpen = false }

  val copyCode: (TokenUiModel) -> Unit = remember(context, snackbarHostState, scope) {
    { token ->
      Clipboard.copySensitive(
        context = context,
        label = context.getString(R.string.account_code),
        text = token.code,
      )
      scope.launch {
        snackbarHostState.currentSnackbarData?.dismiss()
        snackbarHostState.showSnackbar(context.getString(R.string.copied_code, token.title))
      }
    }
  }

  Scaffold(
    modifier = modifier.padding(bottom = contentPadding.calculateBottomPadding()),
    containerColor = MaterialTheme.colorScheme.background,
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.accounts_title)) },
        actions = {
          IconButton(
            onClick = {
              searchVisible = !searchVisible
              if (!searchVisible) query = ""
            },
          ) {
            Icon(
              imageVector = if (searchVisible) Icons.Filled.Close else Icons.Filled.Search,
              contentDescription =
                if (searchVisible) stringResource(R.string.clear_search)
                else stringResource(R.string.search_accounts),
            )
          }
          if (onSettingsClick != null) {
            IconButton(onClick = onSettingsClick) {
              Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
            }
          }
        },
      )
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    floatingActionButton = {
      AddAccountFab(
        expanded = dialOpen,
        onExpandedChange = { dialOpen = it },
        onAddManual = { onAddAccount(AddAccountMode.Manual) },
        onAddLink = { onAddAccount(AddAccountMode.Link) },
        onScanQr = onScanQr,
      )
    },
  ) { innerPadding ->
    Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
      Column(modifier = Modifier.fillMaxSize().padding(horizontal = ScreenPadding)) {
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
            AccountSearchField(query = query, onQueryChange = { query = it })
          }
        }
        Spacer(Modifier.height(12.dp))

        when (state) {
          MainUiState.Loading -> LoadingState(modifier = Modifier.weight(1f))

          is MainUiState.Error -> ErrorState(throwable = state.throwable, onRetry = onRetry, modifier = Modifier.weight(1f))

          is MainUiState.Ready -> {
            val filtered = filterTokens(state.tokens, query)
            when {
              state.tokens.isEmpty() -> EmptyState(modifier = Modifier.weight(1f))
              filtered.isEmpty() -> NoResultsState(modifier = Modifier.weight(1f))
              else ->
                TokenList(
                  tokens = filtered,
                  revealed = revealed,
                  onToggleReveal = { id -> revealed[id] = revealed[id] != true },
                  onCopy = copyCode,
                  onDelete = onDelete,
                  modifier = Modifier.weight(1f),
                )
            }
          }
        }
      }

      SpeedDialScrim(visible = dialOpen, onDismiss = { dialOpen = false })
    }
  }
}

private fun filterTokens(tokens: List<TokenUiModel>, query: String): List<TokenUiModel> {
  val q = query.trim()
  if (q.isEmpty()) return tokens
  return tokens.filter { it.title.contains(q, ignoreCase = true) || it.subtitle.contains(q, ignoreCase = true) }
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

/**
 * The add-account speed dial: one FAB that opens three ways in — typing the values, pasting a
 * link, or scanning the QR code the service printed.
 *
 * The options rise from behind the FAB on springs, the plus rotates into a close mark on the same
 * spring, and the label swaps on the house size spring ([bouncyContentSize]) so the control always
 * says what the next tap does.
 */
@Composable
private fun AddAccountFab(
  expanded: Boolean,
  onExpandedChange: (Boolean) -> Unit,
  onAddManual: () -> Unit,
  onAddLink: () -> Unit,
  onScanQr: () -> Unit,
) {
  val interactionSource = remember { MutableInteractionSource() }
  val rotation by animateFloatAsState(
    targetValue = if (expanded) 45f else 0f,
    animationSpec = bouncySpring(),
    label = "speedDialPlus",
  )

  Column(
    horizontalAlignment = Alignment.End,
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    // Read from the FAB upward: Manual nearest it, then Link, then the camera.
    SpeedDialOption(
      label = "Scan QR",
      icon = ScanQrIcon,
      visible = expanded,
      onClick = {
        onExpandedChange(false)
        onScanQr()
      },
    )
    SpeedDialOption(
      label = "Link",
      icon = LinkIcon,
      visible = expanded,
      onClick = {
        onExpandedChange(false)
        onAddLink()
      },
    )
    SpeedDialOption(
      label = "Manual",
      icon = Icons.Filled.Edit,
      visible = expanded,
      onClick = {
        onExpandedChange(false)
        onAddManual()
      },
    )

    ExpressiveActionButton(
      onClick = { onExpandedChange(!expanded) },
      text = if (expanded) "Close" else "Add account",
      icon = Icons.Filled.Add,
      modifier = Modifier.bouncyContentSize(),
    )
  }
}

/**
 * One entry in the open dial: label pill and icon circle, tappable as a single row.
 *
 * The row is the click target — 48 dp tall by construction, the circle's own height — with the
 * ripple suppressed: the press scale is feedback enough here, and a ripple spanning the gap
 * between two chips would read as a third shape.
 */
@Composable
private fun SpeedDialOption(
  label: String,
  icon: ImageVector,
  visible: Boolean,
  onClick: () -> Unit,
) {
  AnimatedVisibility(
    visible = visible,
    enter =
      fadeIn(animationSpec = StitchMotion.Smooth) +
        slideInVertically(animationSpec = bouncySpring()) { fullHeight -> fullHeight / 2 },
    exit =
      fadeOut(animationSpec = StitchMotion.Smooth) +
        slideOutVertically(animationSpec = bouncySpring()) { fullHeight -> fullHeight / 2 },
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier =
        Modifier.springClickable(
          indication = null,
          role = Role.Button,
          onClick = onClick,
        ),
    ) {
      // The pill carries the name; the circle is deliberately blank so TalkBack does not hear it
      // twice.
      Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurface,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
      }

      Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = CircleShape,
        modifier = Modifier.size(48.dp),
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(12.dp),
        )
      }
    }
  }
}

/**
 * The dim layer behind the open speed dial.
 *
 * It covers the list only — the top bar and the dial itself stay above it — and every tap on it
 * dismisses, so opening the menu costs nothing to back out of.
 */
@Composable
private fun BoxScope.SpeedDialScrim(visible: Boolean, onDismiss: () -> Unit) {
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn(animationSpec = StitchMotion.Smooth),
    exit = fadeOut(animationSpec = StitchMotion.Smooth),
    modifier = Modifier.matchParentSize(),
  ) {
    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
          .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onDismiss,
          ),
    )
  }
}

/*
 * The two glyphs the core icon set does not carry, drawn here so material-icons-extended stays out
 * of the APK. Both are 2-stroke outlines with round joins, sized like the core set's own marks.
 */

/** A QR frame: four corner brackets and the line being read. */
private val ScanQrIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "ScanQr",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ) {
      moveTo(5f, 9f)
      lineTo(5f, 5f)
      lineTo(9f, 5f)
      moveTo(15f, 5f)
      lineTo(19f, 5f)
      lineTo(19f, 9f)
      moveTo(19f, 15f)
      lineTo(19f, 19f)
      lineTo(15f, 19f)
      moveTo(9f, 19f)
      lineTo(5f, 19f)
      lineTo(5f, 15f)
      moveTo(7f, 12f)
      lineTo(17f, 12f)
    }
  }.build()
}

/** Two overlapping chain links: "the link the service gave you". */
private val LinkIcon: ImageVector by lazy {
  ImageVector.Builder(
    name = "Link",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
  ).apply {
    path(
      stroke = SolidColor(Color.Black),
      strokeLineWidth = 2f,
      strokeLineCap = StrokeCap.Round,
      strokeLineJoin = StrokeJoin.Round,
    ) {
      ring(cx = 9f, cy = 12f, radius = 4f)
      ring(cx = 15f, cy = 12f, radius = 4f)
    }
  }.build()
}

/**
 * A closed circle as two half-arcs: [androidx.compose.ui.graphics.vector.PathBuilder] has no
 * circle primitive, and arcs keep the glyph a pure stroke — no fill rule to get wrong.
 */
private fun PathBuilder.ring(cx: Float, cy: Float, radius: Float) {
  moveTo(cx + radius, cy)
  arcTo(
    horizontalEllipseRadius = radius,
    verticalEllipseRadius = radius,
    theta = 0f,
    isMoreThanHalf = false,
    isPositiveArc = true,
    x1 = cx - radius,
    y1 = cy,
  )
  arcTo(
    horizontalEllipseRadius = radius,
    verticalEllipseRadius = radius,
    theta = 0f,
    isMoreThanHalf = false,
    isPositiveArc = true,
    x1 = cx + radius,
    y1 = cy,
  )
  close()
}

@Composable
private fun PrivacyLine(modifier: Modifier = Modifier) {
  Row(
    modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
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
  TextField(
    value = query,
    onValueChange = onQueryChange,
    modifier = modifier.fillMaxWidth().focusRequester(focusRequester),
    singleLine = true,
    shape = MaterialTheme.shapes.extraLarge,
    placeholder = { Text(stringResource(R.string.search_accounts)) },
    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
    trailingIcon = {
      if (query.isNotEmpty()) {
        IconButton(onClick = { onQueryChange("") }) {
          Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.clear_search))
        }
      }
    },
    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    colors = TextFieldDefaults.colors(
      focusedIndicatorColor = Color.Transparent,
      unfocusedIndicatorColor = Color.Transparent,
      disabledIndicatorColor = Color.Transparent,
    ),
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingState(modifier: Modifier = Modifier) {
  Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      CircularWavyProgressIndicator()
      Spacer(Modifier.height(16.dp))
      Text(
        text = stringResource(R.string.loading_accounts),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
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
      Text(text = stringResource(R.string.no_accounts_title), style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.height(8.dp))
      Text(
        text = stringResource(R.string.no_accounts_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
    }
  }
}

@Composable
private fun NoResultsState(modifier: Modifier = Modifier) {
  Column(
    modifier = modifier.fillMaxSize().padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
  ) {
    Text(text = stringResource(R.string.no_results_title), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
      text = stringResource(R.string.no_results_body),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
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
        text = stringResource(R.string.error_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(8.dp))
      Text(
        text = throwable.message ?: stringResource(R.string.error_body),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      Spacer(Modifier.height(24.dp))
      Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
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
      onScanQr = {},
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
      onScanQr = {},
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
      onScanQr = {},
      onDelete = {},
      onRetry = {},
    )
  }
}

@Preview(name = "Speed dial open", showBackground = true)
@Composable
private fun SpeedDialPreview() {
  StitchAuthenticatorTheme {
    Box(
      modifier = Modifier.fillMaxSize().padding(24.dp),
      contentAlignment = Alignment.BottomEnd,
    ) {
      AddAccountFab(
        expanded = true,
        onExpandedChange = {},
        onAddManual = {},
        onAddLink = {},
        onScanQr = {},
      )
    }
  }
}
