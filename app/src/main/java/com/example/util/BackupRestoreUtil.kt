package com.example.util

import com.example.domain.model.CheckListItem
import com.example.domain.model.Note
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

object BackupRestoreUtil {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun exportToJson(notes: List<Note>): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportDate", System.currentTimeMillis())

        val array = JSONArray()
        for (note in notes) {
            val item = JSONObject()
            item.put("id", note.id)
            item.put("title", note.title)
            item.put("content", note.content)
            item.put("colorHex", note.colorHex)
            item.put("isPinned", note.isPinned)
            item.put("isArchived", note.isArchived)
            item.put("isDeleted", note.isDeleted)
            if (note.reminderTime != null) {
                item.put("reminderTime", note.reminderTime)
            }
            item.put("createdAt", note.createdAt)
            item.put("updatedAt", note.updatedAt)
            item.put("checkListJson", note.checkListJson)
            item.put("imageUrisJson", note.imageUrisJson)
            if (note.folder != null) {
                item.put("folder", note.folder)
            }
            if (note.audioUri != null) {
                item.put("audioUri", note.audioUri)
            }

            val tagsArray = JSONArray()
            note.tags.forEach { tagsArray.put(it) }
            item.put("tags", tagsArray)

            array.put(item)
        }
        root.put("notes", array)

        return root.toString(2)
    }

    fun importFromJson(jsonString: String): List<Note> {
        val notes = mutableListOf<Note>()
        try {
            val root = JSONObject(jsonString)
            val array = root.optJSONArray("notes") ?: return emptyList()

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val tagsList = mutableListOf<String>()
                val tagsArray = item.optJSONArray("tags")
                if (tagsArray != null) {
                    for (t in 0 until tagsArray.length()) {
                        tagsList.add(tagsArray.getString(t))
                    }
                }

                val note = Note(
                    id = 0L,
                    title = item.optString("title", ""),
                    content = item.optString("content", ""),
                    colorHex = item.optString("colorHex", "#FFFFFF"),
                    isPinned = item.optBoolean("isPinned", false),
                    isArchived = item.optBoolean("isArchived", false),
                    isDeleted = item.optBoolean("isDeleted", false),
                    reminderTime = if (item.has("reminderTime")) item.getLong("reminderTime") else null,
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    tags = tagsList,
                    checkListJson = item.optString("checkListJson", ""),
                    imageUrisJson = item.optString("imageUrisJson", ""),
                    folder = if (item.has("folder")) item.optString("folder").ifBlank { null } else null,
                    audioUri = if (item.has("audioUri")) item.optString("audioUri").ifBlank { null } else null
                )
                notes.add(note)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return notes
    }
}
