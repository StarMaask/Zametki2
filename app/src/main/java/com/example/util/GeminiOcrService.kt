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

    private val OCR_MODELS = listOf(
        "gemini-3.5-flash",
        "gemini-flash-latest",
        "gemini-3.1-flash-lite-preview"
    )

    data class DocumentAnalysisResult(
        val summary: String,
        val documentType: String,
        val hasRecipient: Boolean,
        val hasApplicant: Boolean,
        val hasTitle: Boolean,
        val hasDate: Boolean,
        val hasSignature: Boolean,
        val detectedErrors: List<String>,
        val formattingRecommendations: List<String>,
        val correctedText: String
    )

    data class DocumentInterpretations(
        val officialGost: String,
        val diplomatic: String,
        val concise: String
    )

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
     * Verifies that the provided API key is valid and has active access to Gemini models.
     * Queries the Google Models API directly to authenticate the key without consuming generation quota or hitting 503 high-demand errors.
     */
    suspend fun testApiKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        val trimmedKey = apiKey.trim()
        if (trimmedKey.isBlank()) {
            return@withContext Result.failure(Exception("Ключ API пуст. Введите ключ для проверки."))
        }
        var connection: HttpURLConnection? = null
        try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models?key=$trimmedKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 12000
                readTimeout = 15000
                doInput = true
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

        // Attempt recognition with fallback models in sequence
        var lastError: String = "Не удалось распознать текст через ИИ"
        for (model in OCR_MODELS) {
            val result = executeGeminiRequest(model, apiKey, prompt, base64Image)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }

        return@withContext Result.failure(Exception(lastError))
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

        var lastError: String = "Не удалось структурировать конспект через ИИ"
        for (model in OCR_MODELS) {
            val result = executeGeminiTextRequest(model, apiKey, prompt)
            if (result.isSuccess) return@withContext result
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }

        return@withContext Result.failure(Exception(lastError))
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
                    "Неверный API-ключ Gemini. Проверьте ключ и попробуйте снова."
                responseCode == 403 ->
                    "Доступ запрещен. Убедитесь, что для ключа включен доступ к Generative Language API."
                responseCode == 429 || message?.contains("RESOURCE_EXHAUSTED", ignoreCase = true) == true ->
                    "Превышен лимит запросов к ИИ. Подождите немного или повторите попытку."
                responseCode == 503 || message?.contains("high demand", ignoreCase = true) == true ->
                    "Сервер Gemini временно перегружен запросами. Повторите попытку через минуту."
                !message.isNullOrBlank() -> message
                else -> "Ошибка сервера ИИ ($responseCode)"
            }
        } catch (_: Exception) {
            "Ошибка сервера ИИ ($responseCode)"
        }
    }

    /**
     * Transcribes an audio recording (lecture, meeting, monologue) into clean, structured text using Gemini AI.
     */
    suspend fun transcribeAudioWithGemini(
        context: Context,
        audioFile: java.io.File,
        customApiKey: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = customApiKey?.trim()?.takeIf { it.isNotBlank() }
            ?: getEffectiveApiKey(context)

        if (apiKey.isBlank()) {
            return@withContext Result.failure(
                IllegalStateException("API-ключ Gemini не найден. Укажите бесплатный ключ в настройках приложения.")
            )
        }

        if (!audioFile.exists() || audioFile.length() == 0L) {
            return@withContext Result.failure(Exception("Аудиофайл пуст или не найден."))
        }

        val base64Audio = try {
            val bytes = audioFile.readBytes()
            Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            return@withContext Result.failure(Exception("Не удалось прочитать аудиозапись: ${e.localizedMessage}"))
        }

        val prompt = "Ты — профессиональная система расшифровки аудио в текст (Speech-to-Text) для русского языка.\n" +
                "Расшифруй предоставленную аудиозапись полностью и точно.\n" +
                "Правила:\n" +
                "1. Точно передай все сказанные слова, мысли, термины и числовые данные.\n" +
                "2. Расставь правильную пунктуацию, заглавные буквы и разбей речь на логические абзацы.\n" +
                "3. Убери слова-паразиты и заикания, если они мешают восприятию смысла.\n" +
                "4. Верни ТОЛЬКО расшифрованный текст заметки без вступительных фраз или обрамления в ```."

        val mimeType = when {
            audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
            audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
            audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
            else -> "audio/mp4"
        }

        var lastError = "Не удалось расшифровать аудиозапись через ИИ"
        for (model in OCR_MODELS) {
            val result = executeGeminiAudioRequest(model, apiKey, prompt, base64Audio, mimeType)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }

        Result.failure(Exception(lastError))
    }

    private fun executeGeminiAudioRequest(
        modelName: String,
        apiKey: String,
        prompt: String,
        base64Audio: String,
        mimeType: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 45000
                readTimeout = 60000
                doOutput = true
                doInput = true
            }

            val rootJson = JSONObject().apply {
                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray()

                // Prompt
                parts.put(JSONObject().apply {
                    put("text", prompt)
                })

                // Audio part
                parts.put(JSONObject().apply {
                    val inlineData = JSONObject().apply {
                        put("mimeType", mimeType)
                        put("data", base64Audio)
                    }
                    put("inlineData", inlineData)
                })

                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

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
                    Result.failure(Exception("ИИ вернул пустой текст расшифровки."))
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                val errorMessage = parseErrorMessage(errorBody, responseCode)
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(Exception("Ошибка соединения с ИИ: ${e.localizedMessage ?: "проверьте сеть"}"))
        } finally {
            connection?.disconnect()
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

    /**
     * Executes a pure text prompt request to Gemini models with fallback.
     */
    suspend fun executeTextPrompt(
        context: Context,
        prompt: String,
        systemInstruction: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API-ключ Gemini не найден. Укажите бесплатный ключ в настройках."))
        }

        var lastError = "Не удалось выполнить запрос к ИИ"
        for (model in OCR_MODELS) {
            val result = executeGeminiTextRequest(model, apiKey, prompt, systemInstruction)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }
        Result.failure(Exception(lastError))
    }

    private fun executeGeminiTextRequest(
        modelName: String,
        apiKey: String,
        prompt: String,
        systemInstruction: String?
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 30000
                readTimeout = 45000
                doOutput = true
                doInput = true
            }

            val rootJson = JSONObject().apply {
                if (!systemInstruction.isNullOrBlank()) {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().apply {
                            put("text", systemInstruction)
                        }))
                    })
                }

                val contents = JSONArray()
                val contentObj = JSONObject()
                val parts = JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                })
                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.2)
                })
            }

            connection.outputStream.use { os ->
                val bytes = rootJson.toString().toByteArray(Charsets.UTF_8)
                os.write(bytes, 0, bytes.size)
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
            Result.failure(Exception("Ошибка соединения: ${e.localizedMessage ?: "проверьте сеть"}"))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Analyzes document text: checks for mandatory requisites (recipient, applicant, title, date, signature),
     * finds legal/grammatical errors, and generates an official, corrected version formatted to standards.
     */
    suspend fun analyzeDocument(
        context: Context,
        text: String
    ): Result<DocumentAnalysisResult> = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            return@withContext Result.failure(Exception("Текст документа пуст для анализа."))
        }

        val prompt = """
            Ты — главный эксперт по делопроизводству и стандарту ГОСТ Р 7.0.97-2016 (организационно-распорядительная документация).
            Проанализируй следующий текст документа и верни ответ СТРОГО в формате JSON без каких-либо внешних символов markdown (без ```json):
            {
              "documentType": "Название типа документа (например: Заявление на отпуск, Служебная записка, Акт, Договор, Произвольный текст)",
              "summary": "Краткая суть документа в 1-2 предложениях",
              "hasRecipient": true/false (есть ли адресат: Кому, например: Директору ООО...),
              "hasApplicant": true/false (есть ли заявитель: От кого, должность, ФИО),
              "hasTitle": true/false (есть ли наименование: ЗАЯВЛЕНИЕ, АКТ и т.д.),
              "hasDate": true/false (есть ли дата),
              "hasSignature": true/false (есть ли указание на подпись),
              "detectedErrors": ["список обнаруженных ошибок в оформлении, орфографии, пунктуации или юридических неточностей"],
              "formattingRecommendations": ["конкретные рекомендации по оформлению по ГОСТ"],
              "correctedText": "Полный исправленный и профессионально оформленный текст документа по всем правилам делопроизводства с шапкой, абзацами и реквизитами"
            }

            Текст для анализа:
            $text
        """.trimIndent()

        val rawResult = executeTextPrompt(context, prompt)
        if (rawResult.isFailure) {
            return@withContext Result.failure(rawResult.exceptionOrNull() ?: Exception("Ошибка анализа"))
        }

        try {
            val jsonStr = rawResult.getOrNull().orEmpty()
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(jsonStr)
            val detectedErrors = mutableListOf<String>()
            val errorsArray = json.optJSONArray("detectedErrors")
            if (errorsArray != null) {
                for (i in 0 until errorsArray.length()) {
                    detectedErrors.add(errorsArray.optString(i))
                }
            }

            val formattingRecs = mutableListOf<String>()
            val recsArray = json.optJSONArray("formattingRecommendations")
            if (recsArray != null) {
                for (i in 0 until recsArray.length()) {
                    formattingRecs.add(recsArray.optString(i))
                }
            }

            Result.success(
                DocumentAnalysisResult(
                    summary = json.optString("summary", "Документ проанализирован"),
                    documentType = json.optString("documentType", "Деловой документ"),
                    hasRecipient = json.optBoolean("hasRecipient", false),
                    hasApplicant = json.optBoolean("hasApplicant", false),
                    hasTitle = json.optBoolean("hasTitle", false),
                    hasDate = json.optBoolean("hasDate", false),
                    hasSignature = json.optBoolean("hasSignature", false),
                    detectedErrors = detectedErrors,
                    formattingRecommendations = formattingRecs,
                    correctedText = json.optString("correctedText", text)
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Не удалось распарсить ответ ИИ: ${e.localizedMessage}"))
        }
    }

    /**
     * Formats document according to Russian standards (ГОСТ):
     * - Right-aligned header block (Кому/От кого)
     * - Centered uppercase title
     * - Justified body with paragraph indents
     * - Date and signature block
     */
    suspend fun formatDocumentByGost(
        context: Context,
        text: String
    ): Result<String> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Текст пуст"))

        val prompt = """
            Оформи следующий текст строго по правилам российского делопроизводства и стандарта ГОСТ Р 7.0.97-2016.
            Правила оформления:
            1. Если это заявление, служебная записка или подобный документ, в самом начале сформируй правый блок реквизитов (Адресат «Кому...», Заявитель «От кого...»).
            2. Наименование документа напиши ПО ЦЕНТРУ заглавными буквами (например: ЗАЯВЛЕНИЕ, СЛУЖЕБНАЯ ЗАПИСКА, АКТ).
            3. Основной текст разбей на аккуратные абзацы с соблюдением делового стиля.
            4. В конце обязательно добавь реквизиты даты (слева) и подписи с расшифровкой (справа).
            5. Верни ТОЛЬКО отформатированный текст без вступительных фраз и без обрамления в ```.

            Исходный текст:
            $text
        """.trimIndent()

        executeTextPrompt(context, prompt)
    }

    /**
     * Generates 3 distinct stylistic interpretations / treatments for a text:
     * 1. Official/Legal (Строгий официально-деловой по ГОСТ)
     * 2. Diplomatic (Дипломатичный, корректный и доброжелательный)
     * 3. Concise (Лаконичный, убедительный, краткий)
     */
    suspend fun generateInterpretations(
        context: Context,
        text: String
    ): Result<DocumentInterpretations> = withContext(Dispatchers.IO) {
        if (text.isBlank()) return@withContext Result.failure(Exception("Текст пуст для трактовок"))

        val prompt = """
            Предложи 3 различные профессиональные трактовки / редакции следующего текста для различных ситуаций.
            Верни ответ СТРОГО в формате JSON без ```json:
            {
              "officialGost": "Официально-деловая трактовка (строгий юридический стиль, терминология ГОСТ, для руководства, судов, госорганов)",
              "diplomatic": "Дипломатичная и конструктивная трактовка (корпоративный вежливый стиль, акцент на взаимовыгоде и сотрудничестве)",
              "concise": "Лаконичная и убедительная трактовка (кратко, чётко, только суть и факты, легко читается за 10 секунд)"
            }

            Исходный текст:
            $text
        """.trimIndent()

        val rawResult = executeTextPrompt(context, prompt)
        if (rawResult.isFailure) {
            return@withContext Result.failure(rawResult.exceptionOrNull() ?: Exception("Ошибка запроса"))
        }

        try {
            val jsonStr = rawResult.getOrNull().orEmpty()
                .trim()
                .removePrefix("```json")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()

            val json = JSONObject(jsonStr)
            Result.success(
                DocumentInterpretations(
                    officialGost = json.optString("officialGost", text),
                    diplomatic = json.optString("diplomatic", text),
                    concise = json.optString("concise", text)
                )
            )
        } catch (e: Exception) {
            Result.failure(Exception("Ошибка разбора трактовок: ${e.localizedMessage}"))
        }
    }

    /**
     * Extracts structured table data from an image/photo into clean Markdown table format.
     */
    suspend fun extractTableFromImage(
        context: Context,
        imageUri: Uri
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey(context)
        if (apiKey.isBlank()) {
            return@withContext Result.failure(Exception("API-ключ Gemini не найден."))
        }

        val base64Image = encodeImageToBase64(context, imageUri)
            ?: return@withContext Result.failure(Exception("Не удалось загрузить изображение."))

        val prompt = "Найди на изображении таблицу, бланк, квитанцию или список данных и преобразуй в чистую Markdown-таблицу (| колонка 1 | колонка 2 |). Точно сохрани все числа, даты и заголовки. Верни ТОЛЬКО markdown-таблицу без комментариев."

        var lastError = "Не удалось извлечь таблицу"
        for (model in OCR_MODELS) {
            val result = executeGeminiRequest(model, apiKey, prompt, base64Image)
            if (result.isSuccess) {
                return@withContext result
            }
            lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
        }
        Result.failure(Exception(lastError))
    }
}
