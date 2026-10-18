package com.stitch.authenticator.data.vault

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * One process-wide vault: the SQLCipher database, opened once behind the biometric gate.
 *
 * [unlock] is the only way in and is called after a successful biometric prompt — the UI never
 * reaches the DAOs before then. A failed or cancelled prompt leaves [database] null, so "the
 * database remains inaccessible" is literal: nothing was opened, nothing was read.
 *
 * Integration note: the artifact's Room bridge is `SupportOpenHelperFactory` — the class the old
 * `android-database-sqlcipher` artifact called `SupportFactory` — and SQLCipher's native core is
 * loaded by its documented preflight before the first factory is built.
 *
 * The passphrase lives inside the `SupportOpenHelperFactory` for the life of the database (Room may
 * rebuild open helpers, so the factory must keep it); it is 32 random bytes that were never
 * persisted in plaintext and never leave process memory.
 */
class VaultRepository(context: Context) {

  private val applicationContext = context.applicationContext
  private val crypto = CryptoManager(applicationContext)

  @Volatile private var database: PasswordDatabase? = null

  /** Opens the database with the Keystore-unwrapped passphrase. Idempotent; runs on IO. */
  suspend fun unlock() {
    if (database != null) return
    withContext(Dispatchers.IO) {
      synchronized(this@VaultRepository) {
        if (database != null) return@withContext
        val passphrase = crypto.getOrCreatePassphrase()
        // One process-wide factory, kept by Room for every helper open it may ever do.
        val factory = SupportOpenHelperFactory(passphrase)
        database =
          Room.databaseBuilder(
            applicationContext,
            PasswordDatabase::class.java,
            PasswordDatabase.NAME,
          )
            .openHelperFactory(factory)
            .build()
      }
    }
  }

  /** Live listing; [query] filters by title/username/URL, and empty shows everything. */
  fun passwords(query: String): Flow<List<PasswordEntry>> =
    requireOpen().passwordDao().observeMatching(query)

  suspend fun byId(id: Long): PasswordEntry? =
    withContext(Dispatchers.IO) { requireOpen().passwordDao().byId(id) }

  suspend fun save(entry: PasswordEntry): Long =
    withContext(Dispatchers.IO) { requireOpen().passwordDao().upsert(entry) }

  suspend fun delete(id: Long) {
    withContext(Dispatchers.IO) { requireOpen().passwordDao().deleteById(id) }
  }

  private fun requireOpen(): PasswordDatabase =
    checkNotNull(database) {
      "The vault is locked — call unlock() after a successful biometric prompt first."
    }

  private companion object {
    /**
     * The artifact's documented preflight: SQLCipher's core ships in `libsqlcipher.so` and the
     * README loads it before any database API is touched. `System.loadLibrary` is idempotent per
     * classloader, so a second call from anywhere else in the process is a no-op.
     */
    init {
      System.loadLibrary("sqlcipher")
    }
  }
}
