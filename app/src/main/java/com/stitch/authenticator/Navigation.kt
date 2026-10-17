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
import com.stitch.authenticator.ui.add.AddAccountScreen
import com.stitch.authenticator.ui.main.MainScreen
import com.stitch.authenticator.ui.settings.SettingsScreen

/**
 * The whole app: a list, the screen that adds to it, and settings.
 *
 * [AddAccount] rises over [Main] and settles back the way it came, which is the shape the gesture
 * already suggests — a form you bring up, do one thing in, and put away. [Settings] pushes the same
 * way one tap in and one tap out, on the same springs, so both screens feel like the same gesture.
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
            onAddAccount = { backStack.add(AddAccount) },
            onSettingsClick = { backStack.add(Settings) }
          )
        }
        entry<AddAccount> {
          // Same pop as the system back gesture, so the exit animation is the same either way.
          AddAccountScreen(onDone = { backStack.removeLastOrNull() })
        }
        entry<Settings> {
          // Same push/pop as the add screen: the view model is scoped to this entry and cleared
          // when it is popped, and the exit animation matches the system back gesture.
          SettingsScreen(onBackClick = { backStack.removeLastOrNull() })
        }
      },
  )
}
