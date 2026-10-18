package com.stitch.authenticator.data.vault

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * The vault database — Room's schema over SQLCipher's encrypted storage.
 *
 * Built by [VaultRepository] with a `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`
 * (the current artifact's rename of the old `SupportFactory`) that holds the
 * Keystore-unwrapped passphrase, so the file on disk is opaque without the device's own
 * keyhole: not even the app can read it without going through the same biometric-gated open.
 *
 * Version 1 with `exportSchema = false`: the schema is born with the feature, so there are no
 * migrations to export yet — an assets tree for migrations nobody runs is dead weight.
 */
@Database(entities = [PasswordEntry::class], version = 1, exportSchema = false)
abstract class PasswordDatabase : RoomDatabase() {
  abstract fun passwordDao(): PasswordDao

  companion object {
    /** No app id in the name, so a package rename cannot orphan the data (house convention). */
    const val NAME = "stitch_passwords.db"
  }
}
