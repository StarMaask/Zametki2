package com.example.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun QrBarcodeScannerDialog(
    onDismissRequest: () -> Unit,
    onScannedResult: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var isScanning by remember { mutableStateOf(false) }
    var scannedText by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("Выберите фото документа, квитанции или QR-кода") }
    var copied by remember { mutableStateOf(false) }

    fun processImageUri(uri: Uri) {
        scope.launch {
            isScanning = true
            statusMessage = "Анализ изображения..."
            try {
                val inputImage = withContext(Dispatchers.IO) {
                    val stream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(stream)
                    InputImage.fromBitmap(bitmap, 0)
                }

                val scanner = BarcodeScanning.getClient()
                scanner.process(inputImage)
                    .addOnSuccessListener { barcodes ->
                        isScanning = false
                        if (barcodes.isNotEmpty()) {
                            val first = barcodes.first()
                            val value = first.rawValue ?: first.displayValue ?: ""
                            scannedText = value
                            statusMessage = "Успешно распознан ${first.format}-код!"
                        } else {
                            statusMessage = "QR-код или штрихкод на фото не обнаружен. Попробуйте другое фото с хорошим освещением."
                        }
                    }
                    .addOnFailureListener { e ->
                        isScanning = false
                        statusMessage = "Ошибка сканирования: ${e.localizedMessage}"
                    }
            } catch (e: Exception) {
                isScanning = false
                statusMessage = "Не удалось открыть изображение: ${e.localizedMessage}"
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            processImageUri(uri)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f)
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
                            Icon(Icons.Filled.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Сканер QR и штрихкодов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Считывание кодов с документов и чеков", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выбрать фото для сканирования")
                    }

                    if (isScanning) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    }

                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (!scannedText.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Считанный результат:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                    TextButton(
                                        onClick = {
                                            clipboardManager.setText(AnnotatedString(scannedText!!))
                                            copied = true
                                        }
                                    ) {
                                        Icon(if (copied) Icons.Filled.Done else Icons.Filled.ContentCopy, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(if (copied) "Скопировано!" else "Копировать", fontSize = 11.sp)
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                SelectionContainer {
                                    Text(
                                        text = scannedText!!,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            if (!scannedText.isNullOrBlank()) {
                                onScannedResult(scannedText!!)
                                onDismissRequest()
                            }
                        },
                        enabled = !scannedText.isNullOrBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Вставить в текст")
                    }
                }
            }
        }
    }
}
