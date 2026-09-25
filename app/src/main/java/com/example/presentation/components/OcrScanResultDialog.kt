package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.preferences.UserPreferencesManager
import com.example.util.CyrillicOcrCorrector
import com.example.util.GeminiOcrService
import com.example.util.HandwritingPhotoDigitizer
import kotlinx.coroutines.launch

enum class OcrMode(val title: String, val subtitle: String) {
    GEMINI_AI("✨ ИИ Gemini", "Идеальный русский язык"),
    LOCAL_DEVICE("⚡ На устройстве", "Офлайн с нормализатором")
}

@Composable
fun OcrScanResultDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onInsertText: (insertedText: String, insertAtCursor: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context) }

    val hasApiKey = remember { GeminiOcrService.hasAvailableApiKey(context) }
    var selectedMode by remember {
        mutableStateOf(if (hasApiKey) OcrMode.GEMINI_AI else OcrMode.LOCAL_DEVICE)
    }

    var isLoading by remember { mutableStateOf(true) }
    var loadingMessage by remember { mutableStateOf("Распознавание текста...") }
    var recognizedText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showApiKeyInput by remember { mutableStateOf(!hasApiKey && selectedMode == OcrMode.GEMINI_AI) }
    var apiKeyInput by remember { mutableStateOf("") }
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // Function to run OCR based on active mode
    fun runRecognition(mode: OcrMode, customKey: String? = null) {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            if (mode == OcrMode.GEMINI_AI) {
                loadingMessage = "ИИ Gemini распознает русский текст..."
                val key = customKey ?: prefs.getGeminiApiKeySync()
                val result = GeminiOcrService.recognizeTextWithGemini(context, imageUri, key)
                if (result.isSuccess) {
                    recognizedText = result.getOrNull() ?: ""
                    errorMessage = null
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                    errorMessage = err
                    // Fallback to local on-device OCR
                    Toast.makeText(context, "ИИ недоступен. Переключаем на локальный режим...", Toast.LENGTH_LONG).show()
                    loadingMessage = "Локальное распознавание и восстановление кириллицы..."
                    val localText = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, imageUri)
                    recognizedText = localText
                    selectedMode = OcrMode.LOCAL_DEVICE
                }
            } else {
                loadingMessage = "Локальное распознавание и восстановление кириллицы..."
                try {
                    val text = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, imageUri)
                    recognizedText = text
                    errorMessage = null
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorMessage = "Не удалось распознать текст с фото."
                }
            }
            isLoading = false
        }
    }

    // Initial scan on dialog launch
    LaunchedEffect(imageUri) {
        if (hasApiKey) {
            runRecognition(OcrMode.GEMINI_AI)
        } else {
            runRecognition(OcrMode.LOCAL_DEVICE)
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header
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
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Распознавание текста с фото",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (selectedMode == OcrMode.GEMINI_AI) "Режим: ИИ Gemini (без искажений)" else "Режим: Локально (на устройстве)",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selectedMode == OcrMode.GEMINI_AI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Mode Selector Tabs (Gemini AI vs Local Device)
                TabRow(
                    selectedTabIndex = selectedMode.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    OcrMode.values().forEach { mode ->
                        val isSelected = selectedMode == mode
                        Tab(
                            selected = isSelected,
                            onClick = {
                                if (selectedMode != mode) {
                                    selectedMode = mode
                                    if (mode == OcrMode.GEMINI_AI && !GeminiOcrService.hasAvailableApiKey(context)) {
                                        showApiKeyInput = true
                                    } else {
                                        showApiKeyInput = false
                                        runRecognition(mode)
                                    }
                                }
                            },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = mode.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Body
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Photo Thumbnail
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(14.dp))
                    ) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Исходное фото",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(bottomStart = 10.dp),
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Text(
                                text = "Исходный снимок",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // API Key input banner if in Gemini mode and key not configured
                    AnimatedVisibility(visible = showApiKeyInput && selectedMode == OcrMode.GEMINI_AI) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Для сверхточного ИИ укажите Gemini API ключ:",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = { apiKeyInput = it },
                                    placeholder = { Text("Вставьте AIzaSy...", fontSize = 12.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                            Icon(
                                                if (isApiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    },
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        selectedMode = OcrMode.LOCAL_DEVICE
                                        showApiKeyInput = false
                                        runRecognition(OcrMode.LOCAL_DEVICE)
                                    }) {
                                        Text("Локальный режим", fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Button(
                                        onClick = {
                                            if (apiKeyInput.isNotBlank()) {
                                                coroutineScope.launch {
                                                    prefs.setGeminiApiKey(apiKeyInput.trim())
                                                    showApiKeyInput = false
                                                    runRecognition(OcrMode.GEMINI_AI, apiKeyInput.trim())
                                                }
                                            } else {
                                                Toast.makeText(context, "Введите ключ", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Распознать через ИИ", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = loadingMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        // Header above editor with actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Распознанный текст:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Manual Cyrillic normalization button
                                TextButton(
                                    onClick = {
                                        val fixed = CyrillicOcrCorrector.correctPseudoLatinText(recognizedText)
                                        recognizedText = fixed
                                        Toast.makeText(context, "Кириллица исправлена", Toast.LENGTH_SHORT).show()
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Исправить буквы", fontSize = 11.sp)
                                }

                                // If in local mode, option to switch to AI
                                if (selectedMode == OcrMode.LOCAL_DEVICE) {
                                    TextButton(
                                        onClick = {
                                            selectedMode = OcrMode.GEMINI_AI
                                            if (!GeminiOcrService.hasAvailableApiKey(context)) {
                                                showApiKeyInput = true
                                            } else {
                                                runRecognition(OcrMode.GEMINI_AI)
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("ИИ Gemini", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = recognizedText,
                            onValueChange = { recognizedText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 280.dp),
                            shape = RoundedCornerShape(12.dp),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp)
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                        val wordCount = remember(recognizedText) {
                            recognizedText.split(Regex("\\s+")).count { it.isNotBlank() }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Слов: $wordCount  •  Символов: ${recognizedText.length}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (selectedMode == OcrMode.GEMINI_AI) {
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Точность: ИИ Gemini",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Локальная нормализация",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = ClipData.newPlainText("OCR Текст", recognizedText)
                            clipboard?.setPrimaryClip(clip)
                            Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        enabled = !isLoading && recognizedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Копировать", fontSize = 11.sp, maxLines = 1)
                    }

                    FilledTonalButton(
                        onClick = {
                            val formatted = "\n\n> 📷 **С фото / доски:**\n> $recognizedText\n"
                            onInsertText(formatted, false)
                            Toast.makeText(context, "Добавлено как блок доски", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        enabled = !isLoading && recognizedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.FormatQuote, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Как цитату", fontSize = 11.sp, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            onInsertText(recognizedText, true)
                            Toast.makeText(context, "Вставлено в заметку", Toast.LENGTH_SHORT).show()
                            onDismissRequest()
                        },
                        modifier = Modifier.weight(1.1f),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
                        enabled = !isLoading && recognizedText.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Вставить", fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }
}
}
