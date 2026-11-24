package com.stitch.authenticator.model

data class AuthenticatorAccount(
    val id: String,
    val issuer: String,
    val accountName: String,
    val code: String,
    val periodSeconds: Int = 30,
    val secondsRemaining: Int = 30,
)
