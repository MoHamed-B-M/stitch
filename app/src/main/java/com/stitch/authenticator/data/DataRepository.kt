package com.stitch.authenticator.data

import com.stitch.authenticator.model.AuthenticatorAccount
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

interface AccountsRepository {
    fun observeAccounts(): Flow<List<AuthenticatorAccount>>
}

class DemoAccountsRepository : AccountsRepository {
    override fun observeAccounts(): Flow<List<AuthenticatorAccount>> = flow {
        delay(450)
        emit(
            listOf(
                AuthenticatorAccount("github", "GitHub", "maya@stitch.app", "482 913"),
                AuthenticatorAccount("google", "Google", "maya@gmail.com", "119 204", secondsRemaining = 24),
                AuthenticatorAccount("proton", "Proton", "maya@pm.me", "730 558", secondsRemaining = 12),
                AuthenticatorAccount("aws", "AWS", "ops-admin", "006 441", secondsRemaining = 18),
            )
        )
    }
}

class EmptyAccountsRepository : AccountsRepository {
    override fun observeAccounts(): Flow<List<AuthenticatorAccount>> = flow {
        delay(300)
        emit(emptyList())
    }
}

