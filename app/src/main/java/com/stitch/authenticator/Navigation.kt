package com.stitch.authenticator

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.ui.KeyGlyph
import com.stitch.authenticator.ui.components.AppDestination
import com.stitch.authenticator.ui.components.StitchFloatingNavigationBar
import com.stitch.authenticator.ui.add.AddAccountMode
import com.stitch.authenticator.ui.add.AddAccountScreen
import com.stitch.authenticator.ui.main.MainScreen
import com.stitch.authenticator.ui.passkeys.PasskeySaveScreen
import com.stitch.authenticator.ui.passwords.PasswordsScreen
import com.stitch.authenticator.ui.scan.QrScannerScreen
import com.stitch.authenticator.ui.settings.SettingsScreen
import com.stitch.authenticator.ui.update.UpdateScreen

/**
 * The whole app: two top-level tabs — the token list and the password vault — plus the screens
 * that add to them and settings.
 *
 * [Main] and [Passwords] are the only destinations that wear the bottom bar; every other
 * destination is pushed full-screen on the same springs, so [AddAccount] still rises over
 * whichever tab pushed it and settles back the way it came, [Settings] is still one tap in and
 * one tap out, and [ScanQr] still leaves by being *replaced* — a scan pops it and pushes
 * [AddAccount] in its place, because the camera must never sit one back press away from
 * re-reading the code it just accepted. The list only shifts as much as to make room, so
 * returning never feels like arriving somewhere new.
 */
@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  // Reading the root in composition is what lights the bar: the stack is snapshot state, so a
  // push or a tab switch recomposes exactly this far.
  val currentKey = backStack.lastOrNull()

  val showNavBar = currentKey is Main || currentKey is Passwords
  val navPadding = if (showNavBar) PaddingValues(bottom = 112.dp) else PaddingValues(0.dp)

  Box(modifier = Modifier.fillMaxSize()) {
    NavDisplay(
      backStack = backStack,
      modifier = Modifier.fillMaxSize(),
      onBack = { backStack.removeLastOrNull() },
      // A view model per destination, cleared when that destination is popped. Left to the default,
      // `viewModel {}` inside a destination would resolve to the activity: the add screen's "saved"
      // flag would outlive its own dismissal and close the screen the moment it was opened again.
      entryDecorators =
        listOf(
          rememberSaveableStateHolderNavEntryDecorator(),
          rememberViewModelStoreNavEntryDecorator(),
        ),
      // Springs, all the way down — including the container's own size change, which
      // [AnimatedContent] takes from its [androidx.compose.animation.ContentTransform].
      transitionSpec = {
        (slideInVertically(
          animationSpec = bouncySpring(),
          initialOffsetY = { fullHeight -> fullHeight },
        ) +
          fadeIn(animationSpec = StitchMotion.Smooth))
          .togetherWith(
            slideOutVertically(
              animationSpec = bouncySpring(),
              targetOffsetY = { fullHeight -> -fullHeight / 8 },
            ) +
              fadeOut(animationSpec = StitchMotion.Smooth)
          )
      },
      popTransitionSpec = {
        (slideInVertically(
          animationSpec = bouncySpring(),
          initialOffsetY = { fullHeight -> -fullHeight / 8 },
        ) +
          fadeIn(animationSpec = StitchMotion.Smooth))
          .togetherWith(
            slideOutVertically(
              animationSpec = bouncySpring(),
              targetOffsetY = { fullHeight -> fullHeight },
            ) +
              fadeOut(animationSpec = StitchMotion.Smooth)
          )
      },
      entryProvider =
        entryProvider {
          entry<Main> {
            // The screen handles its own insets and spacing: `Scaffold` lays out under the system bars
            // and applies the safe-drawing insets itself, so the host must not pad on top of that.
            MainScreen(
              onAddAccount = { mode -> backStack.add(AddAccount(mode = mode)) },
              onScanQr = { backStack.add(ScanQr) },
              onSettingsClick = { backStack.add(Settings) },
              contentPadding = navPadding,
            )
          }
          entry<Passwords> {
            // The tab owns its gate: entering it *is* the prompt - see the screen. The database
            // opens only after a successful biometric result, never on composition, and leaving
            // the tab clears this entry and its view model, so every return re-locks.
            PasswordsScreen(contentPadding = navPadding)
          }
          entry<AddAccount> { key ->
            AddAccountScreen(
              initialMode = key.mode,
              initialLink = key.initialLink,
              // Same pop as the system back gesture, so the exit animation is the same either way.
              onDone = { backStack.removeLastOrNull() },
            )
          }
          entry<ScanQr> {
            QrScannerScreen(
              onBackClick = { backStack.removeLastOrNull() },
              // The scanner hands off rather than stacks: back from the form returns to the list,
              // not to a camera that would immediately re-read the code it just accepted.
              onSetupLinkScanned = { link ->
                backStack.removeLastOrNull()
                backStack.add(AddAccount(mode = AddAccountMode.Link, initialLink = link))
              },
              // Same hand-off for a passkey payload — same back-stack reasoning, different sheet:
              // the camera is not left running behind a bottom sheet the user is typing into.
              onPasskeyScanned = { payload ->
                backStack.removeLastOrNull()
                backStack.add(PasskeySave(payload = payload))
              },
              // The camera was refused, so offer the surface this entry was a detour from — the
              // same swap, for the same back-stack reason.
              onEnterKeyInstead = {
                backStack.removeLastOrNull()
                backStack.add(enterKeyInsteadDestination())
              },
            )
          }
          entry<PasskeySave> { key ->
            PasskeySaveScreen(
              payload = key.payload,
              // Popped after the sheet has sprung down — see the screen; guarded there so the
              // saved-state effect and a swipe-dismiss can never pop twice.
              onDone = { backStack.removeLastOrNull() },
            )
          }
          entry<Settings> {
            // Same push/pop as the add screen: the view model is scoped to this entry and cleared
            // when it is popped, and the exit animation matches the system back gesture.
            SettingsScreen(
              onBackClick = { backStack.removeLastOrNull() },
              // Updates graduated to their own page; Settings keeps only the door to it.
              onOpenSystemUpdate = { backStack.add(SystemUpdate) },
            )
          }
          entry<SystemUpdate> {
            UpdateScreen(onBackClick = { backStack.removeLastOrNull() })
          }
        },
    )

    if (showNavBar) {
      StitchFloatingNavigationBar(
        destinations = listOf(
          AppDestination(Main, "Authenticator", KeyGlyph),
          AppDestination(Passwords, "Passwords", Icons.Filled.Lock),
        ),
        selectedKey = currentKey,
        onSelect = { target ->
          if (currentKey != target) {
            backStack.clear()
            backStack.add(target as NavKey)
          }
        },
        modifier = Modifier.align(Alignment.BottomCenter),
      )
    }
  }
}

/**
 * Where the camera's "Enter key instead" hands off: the manual entry form — the surface the
 * scan was a detour from.
 *
 * Never the link form ([AddAccountMode.Link]): that one still needs a code, and the camera was
 * just refused.
 */
internal fun enterKeyInsteadDestination(): NavKey = AddAccount(mode = AddAccountMode.Manual)


