package com.example.presentation.components

import android.content.Context
import android.content.Intent
import android.os.Environment
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.example.domain.model.AiAttachment
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.util.AiAcademicAndSecretaryService
import com.example.util.AiAttachmentHelper
import com.example.util.DocxGenerator
import com.example.util.GeminiOcrService
import com.example.util.ShareExportUtil
import com.example.util.XlsxGenerator
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

data class AcademicChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val attachments: List<AiAttachment> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

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
    val listState = rememberLazyListState()

    var selectedRole by remember { mutableStateOf(AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) }
    var promptInput by remember { mutableStateOf("") }
    var includeNoteContext by remember { mutableStateOf(initialNote != null && initialNote.content.isNotBlank()) }
    var pendingAttachments by remember { mutableStateOf<List<AiAttachment>>(emptyList()) }
    var isProcessingAttachment by remember { mutableStateOf(false) }

    var messages by remember { mutableStateOf<List<AcademicChatMessage>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessingAttachment = true
                val newAttachments = mutableListOf<AiAttachment>()
                for (uri in uris) {
                    val res = AiAttachmentHelper.processAttachment(context, uri)
                    if (res.isSuccess) {
                        newAttachments.add(res.getOrThrow())
                    }
                }
                pendingAttachments = pendingAttachments + newAttachments
                isProcessingAttachment = false
                if (newAttachments.isNotEmpty()) {
                    Toast.makeText(context, "Фото/сканов добавлено: ${newAttachments.size}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            scope.launch {
                isProcessingAttachment = true
                val newAttachments = mutableListOf<AiAttachment>()
                for (uri in uris) {
                    val res = AiAttachmentHelper.processAttachment(context, uri)
                    if (res.isSuccess) {
                        newAttachments.add(res.getOrThrow())
                    }
                }
                pendingAttachments = pendingAttachments + newAttachments
                isProcessingAttachment = false
                if (newAttachments.isNotEmpty()) {
                    Toast.makeText(context, "Документов загружено: ${newAttachments.size}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val professorChips = listOf(
        "🎓 Реферат по ГОСТ (титульный лист, содержание, сокращения, разделы, список источников)",
        "📘 Курсовая работа (полная структура с методикой, расчетами и таблицами)",
        "📑 Научный отчет по ГОСТ 7.32 с аннотацией, выводами и списком ВАК",
        "📐 Решить математическую задачу по шагам с формулами и выкладками",
        "🔬 Физика / Химия: подробный вывод формулы, законы и размерности",
        "📝 Подробный академический конспект со структурой и терминами"
    )

    val secretaryChips = listOf(
        "📋 Служебная записка по ГОСТ Р 7.0.97-2016 со всеми реквизитами",
        "📄 Заявление на отпуск / компенсацию с визами и согласованием",
        "📑 Акт приёма-передачи материальных ценностей со сводной таблицей",
        "📜 Договор возмездного оказания услуг с правами, обязанностями и штрафами",
        "⚖️ Досудебная претензия о нарушении сроков поставки и неустойке",
        "🖋️ Приказ руководителя с преамбулой и персональной ответственностью"
    )

    val refinementChips = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) {
        listOf(
            "⏩ Продолжить с места обрыва / Дописать следующие разделы",
            "Добавь титульный лист, содержание и перечень сокращений по ГОСТ",
            "Оформи список источников строго по ГОСТ 7.0.5-2008",
            "Распиши подробнее математические выкладки и формулы",
            "Добавь сравнительную расчетную таблицу данных",
            "Разверни заключение и выводы по всем задачам"
        )
    } else {
        listOf(
            "⏩ Продолжить с места обрыва / Дописать следующие разделы",
            "Оформи строго по ГОСТ Р 7.0.97-2016 со всеми реквизитами",
            "Добавь спецификацию в виде расчетной таблицы",
            "Добавь пункт об ответственности сторон и неустойке",
            "Сделай формулировки более строгими и юридически выверенными",
            "Добавь блок подписи и печати организации"
        )
    }

    fun sendMessage(customPrompt: String? = null) {
        val userText = customPrompt ?: promptInput.trim()
        val textToSend = when {
            userText.isNotBlank() -> userText
            pendingAttachments.isNotEmpty() && selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                "Внимательно изучи все прикрепленные материалы, фотографии, формулы или документы. Если это условия задач — подробно реши каждую задачу по всем шагам со всеми формулами, выкладками, пояснениями и проверкой. Если это конспект или учебный материал — составь глубокий академический конспект или реферат."
            pendingAttachments.isNotEmpty() && selectedRole == AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                "Внимательно изучи прикрепленный документ или скан. Проверь его структуру и оформление на соответствие ГОСТ Р 7.0.97-2016, выяви ошибки или неточности и составь идеальный чистовик документа с правильными реквизитами."
            else -> return
        }

        if (!GeminiOcrService.hasAvailableApiKey(context)) {
            showApiKeyDialog = true
            return
        }

        val currentAttachments = pendingAttachments.toList()
        val userMessage = AcademicChatMessage(
            isUser = true,
            text = textToSend,
            attachments = currentAttachments
        )

        val updatedMessages = messages + userMessage
        messages = updatedMessages
        promptInput = ""
        pendingAttachments = emptyList()
        errorMessage = null
        isLoading = true

        scope.launch {
            if (updatedMessages.isNotEmpty()) {
                listState.animateScrollToItem(updatedMessages.size - 1)
            }

            val contextText = if (includeNoteContext && initialNote != null) {
                "Заголовок: ${initialNote.title}\n\nТекст заметки:\n${initialNote.content}"
            } else null

            val dialogueHistory = updatedMessages.dropLast(1).map {
                AiAcademicAndSecretaryService.DialogueMessage(
                    role = if (it.isUser) "user" else "model",
                    text = it.text,
                    attachments = it.attachments
                )
            }

            val result = AiAcademicAndSecretaryService.askAssistantDialogue(
                context = context,
                role = selectedRole,
                history = dialogueHistory,
                userPrompt = textToSend,
                contextText = contextText,
                attachments = currentAttachments
            )

            isLoading = false
            if (result.isSuccess) {
                val modelText = result.getOrNull() ?: ""
                val modelMessage = AcademicChatMessage(
                    isUser = false,
                    text = modelText
                )
                messages = messages + modelMessage
                listState.animateScrollToItem(messages.size - 1)
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
                sendMessage()
            }
        )
    }

    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        if (window != null) {
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        onDispose {}
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
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                ) {
                    // Top Header with Role Switcher & Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                            MaterialTheme.colorScheme.primaryContainer
                                        else
                                            MaterialTheme.colorScheme.secondaryContainer
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) Icons.Filled.School else Icons.Filled.Work,
                                    contentDescription = null,
                                    tint = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = selectedRole.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (messages.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        messages = emptyList()
                                        errorMessage = null
                                        pendingAttachments = emptyList()
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = "Новый диалог", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = onDismissRequest, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Role selector tabs
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR,
                            onClick = { selectedRole = AiAcademicAndSecretaryService.AssistantRole.PROFESSOR },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            icon = { Icon(Icons.Filled.School, null, modifier = Modifier.size(15.dp)) }
                        ) {
                            Text("🎓 Профессор наук", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                        SegmentedButton(
                            selected = selectedRole == AiAcademicAndSecretaryService.AssistantRole.SECRETARY,
                            onClick = { selectedRole = AiAcademicAndSecretaryService.AssistantRole.SECRETARY },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            icon = { Icon(Icons.Filled.Work, null, modifier = Modifier.size(15.dp)) }
                        ) {
                            Text("💼 Умный секретарь", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // CONTENT AREA: Initial Setup Screen VS Dialogue Screen
                    if (messages.isEmpty()) {
                        // ==========================================
                        // INITIAL SCREEN: TASK SETUP (ПОСТАНОВКА ЗАДАЧИ)
                        // Input field is prominently near the top and NOT pushed down by long lists
                        // Quick templates are in a clean DROPDOWN LIST as requested by user
                        // ==========================================
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Info card
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                modifier = Modifier.fillMaxWidth()
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
                                            text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                                "Постановка научной задачи Профессору"
                                            else
                                                "Постановка задачи Секретарю по ГОСТ",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                            "Решит сложную задачу по шагам с формулами, напишет конспект или реферат. Прикрепите фото или скан задания."
                                        else
                                            "Составит официальное заявление, служебную записку, договор или акт с реквизитами по ГОСТ Р 7.0.97-2016.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // QUICK TEMPLATES DROPDOWN (ВЫПАДАЮЩИЙ СПИСОК ШАБЛОНОВ)
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedCard(
                                    onClick = { templateDropdownExpanded = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Filled.PlaylistAddCheck,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Быстрые шаблоны заданий",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = "Нажмите, чтобы выбрать из выпадающего списка...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            Icons.Filled.ArrowDropDown,
                                            contentDescription = "Раскрыть список",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = templateDropdownExpanded,
                                    onDismissRequest = { templateDropdownExpanded = false },
                                    modifier = Modifier
                                        .fillMaxWidth(0.92f)
                                        .heightIn(max = 350.dp)
                                ) {
                                    val currentChips = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                        professorChips
                                    else
                                        secretaryChips

                                    currentChips.forEach { chipText ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = chipText,
                                                    style = MaterialTheme.typography.bodyMedium
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) Icons.Filled.School else Icons.Filled.Description,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            onClick = {
                                                promptInput = chipText
                                                templateDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }

                            // TASK DESCRIPTION TEXT FIELD (Поле описания задачи)
                            Text(
                                text = "Описание задачи / текст запроса:",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                placeholder = {
                                    Text(
                                        text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                            "Опишите задачу (математика, физика, химия, алгоритмы), тему конспекта или реферата. Прикрепите фото или скан..."
                                        else
                                            "Опишите вид документа: заявление, служебная записка, акт приёма-передачи, договор. Укажите реквизиты или прикрепите скан...",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = 220.dp),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // ATTACHMENT ACTIONS & CHIPS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Фото / Скан", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        documentPickerLauncher.launch(
                                            arrayOf(
                                                "application/pdf",
                                                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                "text/*",
                                                "application/msword"
                                            )
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Документ", fontSize = 12.sp)
                                }
                            }

                            // Processing indicator for attachments
                            if (isProcessingAttachment) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Распознавание и считывание документа/фото...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // Pending Attachments chips
                            if (pendingAttachments.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    pendingAttachments.forEach { att ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(
                                                    imageVector = when {
                                                        att.isImage -> Icons.Filled.Image
                                                        att.isPdf -> Icons.Filled.PictureAsPdf
                                                        att.name.endsWith(".docx", ignoreCase = true) -> Icons.Filled.Article
                                                        else -> Icons.Filled.Description
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(14.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = att.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 140.dp)
                                                )
                                                IconButton(
                                                    onClick = { pendingAttachments = pendingAttachments - att },
                                                    modifier = Modifier.size(18.dp)
                                                ) {
                                                    Icon(Icons.Filled.Close, contentDescription = "Удалить", modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Open note context toggle
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
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Использовать текст открытой заметки («${initialNote.title.ifBlank { "Без названия" }}»)",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // PRIMARY ACTION BUTTON (Запустить)
                            Button(
                                onClick = { sendMessage() },
                                enabled = (promptInput.isNotBlank() || pendingAttachments.isNotEmpty()) && !isLoading && !isProcessingAttachment,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.secondary
                                )
                            ) {
                                Icon(
                                    if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) Icons.Filled.School else Icons.Filled.Send,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                        "Запустить подробное решение задачи"
                                    else
                                        "Сформировать документ по ГОСТ",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // ==========================================
                        // DIALOGUE SCREEN: CHAT WITH MESSAGES, CONTINUING CONVERSATION & REFINEMENTS
                        // ==========================================
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                if (msg.isUser) {
                                    UserMessageBubble(msg = msg)
                                } else {
                                    ModelMessageCard(
                                        message = msg,
                                        context = context,
                                        clipboardManager = clipboardManager,
                                        scope = scope,
                                        selectedRole = selectedRole,
                                        repository = repository,
                                        onInsertTextIntoNote = onInsertTextIntoNote,
                                        onDismissRequest = onDismissRequest,
                                        onContinueGeneration = {
                                            sendMessage("Продолжи составление документа строго с того места, где он прервался. Напиши оставшиеся разделы, заключение, список использованных источников и приложения по ГОСТ.")
                                        }
                                    )
                                }
                            }

                            // Loading state
                            if (isLoading) {
                                item {
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                strokeWidth = 2.5.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(14.dp))
                                            Column {
                                                Text(
                                                    text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                                                        "Профессор анализирует задачу и рассчитывает выкладки..."
                                                    else
                                                        "Секретарь формулирует и оформляет документ по ГОСТ...",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Формирование развернутого ответа со всеми деталями",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Error state
                            if (errorMessage != null) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                Icons.Filled.ErrorOutline,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Text(
                                                text = errorMessage!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        }
                                    }
                                }
                            }

                            // Refinement suggestions chips inside scrollable history right below messages
                            if (!isLoading && messages.isNotEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, bottom = 4.dp)
                                    ) {
                                        Text(
                                            text = "💡 Добавить нюанс или доработать документ:",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            refinementChips.forEach { chip ->
                                                SuggestionChip(
                                                    onClick = { sendMessage(chip) },
                                                    label = { Text(chip, fontSize = 11.5.sp) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // PINNED BOTTOM COMPOSER DOCK (Всегда на виду внизу экрана над панелью навигации и клавиатурой)
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                            tonalElevation = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                // Pending Attachments Bar in dialogue mode
                                if (pendingAttachments.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        pendingAttachments.forEach { att ->
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = if (att.isImage) Icons.Filled.Image else Icons.Filled.Description,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        text = att.name,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.widthIn(max = 120.dp)
                                                    )
                                                    IconButton(
                                                        onClick = { pendingAttachments = pendingAttachments - att },
                                                        modifier = Modifier.size(16.dp)
                                                    ) {
                                                        Icon(Icons.Filled.Close, contentDescription = "Удалить", modifier = Modifier.size(12.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Processing indicator for attachments in dialogue mode
                                if (isProcessingAttachment) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Считывание документа/фото...",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                }

                                // Composer Row: Attachment + Input Field + Send Button
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Attachment menu button
                                    Box {
                                        IconButton(
                                            onClick = { showAttachmentMenu = true },
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.AttachFile,
                                                contentDescription = "Прикрепить фото или документ",
                                                tint = if (pendingAttachments.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = showAttachmentMenu,
                                            onDismissRequest = { showAttachmentMenu = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("📷 Фото / Скан задания") },
                                                leadingIcon = { Icon(Icons.Filled.AddPhotoAlternate, null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }
                                            )
                                            DropdownMenuItem(
                                                text = { Text("📄 Документ (PDF, Word, Текст)") },
                                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                                onClick = {
                                                    showAttachmentMenu = false
                                                    documentPickerLauncher.launch(
                                                        arrayOf(
                                                            "application/pdf",
                                                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                                            "text/*",
                                                            "application/msword"
                                                        )
                                                    )
                                                }
                                            )
                                        }
                                    }

                                    // Text input field
                                    OutlinedTextField(
                                        value = promptInput,
                                        onValueChange = { promptInput = it },
                                        placeholder = {
                                            Text(
                                                text = if (pendingAttachments.isNotEmpty())
                                                    "Уточнение к материалам..."
                                                else
                                                    "Продолжить диалог, добавить нюанс...",
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 4.dp),
                                        maxLines = 3,
                                        shape = RoundedCornerShape(12.dp)
                                    )

                                    // Send Button
                                    FilledIconButton(
                                        onClick = { sendMessage() },
                                        enabled = (promptInput.isNotBlank() || pendingAttachments.isNotEmpty()) && !isLoading && !isProcessingAttachment,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Send,
                                            contentDescription = "Отправить",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserMessageBubble(msg: AcademicChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (msg.attachments.isNotEmpty()) {
                    Column(
                        modifier = Modifier.padding(bottom = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        msg.attachments.forEach { att ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        if (att.isImage) Icons.Filled.Image else Icons.Filled.Description,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Text(
                                        text = att.name,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                Text(
                    text = msg.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun ModelMessageCard(
    message: AcademicChatMessage,
    context: Context,
    clipboardManager: androidx.compose.ui.platform.ClipboardManager,
    scope: kotlinx.coroutines.CoroutineScope,
    selectedRole: AiAcademicAndSecretaryService.AssistantRole,
    repository: NoteRepository?,
    onInsertTextIntoNote: ((String) -> Unit)?,
    onDismissRequest: () -> Unit,
    onContinueGeneration: (() -> Unit)? = null
) {
    var isSavedInApp by remember { mutableStateOf(false) }

    val baseTitle = remember(message.text) {
        message.text.lines().firstOrNull { it.isNotBlank() }?.take(40)?.trim()
            ?.removePrefix("#")?.removePrefix("*")?.trim()
            ?: (if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Научный_материал" else "Официальный_документ")
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header with badge, quick save button and copy button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) Icons.Filled.School else Icons.Filled.Work,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Ответ Профессора" else "Документ Секретаря",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Quick Save Button in header
                    if (repository != null) {
                        FilledTonalButton(
                            onClick = {
                                scope.launch {
                                    val newNote = Note(
                                        title = baseTitle,
                                        content = message.text,
                                        folder = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Учеба и Наука" else "Документы",
                                        tags = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) listOf("профессор", "наука") else listOf("секретарь", "гост")
                                    )
                                    repository.insertNote(newNote)
                                    isSavedInApp = true
                                    Toast.makeText(context, "Сохранено в заметку «$baseTitle»!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Icon(
                                if (isSavedInApp) Icons.Filled.Check else Icons.Filled.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isSavedInApp) "Сохранено" else "Сохранить", fontSize = 11.sp)
                        }
                    }

                    // Copy Button
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(message.text))
                            Toast.makeText(context, "Скопировано в буфер обмена", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать", modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Formatted Response Text with real headings, tables and styled markdown
            SelectionContainer {
                MarkdownRenderer(
                    markdownText = message.text,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Quick Continue Button to expand / complete the document without missing parts
            if (onContinueGeneration != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onContinueGeneration,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.FastForward, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "⏩ Продолжить составление / Дописать следующие разделы",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Spacer(modifier = Modifier.height(10.dp))

            // PROMINENT SAVE & EXPORT SECTION
            Text(
                text = "💾 Сохранение и экспорт готового результата:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // 1. SAVE DIRECTLY TO APP NOTES (Кнопка сохранения информации в приложении)
            if (repository != null) {
                Button(
                    onClick = {
                        scope.launch {
                            val newNote = Note(
                                title = baseTitle,
                                content = message.text,
                                folder = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Учеба и Наука" else "Документы",
                                tags = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) listOf("профессор", "наука") else listOf("секретарь", "гост")
                            )
                            repository.insertNote(newNote)
                            isSavedInApp = true
                            Toast.makeText(context, "Успешно сохранено в заметки: «$baseTitle»!", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSavedInApp) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Icon(
                        if (isSavedInApp) Icons.Filled.CheckCircle else Icons.Filled.Save,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isSavedInApp) "✓ Заметка уже сохранена в приложении" else "Сохранить в заметки приложения",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 2. INSERT INTO OPEN NOTE (if opened from note editor)
            if (onInsertTextIntoNote != null) {
                FilledTonalButton(
                    onClick = {
                        onInsertTextIntoNote(message.text)
                        Toast.makeText(context, "Вставлено в текущую открытую заметку!", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Вставить в открытую заметку")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. EXPORT BUTTONS ROW: WORD (.DOCX / .DOC), EXCEL, PDF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Word .docx
                FilledTonalButton(
                    onClick = { exportToDocx(context, baseTitle, message.text) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Word (.docx)", fontSize = 12.sp)
                }

                // Word .doc (RTF)
                FilledTonalButton(
                    onClick = { exportToRtfDoc(context, baseTitle, message.text) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Article, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Word (.doc)", fontSize = 12.sp)
                }

                // Excel .xlsx
                FilledTonalButton(
                    onClick = { exportToXlsx(context, baseTitle, message.text) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.TableChart, null, modifier = Modifier.size(16.dp), tint = Color(0xFF2E7D32))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Excel (.xlsx)", fontSize = 12.sp)
                }

                // PDF (ГОСТ)
                FilledTonalButton(
                    onClick = { exportToPdf(context, baseTitle, message.text) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PDF (ГОСТ)", fontSize = 12.sp)
                }

                // Direct Save to Downloads folder
                FilledTonalButton(
                    onClick = { saveDocxDirectlyToDownloads(context, baseTitle, message.text) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("В Загрузки", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun exportToDocx(context: Context, title: String, text: String) {
    try {
        val file = DocxGenerator.generateDocxFromText(context, title, text)
        shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Открыть в Word (.docx)")
    } catch (e: Exception) {
        val saved = saveDirectlyToDownloads(context, title, "docx", text)
        Toast.makeText(context, "Файл Word сохранен в Загрузки: ${saved?.name}", Toast.LENGTH_LONG).show()
    }
}

private fun exportToRtfDoc(context: Context, title: String, text: String) {
    try {
        val file = DocxGenerator.generateRtfFromText(context, title, text)
        shareFile(context, file, "application/msword", "Открыть в Word (.doc)")
    } catch (e: Exception) {
        val saved = saveDirectlyToDownloads(context, title, "doc", text)
        Toast.makeText(context, "Файл Word .doc сохранен в Загрузки: ${saved?.name}", Toast.LENGTH_LONG).show()
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
        Toast.makeText(context, "Экспорт Excel: ${e.message}", Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, "Экспорт PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveDocxDirectlyToDownloads(context: Context, title: String, text: String) {
    try {
        val file = DocxGenerator.generateDocxFromText(context, title, text)
        val saved = copyFileToDownloads(context, file)
        Toast.makeText(context, "Документ Word сохранен в Загрузки: ${saved?.name ?: file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка сохранения в Загрузки: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun copyFileToDownloads(context: Context, sourceFile: File): File? {
    return try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val destFile = File(downloadsDir, sourceFile.name)
        sourceFile.copyTo(destFile, overwrite = true)
        destFile
    } catch (e: Exception) {
        null
    }
}

private fun saveDirectlyToDownloads(context: Context, title: String, ext: String, text: String): File? {
    return try {
        val cleanTitle = title.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_\\-]"), "_").trim('_').take(35).ifBlank { "document" }
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        downloadsDir.mkdirs()
        val destFile = File(downloadsDir, "${cleanTitle}_${System.currentTimeMillis()}.$ext")
        destFile.writeText(text, Charsets.UTF_8)
        destFile
    } catch (e: Exception) {
        null
    }
}

private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    try {
        val authority = "${context.packageName}.provider"
        val uri = try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (_: Exception) {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val resInfoList = context.packageManager.queryIntentActivities(chooser, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (e: Exception) {
        // Fallback: save to downloads
        val target = copyFileToDownloads(context, file)
        Toast.makeText(context, "Файл сохранен в Загрузки: ${target?.name ?: file.name}", Toast.LENGTH_LONG).show()
    }
}
