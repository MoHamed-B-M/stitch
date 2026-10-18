package com.stitch.authenticator.data.vault

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * The vault's only door to its rows. Every read is a [Flow] so the list re-emits the moment a
 * save lands, and every write is a suspend function Room runs off the main thread.
 *
 * Search happens in SQL, not memory: [observeMatching] filters inside the database so a large
 * vault never has to be decrypted into a list just to be searched — and `LIKE '%%'` (the empty
 * query) matches *every* row, so the unfiltered listing is the same single query with nothing
 * typed yet.
 */
@Dao
interface PasswordDao {

  /** Title/username/URL substring match, ASCII-case-insensitive (`LIKE`), alphabetised. */
  @Query(
    """
    SELECT * FROM passwords
    WHERE title LIKE '%' || :query || '%'
       OR username LIKE '%' || :query || '%'
       OR websiteUrl LIKE '%' || :query || '%'
    ORDER BY title COLLATE NOCASE ASC
    """
  )
  fun observeMatching(query: String): Flow<List<PasswordEntry>>

  @Query("SELECT * FROM passwords WHERE id = :id")
  suspend fun byId(id: Long): PasswordEntry?

  /** Insert (id 0) or update (id set); returns the row id of an insert, -1 on update. */
  @Upsert
  suspend fun upsert(entry: PasswordEntry): Long

  @Query("DELETE FROM passwords WHERE id = :id")
  suspend fun deleteById(id: Long)
}
