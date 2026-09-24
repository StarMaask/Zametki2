package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.Note

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val title: String,
    val content: String,
    val colorHex: String,
    val isPinned: Boolean,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val reminderTime: Long?,
    val tags: String, // comma-separated
    val checkListJson: String,
    val imageUrisJson: String,
    val audioUri: String?,
    val folder: String?,
    val isLocked: Boolean = false,
    val pageFormat: String = "BOOK",
    val fontFormat: String = "DEFAULT",
    val textColorHex: String = "#1C1B1F"
) {
    fun toDomain(): Note {
        return Note(
            id = id,
            title = title,
            content = content,
            colorHex = colorHex,
            isPinned = isPinned,
            isArchived = isArchived,
            isDeleted = isDeleted,
            createdAt = createdAt,
            updatedAt = updatedAt,
            reminderTime = reminderTime,
            tags = if (tags.isBlank()) emptyList() else tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
            checkListJson = checkListJson,
            imageUrisJson = imageUrisJson,
            audioUri = audioUri,
            folder = folder,
            isLocked = isLocked,
            pageFormat = pageFormat,
            fontFormat = fontFormat,
            textColorHex = textColorHex
        )
    }

    companion object {
        fun fromDomain(note: Note): NoteEntity {
            return NoteEntity(
                id = note.id,
                title = note.title,
                content = note.content,
                colorHex = note.colorHex,
                isPinned = note.isPinned,
                isArchived = note.isArchived,
                isDeleted = note.isDeleted,
                createdAt = note.createdAt,
                updatedAt = note.updatedAt,
                reminderTime = note.reminderTime,
                tags = note.tags.joinToString(","),
                checkListJson = note.checkListJson,
                imageUrisJson = note.imageUrisJson,
                audioUri = note.audioUri,
                folder = note.folder,
                isLocked = note.isLocked,
                pageFormat = note.pageFormat,
                fontFormat = note.fontFormat,
                textColorHex = note.textColorHex
            )
        }
    }
}
