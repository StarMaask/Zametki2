package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import com.example.domain.model.AiAttachment
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.math.max

object AiAttachmentHelper {

    suspend fun processAttachment(context: Context, uri: Uri): Result<AiAttachment> = withContext(Dispatchers.IO) {
        try {
            var fileName = "document"
            var fileSize = 0L

            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                    if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                }
            }

            val rawMimeType = context.contentResolver.getType(uri) ?: getMimeTypeFromExtension(fileName)
            val lowerName = fileName.lowercase()

            val isImage = rawMimeType.startsWith("image/") ||
                    lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") ||
                    lowerName.endsWith(".png") || lowerName.endsWith(".webp") || lowerName.endsWith(".bmp")

            val isPdf = rawMimeType == "application/pdf" || lowerName.endsWith(".pdf")
            val isDocx = lowerName.endsWith(".docx")
            val isText = rawMimeType.startsWith("text/") ||
                    lowerName.endsWith(".txt") || lowerName.endsWith(".md") ||
                    lowerName.endsWith(".csv") || lowerName.endsWith(".json") || lowerName.endsWith(".xml")

            var base64Data: String? = null
            var extractedText: String? = null
            var effectiveMime = rawMimeType

            when {
                isImage -> {
                    effectiveMime = "image/jpeg"
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val originalBitmap = inputStream?.use { BitmapFactory.decodeStream(it) }
                    if (originalBitmap != null) {
                        val maxDim = 2048
                        val w = originalBitmap.width
                        val h = originalBitmap.height
                        val scale = if (w > maxDim || h > maxDim) {
                            maxDim.toFloat() / max(w, h)
                        } else 1.0f

                        val scaledBitmap = if (scale < 1.0f) {
                            Bitmap.createScaledBitmap(originalBitmap, (w * scale).toInt(), (h * scale).toInt(), true)
                        } else originalBitmap

                        val bos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 88, bos)
                        val bytes = bos.toByteArray()
                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        // Run local ML Kit OCR as supplementary text representation
                        try {
                            val inputImg = InputImage.fromBitmap(scaledBitmap, 0)
                            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                            val ocrResult = Tasks.await(recognizer.process(inputImg))
                            if (ocrResult.text.isNotBlank()) {
                                extractedText = ocrResult.text.trim()
                            }
                        } catch (_: Exception) {
                            // Ignore OCR errors, multimodal base64 image will still be sent to Gemini
                        }

                        if (scaledBitmap != originalBitmap) {
                            scaledBitmap.recycle()
                        }
                        originalBitmap.recycle()
                    }
                }

                isPdf -> {
                    effectiveMime = "application/pdf"
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bytes = stream.readBytes()
                        if (fileSize <= 0) fileSize = bytes.size.toLong()
                        base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    }
                }

                isDocx -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        extractedText = extractTextFromDocx(stream)
                    }
                }

                isText -> {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        extractedText = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }

                else -> {
                    // Try reading as text by default
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        extractedText = stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                }
            }

            val attachment = AiAttachment(
                uri = uri,
                name = fileName,
                mimeType = effectiveMime,
                sizeBytes = fileSize,
                isImage = isImage,
                isPdf = isPdf,
                isTextDoc = isDocx || isText,
                base64Data = base64Data,
                extractedText = extractedText
            )

            Result.success(attachment)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun extractTextFromDocx(inputStream: InputStream): String {
        val zip = ZipInputStream(inputStream)
        var entry = zip.nextEntry
        val sb = StringBuilder()
        while (entry != null) {
            if (entry.name == "word/document.xml") {
                val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                val text = xml
                    .replace("</w:p>", "\n")
                    .replace("</w:tr>", "\n")
                    .replace("</w:tc>", "\t")
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&apos;", "'")
                sb.append(text.trim())
                break
            }
            entry = zip.nextEntry
        }
        return sb.toString()
    }

    private fun getMimeTypeFromExtension(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".webp") -> "image/webp"
            lower.endsWith(".pdf") -> "application/pdf"
            lower.endsWith(".txt") -> "text/plain"
            lower.endsWith(".md") -> "text/markdown"
            lower.endsWith(".csv") -> "text/csv"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".docx") -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            else -> "application/octet-stream"
        }
    }
}
