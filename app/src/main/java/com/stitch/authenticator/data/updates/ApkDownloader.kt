package com.stitch.authenticator.data.updates

import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Streams an update APK into local storage. */
interface ApkDownloader {
  /**
   * Downloads [url] to a file and returns it, reporting progress as 0f..1f on the way.
   *
   * The file is returned only once fully written: a half-copied APK is not an install candidate,
   * and the caller installs whatever comes back.
   */
  suspend fun download(url: String, onProgress: (Float) -> Unit): File
}

/**
 * Downloads into the app's cache, which the system may reclaim at any time — exactly the right
 * lifetime for an APK that exists only between "download" and "install".
 */
class CacheApkDownloader(
  private val cacheDir: File,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkDownloader {

  override suspend fun download(url: String, onProgress: (Float) -> Unit): File =
    withContext(ioDispatcher) {
      val directory = File(cacheDir, "updates").apply { mkdirs() }
      // One name, rewritten every time: an update replaces the previous attempt, not accumulates.
      val target = File(directory, UPDATE_FILE_NAME)

      val connection = URL(url).openConnection() as HttpURLConnection
      try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        connection.setRequestProperty("User-Agent", "StitchAuthenticator")
        val status = connection.responseCode
        if (status != HttpURLConnection.HTTP_OK) throw HttpStatusException(status)

        val total = connection.contentLength.toLong().coerceAtLeast(0L)
        var written = 0L
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        connection.inputStream.use { input ->
          FileOutputStream(target).use { output ->
            while (true) {
              // A cancelled download must stop pulling bytes, not finish the file anyway.
              coroutineContext.ensureActive()
              val read = input.read(buffer)
              if (read == -1) break
              output.write(buffer, 0, read)
              written += read
              if (total > 0L) onProgress(written.toFloat() / total)
            }
          }
        }
        onProgress(1f)
        target
      } finally {
        connection.disconnect()
      }
    }

  companion object {
    const val UPDATE_FILE_NAME = "stitch-update.apk"
  }
}
