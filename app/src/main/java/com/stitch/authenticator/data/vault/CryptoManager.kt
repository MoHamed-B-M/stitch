package com.stitch.authenticator.data.vault

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Wraps the vault's SQLCipher passphrase behind a non-exportable Android Keystore key.
 *
 * The passphrase is 32 random bytes, minted once by [getOrCreatePassphrase] — never derived
 * from anything the user knows and never displayed. [wrap] hides it behind an AES-256-GCM key
 * the chip refuses to export, and the wrapped blob (IV prepended, because GCM needs a fresh IV
 * per encryption) is the only thing that lands in the `stitch_vault` prefs file. Copying the
 * app's data off the device therefore yields ciphertext that cannot be decrypted anywhere but
 * the same keyhole — which is the whole point of a local-only vault.
 *
 * The wrapping key deliberately does **not** require user authentication per use: the biometric
 * gate sits in front of the *database* (it opens only after a successful prompt, and a failed
 * or cancelled one never reaches this class), while binding the key itself to auth validity
 * would permanently lock every stored password the first time the user re-enrolled a
 * fingerprint.
 *
 * Plain prefs for the wrapped blob, not `EncryptedSharedPreferences`: the blob is already
 * ciphertext under a hardware-bound key, so a second wrapping pass would protect nothing the
 * Keystore key does not. Tokens and passkeys use `EncryptedSharedPreferences` because those
 * payloads start life as *plaintext* secrets — different threat, different tool.
 *
 * Not unit-testable by design — a thin adapter over the platform Keystore, the same honest
 * boundary as the passkey crypto engine. The pure parts (generator output shape) are pinned
 * in [PasswordGeneratorTest].
 */
class CryptoManager(context: Context) {

  private val applicationContext = context.applicationContext

  private val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

  private val preferences =
    applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

  /**
   * The passphrase for the SQLCipher database: decrypted from the wrapped blob if one exists,
   * freshly minted (and wrapped) otherwise. Callers run this on IO — Keystore plus disk I/O.
   */
  fun getOrCreatePassphrase(): ByteArray {
    preferences.getString(KEY_WRAPPED, null)?.let { stored ->
      return unwrap(Base64.decode(stored, Base64.NO_WRAP))
    }
    val passphrase = ByteArray(PassphraseBytes).also { SecureRandom().nextBytes(it) }
    // The blob must be on disk before the caller builds a database on top of it — a save the
    // caller was told succeeded is a commit(), never an apply() that could lose the race.
    preferences
      .edit()
      .putString(KEY_WRAPPED, Base64.encodeToString(wrap(passphrase), Base64.NO_WRAP))
      .commit()
    return passphrase
  }

  /** AES-GCM over [plaintext] with the Keystore key; the output is `iv || ciphertext`. */
  private fun wrap(plaintext: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.ENCRYPT_MODE, vaultKey())
    return cipher.iv + cipher.doFinal(plaintext)
  }

  /** Reverses [wrap]; [blob] is the `iv || ciphertext` form it produced. */
  private fun unwrap(blob: ByteArray): ByteArray {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    cipher.init(Cipher.DECRYPT_MODE, vaultKey(), GCMParameterSpec(GcmTagBits, blob, 0, IvBytes))
    return cipher.doFinal(blob, IvBytes, blob.size - IvBytes)
  }

  /** The per-install wrapping key: AES-256-GCM, sitting behind the StrongBox/TEE Keystore. */
  private fun vaultKey(): SecretKey {
    (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
    val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    generator.init(
      KeyGenParameterSpec.Builder(
        KEY_ALIAS,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
      )
        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
        .setKeySize(KeySizeBits)
        .build()
    )
    return generator.generateKey()
  }

  private companion object {
    const val ANDROID_KEYSTORE = "AndroidKeyStore"

    /** Versioned alias: a future format change mints a new key instead of misreading old blobs. */
    const val KEY_ALIAS = "stitch_vault_key_v1"
    const val TRANSFORMATION = "AES/GCM/NoPadding"

    /** No app id in the file name, so a package rename cannot orphan the data (house convention). */
    const val PREFS_FILE = "stitch_vault"
    const val KEY_WRAPPED = "wrapped_passphrase"

    const val PassphraseBytes = 32
    const val IvBytes = 12
    const val GcmTagBits = 128
    const val KeySizeBits = 256
  }
}
