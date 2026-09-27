package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class NoteVersion(
    val id: Long = 0L,
    val noteId: Long,
    val title: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val label: String = ""
)
