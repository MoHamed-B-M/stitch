package com.stitch.authenticator

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The token list. Every path in the app starts here. */
@Serializable data object Main : NavKey

/** Adding one account, pushed by the list's FAB and popped once the account is stored. */
@Serializable data object AddAccount : NavKey
