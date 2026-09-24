package com.example.domain.model

import androidx.compose.runtime.mutableStateMapOf

data class CustomTemplateData(
    val template: NoteTemplate,
    val title: String,
    val description: String,
    val noteTitle: String,
    val checklistItems: List<String>,
    val folder: String?,
    val tags: List<String>,
    val colorHex: String
)

object NoteTemplateManager {
    private val customTemplates = mutableStateMapOf<NoteTemplate, CustomTemplateData>()

    init {
        resetAllToDefaults()
    }

    fun resetAllToDefaults() {
        customTemplates.clear()
        NoteTemplate.values().forEach { template ->
            customTemplates[template] = CustomTemplateData(
                template = template,
                title = template.title,
                description = template.description,
                noteTitle = template.defaultTitle,
                checklistItems = template.checklistItems.toList(),
                folder = template.defaultFolder,
                tags = template.defaultTags.toList(),
                colorHex = template.colorHex
            )
        }
    }

    fun resetTemplate(template: NoteTemplate) {
        customTemplates[template] = CustomTemplateData(
            template = template,
            title = template.title,
            description = template.description,
            noteTitle = template.defaultTitle,
            checklistItems = template.checklistItems.toList(),
            folder = template.defaultFolder,
            tags = template.defaultTags.toList(),
            colorHex = template.colorHex
        )
    }

    fun getTemplate(template: NoteTemplate): CustomTemplateData {
        return customTemplates[template] ?: CustomTemplateData(
            template = template,
            title = template.title,
            description = template.description,
            noteTitle = template.defaultTitle,
            checklistItems = template.checklistItems.toList(),
            folder = template.defaultFolder,
            tags = template.defaultTags.toList(),
            colorHex = template.colorHex
        )
    }

    fun getAllTemplates(): List<CustomTemplateData> {
        return NoteTemplate.values().map { getTemplate(it) }
    }

    fun updateTemplate(
        template: NoteTemplate,
        items: List<String>,
        noteTitle: String? = null
    ) {
        val current = getTemplate(template)
        customTemplates[template] = current.copy(
            noteTitle = noteTitle ?: current.noteTitle,
            checklistItems = items
        )
    }

    fun deleteItemFromTemplate(template: NoteTemplate, itemIndex: Int) {
        val current = getTemplate(template)
        if (itemIndex in current.checklistItems.indices) {
            val updated = current.checklistItems.toMutableList().apply { removeAt(itemIndex) }
            customTemplates[template] = current.copy(checklistItems = updated)
        }
    }

    fun addItemToTemplate(template: NoteTemplate, text: String) {
        if (text.isBlank()) return
        val current = getTemplate(template)
        val updated = current.checklistItems + text.trim()
        customTemplates[template] = current.copy(checklistItems = updated)
    }

    fun updateItemInTemplate(template: NoteTemplate, itemIndex: Int, newText: String) {
        val current = getTemplate(template)
        if (itemIndex in current.checklistItems.indices) {
            val updated = current.checklistItems.toMutableList().apply { set(itemIndex, newText) }
            customTemplates[template] = current.copy(checklistItems = updated)
        }
    }
}
