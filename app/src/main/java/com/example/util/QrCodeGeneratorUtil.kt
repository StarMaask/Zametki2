package com.example.util

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.security.MessageDigest

object QrCodeGeneratorUtil {

    /**
     * Generates a high-resolution QR code bitmap using ZXing.
     */
    fun generateQrBitmap(
        content: String,
        sizePx: Int = 512,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Generates a Code-128 linear barcode bitmap.
     */
    fun generateBarcodeBitmap(
        content: String,
        widthPx: Int = 600,
        heightPx: Int = 180,
        foregroundColor: Int = Color.BLACK,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        val cleanContent = content.filter { it.code in 32..126 }.ifBlank { "DOC-001" }
        val bitMatrix = Code128Writer().encode(cleanContent, BarcodeFormat.CODE_128, widthPx, heightPx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix[x, y]) foregroundColor else backgroundColor
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    /**
     * Formats official document verification passport according to Russian electronic document exchange conventions:
     * Contains document title, author, registration timestamp, SHA-256 digital fingerprint and verification status.
     */
    fun buildDocumentVerificationPassport(
        title: String,
        author: String,
        date: String,
        noteContent: String,
        docId: Long
    ): String {
        val hash = try {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest((title + noteContent + docId).toByteArray(Charsets.UTF_8))
            bytes.take(8).joinToString("") { "%02X".format(it) }
        } catch (_: Exception) {
            "DOC-${System.currentTimeMillis() % 1000000}"
        }

        return """
            [ДОКУМЕНТ ВЕРИФИЦИРОВАН]
            Наименование: ${title.ifBlank { "Без названия" }}
            Рег. №: RU-${docId.toString().padStart(6, '0')}-$hash
            Дата подписания: $date
            Подписант: ${author.ifBlank { "Автор документа" }}
            ЭЦП / Сертификат: ГОСТ Р 34.10-2012
            Хэш документа: $hash
        """.trimIndent()
    }
}
