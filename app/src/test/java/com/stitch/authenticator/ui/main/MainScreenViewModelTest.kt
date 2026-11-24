package com.stitch.authenticator.ui.main

import com.stitch.authenticator.data.AccountsRepository
import com.stitch.authenticator.model.AuthenticatorAccount
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MainScreenViewModelTest {
    @Test
    fun uiState_initiallyLoading() = runTest {
        val viewModel = MainScreenViewModel(FakeAccountsRepository())
        assertTrue(viewModel.uiState.first().isLoading)
    }

    @Test
    fun uiState_emitsAccounts() = runTest {
        val viewModel = MainScreenViewModel(FakeAccountsRepository())
        val state = viewModel.uiState.first { !it.isLoading }
        assertEquals(1, state.accounts.size)
        assertEquals("GitHub", state.accounts.first().issuer)
    }
}

private class FakeAccountsRepository : AccountsRepository {
    override fun observeAccounts(): Flow<List<AuthenticatorAccount>> = flow {
        emit(listOf(AuthenticatorAccount("1", "GitHub", "user", "123 456")))
    }
}
