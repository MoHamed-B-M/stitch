package com.stitch.authenticator.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stitch.authenticator.data.AccountsRepository
import com.stitch.authenticator.model.AuthenticatorAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

data class MainScreenUiState(
    val isLoading: Boolean = true,
    val accounts: List<AuthenticatorAccount> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
) {
    val filteredAccounts: List<AuthenticatorAccount>
        get() {
            val q = query.trim()
            if (q.isEmpty()) return accounts
            return accounts.filter {
                it.issuer.contains(q, ignoreCase = true) || it.accountName.contains(q, ignoreCase = true)
            }
        }

    val isEmpty: Boolean get() = !isLoading && errorMessage == null && accounts.isEmpty()
    val isNoResults: Boolean get() = !isLoading && errorMessage == null && accounts.isNotEmpty() && filteredAccounts.isEmpty()
}

private sealed interface AccountsLoad {
    data object Loading : AccountsLoad
    data class Success(val accounts: List<AuthenticatorAccount>) : AccountsLoad
    data class Error(val message: String) : AccountsLoad
}

class MainScreenViewModel(private val repository: AccountsRepository) : ViewModel() {
    private val query = MutableStateFlow("")
    private val refreshSignal = MutableStateFlow(0)

    val uiState: StateFlow<MainScreenUiState> = combine(
        refreshSignal.flatMapLatest {
            repository.observeAccounts()
                .map<List<AuthenticatorAccount>, AccountsLoad> { AccountsLoad.Success(it) }
                .onStart { emit(AccountsLoad.Loading) }
                .catch { emit(AccountsLoad.Error(it.message ?: "Unknown error")) }
        },
        query,
    ) { load, q ->
        when (load) {
            AccountsLoad.Loading -> MainScreenUiState(isLoading = true, query = q)
            is AccountsLoad.Error -> MainScreenUiState(isLoading = false, errorMessage = load.message, query = q)
            is AccountsLoad.Success -> MainScreenUiState(isLoading = false, accounts = load.accounts, query = q)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState(isLoading = true))

    private val ticker = MutableStateFlow(0L)

    init {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                ticker.update { it + 1 }
            }
        }
    }

    val tickingUiState: StateFlow<MainScreenUiState> = combine(uiState, ticker) { state, _ ->
        if (state.accounts.isEmpty()) state else state.copy(accounts = state.accounts.map { it.tick() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState(isLoading = true))

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun retry() {
        refreshSignal.update { it + 1 }
    }

    private fun AuthenticatorAccount.tick(): AuthenticatorAccount {
        val next = secondsRemaining - 1
        return if (next <= 0) {
            copy(secondsRemaining = periodSeconds, code = generateCode())
        } else {
            copy(secondsRemaining = next)
        }
    }

    private fun generateCode(): String = (100000 + Random.nextInt(900000)).toString().chunked(3).joinToString(" ")
}
