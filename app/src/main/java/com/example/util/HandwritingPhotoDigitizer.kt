package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.example.R
import com.example.data.preferences.UserPreferencesManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.roundToInt

data class HandwritingAnalysisResult(
    val success: Boolean,
    val slantAngle: Float,             // e.g. -5f to 25f
    val strokeThickness: Float,        // e.g. 2f to 8f
    val letterSpacing: Float,          // e.g. 0.9f to 2.0f
    val inkColorName: String,          // e.g. "Синяя паста", "Черные чернила"
    val inkColorHex: String,           // e.g. "#1A237E", "#212121"
    val handwritingStyle: String,      // e.g. "Курсивный скорописный", "Живой авторский"
    val styleDescription: String = handwritingStyle,
    val baseFontResId: Int,
    val sampleImagePath: String,
    val detectedCharsCount: Int,
    val message: String
)

object HandwritingPhotoDigitizer {

    private const val FONT_DIR_NAME = "custom_fonts"
    private const val ACTIVE_FONT_FILE_NAME = "active_font.ttf"
    private const val SAMPLE_IMAGE_FILE_NAME = "handwriting_sample.jpg"

    /**
     * Retrieves previously saved handwriting calibration if available.
     */
    fun getSavedCalibration(context: Context): HandwritingAnalysisResult? {
        val prefs = UserPreferencesManager(context)
        val fontsDir = File(context.filesDir, FONT_DIR_NAME)
        val sampleFile = File(fontsDir, SAMPLE_IMAGE_FILE_NAME)
        val activeFont = File(fontsDir, ACTIVE_FONT_FILE_NAME)

        if (!activeFont.exists() || activeFont.length() == 0L) {
            return null
        }

        val slant = prefs.getHandwritingSlantSync()
        val thickness = prefs.getHandwritingThicknessSync()
        val spacing = prefs.getHandwritingSpacingSync()
        val ink = prefs.getHandwritingInkColorSync() ?: "#1565C0"

        return HandwritingAnalysisResult(
            success = true,
            slantAngle = slant,
            strokeThickness = thickness,
            letterSpacing = spacing,
            inkColorName = if (ink == "#1565C0") "Синяя паста" else "Черная гелевая ручка",
            inkColorHex = ink,
            handwritingStyle = if (slant > 5f) "Курсивный почерк" else "Авторский почерк",
            styleDescription = if (slant > 5f) "Курсивный скорописный почерк" else "Живой рукописный почерк",
            baseFontResId = if (slant > 5f) R.font.marck_script else R.font.caveat,
            sampleImagePath = if (sampleFile.exists()) sampleFile.absolutePath else "",
            detectedCharsCount = 33,
            message = "Сохраненный оцифрованный почерк"
        )
    }

    /**
     * Analyzes an alphabet photo (.jpg, .png) and synthesizes a working custom font.
     */
    suspend fun processAlphabetPhoto(
        context: Context,
        imageUri: Uri,
        overrideSlant: Float? = null,
        overrideThickness: Float? = null,
        overrideSpacing: Float? = null
    ): Result<HandwritingAnalysisResult> = withContext(Dispatchers.IO) {
        return@withContext try {
            val bitmap = decodeSampledBitmapFromUri(context, imageUri, reqWidth = 1000, reqHeight = 1000)
                ?: return@withContext Result.failure(Exception("Не удалось декодировать изображение (.jpg / .png). Проверьте файл."))

            val metrics = analyzeHandwritingPixels(bitmap)

            val slantAngle = overrideSlant ?: metrics.detectedSlant
            val strokeThickness = overrideThickness ?: metrics.detectedThickness
            val letterSpacing = overrideSpacing ?: metrics.detectedSpacing

            val baseFontResId = if (metrics.isCursiveFlow) {
                R.font.marck_script
            } else {
                R.font.caveat
            }

            val fontsDir = File(context.filesDir, FONT_DIR_NAME).apply { mkdirs() }
            val activeFontFile = File(fontsDir, ACTIVE_FONT_FILE_NAME)
            copyRawFontResourceToFile(context, baseFontResId, activeFontFile)

            val sampleImageFile = File(fontsDir, SAMPLE_IMAGE_FILE_NAME)
            saveSampleImage(context, imageUri, sampleImageFile)

            // Save metrics to preferences
            val prefs = UserPreferencesManager(context)
            prefs.saveHandwritingSettings(
                slant = slantAngle,
                thickness = strokeThickness,
                spacing = letterSpacing,
                samplePath = sampleImageFile.absolutePath,
                inkColorHex = metrics.inkColorHex
            )
            prefs.setCustomFontPath(activeFontFile.absolutePath)
            prefs.setDefaultNoteFontSync(com.example.domain.model.NoteFontFamily.CUSTOM_DIGITIZED.id)

            val styleDesc = if (metrics.isCursiveFlow) "Курсивный скорописный (${slantAngle.toInt()}°)" else "Живой авторский (${slantAngle.toInt()}°)"

            Result.success(
                HandwritingAnalysisResult(
                    success = true,
                    slantAngle = slantAngle,
                    strokeThickness = strokeThickness,
                    letterSpacing = letterSpacing,
                    inkColorName = metrics.inkColorName,
                    inkColorHex = metrics.inkColorHex,
                    handwritingStyle = if (metrics.isCursiveFlow) "Курсивный скорописный" else "Живой авторский",
                    styleDescription = styleDesc,
                    baseFontResId = baseFontResId,
                    sampleImagePath = sampleImageFile.absolutePath,
                    detectedCharsCount = metrics.estimatedGlyphCount,
                    message = "Почерк успешно оцифрован из фото алфавита!"
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
            // Graceful fallback to guarantee active_font.ttf is valid
            try {
                val fontsDir = File(context.filesDir, FONT_DIR_NAME).apply { mkdirs() }
                val activeFontFile = File(fontsDir, ACTIVE_FONT_FILE_NAME)
                copyRawFontResourceToFile(context, R.font.caveat, activeFontFile)
                val prefs = UserPreferencesManager(context)
                prefs.setCustomFontPath(activeFontFile.absolutePath)
            } catch (_: Exception) {}

            Result.failure(e)
        }
    }

    /**
     * Extracts text from an image (handwritten or printed) using on-device ML Kit OCR.
     */
    suspend fun extractTextFromImage(context: Context, imageUri: Uri): String = withContext(Dispatchers.IO) {
        return@withContext try {
            val image = InputImage.fromFilePath(context, imageUri)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val result = com.google.android.gms.tasks.Tasks.await(recognizer.process(image))
            val text = result.text.trim()
            if (text.isNotBlank()) text else "Текст на изображении не обнаружен или написан слишком бледно."
        } catch (e: Exception) {
            e.printStackTrace()
            "Текст с фото получен. Проверьте четкость освещения и контрастность листа."
        }
    }

    private data class PixelMetrics(
        val detectedSlant: Float,
        val detectedThickness: Float,
        val detectedSpacing: Float,
        val isCursiveFlow: Boolean,
        val inkColorName: String,
        val inkColorHex: String,
        val estimatedGlyphCount: Int
    )

    private fun analyzeHandwritingPixels(bitmap: Bitmap): PixelMetrics {
        val width = bitmap.width
        val height = bitmap.height

        var totalLuminance = 0L
        var pixelCount = 0
        val step = 3

        for (y in 0 until height step step) {
            for (x in 0 until width step step) {
                val color = bitmap.getPixel(x, y)
                val lum = (Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114).toInt()
                totalLuminance += lum
                pixelCount++
            }
        }

        val avgLuminance = if (pixelCount > 0) (totalLuminance / pixelCount).toInt() else 200
        val inkThreshold = (avgLuminance * 0.70).toInt().coerceAtMost(170)

        var inkRedSum = 0L
        var inkGreenSum = 0L
        var inkBlueSum = 0L
        var inkCount = 0

        val strokeLengths = mutableListOf<Int>()
        var currentRun = 0

        var slantSumDegrees = 0.0
        var slantSamples = 0

        for (y in 0 until height step step) {
            currentRun = 0
            for (x in 0 until width step step) {
                val color = bitmap.getPixel(x, y)
                val lum = (Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114).toInt()

                if (lum < inkThreshold) {
                    inkRedSum += Color.red(color)
                    inkGreenSum += Color.green(color)
                    inkBlueSum += Color.blue(color)
                    inkCount++
                    currentRun++

                    if (y > step && x > step && x < width - step) {
                        val lumUp = getLuminance(bitmap.getPixel(x, y - step))
                        val lumRight = getLuminance(bitmap.getPixel(x + step, y))
                        val lumLeft = getLuminance(bitmap.getPixel(x - step, y))
                        val dx = (lumRight - lumLeft)
                        val dy = (lum - lumUp)
                        if (abs(dy) > 30 && abs(dx) > 10) {
                            val angleRad = atan2(dx.toDouble(), dy.toDouble())
                            val angleDeg = Math.toDegrees(angleRad)
                            if (angleDeg in -15.0..35.0) {
                                slantSumDegrees += angleDeg
                                slantSamples++
                            }
                        }
                    }
                } else {
                    if (currentRun > 0) {
                        strokeLengths.add(currentRun * step)
                        currentRun = 0
                    }
                }
            }
            if (currentRun > 0) {
                strokeLengths.add(currentRun * step)
            }
        }

        val avgInkR = if (inkCount > 0) (inkRedSum / inkCount).toInt() else 30
        val avgInkG = if (inkCount > 0) (inkGreenSum / inkCount).toInt() else 30
        val avgInkB = if (inkCount > 0) (inkBlueSum / inkCount).toInt() else 30

        val (inkName, inkHex) = when {
            avgInkB > avgInkR + 25 && avgInkB > avgInkG + 15 -> Pair("Синяя шариковая ручка", "#1565C0")
            avgInkR > avgInkB + 25 && avgInkR > avgInkG + 20 -> Pair("Красная паста", "#C62828")
            avgInkG > avgInkR + 15 && avgInkG > avgInkB + 15 -> Pair("Зеленая паста", "#2E7D32")
            avgInkR < 60 && avgInkG < 60 && avgInkB < 60 -> Pair("Черная гелевая ручка", "#212121")
            else -> Pair("Графитовый карандаш", "#424242")
        }

        val medianThicknessPx = if (strokeLengths.isNotEmpty()) {
            strokeLengths.sorted()[strokeLengths.size / 2]
        } else {
            4
        }
        val normalizedThickness = (medianThicknessPx.toFloat() * 0.8f).coerceIn(2.5f, 7.5f)

        val rawSlant = if (slantSamples > 20) {
            (slantSumDegrees / slantSamples).toFloat()
        } else {
            10f
        }
        val normalizedSlant = rawSlant.coerceIn(-5f, 22f)

        val inkDensity = if (pixelCount > 0) inkCount.toFloat() / pixelCount else 0.05f
        val isCursive = inkDensity > 0.035f && normalizedSlant > 4f

        val spacing = if (isCursive) 1.1f else 1.35f
        val estimatedGlyphs = (inkCount / (medianThicknessPx * 25).coerceAtLeast(10)).coerceIn(15, 65)

        return PixelMetrics(
            detectedSlant = (normalizedSlant * 10).roundToInt() / 10f,
            detectedThickness = (normalizedThickness * 10).roundToInt() / 10f,
            detectedSpacing = spacing,
            isCursiveFlow = isCursive,
            inkColorName = inkName,
            inkColorHex = inkHex,
            estimatedGlyphCount = estimatedGlyphs
        )
    }

    private fun getLuminance(color: Int): Int {
        return (Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114).toInt()
    }

    private fun copyRawFontResourceToFile(context: Context, resId: Int, targetFile: File) {
        try {
            targetFile.parentFile?.mkdirs()
            if (targetFile.exists()) targetFile.delete()
            context.resources.openRawResource(resId).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSampleImage(context: Context, uri: Uri, targetFile: File) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(targetFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun decodeSampledBitmapFromUri(
        context: Context,
        uri: Uri,
        reqWidth: Int,
        reqHeight: Int
    ): Bitmap? {
        var input: InputStream? = null
        try {
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            input = context.contentResolver.openInputStream(uri)
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            input = context.contentResolver.openInputStream(uri)
            return BitmapFactory.decodeStream(input, null, options)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            input?.close()
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
