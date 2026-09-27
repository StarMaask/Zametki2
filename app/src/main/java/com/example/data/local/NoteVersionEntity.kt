package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.NoteVersion

@Entity(tableName = "note_versions")
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val noteId: Long,
    val title: String,
    val content: String,
    val timestamp: Long,
    val label: String
) {
    fun toDomain(): NoteVersion {
        return NoteVersion(
            id = id,
            noteId = noteId,
            title = title,
            content = content,
            timestamp = timestamp,
            label = label
        )
    }

    companion object {
        fun fromDomain(version: NoteVersion): NoteVersionEntity {
            return NoteVersionEntity(
                id = version.id,
                noteId = version.noteId,
                title = version.title,
                content = version.content,
                timestamp = version.timestamp,
                label = version.label
            )
        }
    }
}
