package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
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
    GEMINI_AI("✨ ИИ Gemini", "Высокая точность"),
    LOCAL_DEVICE("⚡ На устройстве", "Офлайн с нормализатором")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScanResultDialog(
    imageUri: Uri,
    onDismissRequest: () -> Unit,
    onInsertText: (insertedText: String, insertAtCursor: Boolean, saveImmediately: Boolean) -> Unit
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
    var showImagePreview by remember { mutableStateOf(false) }

    var showApiKeyDialog by remember { mutableStateOf(false) }

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
                    recognizedText = result.getOrNull()?.trim() ?: ""
                    errorMessage = null
                } else {
                    val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                    errorMessage = err
                    // Auto-fallback to local OCR text while keeping error message visible
                    try {
                        val localText = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, imageUri).trim()
                        if (localText.isNotBlank()) {
                            recognizedText = localText
                        }
                    } catch (_: Exception) {}
                }
            } else {
                loadingMessage = "Локальное распознавание и восстановление кириллицы..."
                try {
                    val text = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, imageUri).trim()
                    recognizedText = text
                    errorMessage = null
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Не удалось распознать текст с фото."
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

    val wordCount = remember(recognizedText) {
        recognizedText.split(Regex("\\s+")).count { it.isNotBlank() }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .imePadding(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. TOP HEADER (COMPACT WITH DIRECT SAVE BUTTON)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.DocumentScanner,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Скан текста с фото",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                                Text(
                                    text = if (selectedMode == OcrMode.GEMINI_AI) "Режим: ✨ ИИ Gemini" else "Режим: ⚡ Офлайн",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selectedMode == OcrMode.GEMINI_AI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Direct prominent Save button in top bar (matches Note Editor style)
                            Button(
                                onClick = {
                                    onInsertText(recognizedText, true, true)
                                    onDismissRequest()
                                },
                                enabled = !isLoading && recognizedText.isNotBlank(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showApiKeyDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Ключ", fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // 2. MODE SELECTOR ROW & CONTROLS
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedMode == OcrMode.GEMINI_AI,
                                onClick = {
                                    selectedMode = OcrMode.GEMINI_AI
                                    runRecognition(OcrMode.GEMINI_AI)
                                },
                                label = { Text("✨ ИИ Gemini", fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )

                            FilterChip(
                                selected = selectedMode == OcrMode.LOCAL_DEVICE,
                                onClick = {
                                    selectedMode = OcrMode.LOCAL_DEVICE
                                    runRecognition(OcrMode.LOCAL_DEVICE)
                                },
                                label = { Text("⚡ На устройстве", fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )

                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Ключ", fontSize = 11.sp)
                            }
                        }

                        // Toggle preview button
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = { showImagePreview = !showImagePreview },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (showImagePreview) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = "Исходное фото",
                                    tint = if (showImagePreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = { runRecognition(selectedMode) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Повторить", modifier = Modifier.size(18.dp))
                            }
                        }
                    }

                    // 3. COLLAPSIBLE IMAGE PREVIEW (IF TOGGLED)
                    AnimatedVisibility(visible = showImagePreview) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Исходный снимок",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Error banner with retry / switch options
                    if (errorMessage != null) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = errorMessage!!,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = { showApiKeyDialog = true },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("🔑 Ввести ключ API", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    TextButton(
                                        onClick = {
                                            selectedMode = OcrMode.LOCAL_DEVICE
                                            runRecognition(OcrMode.LOCAL_DEVICE)
                                        },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Локально (ML Kit)", fontSize = 10.sp)
                                    }
                                    Button(
                                        onClick = { runRecognition(selectedMode) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("Повторить", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // 4. MAIN EDITABLE TEXT AREA (FLEXIBLE HEIGHT = WEIGHT 1F)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        if (isLoading) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = loadingMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Распознанный текст:",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    TextButton(
                                        onClick = {
                                            val fixed = CyrillicOcrCorrector.correctPseudoLatinText(recognizedText)
                                            recognizedText = fixed
                                            Toast.makeText(context, "Кириллица исправлена", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(13.dp))
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text("Исправить буквы", fontSize = 10.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(2.dp))

                                OutlinedTextField(
                                    value = recognizedText,
                                    onValueChange = { recognizedText = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                                    placeholder = { Text("Текст распознавания появится здесь...") }
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Слов: $wordCount  •  Символов: ${recognizedText.length}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )

                                    Text(
                                        text = if (selectedMode == OcrMode.GEMINI_AI) "✨ ИИ Gemini" else "⚡ Локальная нормализация",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (selectedMode == OcrMode.GEMINI_AI) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                    }

                    // 5. BOTTOM ACTION BAR (PERMANENTLY DOCKED & VISIBLE)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
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
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                enabled = !isLoading && recognizedText.isNotBlank()
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Копия", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    val formatted = "\n\n> 📷 **С фото / доски:**\n> $recognizedText\n"
                                    onInsertText(formatted, false, true)
                                    Toast.makeText(context, "Добавлено как блок доски", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                },
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                enabled = !isLoading && recognizedText.isNotBlank()
                            ) {
                                Icon(Icons.Filled.FormatQuote, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Цитата", fontSize = 11.sp, maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = {
                                    onInsertText(recognizedText, true, false)
                                    Toast.makeText(context, "Вставлено в заметку", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                },
                                modifier = Modifier.weight(1.0f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                enabled = !isLoading && recognizedText.isNotBlank()
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Вставить", fontSize = 11.sp, maxLines = 1)
                            }

                            Button(
                                onClick = {
                                    onInsertText(recognizedText, true, true)
                                    onDismissRequest()
                                },
                                modifier = Modifier.weight(1.3f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                enabled = !isLoading && recognizedText.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            onDismissRequest = { showApiKeyDialog = false },
            onKeySaved = { newKey ->
                showApiKeyDialog = false
                selectedMode = OcrMode.GEMINI_AI
                runRecognition(OcrMode.GEMINI_AI, newKey)
            }
        )
    }
}
