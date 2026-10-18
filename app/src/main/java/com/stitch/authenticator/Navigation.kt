package com.stitch.authenticator

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.ui.KeyGlyph
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

  // Only the two tabs wear the bar. `contentWindowInsets = 0` keeps the host from padding the
  // pushed full-screen destinations by system bars their own Scaffolds already read — they lay
  // out exactly as before the bar existed. The tabs' content is padded *and* consumed past the
  // bar's height, so their own safe-drawing insets stop at it instead of stacking under it.
  Scaffold(
    contentWindowInsets = WindowInsets(0, 0, 0, 0),
    bottomBar = {
      if (currentKey is Main || currentKey is Passwords) {
        StitchBottomBar(
          selected = currentKey,
          onSelect = { target ->
            // One stack: a tab switch replaces the root rather than stacking tab over tab, so
            // system back from the vault lands on the Authenticator — and leaving a tab clears
            // its entries, clearing its view model exactly like a popped screen would.
            if (currentKey != target) {
              backStack.clear()
              backStack.add(target)
            }
          },
        )
      }
    },
  ) { innerPadding ->
    NavDisplay(
      backStack = backStack,
      // Padded past the bar when it is showing, a no-op when it is not; consuming the same
      // insets stops the tabs' own Scaffolds from double-padding what the bar already lifted
      // them over.
      modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
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
              onSettingsClick = { backStack.add(Settings) }
            )
          }
          entry<Passwords> {
            // The tab owns its gate: entering it *is* the prompt — see the screen. The database
            // opens only after a successful biometric result, never on composition, and leaving
            // the tab clears this entry and its view model, so every return re-locks.
            PasswordsScreen()
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

/**
 * The two top-level tabs. The bar is scaffolding around the content, not content itself: it sits
 * in the host [Scaffold] and shows only while the back stack's root is a tab — every pushed
 * destination is a full-screen surface that owns its own insets.
 *
 * The selection indicator and press ripples run on Material 3's own motion scheme, and switching
 * tabs goes through [NavDisplay]'s content transitions on the house springs — one motion system,
 * no second transition stack for a bar. The glyphs are [KeyGlyph] (hand-drawn; the core set has
 * no key) and the core set's lock, whose label carries the semantics — icons need no
 * content description when their tab already has a name.
 */
@Composable
private fun StitchBottomBar(
  selected: NavKey?,
  onSelect: (NavKey) -> Unit,
) {
  NavigationBar {
    NavigationBarItem(
      selected = selected == Main,
      onClick = { onSelect(Main) },
      icon = { Icon(KeyGlyph, contentDescription = null) },
      label = { Text("Authenticator") },
    )
    NavigationBarItem(
      selected = selected == Passwords,
      onClick = { onSelect(Passwords) },
      icon = { Icon(Icons.Filled.Lock, contentDescription = null) },
      label = { Text("Passwords") },
    )
  }
}
