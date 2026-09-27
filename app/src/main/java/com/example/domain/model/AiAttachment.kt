package com.example.domain.model

import android.net.Uri

data class AiAttachment(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long = 0L,
    val isImage: Boolean = false,
    val isPdf: Boolean = false,
    val isTextDoc: Boolean = false,
    val base64Data: String? = null,
    val extractedText: String? = null
) {
    val formattedSize: String
        get() = when {
            sizeBytes <= 0 -> ""
            sizeBytes < 1024 -> "$sizeBytes Б"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} КБ"
            else -> String.format(java.util.Locale.US, "%.1f МБ", sizeBytes.toDouble() / (1024 * 1024))
        }

    val typeLabel: String
        get() = when {
            isImage -> "Фото / Скан"
            isPdf -> "PDF Документ"
            name.endsWith(".docx", ignoreCase = true) -> "Word Документ"
            name.endsWith(".xlsx", ignoreCase = true) || name.endsWith(".xls", ignoreCase = true) -> "Таблица Excel"
            else -> "Текстовый документ"
        }
}
