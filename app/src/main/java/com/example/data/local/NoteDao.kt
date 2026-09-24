package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY isPinned DESC, updatedAt DESC")
    fun getActiveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 1 ORDER BY updatedAt DESC")
    fun getArchivedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 1 ORDER BY updatedAt DESC")
    fun getDeletedNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 AND (title LIKE '%' || :query || '%' OR content LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') ORDER BY updatedAt DESC")
    fun searchNotes(query: String): Flow<List<NoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Update
    suspend fun updateNote(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNotePermanently(id: Long)

    @Query("DELETE FROM notes WHERE isDeleted = 1")
    suspend fun clearTrash()

    @Query("DELETE FROM notes WHERE isDeleted = 1 AND updatedAt < :thresholdTime")
    suspend fun deleteOldTrashNotes(thresholdTime: Long)

    @Query("SELECT DISTINCT folder FROM notes WHERE folder IS NOT NULL AND folder != '' AND isDeleted = 0")
    fun getAllFolders(): Flow<List<String>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0 AND isArchived = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getLatestActiveNote(): NoteEntity?

    @Query("SELECT * FROM notes")
    suspend fun getAllNotesForBackup(): List<NoteEntity>
}
