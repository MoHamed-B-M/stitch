package com.stitch.authenticator.data.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.stitch.authenticator.data.TokenCodec
import com.stitch.authenticator.data.TokenRepository
import com.stitch.authenticator.data.passkeys.PasskeyCodec
import com.stitch.authenticator.data.passkeys.PasskeyRepository
import com.stitch.authenticator.data.settings.SettingsRepository
import com.stitch.authenticator.data.settings.ThemeMode
import com.stitch.authenticator.data.updates.UpdateChannel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

class BackupManager(
  private val context: Context,
  private val tokenRepository: TokenRepository,
  private val passkeyRepository: PasskeyRepository,
  private val settingsRepository: SettingsRepository,
  private val tokenCodec: TokenCodec = TokenCodec(),
  private val passkeyCodec: PasskeyCodec = PasskeyCodec(),
  private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false },
) {
  private val appContext = context.applicationContext

  private fun legacyPublicBackupFile(): File {
    @Suppress("DEPRECATION")
    val docs = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
    return File(File(docs, "StitchAuthenticatorBackup"), "backup.json")
  }

  private fun appExternalBackupFile(): File {
    val base = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    return File(File(base, "secure_backup"), "backup.json").apply { parentFile?.mkdirs() }
  }

  private fun candidateFiles(): List<File> = listOf(appExternalBackupFile(), legacyPublicBackupFile())

  suspend fun hasBackup(): Boolean = withContext(Dispatchers.IO) {
    candidateFiles().any { it.exists() && it.length() > 0L } || hasMediaStoreBackup()
  }

  suspend fun backupNow(): Result<Unit> = withContext(Dispatchers.IO) {
    runCatching {
      tokenRepository.refresh()
      passkeyRepository.refresh()
      val tokens = tokenRepository.snapshotForBackup()
      val passkeys = passkeyRepository.getAll()
      val settings = BackupSettings(
        themeMode = settingsRepository.themeMode.first().name,
        dynamicColorEnabled = settingsRepository.dynamicColorEnabled.first(),
        biometricLockEnabled = settingsRepository.biometricLockEnabled.first(),
        updateChannel = settingsRepository.updateChannel.first().name,
        reduceMotionEnabled = settingsRepository.reduceMotionEnabled.first(),
      )
      val payload = BackupPayload(
        schemaVersion = 1,
        createdAt = System.currentTimeMillis(),
        tokensJson = tokenCodec.encode(tokens),
        passkeysJson = passkeyCodec.encode(passkeys),
        settings = settings,
      )
      val text = json.encodeToString(BackupPayload.serializer(), payload)
      writeFileAtomically(appExternalBackupFile(), text)
      runCatching { writeFileAtomically(legacyPublicBackupFile(), text) }
      runCatching { writeMediaStoreBackup(text) }
      Unit
    }
  }

  suspend fun restoreIfPresent(): Result<Boolean> = withContext(Dispatchers.IO) {
    runCatching {
      val text = readBackupText() ?: return@runCatching false
      val payload = json.decodeFromString(BackupPayload.serializer(), text)
      payload.tokensJson?.let { raw ->
        tokenRepository.replaceAllForRestore(tokenCodec.decode(raw))
      }
      payload.passkeysJson?.let { raw ->
        passkeyRepository.replaceAllForRestore(passkeyCodec.decode(raw))
      }
      payload.settings?.let { s ->
        s.themeMode?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull()?.let { settingsRepository.setThemeMode(it) } }
        s.dynamicColorEnabled?.let { settingsRepository.setDynamicColorEnabled(it) }
        s.biometricLockEnabled?.let { settingsRepository.setBiometricLockEnabled(it) }
        s.updateChannel?.let { runCatching { UpdateChannel.valueOf(it) }.getOrNull()?.let { settingsRepository.setUpdateChannel(it) } }
        s.reduceMotionEnabled?.let { settingsRepository.setReduceMotionEnabled(it) }
      }
      // After restore, refresh backup in canonical locations.
      backupNow().getOrThrow()
      true
    }
  }

  private fun readBackupText(): String? {
    candidateFiles().firstOrNull { it.exists() && it.length() > 0L }?.let { return it.readText() }
    return readMediaStoreBackup()
  }

  private fun writeFileAtomically(file: File, text: String) {
    file.parentFile?.mkdirs()
    val tmp = File(file.parentFile, file.name + ".tmp")
    tmp.writeText(text)
    if (file.exists()) file.delete()
    if (!tmp.renameTo(file)) {
      file.writeText(text)
      tmp.delete()
    }
  }

  private fun hasMediaStoreBackup(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    val resolver = appContext.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val projection = arrayOf(MediaStore.Downloads._ID)
    val sel = "{MediaStore.Downloads.DISPLAY_NAME}=? AND {MediaStore.Downloads.RELATIVE_PATH}=?"
    val args = arrayOf("stitch_authenticator_backup.json", "Download/StitchAuthenticatorBackup/")
    resolver.query(collection, projection, sel, args, null)?.use { return it.count > 0 }
    return false
  }

  private fun readMediaStoreBackup(): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val resolver = appContext.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val sel = "{MediaStore.Downloads.DISPLAY_NAME}=? AND {MediaStore.Downloads.RELATIVE_PATH}=?"
    val args = arrayOf("stitch_authenticator_backup.json", "Download/StitchAuthenticatorBackup/")
    resolver.query(collection, arrayOf(MediaStore.Downloads._ID), sel, args, null)?.use { c ->
      if (!c.moveToFirst()) return null
      val id = c.getLong(0)
      val uri = android.content.ContentUris.withAppendedId(collection, id)
      resolver.openInputStream(uri)?.bufferedReader()?.use { return it.readText() }
    }
    return null
  }

  private fun writeMediaStoreBackup(text: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    val resolver = appContext.contentResolver
    val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    val sel = "{MediaStore.Downloads.DISPLAY_NAME}=? AND {MediaStore.Downloads.RELATIVE_PATH}=?"
    val args = arrayOf("stitch_authenticator_backup.json", "Download/StitchAuthenticatorBackup/")
    resolver.query(collection, arrayOf(MediaStore.Downloads._ID), sel, args, null)?.use { c ->
      while (c.moveToNext()) {
        val id = c.getLong(0)
        val uri = android.content.ContentUris.withAppendedId(collection, id)
        resolver.delete(uri, null, null)
      }
    }
    val values = ContentValues().apply {
      put(MediaStore.Downloads.DISPLAY_NAME, "stitch_authenticator_backup.json")
      put(MediaStore.Downloads.MIME_TYPE, "application/json")
      put(MediaStore.Downloads.RELATIVE_PATH, "Download/StitchAuthenticatorBackup/")
      put(MediaStore.Downloads.IS_PENDING, 1)
    }
    val uri = resolver.insert(collection, values) ?: return
    resolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(text) }
    values.clear()
    values.put(MediaStore.Downloads.IS_PENDING, 0)
    resolver.update(uri, values, null, null)
  }
}
