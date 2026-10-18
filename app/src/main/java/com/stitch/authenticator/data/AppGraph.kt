package com.stitch.authenticator.data

import android.content.Context
import com.stitch.authenticator.data.passkeys.EncryptedPasskeyStorage
import com.stitch.authenticator.data.passkeys.PasskeyRepository
import com.stitch.authenticator.data.settings.SettingsDataStore
import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.settings.SettingsRepositoryImpl
import com.stitch.authenticator.data.vault.VaultRepository

/**
 * The process-wide singletons the screens share.
 *
 * One [TokenRepository] for the whole app, because every screen has to agree about the list: two
 * repositories would keep two caches over one encrypted file, so a token added on the add screen
 * would not appear on the list until that screen happened to re-read storage. Screens still receive
 * the repository by constructor through their view-model factories, so tests inject fakes instead of
 * reaching for this object.
 *
 * Created on first use rather than in `Application.onCreate`: [EncryptedTokenStorage] defers the
 * Keystore and the file until its first read, and that read already runs on the repository's IO
 * dispatcher, so a cold start pays for storage only if storage is needed.
 */
object AppGraph {

  @Volatile private var repository: TokenRepository? = null
  @Volatile private var settingsRepository: SettingsRepository? = null
  @Volatile private var passkeyRepository: PasskeyRepository? = null
  @Volatile private var vaultRepository: VaultRepository? = null

  /** The single [TokenRepository] every screen in this process reads and writes. */
  fun tokenRepository(context: Context): TokenRepository =
    repository
      ?: synchronized(this) {
        repository
          ?: TokenRepository(EncryptedTokenStorage(context.applicationContext)).also {
            repository = it
          }
      }

  fun settingsRepository(context: Context): SettingsRepository =
    settingsRepository
      ?: synchronized(this) {
        settingsRepository
          ?: SettingsRepositoryImpl(
            SettingsDataStore(context.applicationContext)
          ).also {
            settingsRepository = it
          }
      }

  /** The single [PasskeyRepository] the credential provider service and the UI both read and write. */
  fun passkeyRepository(context: Context): PasskeyRepository =
    passkeyRepository
      ?: synchronized(this) {
        passkeyRepository
          ?: PasskeyRepository(EncryptedPasskeyStorage(context.applicationContext)).also {
            passkeyRepository = it
          }
      }

  /**
   * The single [VaultRepository] — one SQLCipher handle per process. The repository opens its
   * database only through `unlock()`, i.e. only after a successful biometric prompt; asking for
   * the repository never touches the Keystore or the file.
   */
  fun vaultRepository(context: Context): VaultRepository =
    vaultRepository
      ?: synchronized(this) {
        vaultRepository
          ?: VaultRepository(context.applicationContext).also { vaultRepository = it }
      }
}
