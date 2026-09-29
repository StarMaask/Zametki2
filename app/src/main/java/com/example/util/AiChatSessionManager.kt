package com.example.util

import android.content.Context
import android.net.Uri
import com.example.domain.model.AiAttachment
import com.example.presentation.components.AcademicChatMessage
import com.example.presentation.components.TitlePageRequisites
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Manages persistent multi-session storage and history for AI Academic / Secretary chat sessions.
 * Allows the user to exit the chat/app, view past dialogues in history, and return to any conversation seamlessly.
 */
object AiChatSessionManager {
    private const val ACTIVE_FILE_NAME = "active_ai_academic_session.json"
    private const val HISTORY_FILE_NAME = "ai_chat_sessions_history.json"
    private const val MAX_HISTORY_SESSIONS = 50

    data class SavedAiSession(
        val id: String = UUID.randomUUID().toString(),
        val title: String = "Новый диалог",
        val role: AiAcademicAndSecretaryService.AssistantRole = AiAcademicAndSecretaryService.AssistantRole.PROFESSOR,
        val messages: List<AcademicChatMessage> = emptyList(),
        val requisites: TitlePageRequisites = TitlePageRequisites(),
        val customTitlePageText: String? = null,
        val updatedAt: Long = System.currentTimeMillis()
    )

    fun generateTitle(
        messages: List<AcademicChatMessage>,
        requisites: TitlePageRequisites,
        role: AiAcademicAndSecretaryService.AssistantRole
    ): String {
        if (requisites.topic.isNotBlank()) {
            return requisites.topic.trim().take(50)
        }
        val firstUserMsg = messages.firstOrNull { it.isUser }?.text?.trim()
        if (!firstUserMsg.isNullOrBlank()) {
            val clean = firstUserMsg.replace("\n", " ").trim()
            return clean.take(50)
        }
        return if (role == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Научный диалог" else "Официальный документ"
    }

    private fun sessionToJson(session: SavedAiSession): JSONObject {
        val root = JSONObject()
        root.put("id", session.id)
        root.put("title", session.title)
        root.put("role", session.role.name)
        root.put("updatedAt", session.updatedAt)
        if (session.customTitlePageText != null) {
            root.put("customTitlePageText", session.customTitlePageText)
        }

        // Requisites
        val reqObj = JSONObject().apply {
            put("ministry", session.requisites.ministry)
            put("institution", session.requisites.institution)
            put("facultyAndDept", session.requisites.facultyAndDept)
            put("docType", session.requisites.docType)
            put("discipline", session.requisites.discipline)
            put("topic", session.requisites.topic)
            put("author", session.requisites.author)
            put("supervisor", session.requisites.supervisor)
            put("cityAndYear", session.requisites.cityAndYear)
        }
        root.put("requisites", reqObj)

        // Messages
        val msgArr = JSONArray()
        for (msg in session.messages) {
            val mObj = JSONObject().apply {
                put("id", msg.id)
                put("isUser", msg.isUser)
                put("text", msg.text)
                put("timestamp", msg.timestamp)
                val attArr = JSONArray()
                for (att in msg.attachments) {
                    val aObj = JSONObject().apply {
                        put("id", att.id)
                        put("uri", att.uri.toString())
                        put("name", att.name)
                        put("mimeType", att.mimeType)
                        put("sizeBytes", att.sizeBytes)
                        put("isImage", att.isImage)
                        put("isPdf", att.isPdf)
                        put("isTextDoc", att.isTextDoc)
                        att.extractedText?.let { put("extractedText", it) }
                    }
                    attArr.put(aObj)
                }
                put("attachments", attArr)
            }
            msgArr.put(mObj)
        }
        root.put("messages", msgArr)
        return root
    }

    private fun jsonToSession(root: JSONObject): SavedAiSession {
        val id = root.optString("id", UUID.randomUUID().toString())
        val title = root.optString("title", "Диалог с ИИ")
        val roleName = root.optString("role", AiAcademicAndSecretaryService.AssistantRole.PROFESSOR.name)
        val role = try {
            AiAcademicAndSecretaryService.AssistantRole.valueOf(roleName)
        } catch (_: Exception) {
            AiAcademicAndSecretaryService.AssistantRole.PROFESSOR
        }
        val updatedAt = root.optLong("updatedAt", System.currentTimeMillis())
        val customTitle = if (root.has("customTitlePageText")) root.optString("customTitlePageText") else null

        var requisites = TitlePageRequisites()
        val reqObj = root.optJSONObject("requisites")
        if (reqObj != null) {
            requisites = TitlePageRequisites(
                ministry = reqObj.optString("ministry", requisites.ministry),
                institution = reqObj.optString("institution", requisites.institution),
                facultyAndDept = reqObj.optString("facultyAndDept", requisites.facultyAndDept),
                docType = reqObj.optString("docType", requisites.docType),
                discipline = reqObj.optString("discipline", requisites.discipline),
                topic = reqObj.optString("topic", requisites.topic),
                author = reqObj.optString("author", requisites.author),
                supervisor = reqObj.optString("supervisor", requisites.supervisor),
                cityAndYear = reqObj.optString("cityAndYear", requisites.cityAndYear)
            )
        }

        val messages = mutableListOf<AcademicChatMessage>()
        val msgArr = root.optJSONArray("messages")
        if (msgArr != null) {
            for (i in 0 until msgArr.length()) {
                val mObj = msgArr.getJSONObject(i)
                val attachments = mutableListOf<AiAttachment>()
                val attArr = mObj.optJSONArray("attachments")
                if (attArr != null) {
                    for (j in 0 until attArr.length()) {
                        val aObj = attArr.getJSONObject(j)
                        val uriStr = aObj.optString("uri", "")
                        attachments.add(
                            AiAttachment(
                                id = aObj.optString("id", UUID.randomUUID().toString()),
                                uri = Uri.parse(uriStr),
                                name = aObj.optString("name", "Файл"),
                                mimeType = aObj.optString("mimeType", "application/octet-stream"),
                                sizeBytes = aObj.optLong("sizeBytes", 0L),
                                isImage = aObj.optBoolean("isImage", false),
                                isPdf = aObj.optBoolean("isPdf", false),
                                isTextDoc = aObj.optBoolean("isTextDoc", false),
                                extractedText = if (aObj.has("extractedText")) aObj.optString("extractedText") else null
                            )
                        )
                    }
                }

                messages.add(
                    AcademicChatMessage(
                        id = mObj.optString("id", UUID.randomUUID().toString()),
                        isUser = mObj.optBoolean("isUser", false),
                        text = mObj.optString("text", ""),
                        attachments = attachments,
                        timestamp = mObj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        return SavedAiSession(
            id = id,
            title = title,
            role = role,
            messages = messages,
            requisites = requisites,
            customTitlePageText = customTitle,
            updatedAt = updatedAt
        )
    }

    /**
     * Saves or updates a session both as active and in multi-session history.
     */
    fun saveSession(
        context: Context,
        session: SavedAiSession
    ) {
        try {
            val root = sessionToJson(session)

            // 1. Save as currently active
            val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
            activeFile.writeText(root.toString(), Charsets.UTF_8)

            // 2. Upsert in history
            val all = getAllSessions(context).toMutableList()
            val existingIdx = all.indexOfFirst { it.id == session.id }
            if (existingIdx != -1) {
                all[existingIdx] = session
            } else {
                all.add(0, session)
            }

            // Keep top N sessions sorted by updatedAt
            val sorted = all.sortedByDescending { it.updatedAt }.take(MAX_HISTORY_SESSIONS)
            val historyArr = JSONArray()
            for (s in sorted) {
                historyArr.put(sessionToJson(s))
            }
            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            historyFile.writeText(historyArr.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    /**
     * Backward-compatible overload for quick session saving.
     */
    fun saveSession(
        context: Context,
        id: String = UUID.randomUUID().toString(),
        role: AiAcademicAndSecretaryService.AssistantRole,
        messages: List<AcademicChatMessage>,
        requisites: TitlePageRequisites,
        customTitlePageText: String?
    ) {
        val title = generateTitle(messages, requisites, role)
        val session = SavedAiSession(
            id = id,
            title = title,
            role = role,
            messages = messages,
            requisites = requisites,
            customTitlePageText = customTitlePageText,
            updatedAt = System.currentTimeMillis()
        )
        saveSession(context, session)
    }

    /**
     * Loads the active session, or if null, the most recent history session.
     */
    fun loadSession(context: Context, sessionId: String? = null): SavedAiSession? {
        return try {
            if (sessionId != null) {
                return getAllSessions(context).firstOrNull { it.id == sessionId }
            }

            val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
            if (activeFile.exists()) {
                val jsonStr = activeFile.readText(Charsets.UTF_8)
                if (jsonStr.isNotBlank()) {
                    val root = JSONObject(jsonStr)
                    val s = jsonToSession(root)
                    if (s.messages.isNotEmpty() || s.customTitlePageText != null) {
                        return s
                    }
                }
            }

            // Fallback to most recent in history
            getAllSessions(context).firstOrNull()
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Retrieves all saved chat sessions from history.
     */
    fun getAllSessions(context: Context): List<SavedAiSession> {
        return try {
            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            if (!historyFile.exists()) {
                // If active file exists, migrate it
                val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
                if (activeFile.exists()) {
                    val s = loadSession(context)
                    if (s != null && s.messages.isNotEmpty()) return listOf(s)
                }
                return emptyList()
            }

            val jsonStr = historyFile.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return emptyList()
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<SavedAiSession>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(jsonToSession(obj))
            }
            list.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteSession(context: Context, sessionId: String) {
        try {
            val all = getAllSessions(context).filter { it.id != sessionId }
            val historyArr = JSONArray()
            for (s in all) {
                historyArr.put(sessionToJson(s))
            }
            val historyFile = File(context.filesDir, HISTORY_FILE_NAME)
            historyFile.writeText(historyArr.toString(), Charsets.UTF_8)

            // If deleted was active, point active to first remaining or delete
            val active = loadSession(context)
            if (active?.id == sessionId) {
                val nextActive = all.firstOrNull()
                val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
                if (nextActive != null) {
                    activeFile.writeText(sessionToJson(nextActive).toString(), Charsets.UTF_8)
                } else {
                    if (activeFile.exists()) activeFile.delete()
                }
            }
        } catch (_: Exception) {}
    }

    fun clearActiveSession(context: Context) {
        try {
            val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
            if (activeFile.exists()) {
                activeFile.delete()
            }
        } catch (_: Exception) {}
    }

    fun clearSession(context: Context) {
        clearActiveSession(context)
    }

    fun hasActiveSession(context: Context): Boolean {
        val activeFile = File(context.filesDir, ACTIVE_FILE_NAME)
        return (activeFile.exists() && activeFile.length() > 20) || getAllSessions(context).isNotEmpty()
    }
}
