package com.stitch.authenticator.data

import android.content.Context
import com.stitch.authenticator.data.settings.SettingsDataStore
import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.settings.SettingsRepositoryImpl

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
}
