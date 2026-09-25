package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.DocumentExtractorUtil
import com.example.util.ExtractedDocumentInfo
import com.example.util.GeminiOcrService
import kotlinx.coroutines.launch

@Composable
fun DocumentInsertDialog(
    documentUri: Uri,
    onDismissRequest: () -> Unit,
    onInsertText: (insertedText: String, insertAtCursor: Boolean, saveImmediately: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var docInfo by remember { mutableStateOf<ExtractedDocumentInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var currentText by remember { mutableStateOf("") }

    var isStructuringWithAi by remember { mutableStateOf(false) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }

    // Load and extract document on launch
    LaunchedEffect(documentUri) {
        isLoading = true
        val info = DocumentExtractorUtil.extractDocument(context, documentUri)
        docInfo = info
        currentText = info.extractedText
        isLoading = false
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
                .imePadding()
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
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
                                imageVector = when (docInfo?.fileExtension?.lowercase()) {
                                    "pdf" -> Icons.Filled.PictureAsPdf
                                    "doc", "docx" -> Icons.Filled.Article
                                    else -> Icons.Filled.Description
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Вставка документа",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = if (isLoading) "Чтение и извлечение текста..." else docInfo?.fileName ?: "Файл",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Button(
                            onClick = {
                                val info = docInfo
                                val attachmentHeader = if (info != null) {
                                    "📎 Документ: ${info.fileName} (${info.formattedSize})\n\n"
                                } else ""
                                onInsertText(attachmentHeader + currentText, true, true)
                                onDismissRequest()
                            },
                            enabled = currentText.isNotBlank(),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сохранить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        IconButton(
                            onClick = onDismissRequest,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // CONTENT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isLoading) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Извлечение текста из документа...",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "Поддерживаются PDF, DOCX, TXT и другие форматы",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        val info = docInfo
                        Column(modifier = Modifier.fillMaxSize()) {
                            // File Meta Card
                            if (info != null) {
                                ElevatedCard(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = info.fileName,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = buildString {
                                                    append(info.fileExtension.uppercase())
                                                    append(" • ")
                                                    append(info.formattedSize)
                                                    if (info.pageCount > 1) {
                                                        append(" • ${info.pageCount} стр.")
                                                    }
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }

                                        Row {
                                            IconButton(
                                                onClick = {
                                                    DocumentExtractorUtil.openDocumentWithSystem(context, documentUri)
                                                }
                                            ) {
                                                Icon(
                                                    Icons.Filled.OpenInNew,
                                                    contentDescription = "Открыть файл",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            // AI Processing Toolbar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Извлеченный текст:",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                )

                                Button(
                                    onClick = {
                                        if (currentText.isBlank()) return@Button
                                        isStructuringWithAi = true
                                        aiErrorMessage = null
                                        coroutineScope.launch {
                                            val result = GeminiOcrService.structureBatchTexts(
                                                context = context,
                                                pageTexts = listOf(currentText),
                                                mode = GeminiOcrService.TextStructureMode.STRUCTURED_NOTES
                                            )
                                            isStructuringWithAi = false
                                            if (result.isSuccess) {
                                                currentText = result.getOrNull() ?: currentText
                                                aiErrorMessage = null
                                            } else {
                                                aiErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                                            }
                                        }
                                    },
                                    enabled = !isStructuringWithAi && currentText.isNotBlank(),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    if (isStructuringWithAi) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("ИИ структурирует...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Структурировать ИИ", fontSize = 12.sp)
                                    }
                                }
                            }

                            if (aiErrorMessage != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = aiErrorMessage!!,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = currentText,
                                onValueChange = { currentText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                placeholder = {
                                    Text(
                                        if (docInfo?.isSuccess == false) docInfo?.errorMessage ?: "Текст не обнаружен"
                                        else "Извлеченный текст документа..."
                                    )
                                }
                            )
                        }
                    }
                }

                // BOTTOM ACTION BAR
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Document Text", currentText))
                                    Toast.makeText(context, "Текст скопирован", Toast.LENGTH_SHORT).show()
                                },
                                enabled = currentText.isNotBlank(),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Копия", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val info = docInfo
                                    val attachmentHeader = if (info != null) {
                                        "📎 Документ: ${info.fileName} (${info.formattedSize})\n\n"
                                    } else ""
                                    onInsertText(attachmentHeader + currentText, true, false)
                                    onDismissRequest()
                                },
                                enabled = currentText.isNotBlank(),
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Вставить", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val info = docInfo
                                    val attachmentHeader = if (info != null) {
                                        "📎 Документ: ${info.fileName} (${info.formattedSize})\n\n"
                                    } else ""
                                    onInsertText(attachmentHeader + currentText, true, true)
                                    onDismissRequest()
                                },
                                enabled = currentText.isNotBlank(),
                                modifier = Modifier.weight(1.4f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
}
