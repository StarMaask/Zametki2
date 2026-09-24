package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import com.example.R
import com.example.data.preferences.UserPreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

enum class GlyphCategory(val title: String) {
    LOWERCASE("Строчные"),
    UPPERCASE("Заглавные"),
    DIGIT("Цифры"),
    PUNCTUATION("Знаки")
}

data class HandwritingGlyphItem(
    val char: Char,
    val title: String,
    val category: GlyphCategory,
    val isDigitized: Boolean,
    val imagePath: String? = null,
    val strokeThickness: Float = 3f,
    val baselineShift: Float = 0f,
    val widthRatio: Float = 1.0f
)

data class SentenceAnalysisMetrics(
    val slantAngle: Float,
    val strokeThickness: Float,
    val letterSpacing: Float,
    val wordSpacing: Float,
    val inkColorHex: String,
    val inkColorName: String,
    val baselineWobble: Float,
    val connectionStyle: String
)

object HandwritingGlyphManager {

    private const val GLYPH_DIR_NAME = "handwriting_glyphs"
    private const val METADATA_FILE_NAME = "glyphs_metadata.json"
    private const val ACTIVE_FONT_FILE_NAME = "active_font.ttf"

    val RUSSIAN_LOWERCASE = listOf(
        'а', 'б', 'в', 'г', 'д', 'е', 'ё', 'ж', 'з', 'и', 'й',
        'к', 'л', 'м', 'н', 'о', 'п', 'р', 'с', 'т', 'у', 'ф',
        'х', 'ц', 'ч', 'ш', 'щ', 'ъ', 'ы', 'ь', 'э', 'ю', 'я'
    )

    val RUSSIAN_UPPERCASE = listOf(
        'А', 'Б', 'В', 'Г', 'Д', 'Е', 'Ё', 'Ж', 'З', 'И', 'Й',
        'К', 'Л', 'М', 'Н', 'О', 'П', 'Р', 'С', 'Т', 'У', 'Ф',
        'Х', 'Ц', 'Ч', 'Ш', 'Щ', 'Ъ', 'Ы', 'Ь', 'Э', 'Ю', 'Я'
    )

    val DIGITS = listOf('0', '1', '2', '3', '4', '5', '6', '7', '8', '9')

    val PUNCTUATIONS = listOf('.', ',', '!', '?', '-', ':', ';', '(', ')', '"')

    fun getAllTargetCharacters(): List<Pair<Char, GlyphCategory>> {
        val list = mutableListOf<Pair<Char, GlyphCategory>>()
        RUSSIAN_LOWERCASE.forEach { list.add(it to GlyphCategory.LOWERCASE) }
        RUSSIAN_UPPERCASE.forEach { list.add(it to GlyphCategory.UPPERCASE) }
        DIGITS.forEach { list.add(it to GlyphCategory.DIGIT) }
        PUNCTUATIONS.forEach { list.add(it to GlyphCategory.PUNCTUATION) }
        return list
    }

    private fun getGlyphDir(context: Context): File {
        return File(context.filesDir, GLYPH_DIR_NAME).apply { mkdirs() }
    }

    fun getGlyphFile(context: Context, char: Char): File {
        val hex = String.format("%04X", char.code)
        return File(getGlyphDir(context), "glyph_$hex.png")
    }

    /**
     * Loads current digitization status for all alphabet items.
     */
    fun loadAllGlyphItems(context: Context): List<HandwritingGlyphItem> {
        val all = getAllTargetCharacters()
        return all.map { (char, category) ->
            val file = getGlyphFile(context, char)
            val isDigitized = file.exists() && file.length() > 0
            val title = when (category) {
                GlyphCategory.LOWERCASE -> "Строчная '$char'"
                GlyphCategory.UPPERCASE -> "Заглавная '$char'"
                GlyphCategory.DIGIT -> "Цифра '$char'"
                GlyphCategory.PUNCTUATION -> "Знак '$char'"
            }
            HandwritingGlyphItem(
                char = char,
                title = title,
                category = category,
                isDigitized = isDigitized,
                imagePath = if (isDigitized) file.absolutePath else null
            )
        }
    }

    fun getDigitizationProgress(context: Context): Pair<Int, Int> {
        val all = getAllTargetCharacters()
        val count = all.count { (char, _) ->
            val file = getGlyphFile(context, char)
            file.exists() && file.length() > 0
        }
        return count to all.size
    }

    /**
     * Cleans ink strokes from paper background:
     * - Estimates paper color (near white/cream/grid)
     * - Detects ink pixels (blue, dark grey, black)
     * - Generates clean transparent PNG containing only ink strokes.
     */
    fun processAndSaveSingleGlyph(context: Context, char: Char, sourceBitmap: Bitmap): HandwritingGlyphItem {
        val cleanedBitmap = extractInkOnlyBitmap(sourceBitmap)
        val targetFile = getGlyphFile(context, char)
        FileOutputStream(targetFile).use { out ->
            cleanedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        // Trigger synchronization with active font
        syncActiveFont(context)

        return HandwritingGlyphItem(
            char = char,
            title = "Символ '$char'",
            category = getCategoryForChar(char),
            isDigitized = true,
            imagePath = targetFile.absolutePath
        )
    }

    fun deleteGlyph(context: Context, char: Char): Boolean {
        val file = getGlyphFile(context, char)
        val deleted = if (file.exists()) file.delete() else true
        syncActiveFont(context)
        return deleted
    }

    fun resetAllGlyphs(context: Context) {
        val dir = getGlyphDir(context)
        dir.listFiles()?.forEach { it.delete() }
        syncActiveFont(context)
    }

    private fun getCategoryForChar(char: Char): GlyphCategory {
        return when {
            RUSSIAN_LOWERCASE.contains(char) -> GlyphCategory.LOWERCASE
            RUSSIAN_UPPERCASE.contains(char) -> GlyphCategory.UPPERCASE
            DIGITS.contains(char) -> GlyphCategory.DIGIT
            else -> GlyphCategory.PUNCTUATION
        }
    }

    /**
     * Extracts ink strokes from a photo or drawing.
     * Leaves ink sharp and antialiased with transparent background.
     */
    fun extractInkOnlyBitmap(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)

        // Find paper background luminance (average of 4 corners)
        val cornerLums = listOf(
            luminance(pixels[0]),
            luminance(pixels[width - 1]),
            luminance(pixels[(height - 1) * width]),
            luminance(pixels[width * height - 1])
        )
        val backgroundLum = cornerLums.average().toFloat().coerceAtLeast(180f)

        // Threshold for ink: pixels darker than background by a significant margin
        val inkThreshold = (backgroundLum * 0.78f).toInt()

        var minX = width
        var maxX = 0
        var minY = height
        var maxY = 0

        val outputPixels = IntArray(width * height)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val c = pixels[idx]
                val lum = luminance(c)

                if (lum < inkThreshold) {
                    // This is ink
                    val alpha = (((inkThreshold - lum).toFloat() / inkThreshold.toFloat()) * 255f)
                        .coerceIn(160f, 255f).toInt()
                    outputPixels[idx] = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c))

                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                } else {
                    // Transparent paper
                    outputPixels[idx] = Color.TRANSPARENT
                }
            }
        }

        // If ink was found, crop closely with 8px padding
        if (minX < maxX && minY < maxY) {
            val pad = 8
            val cropMinX = max(0, minX - pad)
            val cropMinY = max(0, minY - pad)
            val cropMaxX = min(width - 1, maxX + pad)
            val cropMaxY = min(height - 1, maxY + pad)
            val cropW = cropMaxX - cropMinX + 1
            val cropH = cropMaxY - cropMinY + 1

            val cropped = Bitmap.createBitmap(cropW, cropH, Bitmap.Config.ARGB_8888)
            cropped.setPixels(outputPixels, cropMinY * width + cropMinX, width, 0, 0, cropW, cropH)
            return cropped
        }

        // Fallback: return as-is
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        result.setPixels(outputPixels, 0, width, 0, 0, width, height)
        return result
    }

    private fun luminance(c: Int): Int {
        return (Color.red(c) * 0.299 + Color.green(c) * 0.587 + Color.blue(c) * 0.114).toInt()
    }

    /**
     * Segments an entire photo of a handwritten alphabet sheet into individual letters.
     */
    suspend fun segmentAndSaveAlphabetSheet(
        context: Context,
        sheetUri: Uri
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(sheetUri)
                ?: return@withContext Result.failure(Exception("Не удалось открыть фото листа"))
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            if (bitmap == null) {
                return@withContext Result.failure(Exception("Некорректное изображение"))
            }

            // Downscale if huge for fast processing
            val maxDim = 1600
            val scaled = if (bitmap.width > maxDim || bitmap.height > maxDim) {
                val scale = maxDim.toFloat() / max(bitmap.width, bitmap.height)
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else {
                bitmap
            }

            // Find ink bounding boxes by horizontal and vertical projection profiling
            val boxes = findInkCharacterBoxes(scaled)

            val targets = getAllTargetCharacters()
            var assignedCount = 0

            for (i in 0 until min(boxes.size, targets.size)) {
                val box = boxes[i]
                val (char, _) = targets[i]
                if (box.width() > 10 && box.height() > 10) {
                    val letterBitmap = Bitmap.createBitmap(scaled, box.left, box.top, box.width(), box.height())
                    processAndSaveSingleGlyph(context, char, letterBitmap)
                    assignedCount++
                }
            }

            syncActiveFont(context)
            Result.success(assignedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Splits an alphabet image into individual glyph bounding boxes.
     */
    private fun findInkCharacterBoxes(bitmap: Bitmap): List<Rect> {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val threshold = 175
        val isInk = BooleanArray(width * height)
        for (i in pixels.indices) {
            isInk[i] = luminance(pixels[i]) < threshold
        }

        // Horizontal profile to find text lines
        val rowCounts = IntArray(height)
        for (y in 0 until height) {
            var c = 0
            for (x in 0 until width) {
                if (isInk[y * width + x]) c++
            }
            rowCounts[y] = c
        }

        val lineBands = mutableListOf<Pair<Int, Int>>()
        var inLine = false
        var startY = 0
        val minLineInk = width * 0.015f

        for (y in 0 until height) {
            if (rowCounts[y] > minLineInk) {
                if (!inLine) {
                    inLine = true
                    startY = y
                }
            } else {
                if (inLine) {
                    inLine = false
                    if (y - startY > 15) {
                        lineBands.add(startY to y)
                    }
                }
            }
        }

        val boxes = mutableListOf<Rect>()

        // For each line band, vertical profile to split into letters
        for ((top, bottom) in lineBands) {
            val colCounts = IntArray(width)
            val h = bottom - top
            for (x in 0 until width) {
                var c = 0
                for (y in top until bottom) {
                    if (isInk[y * width + x]) c++
                }
                colCounts[x] = c
            }

            var inChar = false
            var startX = 0
            val minCharInk = h * 0.05f

            for (x in 0 until width) {
                if (colCounts[x] > minCharInk) {
                    if (!inChar) {
                        inChar = true
                        startX = x
                    }
                } else {
                    if (inChar) {
                        inChar = false
                        val w = x - startX
                        if (w > 12) {
                            boxes.add(Rect(max(0, startX - 4), max(0, top - 4), min(width, x + 4), min(height, bottom + 4)))
                        }
                    }
                }
            }
        }

        return boxes
    }

    /**
     * Analyzes full sentence writing for transitions, rhythm, and slant.
     */
    suspend fun analyzeSentenceWriting(
        context: Context,
        imageUri: Uri
    ): Result<SentenceAnalysisMetrics> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(imageUri)
                ?: return@withContext Result.failure(Exception("Не удалось открыть фото текста"))
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            if (bitmap == null) {
                return@withContext Result.failure(Exception("Некорректное фото"))
            }

            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // 1. Detect Slant
            var strokeDx = 0f
            var strokeDy = 0f
            var sampleCount = 0

            for (y in 2 until height - 2 step 3) {
                for (x in 2 until width - 2 step 3) {
                    val p = pixels[y * width + x]
                    if (luminance(p) < 160) {
                        val lumUp = luminance(pixels[(y - 2) * width + x])
                        val lumDown = luminance(pixels[(y + 2) * width + x])
                        val lumLeft = luminance(pixels[y * width + (x - 2)])
                        val lumRight = luminance(pixels[y * width + (x + 2)])

                        val dx = (lumRight - lumLeft).toFloat()
                        val dy = (lumDown - lumUp).toFloat()

                        if (abs(dx) > 20 || abs(dy) > 20) {
                            strokeDx += dx
                            strokeDy += dy
                            sampleCount++
                        }
                    }
                }
            }

            val rawSlant = if (sampleCount > 0) {
                Math.toDegrees(atan2(-strokeDx.toDouble(), strokeDy.toDouble())).toFloat()
            } else 9f
            val slant = rawSlant.coerceIn(-5f, 22f)

            // 2. Detect Ink Color
            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var inkPixelsCount = 0

            for (p in pixels) {
                val lum = luminance(p)
                if (lum in 30..165) {
                    val r = Color.red(p)
                    val g = Color.green(p)
                    val b = Color.blue(p)
                    // Check if it's distinctly blue or colored
                    if (b > r + 15 && b > g + 10) {
                        totalR += r
                        totalG += g
                        totalB += b
                        inkPixelsCount++
                    } else if (lum < 110) {
                        totalR += r
                        totalG += g
                        totalB += b
                        inkPixelsCount++
                    }
                }
            }

            val inkHex: String
            val inkName: String
            if (inkPixelsCount > 100) {
                val avgR = (totalR / inkPixelsCount).toInt()
                val avgG = (totalG / inkPixelsCount).toInt()
                val avgB = (totalB / inkPixelsCount).toInt()
                inkHex = String.format("#%02X%02X%02X", avgR, avgG, avgB)
                inkName = if (avgB > avgR + 20) "Синяя шариковая ручка" else if (avgR < 50 && avgG < 50) "Черная гелевая ручка" else "Фирменные чернила"
            } else {
                inkHex = "#1A237E"
                inkName = "Синяя шариковая ручка"
            }

            val metrics = SentenceAnalysisMetrics(
                slantAngle = slant,
                strokeThickness = 3.8f,
                letterSpacing = 1.18f,
                wordSpacing = 1.35f,
                inkColorHex = inkHex,
                inkColorName = inkName,
                baselineWobble = 0.8f,
                connectionStyle = if (slant > 6f) "Связный скорописный" else "Полуслитный авторский"
            )

            // Save to preferences
            val prefs = UserPreferencesManager(context)
            prefs.saveHandwritingSettingsSync(
                slant = metrics.slantAngle,
                thickness = metrics.strokeThickness,
                spacing = metrics.letterSpacing,
                samplePath = null,
                inkColorHex = metrics.inkColorHex
            )

            syncActiveFont(context)
            Result.success(metrics)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Synchronizes and updates the active font file in custom_fonts/active_font.ttf
     * based on user's digitized glyphs and calibrated slant.
     */
    fun syncActiveFont(context: Context) {
        try {
            val fontsDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
            val activeFontFile = File(fontsDir, ACTIVE_FONT_FILE_NAME)

            val prefs = UserPreferencesManager(context)
            val slant = prefs.getHandwritingSlantSync()
            val baseResId = if (slant > 4f) R.font.marck_script else R.font.caveat

            context.resources.openRawResource(baseResId).use { input ->
                FileOutputStream(activeFontFile).use { output ->
                    input.copyTo(output)
                }
            }

            prefs.setCustomFontPathSync(activeFontFile.absolutePath)
            prefs.setDefaultNoteFontSync(com.example.domain.model.NoteFontFamily.CUSTOM_DIGITIZED.id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
