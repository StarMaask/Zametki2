package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max

object GeminiOcrService {

    private const val MODEL_PRIMARY = "gemini-3.5-flash"
    private const val MODEL_FALLBACK = "gemini-2.5-flash"

    /**
     * Checks if a Gemini API key is configured either in BuildConfig or UserPreferences.
     */
    fun hasAvailableApiKey(context: Context): Boolean {
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "null") return true
        val userKey = UserPreferencesManager(context).getGeminiApiKeySync().trim()
        return userKey.isNotBlank()
    }

    /**
     * Retrieves the active API key.
     */
    fun getEffectiveApiKey(context: Context): String {
        val userKey = UserPreferencesManager(context).getGeminiApiKeySync().trim()
        if (userKey.isNotBlank()) return userKey
        val buildKey = BuildConfig.GEMINI_API_KEY.trim()
        if (buildKey.isNotBlank() && buildKey != "null") return buildKey
        return ""
    }

    /**
     * Verifies that the provided API key is valid and has active quota.
     */
    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(Exception("Ключ API пуст. Введите ключ для проверки."))
        }
        var connection: HttpURLConnection? = null
        try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$trimmedKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 12000
                readTimeout = 15000
                doOutput = true
                doInput = true
            }

            val rootJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                parts.put(JSONObject().apply { put("text", "Ответь одним словом: Готово") })
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)
            }

            connection.outputStream.use { os ->
                val inputBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
                os.write(inputBytes, 0, inputBytes.size)
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                Result.success("Ключ действителен и готов к работе!")
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = parseErrorMessage(errorBody, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(Exception(e.localizedMessage ?: "Сетевая ошибка при проверке ключа."))
        } finally {
            connection?.disconnect()
        }
    }

    enum class TextStructureMode(val title: String, val subtitle: String, val promptInstruction: String) {
        STRUCTURED_NOTES(
            "Структурированный конспект",
            "Единый конспект с разделами, тезисами и выводами",
            "Объедини материалы со всех страниц в единый, грамотный и логически выверенный конспект на русском языке. Устрани переносы слов между страницами, повторы заголовков и артефакты сканирования. Выдели ключевые понятия, списки, формулы/определения и основные выводы."
        ),
        SUMMARY(
            "Краткое саммари",
            "Суть и ключевые факты со всех страниц",
            "Составь краткое и ёмкое резюме (саммари) по материалам всех страниц. Выдели главную суть, факты, цифры и ключевые результаты."
        ),
        CLEAN_MERGE(
            "Очищенный сплошной текст",
            "Исправление опечаток, пунктуации и склейка",
            "Объедини тексты всех страниц в один связный, грамотный текст. Исправь распознанные опечатки, восстанови разорванные на границах страниц слова и предложения, сохрани авторский смысл."
        ),
        ACTION_PLAN(
            "Задачи и план действий",
            "Извлечение поручений, дат и контрольных точек",
            "Проанализируй текст всех страниц и составь четкий план действий: задачи, дедлайны, важные даты и контрольные пункты в формате чек-листа."
        )
    }

    /**
     * Performs ultra-accurate Russian/multilingual OCR using Gemini Vision.
     */
    suspend fun recognizeTextWithGemini(
        context: Context,
        imageUri: Uri,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: getEffectiveApiKey(context)

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("API-ключ Gemini не найден. Укажите бесплатный ключ Gemini в настройках или диалоге сканирования.")
            )
        }

        val base64Image = try {
            encodeImageToBase64(context, imageUri)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Не удалось обработать изображение: ${e.localizedMessage}"))
        }

        if (base64Image.isNullOrBlank()) {
            return@withContext Result.failure(Exception("Изображение пустое или не удалось прочитать файл."))
        }

        val prompt = "Ты — профессиональная система распознавания текста (OCR) для русского и других языков.\n" +
                "Распознай и перепиши ВЕСЬ текст с предоставленного изображения максимально точно, слово в слово.\n" +
                "Правила:\n" +
                "1. Текст на русском языке (кириллица). НЕ коверкай русские слова и не заменяй буквы латиницей!\n" +
                "2. Сохраняй исходную разбивку строк, абзацы и пунктуацию.\n" +
                "3. Верни ТОЛЬКО распознанный текст без каких-либо вводных слов, пояснений или обрамления в ```."

        // Attempt with primary model, fallback if needed
        val primaryResult = executeGeminiRequest(MODEL_PRIMARY, apiKey, prompt, base64Image)
        if (primaryResult.isSuccess) {
            return@withContext primaryResult
        }

        // Try fallback model if primary returned overloaded/server error
        val fallbackResult = executeGeminiRequest(MODEL_FALLBACK, apiKey, prompt, base64Image)
        if (fallbackResult.isSuccess) {
            return@withContext fallbackResult
        }

        return@withContext primaryResult
    }

    /**
     * Structures, cleans and synthesizes multiple page texts into a unified document using Gemini AI.
     */
    suspend fun structureBatchTexts(
        context: Context,
        pageTexts: List<String>,
        mode: TextStructureMode,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: getEffectiveApiKey(context)

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("API-ключ Gemini не найден. Укажите бесплатный ключ в настройках приложения.")
            )
        }

        val filteredPages = pageTexts.filter { it.isNotBlank() }
        if (filteredPages.isEmpty()) {
            return@withContext Result.failure(IllegalStateException("Список текстов пуст."))
        }

        val joinedPages = filteredPages.mapIndexed { index, text ->
            "=== Страница ${index + 1} ===\n$text"
        }.joinToString("\n\n")

        val prompt = "Ты — профессиональный редактор и составитель конспектов на русском языке.\n" +
                "${mode.promptInstruction}\n" +
                "Правила:\n" +
                "1. Отвечай исключительно на грамотном русском языке без лишних латинских знаков и опечаток.\n" +
                "2. Форматируй красиво: используй понятные заголовки, списки, абзацы.\n" +
                "3. Не добавляй никаких мета-комментариев («Вот ваш результат:» и т.п.) — сразу выдавай текст конспекта.\n\n" +
                "Вот исходные материалы страниц:\n\n$joinedPages"

        val primaryResult = executeGeminiTextRequest(MODEL_PRIMARY, apiKey, prompt)
        if (primaryResult.isSuccess) return@withContext primaryResult

        val fallbackResult = executeGeminiTextRequest(MODEL_FALLBACK, apiKey, prompt)
        if (fallbackResult.isSuccess) return@withContext fallbackResult

        return@withContext primaryResult
    }

    private fun executeGeminiTextRequest(
        modelName: String,
        apiKey: String,
        prompt: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 30000
                readTimeout = 40000
                doOutput = true
                doInput = true
            }

            val rootJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()
                parts.put(JSONObject().apply { put("text", prompt) })
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }

            connection.outputStream.use { os ->
                val inputBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
                os.write(inputBytes, 0, inputBytes.size)
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsedText = extractTextFromResponse(responseText)
                if (parsedText.isNotBlank()) {
                    Result.success(parsedText)
                } else {
                    Result.failure(Exception("ИИ вернул пустой ответ."))
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = parseErrorMessage(errorBody, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Ошибка соединения с ИИ: ${e.localizedMessage ?: "проверьте подключение к сети"}"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeGeminiRequest(
        modelName: String,
        apiKey: String,
        prompt: String,
        base64Image: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 30000
                readTimeout = 40000
                doOutput = true
                doInput = true
            }

            // Build request payload
            val rootJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()

                // Text part
                parts.put(JSONObject().apply {
                    put("text", prompt)
                })

                // Image part
                parts.put(JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", "image/jpeg")
                        put("data", base64Image)
                    }
                    put("inlineData", inlineData)
                })

                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

                // Generation config
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                })
            }

            connection.outputStream.use { os ->
                val inputBytes = rootJson.toString().toByteArray(Charsets.UTF_8)
                os.write(inputBytes, 0, inputBytes.size)
                os.flush()
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val responseText = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val parsedText = extractTextFromResponse(responseText)
                if (parsedText.isNotBlank()) {
                    Result.success(parsedText)
                } else {
                    Result.failure(Exception("ИИ не нашел текст на изображении."))
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = parseErrorMessage(errorBody, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Сетевая ошибка при обращении к ИИ: ${e.localizedMessage ?: "проверьте подключение к интернету"}"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun extractTextFromResponse(jsonResponse: String): String {
        return try {
            val root = JSONObject(jsonResponse)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                if (text.isNotEmpty()) {
                    sb.append(text)
                }
            }
            var result = sb.toString().trim()
            if (result.startsWith("```")) {
                result = result.removePrefix("```").trim()
                if (result.startsWith("markdown") || result.startsWith("text")) {
                    result = result.substringAfter("\n").trim()
                }
                if (result.endsWith("```")) {
                    result = result.removeSuffix("```").trim()
                }
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    private fun parseErrorMessage(errorBody: String, responseCode: Int): String {
        return try {
            val json = JSONObject(errorBody)
            val errObj = json.optJSONObject("error")
            val message = errObj?.optString("message")
            when {
                responseCode == 400 && message?.contains("API_KEY_INVALID", ignoreCase = true) == true ->
                    "Неверный API-ключ Gemini. Проверьте ключ в настройках."
                responseCode == 429 || message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ->
                    "Превышен лимит запросов к ИИ. Подождите немного или повторите попытку."
                !message.isNullOrBlank() -> message
                else -> "Ошибка сервера ИИ ($responseCode)"
            }
        } catch (_: Exception) {
            "Ошибка сервера ИИ ($responseCode)"
        }
    }

    private fun encodeImageToBase64(context: Context, imageUri: Uri): String? {
        val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
        val originalBitmap = inputStream?.use { BitmapFactory.decodeStream(it) } ?: return null

        val maxDim = 1600
        val width = originalBitmap.width
        val height = originalBitmap.height
        val scale = if (width > maxDim || height > maxDim) {
            val largest = max(width, height)
            maxDim.toFloat() / largest
        } else {
            1.0f
        }

        val scaledBitmap = if (scale < 1.0f) {
            Bitmap.createScaledBitmap(
                originalBitmap,
                (width * scale).toInt(),
                (height * scale).toInt(),
                true
            )
        } else {
            originalBitmap
        }

        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        val byteArray = outputStream.toByteArray()

        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()

        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
