package com.stitch.authenticator.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Keeps the token list in `EncryptedSharedPreferences`: a per-install master key lives in the
 * Android Keystore (hardware-backed where the device has one) and the file is encrypted with
 * AES-256-SIV names and AES-256-GCM values, so pulling `data/data/…` off the device reveals nothing.
 *
 * The preferences handle is created lazily because the Keystore and the file are first touched there;
 * construction must stay cheap enough to happen inside a `ViewModel` factory.
 *
 * `EncryptedSharedPreferences` is deprecated upstream but is still the smallest correct dependency for
 * the job; [TokenStorage] is the seam that would absorb a swap to a raw Keystore cipher.
 *
 * Not unit-testable by design — it is a thin adapter over `TokenCodec` (which is) and the platform.
 */
class EncryptedTokenStorage(
  context: Context,
  private val codec: TokenCodec = TokenCodec(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : TokenStorage {

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

  override suspend fun load(): List<Token> = withContext(ioDispatcher) {
    preferences.getString(KEY_TOKENS, null)?.let(codec::decode) ?: emptyList()
  }

  override suspend fun save(tokens: List<Token>) {
    // commit(), not apply(): a save the caller was told succeeded must already be on disk.
    withContext(ioDispatcher) {
      preferences.edit(commit = true) { putString(KEY_TOKENS, codec.encode(tokens)) }
    }
  }

  private companion object {
    /** No app id in the name, so a package rename cannot orphan the data. */
    const val FILE_NAME = "stitch_tokens"
    const val KEY_TOKENS = "tokens"
  }
}
