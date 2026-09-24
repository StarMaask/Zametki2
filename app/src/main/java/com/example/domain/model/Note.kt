package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val colorHex: String = "#FFFFFF",
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null,
    val tags: List<String> = emptyList(),
    val checkListJson: String = "",
    val imageUrisJson: String = "",
    val audioUri: String? = null,
    val folder: String? = null,
    val isLocked: Boolean = false,
    val pageFormat: String = PageFormat.BOOK.name,
    val fontFormat: String = NoteFontFamily.DEFAULT.id,
    val textColorHex: String = "#1C1B1F"
) {
    val format: PageFormat
        get() = try { PageFormat.valueOf(pageFormat) } catch (_: Exception) { PageFormat.BOOK }

    val noteFont: NoteFontFamily
        get() = NoteFontFamily.fromId(fontFormat)
}
