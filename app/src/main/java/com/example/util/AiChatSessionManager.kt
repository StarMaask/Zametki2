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
 * Manages persistent storage of active and recent AI Academic / Secretary chat sessions.
 * Allows the user to exit the chat/app and return to their conversation and document work seamlessly.
 */
object AiChatSessionManager {
    private const val FILE_NAME = "active_ai_academic_session.json"

    data class SavedAiSession(
        val role: AiAcademicAndSecretaryService.AssistantRole,
        val messages: List<AcademicChatMessage>,
        val requisites: TitlePageRequisites,
        val customTitlePageText: String?
    )

    fun saveSession(
        context: Context,
        role: AiAcademicAndSecretaryService.AssistantRole,
        messages: List<AcademicChatMessage>,
        requisites: TitlePageRequisites,
        customTitlePageText: String?
    ) {
        try {
            val root = JSONObject()
            root.put("role", role.name)
            if (customTitlePageText != null) {
                root.put("customTitlePageText", customTitlePageText)
            }

            // Requisites
            val reqObj = JSONObject().apply {
                put("ministry", requisites.ministry)
                put("institution", requisites.institution)
                put("facultyAndDept", requisites.facultyAndDept)
                put("docType", requisites.docType)
                put("discipline", requisites.discipline)
                put("topic", requisites.topic)
                put("author", requisites.author)
                put("supervisor", requisites.supervisor)
                put("cityAndYear", requisites.cityAndYear)
            }
            root.put("requisites", reqObj)

            // Messages
            val msgArr = JSONArray()
            for (msg in messages) {
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

            val file = File(context.filesDir, FILE_NAME)
            file.writeText(root.toString(), Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    fun loadSession(context: Context): SavedAiSession? {
        return try {
            val file = File(context.filesDir, FILE_NAME)
            if (!file.exists()) return null
            val jsonStr = file.readText(Charsets.UTF_8)
            if (jsonStr.isBlank()) return null
            val root = JSONObject(jsonStr)

            val roleName = root.optString("role", AiAcademicAndSecretaryService.AssistantRole.PROFESSOR.name)
            val role = try {
                AiAcademicAndSecretaryService.AssistantRole.valueOf(roleName)
            } catch (_: Exception) {
                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR
            }

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

            if (messages.isEmpty() && customTitle == null) return null

            SavedAiSession(
                role = role,
                messages = messages,
                requisites = requisites,
                customTitlePageText = customTitle
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession(context: Context) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }

    fun hasActiveSession(context: Context): Boolean {
        val file = File(context.filesDir, FILE_NAME)
        return file.exists() && file.length() > 20
    }
}
