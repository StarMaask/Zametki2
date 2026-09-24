package com.example.data.repository

import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class NoteRepositoryImpl(
    private val dao: NoteDao
) : NoteRepository {

    override fun getActiveNotes(): Flow<List<Note>> {
        return dao.getActiveNotes().map { list -> list.map { it.toDomain() } }
    }

    override fun getArchivedNotes(): Flow<List<Note>> {
        return dao.getArchivedNotes().map { list -> list.map { it.toDomain() } }
    }

    override fun getDeletedNotes(): Flow<List<Note>> {
        return dao.getDeletedNotes().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getNoteById(id: Long): Note? {
        return dao.getNoteById(id)?.toDomain()
    }

    override fun searchNotes(query: String): Flow<List<Note>> {
        return dao.searchNotes(query).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun insertNote(note: Note): Long {
        return dao.insertNote(NoteEntity.fromDomain(note))
    }

    override suspend fun updateNote(note: Note) {
        dao.updateNote(NoteEntity.fromDomain(note))
    }

    override suspend fun deleteNotePermanently(id: Long) {
        dao.deleteNotePermanently(id)
    }

    override suspend fun clearTrash() {
        dao.clearTrash()
    }

    override suspend fun deleteOldTrashNotes(thresholdTime: Long) {
        dao.deleteOldTrashNotes(thresholdTime)
    }

    override fun getAllFolders(): Flow<List<String>> {
        return dao.getAllFolders()
    }

    override suspend fun getAllNotesForBackup(): List<Note> {
        return dao.getAllNotesForBackup().map { it.toDomain() }
    }

    override suspend fun restoreNotes(notes: List<Note>) {
        dao.insertNotes(notes.map { NoteEntity.fromDomain(it) })
    }
}
