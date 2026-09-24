package com.example.domain.repository

import com.example.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun getActiveNotes(): Flow<List<Note>>
    fun getArchivedNotes(): Flow<List<Note>>
    fun getDeletedNotes(): Flow<List<Note>>
    suspend fun getNoteById(id: Long): Note?
    fun searchNotes(query: String): Flow<List<Note>>
    suspend fun insertNote(note: Note): Long
    suspend fun updateNote(note: Note)
    suspend fun deleteNotePermanently(id: Long)
    suspend fun clearTrash()
    suspend fun deleteOldTrashNotes(thresholdTime: Long)
    fun getAllFolders(): Flow<List<String>>
    suspend fun getAllNotesForBackup(): List<Note>
    suspend fun restoreNotes(notes: List<Note>)
}
