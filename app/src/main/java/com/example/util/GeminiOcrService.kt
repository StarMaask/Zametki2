package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import com.example.BuildConfig
import com.example.data.preferences.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
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

    internal fun parseErrorMessage(errorBody: String, responseCode: Int): String {
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
     * Transcribes an audio recording (lecture, meeting, monologue, long recording up to 60+ min)
     * into clean, structured text using Gemini AI.
     *
     * Features:
     * 1. Streaming upload via Gemini Files API for large recordings (>8MB / >5min, e.g. 55-minute files).
     *    Eliminates OutOfMemoryError by avoiding loading entire files into JVM heap memory.
     * 2. Direct inline audio for small voice clips (<=8MB).
     * 3. Intelligent fallback chunking via AudioChunkerUtil if direct upload times out or fails.
     * 4. Multi-level safety catches Throwable to guarantee the app never crashes or force-closes.
     * 5. Real-time progress updates through [onProgress] callback.
     * 6. CPU WakeLock protection so transcription completes even when screen is locked.
     */
    suspend fun transcribeAudioWithGemini(
        context: Context,
        audioFile: java.io.File,
        customApiKey: String? = null,
        onProgress: ((String) -> Unit)? = null
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

        // Acquire temporary WakeLock to prevent CPU sleep during long transcription
        var wakeLock: PowerManager.WakeLock? = null
        try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "NotesApp:GeminiAudioTranscription")
            wakeLock?.acquire(10 * 60 * 1000L) // 10 minutes max
        } catch (_: Throwable) {}

        try {
            val fileLength = audioFile.length()
            val durationMs = AudioChunkerUtil.getAudioDurationMs(audioFile)
            val durationMinutes = durationMs / (60 * 1000L)

            val mimeType = when {
                audioFile.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                audioFile.name.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
                audioFile.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                audioFile.name.endsWith(".aac", ignoreCase = true) -> "audio/aac"
                audioFile.name.endsWith(".ogg", ignoreCase = true) -> "audio/ogg"
                audioFile.name.endsWith(".flac", ignoreCase = true) -> "audio/flac"
                else -> "audio/mp4"
            }

            val prompt = "Ты — профессиональная система расшифровки аудио в текст (Speech-to-Text) для русского языка.\n" +
                    "Расшифруй предоставленную аудиозапись полностью и точно от начала до самого конца.\n" +
                    "Правила:\n" +
                    "1. Точно передай все сказанные слова, мысли, термины и числовые данные.\n" +
                    "2. Расставь правильную пунктуацию, заглавные буквы и разбей речь на логические абзацы.\n" +
                    "3. Убери слова-паразиты и заикания, если они мешают восприятию смысла.\n" +
                    "4. Не обрывай и не сокращай текст, передай всю запись полностью.\n" +
                    "5. Верни ТОЛЬКО расшифрованный текст заметки без вступительных фраз или обрамления в ```."

            // For large files (> 8 MB or > 5 min, e.g. 55-minute recordings):
            // Use Gemini Files API with streaming upload (Zero RAM overhead, supports up to 2GB)
            val isLargeAudio = fileLength > 8 * 1024 * 1024L || durationMinutes >= 5

            if (isLargeAudio) {
                onProgress?.invoke("Подготовка записи (~${if (durationMinutes > 0) "$durationMinutes мин" else "${fileLength / 1024 / 1024} МБ"})...")

                // Try Tier 1: Gemini Files API streaming upload
                val filesApiResult = transcribeWithGeminiFilesApi(
                    apiKey = apiKey,
                    audioFile = audioFile,
                    mimeType = mimeType,
                    prompt = prompt,
                    onProgress = onProgress
                )

                if (filesApiResult.isSuccess) {
                    return@withContext filesApiResult
                }

                // If Files API failed, try Tier 2: Chunked transcription via AudioChunkerUtil
                Log.w("GeminiOcrService", "Files API failed (${filesApiResult.exceptionOrNull()?.localizedMessage}), falling back to audio chunking...")
                onProgress?.invoke("Оптимизация длинной записи (разбивка на блоки)...")

                val chunkedResult = transcribeWithChunkingFallback(
                    context = context,
                    apiKey = apiKey,
                    audioFile = audioFile,
                    prompt = prompt,
                    onProgress = onProgress
                )

                return@withContext chunkedResult
            }

            // For small files (<= 8 MB and < 5 min):
            // Try direct inline audio with strict memory bounds
            val inlineResult = transcribeSmallAudioInline(
                apiKey = apiKey,
                audioFile = audioFile,
                mimeType = mimeType,
                prompt = prompt,
                onProgress = onProgress
            )

            if (inlineResult.isSuccess) {
                return@withContext inlineResult
            }

            // If inline failed (e.g. 400 payload limit or OOM), fallback to Files API
            onProgress?.invoke("Загрузка аудиозаписи через облачный буфер...")
            return@withContext transcribeWithGeminiFilesApi(
                apiKey = apiKey,
                audioFile = audioFile,
                mimeType = mimeType,
                prompt = prompt,
                onProgress = onProgress
            )

        } catch (t: Throwable) {
            Log.e("GeminiOcrService", "Fatal error in transcribeAudioWithGemini", t)
            System.gc()
            val errorText = when {
                t is OutOfMemoryError -> "Недостаточно оперативной памяти для обработки записи. Рекомендуется закрыть другие приложения и повторить попытку."
                t.message?.contains("20971520") == true -> "Размер записи превысил лимит одного запроса. Рекомендуется повторить попытку через облако."
                else -> t.localizedMessage ?: "Внутренняя ошибка при распознавании звука"
            }
            Result.failure(Exception(errorText))
        } finally {
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (_: Throwable) {}
        }
    }

    /**
     * Uploads large audio files (up to 2GB) using the Google Gemini Files API via streaming.
     * Reads the file in 64 KB chunks directly from disk to network, ensuring near-zero memory footprint.
     */
    private suspend fun transcribeWithGeminiFilesApi(
        apiKey: String,
        audioFile: java.io.File,
        mimeType: String,
        prompt: String,
        onProgress: ((String) -> Unit)?
    ): Result<String> {
        var remoteFileName = ""
        try {
            val fileLength = audioFile.length()
            onProgress?.invoke("Подключение к облаку ИИ (Files API)...")

            // Step 1: Initiate Resumable Upload
            val initUrl = URL("https://generativelanguage.googleapis.com/upload/v1beta/files?key=$apiKey")
            val initConn = (initUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("X-Goog-Upload-Protocol", "resumable")
                setRequestProperty("X-Goog-Upload-Command", "start")
                setRequestProperty("X-Goog-Upload-Header-Content-Length", fileLength.toString())
                setRequestProperty("X-Goog-Upload-Header-Content-Type", mimeType)
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 45000
                readTimeout = 45000
                doOutput = true
                doInput = true
            }

            val metadataJson = JSONObject().apply {
                val fileObj = JSONObject().apply {
                    put("display_name", audioFile.name)
                }
                put("file", fileObj)
            }

            initConn.outputStream.use { os ->
                os.write(metadataJson.toString().toByteArray(Charsets.UTF_8))
                os.flush()
            }

            val initCode = initConn.responseCode
            if (initCode !in 200..299) {
                val errBody = initConn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                initConn.disconnect()
                return Result.failure(Exception(parseErrorMessage(errBody, initCode)))
            }

            var uploadUrl = initConn.getHeaderField("X-Goog-Upload-URL")
            if (uploadUrl.isNullOrBlank()) {
                uploadUrl = initConn.getHeaderField("x-goog-upload-url")
            }
            initConn.disconnect()

            if (uploadUrl.isNullOrBlank()) {
                return Result.failure(Exception("Gemini Files API не вернул URL для загрузки файла."))
            }

            // Step 2: Stream audio data directly from disk (64KB buffer, zero OOM risk)
            onProgress?.invoke("Загрузка аудио в Gemini: 0%...")
            val uploadConn = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Length", fileLength.toString())
                setRequestProperty("X-Goog-Upload-Offset", "0")
                setRequestProperty("X-Goog-Upload-Command", "upload, finalize")
                connectTimeout = 60000
                readTimeout = 300000 // 5 minutes for upload
                doOutput = true
                doInput = true
            }

            var bytesSent = 0L
            var lastReportedPercent = -1
            FileInputStream(audioFile).use { fis ->
                uploadConn.outputStream.use { os ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    while (fis.read(buffer).also { read = it } != -1) {
                        os.write(buffer, 0, read)
                        bytesSent += read
                        val percent = if (fileLength > 0) ((bytesSent * 100) / fileLength).toInt() else 0
                        if (percent != lastReportedPercent && percent % 5 == 0) {
                            lastReportedPercent = percent
                            onProgress?.invoke("Загрузка аудио в Gemini: $percent%...")
                        }
                    }
                    os.flush()
                }
            }

            val uploadCode = uploadConn.responseCode
            if (uploadCode !in 200..299) {
                val errBody = uploadConn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
                uploadConn.disconnect()
                return Result.failure(Exception(parseErrorMessage(errBody, uploadCode)))
            }

            val uploadResponseBody = uploadConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            uploadConn.disconnect()

            val uploadJson = JSONObject(uploadResponseBody)
            val fileObj = uploadJson.optJSONObject("file")
                ?: return Result.failure(Exception("Некорректный ответ от Gemini Files API."))
            val fileUri = fileObj.getString("uri")
            remoteFileName = fileObj.optString("name") // e.g. "files/xyz123"

            // Step 3: Wait if file state is PROCESSING (audio is usually ACTIVE immediately)
            var state = fileObj.optString("state", "ACTIVE")
            var checkAttempts = 0
            while (state == "PROCESSING" && checkAttempts < 15) {
                delay(2000)
                checkAttempts++
                onProgress?.invoke("Сервер обрабатывает аудиозапись...")
                try {
                    val statusUrl = URL("https://generativelanguage.googleapis.com/v1beta/$remoteFileName?key=$apiKey")
                    val statusConn = (statusUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15000
                        readTimeout = 15000
                    }
                    if (statusConn.responseCode in 200..299) {
                        val body = statusConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                        state = JSONObject(body).optString("state", "ACTIVE")
                    }
                    statusConn.disconnect()
                } catch (_: Throwable) {}
            }

            if (state == "FAILED") {
                return Result.failure(Exception("Ошибка обработки аудио на стороне сервера Gemini."))
            }

            // Step 4: Execute generateContent with fileUri
            onProgress?.invoke("ИИ выполняет полную расшифровку лекции (это может занять 1–2 мин)...")

            var lastError = "Не удалось расшифровать аудиозапись"
            for (model in OCR_MODELS) {
                val result = executeGeminiAudioRequestWithFileUri(
                    modelName = model,
                    apiKey = apiKey,
                    prompt = prompt,
                    fileUri = fileUri,
                    mimeType = mimeType
                )
                if (result.isSuccess) {
                    return result
                }
                lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
            }

            return Result.failure(Exception(lastError))
        } catch (t: Throwable) {
            Log.e("GeminiOcrService", "Files API upload failed", t)
            return Result.failure(Exception(t.localizedMessage ?: "Сетевая ошибка при загрузке аудиофайла"))
        } finally {
            // Step 5: Clean up file on Gemini server
            if (remoteFileName.isNotBlank()) {
                try {
                    val delUrl = URL("https://generativelanguage.googleapis.com/v1beta/$remoteFileName?key=$apiKey")
                    val delConn = (delUrl.openConnection() as HttpURLConnection).apply {
                        requestMethod = "DELETE"
                        connectTimeout = 10000
                        readTimeout = 10000
                    }
                    delConn.responseCode
                    delConn.disconnect()
                } catch (_: Throwable) {}
            }
        }
    }

    private fun executeGeminiAudioRequestWithFileUri(
        modelName: String,
        apiKey: String,
        prompt: String,
        fileUri: String,
        mimeType: String
    ): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
            val url = URL(urlString)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 60000
                readTimeout = 300000 // 5 minutes for long audio transcription!
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

                // Audio part via fileData
                parts.put(JSONObject().apply {
                    val fileData = JSONObject().apply {
                        put("mimeType", mimeType)
                        put("fileUri", fileUri)
                    }
                    put("fileData", fileData)
                })

                contentObj.put("parts", parts)
                contents.put(contentObj)
                put("contents", contents)

                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    put("maxOutputTokens", 65536)
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
        } catch (t: Throwable) {
            Result.failure(Exception("Ошибка соединения с ИИ: ${t.localizedMessage ?: "таймаут связи"}"))
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Splits long audio into manageable chunks (~10 minutes each), transcribes each chunk sequentially,
     * and stitches the result together.
     */
    private suspend fun transcribeWithChunkingFallback(
        context: Context,
        apiKey: String,
        audioFile: java.io.File,
        prompt: String,
        onProgress: ((String) -> Unit)?
    ): Result<String> {
        val chunks = AudioChunkerUtil.splitAudioIfNeeded(
            context = context,
            sourceFile = audioFile,
            targetChunkDurationMs = 10 * 60 * 1000L // 10-minute chunks
        )

        if (chunks.isEmpty()) {
            return Result.failure(Exception("Не удалось разделить аудиозапись на части."))
        }

        try {
            val totalChunks = chunks.size
            val partialResults = mutableListOf<String>()

            for ((index, chunk) in chunks.withIndex()) {
                val chunkNum = index + 1
                onProgress?.invoke("Расшифровка части $chunkNum из $totalChunks...")

                val chunkMime = when {
                    chunk.name.endsWith(".m4a", ignoreCase = true) -> "audio/mp4"
                    chunk.name.endsWith(".mp3", ignoreCase = true) -> "audio/mp3"
                    chunk.name.endsWith(".wav", ignoreCase = true) -> "audio/wav"
                    else -> "audio/mp4"
                }

                // If chunk is still > 8MB, use Files API for chunk, else inline
                val chunkResult = if (chunk.length() > 8 * 1024 * 1024L) {
                    transcribeWithGeminiFilesApi(apiKey, chunk, chunkMime, prompt, onProgress = {
                        onProgress?.invoke("Часть $chunkNum/$totalChunks: $it")
                    })
                } else {
                    transcribeSmallAudioInline(apiKey, chunk, chunkMime, prompt, onProgress = {
                        onProgress?.invoke("Часть $chunkNum/$totalChunks: $it")
                    })
                }

                if (chunkResult.isSuccess) {
                    val text = chunkResult.getOrNull().orEmpty().trim()
                    if (text.isNotBlank()) {
                        partialResults.add(text)
                    }
                } else {
                    val err = chunkResult.exceptionOrNull()?.localizedMessage ?: "Ошибка части $chunkNum"
                    // If we have some partial results, return what we have so user doesn't lose everything!
                    if (partialResults.isNotEmpty()) {
                        val joined = partialResults.joinToString("\n\n")
                        return Result.success("$joined\n\n[Примечание: часть $chunkNum не была расшифрована: $err]")
                    }
                    return Result.failure(Exception("Ошибка расшифровки части $chunkNum: $err"))
                }
            }

            if (partialResults.isEmpty()) {
                return Result.failure(Exception("ИИ не распознал текст ни в одной из частей аудиозаписи."))
            }

            val finalTranscript = partialResults.joinToString("\n\n")
            return Result.success(finalTranscript)
        } finally {
            AudioChunkerUtil.cleanUpChunks(chunks, audioFile)
        }
    }

    private fun transcribeSmallAudioInline(
        apiKey: String,
        audioFile: java.io.File,
        mimeType: String,
        prompt: String,
        onProgress: ((String) -> Unit)?
    ): Result<String> {
        try {
            onProgress?.invoke("Кодирование аудио...")
            val fileLength = audioFile.length()
            if (fileLength > 10 * 1024 * 1024L) {
                return Result.failure(Exception("Файл слишком велик для inline передачи (${fileLength / 1024 / 1024} МБ)"))
            }

            val bytes = audioFile.readBytes()
            val base64Audio = Base64.encodeToString(bytes, Base64.NO_WRAP)

            onProgress?.invoke("ИИ расшифровывает запись...")
            var lastError = "Не удалось расшифровать аудиозапись"
            for (model in OCR_MODELS) {
                val result = executeGeminiAudioRequest(model, apiKey, prompt, base64Audio, mimeType)
                if (result.isSuccess) {
                    return result
                }
                lastError = result.exceptionOrNull()?.localizedMessage ?: lastError
            }
            return Result.failure(Exception(lastError))
        } catch (t: Throwable) {
            System.gc()
            return Result.failure(Exception(if (t is OutOfMemoryError) "Недостаточно памяти для inline-обработки" else t.localizedMessage ?: "Ошибка чтения файла"))
        }
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
                readTimeout = 120000 // 2 minutes for inline audio
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
                    put("maxOutputTokens", 65536)
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
