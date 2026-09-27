package com.example.presentation.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.domain.model.Note
import com.example.util.QrCodeGeneratorUtil
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun QrCodeDocumentDialog(
    note: Note,
    authorName: String,
    onDismissRequest: () -> Unit,
    onInsertQrIntoNote: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    var selectedMode by remember { mutableStateOf(0) } // 0 = Passport, 1 = Link/Text, 2 = Author
    var customText by remember { mutableStateOf("") }
    var copiedToClipboard by remember { mutableStateOf(false) }

    val qrPayload = remember(selectedMode, customText, note, authorName) {
        when (selectedMode) {
            0 -> QrCodeGeneratorUtil.buildDocumentVerificationPassport(
                title = note.title,
                author = authorName,
                date = dateFormat.format(Date(note.updatedAt)),
                noteContent = note.content,
                docId = note.id
            )
            1 -> customText.ifBlank { note.content.take(200).ifBlank { "Заметка: ${note.title}" } }
            2 -> "BEGIN:VCARD\nVERSION:3.0\nFN:$authorName\nNOTE:Автор документа: ${note.title}\nEND:VCARD"
            else -> note.title
        }
    }

    val qrBitmap = remember(qrPayload) {
        try {
            QrCodeGeneratorUtil.generateQrBitmap(qrPayload, sizePx = 512)
        } catch (_: Exception) {
            null
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.QrCode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("QR-код документа", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Верификация ГОСТ и быстрый обмен", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Mode selector
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedMode == 0,
                            onClick = { selectedMode = 0 },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3)
                        ) {
                            Text("Паспорт", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = selectedMode == 1,
                            onClick = { selectedMode = 1 },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3)
                        ) {
                            Text("Текст/Ссылка", fontSize = 12.sp)
                        }
                        SegmentedButton(
                            selected = selectedMode == 2,
                            onClick = { selectedMode = 2 },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3)
                        ) {
                            Text("Визитка", fontSize = 12.sp)
                        }
                    }

                    if (selectedMode == 1) {
                        OutlinedTextField(
                            value = customText,
                            onValueChange = { customText = it },
                            placeholder = { Text("Введите URL, реквизит или текст для QR-кода...") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )
                    }

                    // QR Display Box
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(220.dp)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (qrBitmap != null) {
                                Image(
                                    bitmap = qrBitmap.asImageBitmap(),
                                    contentDescription = "QR-код",
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Text("Ошибка формирования QR-кода", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // Preview of data
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Данные в коде:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                TextButton(
                                    onClick = {
                                        clipboardManager.setText(AnnotatedString(qrPayload))
                                        copiedToClipboard = true
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        if (copiedToClipboard) Icons.Filled.Done else Icons.Filled.ContentCopy,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (copiedToClipboard) "Скопировано!" else "Копировать", fontSize = 11.sp)
                                }
                            }
                            Text(
                                text = qrPayload,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 5,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (qrBitmap != null) {
                                shareQrBitmap(context, qrBitmap, note.title)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Поделиться", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            if (qrBitmap != null) {
                                val savedFile = saveQrBitmapToStorage(context, qrBitmap, note.id)
                                val markdownImg = "\n\n![QR-код верификации](${savedFile.absolutePath})\n"
                                onInsertQrIntoNote(markdownImg)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Вставить в текст", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun saveQrBitmapToStorage(context: Context, bitmap: Bitmap, noteId: Long): File {
    val dir = File(context.filesDir, "qr_codes").apply { mkdirs() }
    val file = File(dir, "qr_doc_${noteId}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    return file
}

private fun shareQrBitmap(context: Context, bitmap: Bitmap, title: String) {
    try {
        val cacheDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(cacheDir, "qr_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "QR-код документа: $title")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поделиться QR-кодом"))
    } catch (_: Exception) {}
}
