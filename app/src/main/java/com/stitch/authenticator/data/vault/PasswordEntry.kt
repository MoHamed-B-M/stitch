package com.stitch.authenticator.data.vault

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One saved login, encrypted at rest by SQLCipher — see [PasswordDatabase].
 *
 * [password] and [notes] are the payload the whole vault exists to protect: they are only ever
 * held in memory after a successful biometric unlock, never logged, and never written anywhere
 * unencrypted.
 *
 * [id] is the database row id, not a username or email — two logins for the same account are
 * legal, and renaming an account must never re-key the row.
 */
@Entity(tableName = "passwords")
data class PasswordEntry(
  @PrimaryKey(autoGenerate = true) val id: Long = 0L,
  val title: String,
  val username: String = "",
  val password: String,
  val websiteUrl: String = "",
  val notes: String = "",
  val createdAt: Long,
)
