package com.stitch.authenticator

import androidx.compose.runtime.Composable
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.stitch.authenticator.ui.main.MainScreen

@Composable
fun MainNavigation() {
  val backStack = rememberNavBackStack(Main)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Main> {
          // The screen handles its own insets and spacing: `Scaffold` lays out under the system bars
          // and applies the safe-drawing insets itself, so the host must not pad on top of that.
          MainScreen(
            onAddAccount = {
              // Phase 5 pushes the add-account destination onto [backStack] here.
            }
          )
        }
      },
  )
}
