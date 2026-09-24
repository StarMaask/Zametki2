package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
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
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    fun shareAsPdf(context: Context, note: Note) {
        try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 width (points)
            val pageHeight = 842 // A4 height (points)
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val noteTypeface = NoteFontHelper.getTypeface(context, note.noteFont)

            val titlePaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 22f
                typeface = Typeface.create(noteTypeface, Typeface.BOLD)
                isAntiAlias = true
            }

            val metaPaint = TextPaint().apply {
                color = Color.DKGRAY
                textSize = 10f
                typeface = noteTypeface
                isAntiAlias = true
            }

            val bodyPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 12f
                typeface = noteTypeface
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }

            var currentY = 50f
            val margin = 50f
            val contentWidth = (pageWidth - margin * 2).toInt()

            // Header Title
            val titleText = if (note.title.isNotBlank()) note.title else "Без названия"
            val titleLayout = StaticLayout.Builder.obtain(titleText, 0, titleText.length, titlePaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(margin, currentY)
            titleLayout.draw(canvas)
            canvas.restore()
            currentY += titleLayout.height + 10f

            // Metadata (date, folder, tags, format)
            val metaBuilder = StringBuilder()
            metaBuilder.append(dateFormat.format(Date(note.updatedAt)))
            if (!note.folder.isNullOrBlank()) {
                metaBuilder.append("  •  Папка: ").append(note.folder)
            }
            if (note.tags.isNotEmpty()) {
                metaBuilder.append("  •  Теги: ").append(note.tags.joinToString(", "))
            }
            metaBuilder.append("  •  Формат: ").append(note.format.title)

            val metaString = metaBuilder.toString()
            val metaLayout = StaticLayout.Builder.obtain(metaString, 0, metaString.length, metaPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(margin, currentY)
            metaLayout.draw(canvas)
            canvas.restore()
            currentY += metaLayout.height + 14f

            // Separator line
            canvas.drawLine(margin, currentY, pageWidth - margin, currentY, linePaint)
            currentY += 18f

            // Body Content
            val checklist = parseChecklist(note.checkListJson)
            val bodyText = if (checklist.isNotEmpty()) {
                val clText = checklist.joinToString("\n") { item ->
                    val mark = if (item.isChecked) "[✓]" else "[  ]"
                    "$mark ${item.text}"
                }
                if (note.content.isNotBlank()) "$clText\n\n${note.content}" else clText
            } else {
                note.content
            }

            val bodyLayout = StaticLayout.Builder.obtain(bodyText, 0, bodyText.length, bodyPaint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .build()
            canvas.save()
            canvas.translate(margin, currentY)
            bodyLayout.draw(canvas)
            canvas.restore()

            // Footer
            val footerPaint = TextPaint().apply {
                color = Color.GRAY
                textSize = 9f
                isAntiAlias = true
            }
            canvas.drawText("Создано в приложении Заметки • ${dateFormat.format(Date())}", margin, pageHeight - 30f, footerPaint)

            pdfDocument.finishPage(page)

            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val cleanTitle = note.title.replace(Regex("[^a-zA-Zа-яА-Я0-9_]"), "_").take(25).ifBlank { "note" }
            val pdfFile = File(exportDir, "${cleanTitle}_${System.currentTimeMillis()}.pdf")
            val outputStream = FileOutputStream(pdfFile)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", pdfFile)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, titleText)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Экспорт в PDF"))
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Ошибка создания PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
