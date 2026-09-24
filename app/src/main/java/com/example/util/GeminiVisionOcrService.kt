package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object GeminiVisionOcrService {

    private const val MODEL_NAME = "gemini-2.5-flash"
    private const val API_URL = "https://generativelanguage.googleapis.com/v1beta/models/$MODEL_NAME:generateContent"

    sealed class OcrResult {
        data class Success(val text: String) : OcrResult()
        data class MissingApiKey(val message: String) : OcrResult()
        data class Error(val message: String) : OcrResult()
    }

    /**
     * Checks if Gemini API key is configured.
     */
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotBlank() && key != "MY_GEMINI_API_KEY"
    }

    /**
     * Performs optical character recognition on an image using Gemini Multimodal Vision API.
     */
    suspend fun recognizeTextWithGemini(
        context: Context,
        imageUri: Uri,
        userCustomKey: String? = null
    ): OcrResult = withContext(Dispatchers.IO) {
        val apiKey = when {
            !userCustomKey.isNullOrBlank() -> userCustomKey.trim()
            isApiKeyConfigured() -> BuildConfig.GEMINI_API_KEY.trim()
            else -> ""
        }

        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext OcrResult.MissingApiKey(
                "Для точного распознавания русского текста через ИИ требуется Gemini API ключ. " +
                "Вы можете настроить его в панели Secrets в AI Studio или воспользоваться локальным сканером с автоисправлением."
            )
        }

        return@withContext try {
            val bitmap = loadOptimizedBitmap(context, imageUri)
                ?: return@withContext OcrResult.Error("Не удалось загрузить изображение с устройства.")

            val base64Data = bitmapToBase64Jpeg(bitmap)

            val prompt = "Распознай весь печатный и рукописный текст с изображения точно, дословно, без ошибок и искажений. " +
                    "Язык текста — русский. Сохраняй структуру абзацев, пунктуацию, регистр букв, нумерованные и маркированные списки, таблицы. " +
                    "Выведи исключительно распознанный текст без каких-либо вводных слов, пояснений и кавычек."

            val requestJson = buildJsonObject {
                putJsonArray("contents") {
                    addJsonObject {
                        putJsonArray("parts") {
                            addJsonObject {
                                put("text", prompt)
                            }
                            addJsonObject {
                                putJsonObject("inlineData") {
                                    put("mimeType", "image/jpeg")
                                    put("data", base64Data)
                                }
                            }
                        }
                    }
                }
            }.toString()

            val endpoint = "$API_URL?key=$apiKey"
            val url = URL(endpoint)
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                connectTimeout = 30_000
                readTimeout = 45_000
                doInput = true
                doOutput = true
            }

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestJson)
                writer.flush()
            }

            val responseCode = connection.responseCode
            val responseBody = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } else {
                val errorStream = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                connection.disconnect()
                return@withContext OcrResult.Error("Ошибка API Gemini ($responseCode): $errorStream")
            }
            connection.disconnect()

            val rootJson = Json.parseToJsonElement(responseBody).jsonObject
            val candidates = rootJson["candidates"]?.jsonArray
            val firstCandidate = candidates?.firstOrNull()?.jsonObject
            val content = firstCandidate?.get("content")?.jsonObject
            val parts = content?.get("parts")?.jsonArray
            val extractedText = parts?.firstOrNull()?.jsonObject?.get("text")?.jsonPrimitive?.content

            if (!extractedText.isNullOrBlank()) {
                OcrResult.Success(extractedText.trim())
            } else {
                OcrResult.Error("Изображение обработано, но текст не найден.")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            OcrResult.Error("Сетевая ошибка при обращении к ИИ: ${e.localizedMessage ?: e.message}")
        }
    }

    private fun loadOptimizedBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, options)
            }

            val maxDimension = 1400
            var sampleSize = 1
            while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val bitmap = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, decodeOptions)
            } ?: return null

            // Handle Exif rotation
            val rotation = getExifOrientationDegrees(context, uri)
            if (rotation != 0) {
                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getExifOrientationDegrees(context: Context, uri: Uri): Int {
        return try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        } catch (_: Exception) {
            0
        }
    }

    private fun bitmapToBase64Jpeg(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}
