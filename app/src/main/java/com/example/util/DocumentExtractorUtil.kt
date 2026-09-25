package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

data class ExtractedDocumentInfo(
    val uri: Uri,
    val fileName: String,
    val fileExtension: String,
    val fileSizeBytes: Long,
    val formattedSize: String,
    val pageCount: Int,
    val extractedText: String,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

object DocumentExtractorUtil {

    suspend fun extractDocument(
        context: Context,
        uri: Uri,
        maxPdfPages: Int = 20
    ): ExtractedDocumentInfo = withContext(Dispatchers.IO) {
        val (fileName, fileSize) = queryFileInfo(context, uri)
        val ext = fileName.substringAfterLast(".", "").lowercase()

        try {
            when (ext) {
                "pdf" -> {
                    val (pages, text) = extractPdfText(context, uri, maxPdfPages)
                    ExtractedDocumentInfo(
                        uri = uri,
                        fileName = fileName,
                        fileExtension = ext,
                        fileSizeBytes = fileSize,
                        formattedSize = formatFileSize(fileSize),
                        pageCount = pages,
                        extractedText = text,
                        isSuccess = text.isNotBlank(),
                        errorMessage = if (text.isBlank()) "В PDF не обнаружен текст или страницы пустые" else null
                    )
                }

                "docx" -> {
                    val text = extractDocxText(context, uri)
                    ExtractedDocumentInfo(
                        uri = uri,
                        fileName = fileName,
                        fileExtension = ext,
                        fileSizeBytes = fileSize,
                        formattedSize = formatFileSize(fileSize),
                        pageCount = 1,
                        extractedText = text,
                        isSuccess = text.isNotBlank(),
                        errorMessage = if (text.isBlank()) "В документе Word не обнаружен текст" else null
                    )
                }

                "doc" -> {
                    // For legacy binary .doc, try reading as raw text strings
                    val text = extractBinaryDocText(context, uri)
                    ExtractedDocumentInfo(
                        uri = uri,
                        fileName = fileName,
                        fileExtension = ext,
                        fileSizeBytes = fileSize,
                        formattedSize = formatFileSize(fileSize),
                        pageCount = 1,
                        extractedText = text,
                        isSuccess = text.isNotBlank(),
                        errorMessage = if (text.isBlank()) "Формат .doc устарел. Рекомендуется сохранить как .docx или PDF" else null
                    )
                }

                // Plain text formats
                "txt", "md", "markdown", "csv", "tsv", "json", "xml", "html", "htm", "log", "rtf" -> {
                    val text = extractPlainText(context, uri)
                    ExtractedDocumentInfo(
                        uri = uri,
                        fileName = fileName,
                        fileExtension = ext,
                        fileSizeBytes = fileSize,
                        formattedSize = formatFileSize(fileSize),
                        pageCount = 1,
                        extractedText = text,
                        isSuccess = text.isNotBlank(),
                        errorMessage = if (text.isBlank()) "Текстовый файл пуст" else null
                    )
                }

                else -> {
                    // Try plain text extraction first
                    val text = extractPlainText(context, uri)
                    if (text.isNotBlank() && isMostlyReadableText(text)) {
                        ExtractedDocumentInfo(
                            uri = uri,
                            fileName = fileName,
                            fileExtension = ext,
                            fileSizeBytes = fileSize,
                            formattedSize = formatFileSize(fileSize),
                            pageCount = 1,
                            extractedText = text,
                            isSuccess = true
                        )
                    } else {
                        ExtractedDocumentInfo(
                            uri = uri,
                            fileName = fileName,
                            fileExtension = ext,
                            fileSizeBytes = fileSize,
                            formattedSize = formatFileSize(fileSize),
                            pageCount = 0,
                            extractedText = "",
                            isSuccess = false,
                            errorMessage = "Формат файла .$ext не поддерживается для прямого извлечения текста"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ExtractedDocumentInfo(
                uri = uri,
                fileName = fileName,
                fileExtension = ext,
                fileSizeBytes = fileSize,
                formattedSize = formatFileSize(fileSize),
                pageCount = 0,
                extractedText = "",
                isSuccess = false,
                errorMessage = "Ошибка при чтении документа: ${e.localizedMessage ?: "неизвестная ошибка"}"
            )
        }
    }

    private fun extractPlainText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        if (bytes.isEmpty()) return ""

        // Try UTF-8
        val utf8 = try {
            val decoder = Charsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }

        if (utf8 != null && isMostlyReadableText(utf8)) {
            return utf8.trim()
        }

        // Fallback to Windows-1251 (Russian CP1251)
        val cp1251 = try {
            String(bytes, Charset.forName("windows-1251"))
        } catch (_: Exception) {
            null
        }

        if (cp1251 != null && isMostlyReadableText(cp1251)) {
            return cp1251.trim()
        }

        return String(bytes, Charsets.ISO_8859_1).trim()
    }

    private fun extractDocxText(context: Context, uri: Uri): String {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
        val sb = StringBuilder()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val xmlContent = zis.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    return parseDocxXml(xmlContent)
                }
                entry = zis.nextEntry
            }
        }
        return sb.toString().trim()
    }

    private fun parseDocxXml(xml: String): String {
        val sb = StringBuilder()
        val paragraphRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
        val textRegex = Regex("<w:t[ >](.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)

        val paragraphs = paragraphRegex.findAll(xml)
        if (paragraphs.any()) {
            for (p in paragraphs) {
                val pText = StringBuilder()
                val textMatches = textRegex.findAll(p.value)
                for (tm in textMatches) {
                    val raw = tm.groupValues[1]
                    val cleaned = raw.replace(Regex("<.*?>"), "")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&amp;", "&")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                    pText.append(cleaned)
                }
                val line = pText.toString().trim()
                if (line.isNotEmpty()) {
                    sb.append(line).append("\n\n")
                }
            }
        } else {
            val textMatches = textRegex.findAll(xml)
            for (tm in textMatches) {
                val cleaned = tm.groupValues[1].replace(Regex("<.*?>"), "")
                sb.append(cleaned).append(" ")
            }
        }
        return sb.toString().trim()
    }

    private fun extractBinaryDocText(context: Context, uri: Uri): String {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val sb = StringBuilder()
        var i = 0
        while (i < bytes.size - 1) {
            val b = bytes[i]
            // Look for printable ASCII or Cyrillic bytes
            if (b in 32..126 || b in -64..-1) { // Windows-1251 Cyrillic range
                val start = i
                while (i < bytes.size && (bytes[i] in 32..126 || bytes[i] in -64..-1 || bytes[i] == 10.toByte() || bytes[i] == 13.toByte())) {
                    i++
                }
                if (i - start > 4) {
                    val slice = bytes.sliceArray(start until i)
                    try {
                        val word = String(slice, Charset.forName("windows-1251")).trim()
                        if (word.length >= 3) {
                            sb.append(word).append(" ")
                        }
                    } catch (_: Exception) {}
                }
            }
            i++
        }
        return sb.toString().trim()
    }

    private fun extractPdfText(context: Context, uri: Uri, maxPages: Int): Pair<Int, String> {
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return Pair(0, "")
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        var totalPages = 0

        pfd.use { fd ->
            val renderer = PdfRenderer(fd)
            totalPages = renderer.pageCount
            val pagesToProcess = minOf(totalPages, maxPages)

            for (i in 0 until pagesToProcess) {
                val page = renderer.openPage(i)
                val width = 1200
                val height = (width * (page.height.toFloat() / page.width.toFloat())).toInt().coerceIn(600, 2400)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                try {
                    val inputImage = InputImage.fromBitmap(bitmap, 0)
                    val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(inputImage))
                    val rawText = result.text.trim()
                    val corrected = CyrillicOcrCorrector.correctPseudoLatinText(rawText)
                    if (corrected.isNotBlank()) {
                        sb.append("--- Страница ${i + 1} ---\n")
                        sb.append(corrected).append("\n\n")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    bitmap.recycle()
                }
            }
            renderer.close()
        }

        try { recognizer.close() } catch (_: Exception) {}

        if (totalPages > maxPages) {
            sb.append("\n(Обработаны первые $maxPages страниц из $totalPages)")
        }

        return Pair(totalPages, sb.toString().trim())
    }

    private fun isMostlyReadableText(text: String): Boolean {
        if (text.isBlank()) return false
        val sample = text.take(1000)
        val readableCount = sample.count { it.isLetterOrDigit() || it.isWhitespace() || it in ".,!?:;-\"\'()[]{}«»" }
        return (readableCount.toFloat() / sample.length) > 0.75f
    }

    fun queryFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = "Документ"
        var size = 0L

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex) ?: name
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (_: Exception) {}

        if (name == "Документ") {
            uri.lastPathSegment?.let { segment ->
                val clean = segment.substringAfterLast("/")
                if (clean.isNotBlank()) name = clean
            }
        }

        return Pair(name, size)
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "Размер не определен"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f МБ", mb)
            kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f КБ", kb)
            else -> "$bytes байт"
        }
    }

    fun openDocumentWithSystem(context: Context, uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
