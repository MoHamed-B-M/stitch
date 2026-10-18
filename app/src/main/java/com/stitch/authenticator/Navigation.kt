package com.stitch.authenticator

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.stitch.authenticator.theme.StitchMotion
import com.stitch.authenticator.theme.bouncySpring
import com.stitch.authenticator.ui.add.AddAccountMode
import com.stitch.authenticator.ui.add.AddAccountScreen
import com.stitch.authenticator.ui.main.MainScreen
import com.stitch.authenticator.ui.passkeys.PasskeySaveScreen
import com.stitch.authenticator.ui.scan.QrScannerScreen
import com.stitch.authenticator.ui.settings.SettingsScreen
import com.stitch.authenticator.ui.update.UpdateScreen

/**
 * The whole app: a list, the screens that add to it, and settings.
 *
 * [AddAccount] rises over [Main] and settles back the way it came, which is the shape the gesture
 * already suggests — a form you bring up, do one thing in, and put away. [Settings] pushes the same
 * way one tap in and one tap out, on the same springs, so both screens feel like the same gesture.
 * [ScanQr] rises the same way but leaves by being *replaced*: a scan pops it and pushes
 * [AddAccount] in its place, because the camera must never sit one back press away from re-reading
 * the code it just accepted.
 * The list only shifts as much as to make room, so returning never feels like arriving somewhere
 * new.
 */
@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
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
              backStack.add(AddAccount(mode = AddAccountMode.Manual))
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
