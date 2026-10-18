package com.stitch.authenticator.data.vault

import kotlinx.coroutines.flow.Flow

/**
 * The door the passwords screen reads the vault through — the house `TokenStorage` pattern: an
 * interface over the real [VaultRepository] so the view model's gate can be pinned on the JVM
 * with a fake, exactly as the token list pins its chain over a fake storage.
 *
 * The biometric gate is deliberately *not* part of this seam: [unlock] opening the database is
 * the invariant the view model enforces around it, and the JVM tests prove a fake that refuses
 * to answer before [unlock] leaves the state machine locked.
 */
interface VaultStorage {
  /** Opens the SQLCipher database. Called only after a successful biometric prompt. */
  suspend fun unlock()

  /** Live listing, filtered by title/username/URL; an empty [query] matches everything. */
  fun passwords(query: String): Flow<List<PasswordEntry>>

  suspend fun byId(id: Long): PasswordEntry?

  /** Insert (id 0) or update (id set); returns the row id of an insert, -1 on update. */
  suspend fun save(entry: PasswordEntry): Long

  suspend fun delete(id: Long)
}
