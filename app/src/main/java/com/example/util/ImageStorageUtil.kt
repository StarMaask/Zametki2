package com.example.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageUtil {

    /**
     * Копирует изображение из content Uri во внутреннее хранилище приложения
     * и возвращает стабильный путь file://
     */
    fun saveImageToInternalStorage(context: Context, uri: Uri): String? {
        return try {
            val directory = File(context.filesDir, "note_images").apply {
                if (!exists()) mkdirs()
            }
            val fileName = "img_${UUID.randomUUID()}.jpg"
            val file = File(directory, fileName)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(file).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
