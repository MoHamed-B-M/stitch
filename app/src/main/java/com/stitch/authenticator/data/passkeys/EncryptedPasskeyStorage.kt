package com.stitch.authenticator.data.passkeys

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the passkey list in `EncryptedSharedPreferences` under the same per-install Keystore
 * master key as the tokens: pulling `data/data/…` off the device reveals neither TOTP secrets nor
 * which relying parties the user has passkeys for.
 *
 * The passkey *private* keys are not here at all — they live in the Android Keystore behind
 * [PasskeyEntity.keyAlias]; this file only holds public material and identifiers.
 *
 * Preferences handle created lazily for the same reason [com.stitch.authenticator.data
 * .EncryptedTokenStorage] does it: construction must stay cheap enough for a `Service.onCreate`.
 *
 * Not unit-testable by design — it is a thin adapter over [PasskeyCodec] (which is) and the
 * platform.
 */
class EncryptedPasskeyStorage(
  context: Context,
  private val codec: PasskeyCodec = PasskeyCodec(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PasskeyStorage {

  private val applicationContext = context.applicationContext

  private val preferences: SharedPreferences by lazy {
    val masterKey = MasterKey.Builder(applicationContext)
      .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
      .build()
    EncryptedSharedPreferences.create(
      applicationContext,
      FILE_NAME,
      masterKey,
      EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
      EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
  }

  override suspend fun load(): List<PasskeyEntity> = withContext(ioDispatcher) {
    preferences.getString(KEY_PASSKEYS, null)?.let(codec::decode) ?: emptyList()
  }

  override suspend fun save(passkeys: List<PasskeyEntity>) {
    // commit(), not apply(): a save the caller was told succeeded must already be on disk.
    withContext(ioDispatcher) {
      preferences.edit(commit = true) { putString(KEY_PASSKEYS, codec.encode(passkeys)) }
    }
  }

  private companion object {
    /** No app id in the name, so a package rename cannot orphan the data. */
    const val FILE_NAME = "stitch_passkeys"
    const val KEY_PASSKEYS = "passkeys"
  }
}
