package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteVersionDao {

    @Query("SELECT * FROM note_versions WHERE noteId = :noteId ORDER BY timestamp DESC")
    fun getVersionsForNote(noteId: Long): Flow<List<NoteVersionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVersion(version: NoteVersionEntity): Long

    @Query("DELETE FROM note_versions WHERE noteId = :noteId")
    suspend fun deleteVersionsForNote(noteId: Long)

    @Query("DELETE FROM note_versions WHERE id = :id")
    suspend fun deleteVersionById(id: Long)

    @Query("SELECT COUNT(*) FROM note_versions WHERE noteId = :noteId")
    suspend fun getVersionCount(noteId: Long): Int
}
