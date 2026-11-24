package com.stitch.authenticator.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.stitch.authenticator.model.AuthenticatorAccount
import com.stitch.authenticator.theme.StitchAuthenticatorTheme
import org.junit.Rule
import org.junit.Test

class MainScreenTest {

    @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun accounts_areDisplayed() {
        composeTestRule.setContent {
            StitchAuthenticatorTheme {
                MainScreenContent(
                    state = MainScreenUiState(
                        isLoading = false,
                        accounts = listOf(
                            AuthenticatorAccount("1", "GitHub", "maya@stitch.app", "482 913"),
                            AuthenticatorAccount("2", "Google", "maya@gmail.com", "119 204"),
                        ),
                    ),
                    onQueryChange = {},
                    onRetry = {},
                )
            }
        }
        composeTestRule.onNodeWithText("GitHub").assertExists()
        composeTestRule.onNodeWithText("482 913").assertExists()
    }
}
