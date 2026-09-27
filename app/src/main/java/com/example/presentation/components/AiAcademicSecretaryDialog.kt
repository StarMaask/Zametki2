package com.example.presentation.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.util.AiAcademicAndSecretaryService
import com.example.util.DocxGenerator
import com.example.util.GeminiOcrService
import com.example.util.ShareExportUtil
import com.example.util.XlsxGenerator
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAcademicSecretaryDialog(
    initialNote: Note? = null,
    repository: NoteRepository? = null,
    onDismissRequest: () -> Unit,
    onInsertTextIntoNote: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var selectedRole by remember { mutableStateOf(AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) }
    var promptInput by remember { mutableStateOf("") }
    var includeNoteContext by remember { mutableStateOf(initialNote != null && initialNote.content.isNotBlank()) }

    var isLoading by remember { mutableStateOf(false) }
    var responseText by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    val hasApiKey = remember { GeminiOcrService.hasAvailableApiKey(context) }

    val professorChips = listOf(
        "Решить математическую задачу по шагам с формулами",
        "Составить подробный конспект лекции со структурой и терминами",
        "Написать реферат с введением, главами и списком литературы по ГОСТ",
        "Разработать аналитический раздел дипломной работы с расчетами",
        "Физика: подробный вывод формулы и законы сохранения",
        "Химия: уравнение реакции, механизм и стехиометрический расчет"
    )

    val secretaryChips = listOf(
        "Служебная записка о премировании сотрудника по ГОСТ Р 7.0.97-2016",
        "Заявление на ежегодный оплачиваемый отпуск с реквизитами",
        "Акт приёма-передачи материальных ценностей со сводной таблицей",
        "Договор возмездного оказания услуг с правами и обязанностями сторон",
        "Досудебная претензия о нарушении сроков поставки товара",
        "Приказ генерального директора о назначении ответственного лица"
    )

    fun executeAiRequest(customPrompt: String? = null) {
        val promptToUse = customPrompt ?: promptInput.trim()
        if (promptToUse.isBlank()) return

        if (!GeminiOcrService.hasAvailableApiKey(context)) {
            showApiKeyDialog = true
            return
        }

        isLoading = true
        errorMessage = null
        scope.launch {
            val contextText = if (includeNoteContext && initialNote != null) {
                "Название: ${initialNote.title}\nСодержание:\n${initialNote.content}"
            } else null

            val result = AiAcademicAndSecretaryService.askAssistant(
                context = context,
                role = selectedRole,
                userPrompt = promptToUse,
                contextText = contextText
            )

            isLoading = false
            if (result.isSuccess) {
                responseText = result.getOrNull() ?: ""
            } else {
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Ошибка генерации ответа"
            }
        }
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            onDismissRequest = { showApiKeyDialog = false },
            onKeySaved = { _ ->
                showApiKeyDialog = false
                executeAiRequest()
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 10.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Top Header with Role Switcher
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) Icons.Filled.School else Icons.Filled.Work,
                                contentDescription = null,
                                tint = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = selectedRole.title,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = selectedRole.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Role selector tabs
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR,
                        onClick = { selectedRole = AiAcademicAndSecretaryService.AssistantRole.PROFESSOR },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Filled.School, null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("🎓 Профессор наук", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    SegmentedButton(
                        selected = selectedRole == AiAcademicAndSecretaryService.AssistantRole.SECRETARY,
                        onClick = { selectedRole = AiAcademicAndSecretaryService.AssistantRole.SECRETARY },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Filled.Work, null, modifier = Modifier.size(16.dp)) }
                    ) {
                        Text("👔 Умный секретарь", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Central Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Quick chips horizontal scroll
                    Text(
                        text = "Быстрые шаблоны задач:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val activeChips = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) professorChips else secretaryChips
                        activeChips.forEach { chipText ->
                            SuggestionChip(
                                onClick = {
                                    promptInput = chipText
                                    executeAiRequest(chipText)
                                },
                                label = { Text(chipText, fontSize = 11.5.sp) }
                            )
                        }
                    }

                    // Input Box
                    OutlinedTextField(
                        value = promptInput,
                        onValueChange = { promptInput = it },
                        placeholder = {
                            Text(
                                if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                    "Опишите задачу по математике, физике, тему конспекта, реферата или диплома..."
                                else
                                    "Опишите вид документа: заявление, служебная записка, акт или договор..."
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4
                    )

                    // Context toggle
                    if (initialNote != null && initialNote.content.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { includeNoteContext = !includeNoteContext }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = includeNoteContext,
                                onCheckedChange = { includeNoteContext = it }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Использовать текст открытой заметки («${initialNote.title.take(24)}») как контекст",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Submit Button
                    Button(
                        onClick = { executeAiRequest() },
                        enabled = promptInput.isNotBlank() && !isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ИИ формирует развернутый ответ...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Получить развернутый ответ / решение")
                        }
                    }

                    if (errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = errorMessage!!,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    // Response Result Container
                    if (responseText.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Готовый результат:",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Row {
                                        IconButton(
                                            onClick = {
                                                clipboardManager.setText(AnnotatedString(responseText))
                                                Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                SelectionContainer {
                                    Text(
                                        text = responseText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        lineHeight = 20.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Export Bar (Shows when responseText is ready)
                if (responseText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Экспорт и сохранение ответа:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Word .docx
                        FilledTonalButton(
                            onClick = {
                                exportToDocx(context, promptInput.take(25).ifBlank { "Документ" }, responseText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Word (.docx)", fontSize = 11.5.sp)
                        }

                        // Excel .xlsx
                        FilledTonalButton(
                            onClick = {
                                exportToXlsx(context, promptInput.take(25).ifBlank { "Таблица" }, responseText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Excel (.xlsx)", fontSize = 11.5.sp)
                        }

                        // PDF
                        FilledTonalButton(
                            onClick = {
                                exportToPdf(context, promptInput.take(25).ifBlank { "Документ" }, responseText)
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("PDF (ГОСТ)", fontSize = 11.5.sp)
                        }

                        // Save as New Note
                        if (repository != null) {
                            FilledTonalButton(
                                onClick = {
                                    scope.launch {
                                        val title = promptInput.lines().firstOrNull()?.take(40)?.trim()
                                            ?.ifBlank { "Ответ ИИ: ${selectedRole.title}" } ?: "Документ ИИ"
                                        val newNote = Note(
                                            title = title,
                                            content = responseText,
                                            folder = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Учеба и Наука" else "Документы",
                                            tags = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) listOf("профессор", "наука") else listOf("секретарь", "гост")
                                        )
                                        repository.insertNote(newNote)
                                        Toast.makeText(context, "Создана новая заметка «$title»", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.NoteAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Создать заметку", fontSize = 11.5.sp)
                            }
                        }

                        // Insert into open note
                        if (onInsertTextIntoNote != null) {
                            Button(
                                onClick = {
                                    onInsertTextIntoNote(responseText)
                                    Toast.makeText(context, "Вставлено в текущую заметку", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.PostAdd, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Вставить в текст", fontSize = 11.5.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun exportToDocx(context: Context, title: String, text: String) {
    try {
        val file = DocxGenerator.generateDocxFromText(context, title, text)
        shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Открыть в Word")
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка экспорта Word: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportToXlsx(context: Context, title: String, text: String) {
    try {
        val tables = XlsxGenerator.extractTablesFromMarkdown(text)
        val file = if (tables.isNotEmpty()) {
            val firstTable = tables.first()
            XlsxGenerator.generateXlsxFile(
                context = context,
                fileName = title,
                headers = firstTable.headers,
                rows = firstTable.rows,
                tableTitle = firstTable.title ?: title
            )
        } else {
            // Create a structured 2-column table from paragraphs
            val rows = text.lines().filter { it.isNotBlank() }.mapIndexed { idx, line ->
                listOf((idx + 1).toString(), line)
            }
            XlsxGenerator.generateXlsxFile(
                context = context,
                fileName = title,
                headers = listOf("№ п/п", "Содержание"),
                rows = rows,
                tableTitle = title
            )
        }
        shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Открыть в Excel / Таблицах")
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка экспорта Excel: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportToPdf(context: Context, title: String, text: String) {
    try {
        val tempNote = Note(title = title, content = text)
        val file = ShareExportUtil.generatePdfFile(
            context = context,
            note = tempNote,
            config = com.example.util.PdfExportConfig(
                includePageNumbers = true,
                includeCorporateLetterhead = true,
                includeVerificationQr = true
            )
        )
        if (file != null) {
            shareFile(context, file, "application/pdf", "Открыть PDF")
        } else {
            Toast.makeText(context, "Не удалось сформировать PDF", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка экспорта PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, chooserTitle))
}
