package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class CheckListItem(
    val id: String,
    val text: String,
    val isChecked: Boolean = false
)
