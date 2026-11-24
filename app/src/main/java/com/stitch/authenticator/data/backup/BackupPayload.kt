package com.stitch.authenticator.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class BackupPayload(
  val schemaVersion: Int = 1,
  val createdAt: Long = 0L,
  val tokensJson: String? = null,
  val passkeysJson: String? = null,
  val settings: BackupSettings? = null,
)

@Serializable
data class BackupSettings(
  val themeMode: String? = null,
  val dynamicColorEnabled: Boolean? = null,
  val biometricLockEnabled: Boolean? = null,
  val updateChannel: String? = null,
)
