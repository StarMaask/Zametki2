package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.domain.model.CheckListItem
import com.example.domain.model.Note
import com.example.domain.model.NoteFontFamily
import com.example.domain.model.PageFormat
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class PdfTheme(
    val title: String,
    val subtitle: String,
    val pageColor: Int,
    val inkColor: Int,
    val metaColor: Int,
    val ruledColor: Int?,
    val marginColor: Int?,
    val gridColor: Int?
) {
    ACADEMIC_A4(
        title = "Академический A4",
        subtitle = "Белый чистый лист, деловой вид",
        pageColor = Color.WHITE,
        inkColor = Color.rgb(24, 24, 27),
        metaColor = Color.rgb(105, 105, 115),
        ruledColor = null,
        marginColor = null,
        gridColor = null
    ),
    RULED_NOTEBOOK(
        title = "Тетрадь в линейку",
        subtitle = "Синие строки и красные поля",
        pageColor = Color.rgb(254, 252, 247),
        inkColor = Color.rgb(20, 40, 75),
        metaColor = Color.rgb(85, 100, 130),
        ruledColor = Color.argb(40, 90, 140, 220),
        marginColor = Color.argb(85, 220, 50, 50),
        gridColor = null
    ),
    GRID_NOTEBOOK(
        title = "Тетрадь в клетку",
        subtitle = "Математическая тетрадная сетка",
        pageColor = Color.rgb(253, 253, 255),
        inkColor = Color.rgb(25, 30, 45),
        metaColor = Color.rgb(90, 100, 120),
        ruledColor = null,
        marginColor = Color.argb(70, 220, 50, 50),
        gridColor = Color.argb(32, 100, 135, 195)
    ),
    VINTAGE_KRAFT(
        title = "Крафт / Пергамент",
        subtitle = "Тёплый крафт и тёмный сепия",
        pageColor = Color.rgb(244, 233, 212),
        inkColor = Color.rgb(55, 36, 18),
        metaColor = Color.rgb(115, 90, 65),
        ruledColor = Color.argb(32, 110, 80, 50),
        marginColor = null,
        gridColor = null
    )
}

data class PdfExportConfig(
    val theme: PdfTheme = PdfTheme.ACADEMIC_A4,
    val includeMetadata: Boolean = true,
    val includeImages: Boolean = true,
    val includePageNumbers: Boolean = true,
    val includeChecklist: Boolean = true
)

object ShareExportUtil {

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
    private val niceDateFormat = SimpleDateFormat("d MMMM yyyy г., HH:mm", Locale("ru"))

    private fun parseChecklist(jsonStr: String): List<CheckListItem> {
        return try {
            if (jsonStr.isNotBlank()) Json.decodeFromString<List<CheckListItem>>(jsonStr) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun buildNoteShareText(note: Note): String {
        val sb = StringBuilder()
        val title = if (note.title.isNotBlank()) note.title else "Без названия"
        sb.append("📝 ").append(title).append("\n")

        val folder = note.folder
        if (!folder.isNullOrBlank()) {
            sb.append("📁 Папка: ").append(folder).append("\n")
        }

        sb.append("📅 ").append(dateFormat.format(Date(note.updatedAt))).append("\n")

        if (note.tags.isNotEmpty()) {
            sb.append("🏷️ ").append(note.tags.joinToString(" ") { "#$it" }).append("\n")
        }

        sb.append("\n")

        val checklist = parseChecklist(note.checkListJson)
        if (checklist.isNotEmpty()) {
            for (item in checklist) {
                val checkMark = if (item.isChecked) "✓" else "☐"
                sb.append("$checkMark ${item.text}\n")
            }
            if (note.content.isNotBlank()) {
                sb.append("\n").append(note.content).append("\n")
            }
        } else {
            sb.append(note.content)
        }

        return sb.toString().trim()
    }

    fun copyToClipboard(context: Context, note: Note) {
        try {
            val text = buildNoteShareText(note)
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Заметка: ${note.title.ifBlank { "Без названия" }}", text)
            clipboard.setPrimaryClip(clip)
            Toast.makeText(context, "Текст заметки скопирован в буфер", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(context, "Не удалось скопировать: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAsText(context: Context, note: Note) {
        val text = buildNoteShareText(note)
        val title = if (note.title.isNotBlank()) note.title else "Заметка"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться текстом заметки"))
    }

    fun shareAsTxtFile(context: Context, note: Note) {
        try {
            val text = buildNoteShareText(note)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(25).ifBlank { "note" }
            val file = File(exportDir, "${cleanTitle}_${System.currentTimeMillis()}.txt")
            file.writeText(text)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Заметка" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт в TXT"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка создания TXT: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun shareAsMarkdownFile(context: Context, note: Note) {
        try {
            val mdContent = buildString {
                if (note.title.isNotBlank()) {
                    append("# ").append(note.title).append("\n\n")
                }
                append(note.content)
            }
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(25).ifBlank { "note" }
            val file = File(exportDir, "${cleanTitle}_${System.currentTimeMillis()}.md")
            file.writeText(mdContent)

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/markdown"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Конспект (Markdown)" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт в Markdown (.md)"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка создания Markdown: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Renders note as an authentic notebook page sheet image (PNG/JPEG)
     * with paper styling, lines/grid, handwriting/typography, and stamps.
     */
    fun shareAsNotebookSheet(context: Context, note: Note) {
        try {
            val width = 1080
            val padding = 64f
            val contentWidth = (width - padding * 2).toInt()

            val format = note.format
            val isDark = format == PageFormat.MIDNIGHT
            val noteTypeface = NoteFontHelper.getTypeface(context, note.noteFont)

            // Paper Background Color
            val paperColor = when (format) {
                PageFormat.BOOK -> Color.rgb(253, 250, 243)
                PageFormat.RULED -> Color.rgb(254, 254, 252)
                PageFormat.GRID -> Color.rgb(250, 250, 252)
                PageFormat.KRAFT -> Color.rgb(226, 206, 178)
                PageFormat.VINTAGE -> Color.rgb(244, 235, 214)
                PageFormat.MIDNIGHT -> Color.rgb(24, 28, 36)
                PageFormat.BLUEPRINT -> Color.rgb(18, 48, 92)
                PageFormat.BLANK -> try { Color.parseColor(note.colorHex) } catch (_: Exception) { Color.rgb(255, 255, 255) }
            }

            val inkColor = when {
                isDark || format == PageFormat.BLUEPRINT -> Color.rgb(240, 245, 255)
                format == PageFormat.VINTAGE -> Color.rgb(44, 30, 18)
                format == PageFormat.KRAFT -> Color.rgb(48, 32, 16)
                else -> try { Color.parseColor(note.textColorHex) } catch (_: Exception) { Color.rgb(28, 27, 31) }
            }

            val metaColor = when {
                isDark || format == PageFormat.BLUEPRINT -> Color.rgb(170, 195, 230)
                else -> Color.rgb(110, 105, 115)
            }

            // Paints setup
            val titlePaint = TextPaint().apply {
                color = inkColor
                textSize = 52f
                typeface = Typeface.create(noteTypeface, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = TextPaint().apply {
                color = metaColor
                textSize = 28f
                typeface = noteTypeface
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = inkColor
                textSize = 34f
                typeface = noteTypeface
                isAntiAlias = true
            }

            // Pre-measure title layout
            val titleText = if (note.title.isNotBlank()) note.title else "Без названия"
            val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            // Pre-measure body layout
            val checklist = parseChecklist(note.checkListJson)
            val fullBodyText = buildString {
                if (checklist.isNotEmpty()) {
                    checklist.forEach { item ->
                        val mark = if (item.isChecked) "☑ " else "☐ "
                        append(mark).append(item.text).append("\n")
                    }
                    if (note.content.isNotBlank()) append("\n")
                }
                append(note.content)
            }.trim()

            val bodyLayout = StaticLayout.Builder.obtain(fullBodyText, 0, fullBodyText.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            val estimatedHeight = (padding * 2 + 80f + titleLayout.height + 40f + bodyLayout.height + 120f).toInt()
            val height = maxOf(1440, estimatedHeight)

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // 1. Draw Paper background
            val bgPaint = Paint().apply { color = paperColor; style = Paint.Style.FILL }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // 2. Draw Paper Patterns (Lines / Grid / Book Header / Margin)
            when (format) {
                PageFormat.RULED -> {
                    val linePaint = Paint().apply {
                        color = Color.argb(45, 120, 150, 200)
                        strokeWidth = 2f
                    }
                    val lineSpacing = 48f
                    var y = 140f
                    while (y < height - 60f) {
                        canvas.drawLine(0f, y, width.toFloat(), y, linePaint)
                        y += lineSpacing
                    }
                    // Left notebook red margin line
                    val marginPaint = Paint().apply {
                        color = Color.argb(80, 220, 50, 50)
                        strokeWidth = 3f
                    }
                    canvas.drawLine(padding + 20f, 0f, padding + 20f, height.toFloat(), marginPaint)
                }
                PageFormat.GRID -> {
                    val gridPaint = Paint().apply {
                        color = Color.argb(35, 100, 140, 200)
                        strokeWidth = 1.5f
                    }
                    val step = 40f
                    var x = 0f
                    while (x < width) {
                        canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                        x += step
                    }
                    var y = 0f
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                        y += step
                    }
                }
                PageFormat.BOOK -> {
                    // Decorative book bookmark ribbon top-left
                    val bookmarkPaint = Paint().apply {
                        color = Color.rgb(180, 40, 40)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(padding, 0f, padding + 36f, 90f, bookmarkPaint)

                    // Book chapter rule
                    val rulePaint = Paint().apply {
                        color = Color.argb(60, 0, 0, 0)
                        strokeWidth = 2f
                    }
                    canvas.drawLine(padding + 50f, 50f, width - padding, 50f, rulePaint)
                }
                PageFormat.VINTAGE -> {
                    val borderPaint = Paint().apply {
                        color = Color.argb(70, 90, 55, 25)
                        strokeWidth = 3f
                        style = Paint.Style.STROKE
                    }
                    canvas.drawRoundRect(RectF(24f, 24f, width - 24f, height - 24f), 16f, 16f, borderPaint)
                }
                PageFormat.BLUEPRINT -> {
                    val gridPaint = Paint().apply {
                        color = Color.argb(45, 100, 180, 255)
                        strokeWidth = 1.5f
                    }
                    val step = 48f
                    var x = 0f
                    while (x < width) {
                        canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
                        x += step
                    }
                    var y = 0f
                    while (y < height) {
                        canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
                        y += step
                    }
                }
                else -> {}
            }

            var currentY = padding + 30f

            // Top Header Note Info (Format, Date)
            val headerString = "${format.title.uppercase()}  •  ${niceDateFormat.format(Date(note.updatedAt))}"
            canvas.drawText(headerString, padding, currentY, metaPaint)
            currentY += 36f

            // Folder and Tags if present
            val metaParts = mutableListOf<String>()
            if (!note.folder.isNullOrBlank()) metaParts.add("📁 ${note.folder}")
            if (note.tags.isNotEmpty()) metaParts.add("🏷️ " + note.tags.joinToString(" ") { "#$it" })
            if (metaParts.isNotEmpty()) {
                canvas.drawText(metaParts.joinToString("   "), padding, currentY, metaPaint)
                currentY += 40f
            } else {
                currentY += 10f
            }

            // Divider rule
            val sepPaint = Paint().apply {
                color = metaColor
                alpha = 60
                strokeWidth = 2f
            }
            canvas.drawLine(padding, currentY, width - padding, currentY, sepPaint)
            currentY += 32f

            // Draw Title
            canvas.save()
            canvas.translate(padding, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 28f

            // Draw Body
            canvas.save()
            canvas.translate(padding, currentY)
            bodyLayout.draw(canvas)
            canvas.restore()

            // Footer watermark
            val footerPaint = TextPaint().apply {
                color = metaColor
                alpha = 90
                textSize = 24f
                typeface = noteTypeface
                isAntiAlias = true
            }
            val footerText = "Страница блокнота • Заметки"
            canvas.drawText(footerText, padding, height - padding + 10f, footerPaint)

            // Save to file and share
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(25).ifBlank { "note" }
            val imageFile = File(exportDir, "${cleanTitle}_sheet_${System.currentTimeMillis()}.png")
            val out = FileOutputStream(imageFile)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
            out.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", imageFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Листок блокнота" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Поделиться листком заметки"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка создания изображения: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun parseImageUris(imageUrisJson: String): List<String> {
        if (imageUrisJson.isBlank()) return emptyList()
        return try {
            Json.decodeFromString<List<String>>(imageUrisJson)
        } catch (_: Exception) {
            try {
                imageUrisJson.removeSurrounding("[", "]")
                    .split(",")
                    .map { it.trim().removeSurrounding("\"") }
                    .filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    private fun loadScaledBitmap(context: Context, pathOrUri: String, maxWidth: Int, maxHeight: Int): Bitmap? {
        return try {
            val uri = if (pathOrUri.startsWith("content://") || pathOrUri.startsWith("file://")) {
                android.net.Uri.parse(pathOrUri)
            } else {
                android.net.Uri.fromFile(File(pathOrUri))
            }
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            if (options.outWidth <= 0 || options.outHeight <= 0) return null

            var sampleSize = 1
            while (options.outWidth / (sampleSize * 2) >= maxWidth && options.outHeight / (sampleSize * 2) >= maxHeight) {
                sampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val orig = BitmapFactory.decodeStream(stream, null, decodeOptions) ?: return null
                val scale = minOf(maxWidth.toFloat() / orig.width, maxHeight.toFloat() / orig.height, 1.0f)
                val scaledW = (orig.width * scale).toInt().coerceAtLeast(1)
                val scaledH = (orig.height * scale).toInt().coerceAtLeast(1)
                if (scaledW == orig.width && scaledH == orig.height) orig
                else Bitmap.createScaledBitmap(orig, scaledW, scaledH, true)
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Generates a multi-page PDF document with custom page themes (A4, Ruled, Grid, Kraft),
     * intelligent text wrapping/pagination across pages, running headers, footers with
     * page numbering ("Стр. X из Y"), and embedded pictures/drawings.
     */
    fun generatePdfFile(context: Context, note: Note, config: PdfExportConfig = PdfExportConfig()): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width (pt)
            val pageHeight = 842 // A4 standard height (pt)

            val theme = config.theme
            val marginLeft = if (theme.marginColor != null) 60f else 46f
            val marginRight = 46f
            val marginTop = 48f
            val marginBottom = 52f
            val contentWidth = (pageWidth - marginLeft - marginRight).toInt()
            val usableBottomY = pageHeight - marginBottom

            val noteTypeface = NoteFontHelper.getTypeface(context, note.noteFont)

            val titlePaint = TextPaint().apply {
                color = theme.inkColor
                textSize = 22f
                typeface = Typeface.create(noteTypeface, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = TextPaint().apply {
                color = theme.metaColor
                textSize = 9.5f
                typeface = noteTypeface
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = theme.inkColor
                textSize = 12f
                typeface = noteTypeface
                isAntiAlias = true
            }

            val dividerPaint = Paint().apply {
                color = Color.argb(40, Color.red(theme.metaColor), Color.green(theme.metaColor), Color.blue(theme.metaColor))
                strokeWidth = 1f
            }

            // Title element
            val titleText = if (note.title.isNotBlank()) note.title else "Без названия"
            val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()

            // Metadata element
            val metaString: String? = if (config.includeMetadata) {
                buildString {
                    append(dateFormat.format(Date(note.updatedAt)))
                    if (!note.folder.isNullOrBlank()) {
                        append("  •  Папка: ").append(note.folder)
                    }
                    if (note.tags.isNotEmpty()) {
                        append("  •  Теги: ").append(note.tags.joinToString(", "))
                    }
                    if (!note.audioUri.isNullOrBlank()) {
                        append("  •  🎙️ Аудиодорожка")
                    }
                    append("  •  Стиль: ").append(theme.title)
                }
            } else null

            val metaLayout: StaticLayout? = metaString?.let {
                StaticLayout.Builder.obtain(it, 0, it.length, metaPaint, contentWidth)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .build()
            }

            // Checklist lines
            val checklist = if (config.includeChecklist) parseChecklist(note.checkListJson) else emptyList()

            // Body text paragraphs
            val paragraphs = if (note.content.isNotBlank()) {
                note.content.split("\n")
            } else emptyList()

            // Attached images
            val imageUris = if (config.includeImages) parseImageUris(note.imageUrisJson) else emptyList()

            // Two-pass rendering: dryRun measures totalPages, non-dryRun renders real pages
            fun renderPages(dryRun: Boolean, totalPages: Int): Int {
                var pageCount = 0
                var activePage: PdfDocument.Page? = null
                var activeCanvas: Canvas? = null
                var currentY = marginTop

                fun startNewPage() {
                    if (!dryRun && activePage != null) {
                        pdfDocument.finishPage(activePage)
                    }
                    pageCount++
                    currentY = marginTop

                    if (!dryRun) {
                        val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
                        val newPage = pdfDocument.startPage(pageInfo)
                        activePage = newPage
                        val canvas = newPage.canvas
                        activeCanvas = canvas

                        // 1. Draw page background
                        canvas.drawColor(theme.pageColor)

                        // 2. Ruled horizontal notebook lines
                        theme.ruledColor?.let { rColor ->
                            val lineP = Paint().apply {
                                color = rColor
                                strokeWidth = 0.8f
                            }
                            var y = marginTop + 12f
                            while (y < usableBottomY) {
                                canvas.drawLine(0f, y, pageWidth.toFloat(), y, lineP)
                                y += 22f
                            }
                        }

                        // 3. Grid lines
                        theme.gridColor?.let { gColor ->
                            val gridP = Paint().apply {
                                color = gColor
                                strokeWidth = 0.6f
                            }
                            val step = 15f
                            var x = 0f
                            while (x < pageWidth) {
                                canvas.drawLine(x, 0f, x, pageHeight.toFloat(), gridP)
                                x += step
                            }
                            var y = 0f
                            while (y < pageHeight) {
                                canvas.drawLine(0f, y, pageWidth.toFloat(), y, gridP)
                                y += step
                            }
                        }

                        // 4. Notebook left vertical red margin line
                        theme.marginColor?.let { mColor ->
                            val marginP = Paint().apply {
                                color = mColor
                                strokeWidth = 1.5f
                            }
                            val marginX = marginLeft - 12f
                            canvas.drawLine(marginX, 0f, marginX, pageHeight.toFloat(), marginP)
                        }

                        // 5. Running header for pages >= 2
                        if (pageCount > 1) {
                            val runP = TextPaint().apply {
                                color = theme.metaColor
                                textSize = 8.5f
                                typeface = noteTypeface
                                isAntiAlias = true
                            }
                            val hTitle = if (titleText.length > 40) titleText.take(40) + "..." else titleText
                            canvas.drawText(hTitle, marginLeft, marginTop - 16f, runP)
                            val ruleP = Paint().apply {
                                color = Color.argb(30, Color.red(theme.metaColor), Color.green(theme.metaColor), Color.blue(theme.metaColor))
                                strokeWidth = 0.7f
                            }
                            canvas.drawLine(marginLeft, marginTop - 10f, pageWidth - marginRight, marginTop - 10f, ruleP)
                        }

                        // 6. Running footer for all pages
                        if (config.includePageNumbers) {
                            val footP = TextPaint().apply {
                                color = theme.metaColor
                                textSize = 8.5f
                                typeface = noteTypeface
                                isAntiAlias = true
                            }
                            val footerY = pageHeight - 22f
                            val leftText = "Лекции и Заметки"
                            val pageStr = "Стр. $pageCount из $totalPages"
                            canvas.drawText(leftText, marginLeft, footerY, footP)

                            val pageStrW = footP.measureText(pageStr)
                            canvas.drawText(pageStr, pageWidth - marginRight - pageStrW, footerY, footP)

                            val footRuleP = Paint().apply {
                                color = Color.argb(25, Color.red(theme.metaColor), Color.green(theme.metaColor), Color.blue(theme.metaColor))
                                strokeWidth = 0.6f
                            }
                            canvas.drawLine(marginLeft, footerY - 12f, pageWidth - marginRight, footerY - 12f, footRuleP)
                        }
                    }
                }

                // Start first page
                startNewPage()

                // Render Title on Page 1
                if (!dryRun && activeCanvas != null) {
                    activeCanvas?.save()
                    activeCanvas?.translate(marginLeft, currentY)
                    titleLayout.draw(activeCanvas!!)
                    activeCanvas?.restore()
                }
                currentY += titleLayout.height + 8f

                // Render Metadata on Page 1
                if (metaLayout != null) {
                    if (!dryRun && activeCanvas != null) {
                        activeCanvas?.save()
                        activeCanvas?.translate(marginLeft, currentY)
                        metaLayout.draw(activeCanvas!!)
                        activeCanvas?.restore()
                    }
                    currentY += metaLayout.height + 12f

                    if (!dryRun && activeCanvas != null) {
                        activeCanvas?.drawLine(marginLeft, currentY, pageWidth - marginRight, currentY, dividerPaint)
                    }
                    currentY += 16f
                }

                // Render Checklist Items
                if (checklist.isNotEmpty()) {
                    checklist.forEach { item ->
                        val prefix = if (item.isChecked) "☑  " else "☐  "
                        val itemStr = "$prefix${item.text}"
                        val itemLayout = StaticLayout.Builder.obtain(itemStr, 0, itemStr.length, bodyPaint, contentWidth)
                            .setLineSpacing(2f, 1.15f)
                            .build()

                        if (currentY + itemLayout.height > usableBottomY) {
                            startNewPage()
                        }

                        if (!dryRun && activeCanvas != null) {
                            activeCanvas?.save()
                            activeCanvas?.translate(marginLeft, currentY)
                            itemLayout.draw(activeCanvas!!)
                            activeCanvas?.restore()
                        }
                        currentY += itemLayout.height + 6f
                    }
                    if (paragraphs.isNotEmpty()) {
                        currentY += 10f
                    }
                }

                // Render Body Paragraphs with pagination line-wrapping
                paragraphs.forEach { paragraph ->
                    if (paragraph.isBlank()) {
                        currentY += 14f
                        if (currentY > usableBottomY) {
                            startNewPage()
                        }
                    } else {
                        val pLayout = StaticLayout.Builder.obtain(paragraph, 0, paragraph.length, bodyPaint, contentWidth)
                            .setLineSpacing(2f, 1.15f)
                            .build()

                        if (currentY + pLayout.height <= usableBottomY) {
                            // Entire paragraph fits on current page
                            if (!dryRun && activeCanvas != null) {
                                activeCanvas?.save()
                                activeCanvas?.translate(marginLeft, currentY)
                                pLayout.draw(activeCanvas!!)
                                activeCanvas?.restore()
                            }
                            currentY += pLayout.height + 6f
                        } else {
                            // Split paragraph across page boundaries line-by-line
                            var lineIdx = 0
                            while (lineIdx < pLayout.lineCount) {
                                val remainingSpace = usableBottomY - currentY
                                if (remainingSpace < 22f) {
                                    startNewPage()
                                }
                                var fitCount = 0
                                while (lineIdx + fitCount < pLayout.lineCount) {
                                    val startY = pLayout.getLineTop(lineIdx)
                                    val endY = pLayout.getLineBottom(lineIdx + fitCount)
                                    if (endY - startY <= usableBottomY - currentY) {
                                        fitCount++
                                    } else {
                                        break
                                    }
                                }
                                if (fitCount == 0) fitCount = 1

                                val startChar = pLayout.getLineStart(lineIdx)
                                val endChar = pLayout.getLineEnd(lineIdx + fitCount - 1)
                                val subText = paragraph.substring(startChar, endChar).trimEnd('\n')

                                if (subText.isNotBlank()) {
                                    val subLayout = StaticLayout.Builder.obtain(subText, 0, subText.length, bodyPaint, contentWidth)
                                        .setLineSpacing(2f, 1.15f)
                                        .build()

                                    if (!dryRun && activeCanvas != null) {
                                        activeCanvas?.save()
                                        activeCanvas?.translate(marginLeft, currentY)
                                        subLayout.draw(activeCanvas!!)
                                        activeCanvas?.restore()
                                    }
                                    currentY += subLayout.height + 4f
                                }
                                lineIdx += fitCount
                                if (lineIdx < pLayout.lineCount) {
                                    startNewPage()
                                }
                            }
                            currentY += 4f
                        }
                    }
                }

                // Render Attached Images / Sketches
                if (imageUris.isNotEmpty()) {
                    imageUris.forEachIndexed { _, uriStr ->
                        val bitmap = loadScaledBitmap(context, uriStr, contentWidth - 16, 260)
                        if (bitmap != null) {
                            val requiredH = bitmap.height + 28f
                            if (currentY + requiredH > usableBottomY) {
                                startNewPage()
                            }

                            if (!dryRun && activeCanvas != null) {
                                val imgX = marginLeft + (contentWidth - bitmap.width) / 2f
                                val imgY = currentY + 6f
                                val frameP = Paint().apply {
                                    color = Color.argb(25, 0, 0, 0)
                                    style = Paint.Style.STROKE
                                    strokeWidth = 1f
                                }
                                activeCanvas?.drawRoundRect(
                                    RectF(imgX - 3f, imgY - 3f, imgX + bitmap.width + 3f, imgY + bitmap.height + 3f),
                                    6f, 6f, frameP
                                )
                                activeCanvas?.drawBitmap(bitmap, imgX, imgY, Paint().apply { isFilterBitmap = true })
                            }
                            currentY += requiredH + 12f
                        }
                    }
                }

                // Finish final page if real run
                if (!dryRun && activePage != null) {
                    pdfDocument.finishPage(activePage)
                }

                return pageCount
            }

            // Pass 1: Dry run to measure total pages
            val totalPages = renderPages(dryRun = true, totalPages = 1).coerceAtLeast(1)

            // Pass 2: Real rendering with exact total page count
            renderPages(dryRun = false, totalPages = totalPages)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(30).ifBlank { "note" }
            val pdfFile = File(exportDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            pdfFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Standard Android printing flow: launches the native system Print Dialog (Wi-Fi, Cloud Print, Save as PDF).
     */
    fun printNote(context: Context, note: Note, config: PdfExportConfig = PdfExportConfig()) {
        try {
            val pdfFile = generatePdfFile(context, note, config) ?: run {
                Toast.makeText(context, "Не удалось сформировать PDF для печати", Toast.LENGTH_SHORT).show()
                return
            }

            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager == null) {
                Toast.makeText(context, "Служба печати недоступна на устройстве", Toast.LENGTH_SHORT).show()
                return
            }

            val jobName = "Заметка - ${note.title.ifBlank { "Без названия" }}"
            val printAdapter = object : PrintDocumentAdapter() {
                override fun onLayout(
                    oldAttributes: PrintAttributes?,
                    newAttributes: PrintAttributes?,
                    cancellationSignal: CancellationSignal?,
                    callback: LayoutResultCallback?,
                    extras: Bundle?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onLayoutCancelled()
                        return
                    }
                    val info = PrintDocumentInfo.Builder(jobName)
                        .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                        .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                        .build()
                    callback?.onLayoutFinished(info, true)
                }

                override fun onWrite(
                    pages: Array<out PageRange>?,
                    destination: ParcelFileDescriptor?,
                    cancellationSignal: CancellationSignal?,
                    callback: WriteResultCallback?
                ) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback?.onWriteCancelled()
                        return
                    }
                    var input: FileInputStream? = null
                    var output: FileOutputStream? = null
                    try {
                        input = FileInputStream(pdfFile)
                        output = FileOutputStream(destination?.fileDescriptor)
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } >= 0) {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onWriteCancelled()
                                return
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        e.printStackTrace()
                        callback?.onWriteFailed(e.message)
                    } finally {
                        try { input?.close() } catch (_: Exception) {}
                        try { output?.close() } catch (_: Exception) {}
                    }
                }
            }

            val printAttributes = PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setResolution(PrintAttributes.Resolution("pdf", "PDF", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            printManager.print(jobName, printAdapter, printAttributes)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка печати: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Share PDF file via Android system share chooser.
     */
    fun shareAsPdf(context: Context, note: Note, config: PdfExportConfig = PdfExportConfig()) {
        try {
            val pdfFile = generatePdfFile(context, note, config) ?: run {
                Toast.makeText(context, "Не удалось создать PDF документ", Toast.LENGTH_SHORT).show()
                return
            }

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, note.title.ifBlank { "Заметка" })
                putExtra(Intent.EXTRA_TEXT, "Документ PDF: ${note.title.ifBlank { "Заметка" }}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт в PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка создания PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Save PDF file into the public Downloads directory.
     */
    fun savePdfToDownloads(context: Context, note: Note, config: PdfExportConfig = PdfExportConfig()): File? {
        return try {
            val pdfFile = generatePdfFile(context, note, config) ?: return null
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(30).ifBlank { "note" }
            val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
            val targetFile = File(downloadsDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")
            pdfFile.copyTo(targetFile, overwrite = true)
            Toast.makeText(context, "Сохранено в Загрузки: ${targetFile.name}", Toast.LENGTH_LONG).show()
            targetFile
        } catch (e: Exception) {
            Toast.makeText(context, "Файл готов во временном хранилище", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
