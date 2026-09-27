package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.example.domain.model.UserProfileRequisites
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages the user's handwritten digital signature and facsimile stamp.
 * Allows drawing with stylus or finger, saving as transparent high-res PNG,
 * and embedding into PDF and DOCX document exports.
 */
object SignatureManager {

    private const val SIGNATURE_DIR = "signatures"
    private const val SIGNATURE_FILE_NAME = "user_signature.png"
    private const val STAMP_FILE_NAME = "user_stamp.png"

    private val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))

    fun getSignatureFile(context: Context): File {
        val dir = File(context.filesDir, SIGNATURE_DIR).apply { mkdirs() }
        return File(dir, SIGNATURE_FILE_NAME)
    }

    fun hasSignature(context: Context): Boolean {
        val file = getSignatureFile(context)
        return file.exists() && file.length() > 0
    }

    fun getSignatureBitmap(context: Context): Bitmap? {
        val file = getSignatureFile(context)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }

    fun saveSignature(context: Context, bitmap: Bitmap): File {
        val file = getSignatureFile(context)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.flush()
        }
        return file
    }

    fun deleteSignature(context: Context): Boolean {
        val file = getSignatureFile(context)
        val stampFile = File(context.filesDir, "$SIGNATURE_DIR/$STAMP_FILE_NAME")
        if (stampFile.exists()) stampFile.delete()
        return if (file.exists()) file.delete() else false
    }

    /**
     * Creates a composite official facsimile stamp bitmap containing:
     * - Russian formal digital signature frame: "ДОКУМЕНТ ПОДПИСАН ЭЛЕКТРОННОЙ ПОДПИСЬЮ"
     * - Signer's Name / Position
     * - Date and time of signing
     * - Overlaid handwritten signature
     */
    fun createFacsimileStamp(
        context: Context,
        profile: UserProfileRequisites?,
        includeFrame: Boolean = true
    ): Bitmap? {
        val sigBitmap = getSignatureBitmap(context) ?: return null
        if (!includeFrame) return sigBitmap

        val width = 480
        val height = 200
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)

        // Stamp ink color (Official Russian blue ink: #002FA7)
        val stampColor = Color.rgb(0, 47, 167)

        val borderPaint = Paint().apply {
            color = stampColor
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        // Draw double rounded border
        val outerRect = RectF(4f, 4f, width - 4f, height - 4f)
        val innerRect = RectF(8f, 8f, width - 8f, height - 8f)
        canvas.drawRoundRect(outerRect, 10f, 10f, borderPaint)
        borderPaint.strokeWidth = 1.2f
        canvas.drawRoundRect(innerRect, 8f, 8f, borderPaint)

        // Stamp typography
        val titlePaint = Paint().apply {
            color = stampColor
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val textPaint = Paint().apply {
            color = stampColor
            textSize = 13f
            typeface = Typeface.DEFAULT
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        val boldTextPaint = Paint().apply {
            color = stampColor
            textSize = 13.5f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.LEFT
        }

        canvas.drawText("ДОКУМЕНТ ПОДПИСАН", width / 2f, 28f, titlePaint)
        canvas.drawText("ПРОСТОЙ ЭЛЕКТРОННОЙ ПОДПИСЬЮ", width / 2f, 46f, titlePaint)

        // Divider line
        borderPaint.strokeWidth = 1f
        canvas.drawLine(12f, 54f, width - 12f, 54f, borderPaint)

        val signerName = profile?.fullName?.ifBlank { profile.shortName } ?: "Владелец подписи"
        val signerPos = profile?.position?.takeIf { it.isNotBlank() } ?: "Заявитель"
        val signDate = dateFormat.format(Date())

        canvas.drawText("Владелец: ", 16f, 76f, textPaint)
        val nameToDraw = if (signerName.length > 28) signerName.take(28) + "..." else signerName
        canvas.drawText(nameToDraw, 86f, 76f, boldTextPaint)

        canvas.drawText("Должность: ", 16f, 96f, textPaint)
        val posToDraw = if (signerPos.length > 28) signerPos.take(28) + "..." else signerPos
        canvas.drawText(posToDraw, 94f, 96f, textPaint)

        canvas.drawText("Дата: $signDate", 16f, 116f, textPaint)

        // Draw scaled signature across the right side
        val sigTargetW = 160
        val sigScale = minOf(sigTargetW.toFloat() / sigBitmap.width, 70f / sigBitmap.height)
        val scaledW = (sigBitmap.width * sigScale).toInt().coerceAtLeast(1)
        val scaledH = (sigBitmap.height * sigScale).toInt().coerceAtLeast(1)
        val scaledSig = Bitmap.createScaledBitmap(sigBitmap, scaledW, scaledH, true)

        canvas.drawBitmap(scaledSig, (width - scaledW - 18).toFloat(), (height - scaledH - 14).toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))

        return result
    }
}
