package com.example.presentation.components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Environment
import android.speech.RecognizerIntent
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.WindowCompat
import com.example.domain.model.AiAttachment
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.util.AiAcademicAndSecretaryService
import com.example.util.AiAttachmentHelper
import com.example.util.AiChatSessionManager
import com.example.util.DocxGenerator
import com.example.util.FormulaSanitizer
import com.example.util.GeminiOcrService
import com.example.util.ShareExportUtil
import com.example.util.TableOfContentsExtractor
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

    val savedSession = remember {
        AiChatSessionManager.loadSession(context)
    }

    var currentSessionId by remember {
        mutableStateOf(savedSession?.id ?: UUID.randomUUID().toString())
    }

    var showHistoryBottomSheet by remember { mutableStateOf(false) }
    var lastFailedRequest by remember { mutableStateOf<Pair<String, List<AiAttachment>>?>(null) }

    var selectedRole by remember {
        mutableStateOf(savedSession?.role ?: AiAcademicAndSecretaryService.AssistantRole.GENERAL)
    }
    var promptInput by remember { mutableStateOf("") }
    var includeNoteContext by remember { mutableStateOf(initialNote != null && initialNote.content.isNotBlank()) }
    var pendingAttachments by remember { mutableStateOf<List<AiAttachment>>(emptyList()) }
    var isProcessingAttachment by remember { mutableStateOf(false) }

    var messages by remember {
        mutableStateOf(savedSession?.messages ?: emptyList())
    }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var templateDropdownExpanded by remember { mutableStateOf(false) }
    var showTitlePageDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var requisites by remember {
        mutableStateOf(
            savedSession?.requisites ?: TitlePageRequisites(
                topic = initialNote?.title ?: ""
            )
        )
    }
    var customTitlePageText by remember { mutableStateOf(savedSession?.customTitlePageText) }

    val sessionImageUris = remember(messages, pendingAttachments, initialNote) {
        val uris = mutableListOf<String>()
        if (initialNote != null) {
            uris.addAll(ShareExportUtil.parseImageUris(initialNote.imageUrisJson))
        }
        messages.forEach { m ->
            m.attachments.filter { it.isImage }.forEach { uris.add(it.uri.toString()) }
        }
        pendingAttachments.filter { it.isImage }.forEach { uris.add(it.uri.toString()) }
        uris.distinct()
    }

    LaunchedEffect(messages, selectedRole, requisites, customTitlePageText, currentSessionId) {
        if (messages.isNotEmpty() || customTitlePageText != null) {
            val title = AiChatSessionManager.generateTitle(messages, requisites, selectedRole)
            AiChatSessionManager.saveSession(
                context = context,
                session = AiChatSessionManager.SavedAiSession(
                    id = currentSessionId,
                    title = title,
                    role = selectedRole,
                    messages = messages,
                    requisites = requisites,
                    customTitlePageText = customTitlePageText,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val spokenText = matches?.firstOrNull()?.trim()
            if (!spokenText.isNullOrBlank()) {
                promptInput = if (promptInput.isBlank()) spokenText else "$promptInput $spokenText"
                Toast.makeText(context, "Голос распознан!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите задачу — голос преобразуется в текст запроса...")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Распознавание речи недоступно на данном устройстве", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Требуется доступ к микрофону для голосового ввода", Toast.LENGTH_SHORT).show()
        }
    }

    fun startVoiceInput() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите задачу — голос преобразуется в текст запроса...")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Распознавание речи недоступно на данном устройстве", Toast.LENGTH_SHORT).show()
            }
        } else {
            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun assembleUnifiedDocument(): String {
        val modelTexts = messages.filter { !it.isUser }.map { it.text.trim() }.filter { it.isNotBlank() }
        if (modelTexts.isEmpty()) return ""

        val rawCombined = if (modelTexts.size == 1) {
            modelTexts.first()
        } else {
            val sb = StringBuilder()
            modelTexts.forEachIndexed { index, part ->
                if (index == 0) {
                    sb.append(part)
                } else {
                    val lines = part.lines()
                    val filteredLines = mutableListOf<String>()
                    var skipHeading = true
                    for (line in lines) {
                        val trimmedL = line.trim()
                        val upper = trimmedL.uppercase().removePrefix("#").trim()
                        if (skipHeading && (trimmedL.startsWith("# ") || upper.startsWith("ТЕМА:") || upper.startsWith("РЕФЕРАТ") || upper.startsWith("КУРСОВАЯ") || upper.contains("РАЗРЫВ СТРАНИЦЫ"))) {
                            continue
                        }
                        skipHeading = false
                        filteredLines.add(line)
                    }
                    val cleanContinuation = filteredLines.joinToString("\n").trim()
                    if (cleanContinuation.isNotBlank()) {
                        val firstLineUpper = cleanContinuation.lines().firstOrNull { it.isNotBlank() }?.trim()?.uppercase()?.removePrefix("#")?.trim() ?: ""
                        val alreadyBreaks = DocxGenerator.isMajorAcademicSection(firstLineUpper) || firstLineUpper.startsWith("ПРИЛОЖЕНИЕ")
                        if (!alreadyBreaks) {
                            sb.append("\n\n--- РАЗРЫВ СТРАНИЦЫ ---\n")
                        } else {
                            sb.append("\n\n")
                        }
                        sb.append(cleanContinuation)
                    }
                }
            }
            sb.toString()
        }

        val finalText = if (customTitlePageText != null) {
            val titleText = customTitlePageText!!.trim()
            val lines = rawCombined.lines()
            val (existingTitleInfo, titleEndIdx) = DocxGenerator.tryExtractTitlePage(lines)
            if (existingTitleInfo != null && titleEndIdx > 0 && titleEndIdx < lines.size) {
                titleText + "\n\n" + lines.subList(titleEndIdx, lines.size).joinToString("\n").trim()
            } else {
                titleText + "\n\n" + rawCombined
            }
        } else {
            rawCombined
        }

        val cleaned = DocxGenerator.cleanAcademicTextAndFormulas(finalText)
        val normalizedBreaks = cleaned
            .replace(Regex("""(?m)(^\s*---\s*РАЗРЫВ\s*СТРАНИЦЫ\s*---\s*[\r\n]+){2,}"""), "--- РАЗРЫВ СТРАНИЦЫ ---\n\n")
            .replace(Regex("""(?m)^\s*---\s*РАЗРЫВ\s*СТРАНИЦЫ\s*---\s*[\r\n]+(?=(?:#+\s*)?(?:ВВЕДЕНИЕ|СОДЕРЖАНИЕ|ОГЛАВЛЕНИЕ|ПРИЛОЖЕНИЕ))""", RegexOption.IGNORE_CASE), "")

        var docWithAppendices = normalizedBreaks
        val linesAfterToc = docWithAppendices.lines().drop(15)
        val hasAppendicesInBody = linesAfterToc.any { line ->
            val u = line.trim().uppercase().removePrefix("#").trim()
            u.startsWith("ПРИЛОЖЕНИЕ А") || u.startsWith("ПРИЛОЖЕНИЕ 1") || (u.startsWith("ПРИЛОЖЕНИЕ ") && !line.contains("..."))
        }
        val mentionsAppendices = docWithAppendices.contains("ПРИЛОЖЕНИЕ", ignoreCase = true) || docWithAppendices.contains("ПРИЛОЖЕНИЯ", ignoreCase = true)

        if (!hasAppendicesInBody && mentionsAppendices) {
            val appendixBlock = buildString {
                append("\n\n--- РАЗРЫВ СТРАНИЦЫ ---\n\n")
                append("# ПРИЛОЖЕНИЕ А\n")
                append("## Структурно-логическая схема и модель исследования\n\n")
                append("[Рисунок А.1 — Структурно-логическая схема и взаимосвязи параметров исследования: Входные параметры -> Модуль аналитической обработки -> Структурный синтез -> Результирующие показатели]\n\n")
                append("--- РАЗРЫВ СТРАНИЦЫ ---\n\n")
                append("# ПРИЛОЖЕНИЕ Б\n")
                append("## Блок-схема алгоритма реализации и практического применения\n\n")
                append("[Рисунок Б.1 — Блок-схема аналитического алгоритма: Постановка задачи -> Сбор и верификация параметров -> Итерационные вычисления -> Формирование итогового отчета]\n")
            }
            docWithAppendices += appendixBlock
        }

        return TableOfContentsExtractor.synchronizeDocumentToc(docWithAppendices)
    }

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

    val generalChips = listOf(
        "📐 Реши задачу по математике / физике с подробным объяснением каждого шага",
        "📚 Помоги составить план и написать сочинение / доклад к уроку",
        "💡 Объясни простыми словами сложную тему из учебника (с примерами)",
        "🇬🇧 Разбери правила, грамматику и переведи текст по английскому",
        "🧪 Объясни формулы, физические законы или химическую реакцию",
        "🧠 Составь 5 вопросов для самопроверки и краткую шпаргалку к параграфу",
        "💻 Напиши и подробно прокомментируй программный код / алгоритм",
        "📝 Составь пошаговый план подготовки к экзамену или контрольной",
        "🔍 Выдели главные тезисы, термины и выводы из текста",
        "❓ Ответь на вопрос из домашнего задания с подробностями"
    )

    val professorChips = listOf(
        "📐 Подробное решение задачи по шагам с формулами, выкладками и размерностями",
        "🎓 Школьный доклад / Реферат по ГОСТ с оглавлением, введением и выводами",
        "📘 Полная курсовая работа (с методикой, расчетами, кейсами и таблицами)",
        "📑 Научный отчет по ГОСТ 7.32 с аннотацией, выводами и списком литературы",
        "🔬 Физика / Химия / Биология: исчерпывающий вывод законов и моделей",
        "📝 Развернутый конспект лекции или урока со структурой и терминами"
    )

    val secretaryChips = listOf(
        "📋 Служебная записка по ГОСТ Р 7.0.97-2016 со всеми реквизитами",
        "📄 Заявление на отпуск / компенсацию с визами и согласованием",
        "📑 Акт приёма-передачи материальных ценностей со сводной таблицей",
        "📜 Договор возмездного оказания услуг с правами, обязанностями и штрафами",
        "⚖️ Досудебная претензия о нарушении сроков поставки и неустойке",
        "🖋️ Приказ руководителя с преамбулой и персональной ответственностью"
    )

    val editorChips = listOf(
        "✍️ Исправь все орфографические, пунктуационные и речевые ошибки",
        "✂️ Сократи текст, убери воду и канцеляризмы, сохранив смысл",
        "🎯 Сделай краткое структурированное саммари (выжимку) по пунктам",
        "🚀 Перепиши в живом, вовлекающем стиле для статьи или блога",
        "👔 Переведи текст в строгий, убедительный деловой стиль"
    )

    val refinementChips = when (selectedRole) {
        AiAcademicAndSecretaryService.AssistantRole.GENERAL -> listOf(
            "💡 Объясни еще проще и нагляднее",
            "🔍 Распиши подробнее с деталями и примерами",
            "💻 Покажи практический пример реализации в коде",
            "📋 Оформи ответ в виде наглядной таблицы",
            "❓ А какие есть альтернативные мнения и подходы?"
        )
        AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> listOf(
            "✂️ Сделай еще лаконичнее и короче",
            "✍️ Предложи 3 альтернативных варианта заголовка",
            "🎯 Выдели ключевые мысли жирным шрифтом",
            "👔 Переведи в более деловой тон"
        )
        AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> listOf(
            "📚 Сделать работу в 2-3 раза объемнее и детальнее (углубить все разделы по ГОСТ)",
            "⏩ Продолжить составление / Дописать следующие разделы работы",
            "📖 Расширить теоретический раздел (добавить научные школы, концепции ученых)",
            "🔬 Расписать формулы подробнее (с пошаговым выводом и размерностями СИ)",
            "📊 Добавить большую сравнительную аналитическую таблицу данных",
            "⚙️ Углубить практическую часть (добавить численные расчеты и практические кейсы)",
            "🎓 Расширить список источников до 25 научных публикаций ВАК по ГОСТ"
        )
        AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> listOf(
            "⏩ Продолжить с места обрыва / Дописать следующие разделы",
            "Оформи строго по ГОСТ Р 7.0.97-2016 со всеми реквизитами",
            "Добавь спецификацию в виде расчетной таблицы",
            "Добавь пункт об ответственности сторон и неустойке",
            "Сделай формулировки более строгими и юридически выверенными",
            "Добавь блок подписи и печати организации"
        )
    }

    fun sendMessage(
        customPrompt: String? = null,
        isRetry: Boolean = false,
        explicitAttachments: List<AiAttachment>? = null
    ) {
        val userText = customPrompt ?: promptInput.trim()
        val textToSend = when {
            userText.isNotBlank() -> userText
            (explicitAttachments?.isNotEmpty() == true || pendingAttachments.isNotEmpty()) && selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                "Внимательно изучи все прикрепленные материалы, фотографии, формулы или документы. Если это условия задач — подробно реши каждую задачу по всем шагам со всеми формулами, выкладками, пояснениями и проверкой. Если это конспект или учебный материал — составь глубокий академический конспект или реферат."
            (explicitAttachments?.isNotEmpty() == true || pendingAttachments.isNotEmpty()) && selectedRole == AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                "Внимательно изучи прикрепленный документ или скан. Проверь его структуру и оформление на соответствие ГОСТ Р 7.0.97-2016, выяви ошибки или неточности и составь идеальный чистовик документа с правильными реквизитами."
            else -> return
        }

        if (!GeminiOcrService.hasAvailableApiKey(context)) {
            showApiKeyDialog = true
            return
        }

        val currentAttachments = explicitAttachments ?: pendingAttachments.toList()
        val userMessage = AcademicChatMessage(
            isUser = true,
            text = textToSend,
            attachments = currentAttachments
        )

        val updatedMessages = if (isRetry && messages.isNotEmpty() && messages.last().isUser) {
            messages
        } else {
            messages + userMessage
        }

        messages = updatedMessages
        if (!isRetry) {
            promptInput = ""
            pendingAttachments = emptyList()
        }
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
                lastFailedRequest = null
                listState.animateScrollToItem(messages.size - 1)
            } else {
                lastFailedRequest = Pair(textToSend, currentAttachments)
                errorMessage = result.exceptionOrNull()?.localizedMessage ?: "Ошибка генерации ответа. Проверьте интернет или API-ключ."
            }
        }
    }

    fun retryLastRequest() {
        val failed = lastFailedRequest
        if (failed != null) {
            sendMessage(customPrompt = failed.first, isRetry = true, explicitAttachments = failed.second)
        } else {
            val lastUser = messages.lastOrNull { it.isUser }
            if (lastUser != null) {
                sendMessage(customPrompt = lastUser.text, isRetry = true, explicitAttachments = lastUser.attachments)
            }
        }
    }

    fun resumeLastGeneration() {
        val resumePrompt = "Пожалуйста, продолжи составление документа строго с места, где ты остановился, сохраняя глубокий академический стиль, точность и структуру."
        sendMessage(customPrompt = resumePrompt)
    }

    fun editLastRequest() {
        val failed = lastFailedRequest
        val textToEdit = failed?.first ?: messages.lastOrNull { it.isUser }?.text ?: ""
        if (textToEdit.isNotBlank()) {
            promptInput = textToEdit
            if (failed?.second != null && failed.second.isNotEmpty()) {
                pendingAttachments = failed.second
            }
        }
        if (messages.lastOrNull()?.isUser == true) {
            messages = messages.dropLast(1)
        }
        errorMessage = null
        lastFailedRequest = null
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

    if (showHistoryBottomSheet) {
        AiChatHistoryBottomSheet(
            currentSessionId = currentSessionId,
            onDismissRequest = { showHistoryBottomSheet = false },
            onSelectSession = { session ->
                currentSessionId = session.id
                selectedRole = session.role
                messages = session.messages
                requisites = session.requisites
                customTitlePageText = session.customTitlePageText
                errorMessage = null
                lastFailedRequest = null
                pendingAttachments = emptyList()
                showHistoryBottomSheet = false
            },
            onStartNewSession = {
                currentSessionId = UUID.randomUUID().toString()
                messages = emptyList()
                errorMessage = null
                lastFailedRequest = null
                pendingAttachments = emptyList()
                customTitlePageText = null
                requisites = TitlePageRequisites(topic = initialNote?.title ?: "")
                AiChatSessionManager.clearActiveSession(context)
                showHistoryBottomSheet = false
                Toast.makeText(context, "Начат новый диалог", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showTitlePageDialog) {
        TitlePageRequisitesDialog(
            initialRequisites = requisites,
            onDismissRequest = { showTitlePageDialog = false },
            onApply = { newReq, formattedTitlePage ->
                requisites = newReq
                customTitlePageText = formattedTitlePage
                showTitlePageDialog = false
                if (promptInput.isBlank() && newReq.topic.isNotBlank()) {
                    promptInput = "Напиши ${newReq.docType.lowercase()} на тему «${newReq.topic}» строго по ГОСТ со всеми обязательными разделами"
                }
                Toast.makeText(context, "Реквизиты сохранены для титульного листа!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Начать новый диалог?") },
            text = { Text("Текущий диалог и созданный документ будут сброшены. Вы сможете сформулировать новую задачу с чистого листа.") },
            confirmButton = {
                Button(
                    onClick = {
                        currentSessionId = UUID.randomUUID().toString()
                        messages = emptyList()
                        errorMessage = null
                        lastFailedRequest = null
                        pendingAttachments = emptyList()
                        customTitlePageText = null
                        AiChatSessionManager.clearSession(context)
                        showResetConfirmDialog = false
                        Toast.makeText(context, "Начат новый диалог", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Начать заново")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
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

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp)
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
                            val (headerRoleIcon, headerRoleColor, headerRoleBg) = when (selectedRole) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Triple(
                                    Icons.Filled.AutoAwesome,
                                    MaterialTheme.colorScheme.tertiary,
                                    MaterialTheme.colorScheme.tertiaryContainer
                                )
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Triple(
                                    Icons.Filled.School,
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Triple(
                                    Icons.Filled.Work,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Triple(
                                    Icons.Filled.EditNote,
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(headerRoleBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = headerRoleIcon,
                                    contentDescription = null,
                                    tint = headerRoleColor,
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
                            if (messages.any { !it.isUser }) {
                                IconButton(
                                    onClick = {
                                        exportChatHistoryToTxt(context, messages, selectedRole)
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FileDownload,
                                        contentDescription = "Скачать весь чат в текстовый файл (.txt)",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            IconButton(
                                onClick = { showHistoryBottomSheet = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Forum,
                                    contentDescription = "История диалогов",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            if (messages.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        showResetConfirmDialog = true
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

                    // Role selector chips (4 modes)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        for (role in AiAcademicAndSecretaryService.AssistantRole.values()) {
                            val isSelected = selectedRole == role
                            val (chipIcon, chipLabel) = when (role) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Icons.Filled.AutoAwesome to "🌐 Общий AI"
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Icons.Filled.School to "🎓 Профессор"
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Icons.Filled.Work to "💼 Секретарь"
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Icons.Filled.EditNote to "✍️ Редактор"
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedRole = role },
                                leadingIcon = {
                                    Icon(
                                        imageVector = chipIcon,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = chipLabel,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            )
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
                            val (infoTitle, infoDesc) = when (selectedRole) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL ->
                                    "Универсальный помощник" to "Задайте любой интересующий вас вопрос, попросите написать код, составить план, перевести текст или дать совет."
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                                    "Постановка научной задачи Профессору" to "Решит сложную задачу по шагам с формулами, напишет конспект или реферат. Прикрепите фото или скан задания."
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                                    "Постановка задачи Секретарю по ГОСТ" to "Составит официальное заявление, служебную записку, договор или акт с реквизитами по ГОСТ Р 7.0.97-2016."
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR ->
                                    "Редактор и копирайтер" to "Исправит ошибки, уберет воду, перепишет текст в нужном стиле и сделает структурированное саммари."
                            }
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
                                            text = infoTitle,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = infoDesc,
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
                                    val currentChips = when (selectedRole) {
                                        AiAcademicAndSecretaryService.AssistantRole.GENERAL -> generalChips
                                        AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> professorChips
                                        AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> secretaryChips
                                        AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> editorChips
                                    }
                                    val chipIcon = when (selectedRole) {
                                        AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Icons.Filled.AutoAwesome
                                        AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Icons.Filled.School
                                        AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Icons.Filled.Description
                                        AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Icons.Filled.EditNote
                                    }

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
                                                    chipIcon,
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

                            // Horizontal Quick Prompts Carousel
                            val currentRoleChips = when (selectedRole) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL -> generalChips
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> professorChips
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> secretaryChips
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> editorChips
                            }
                            val rolePromptIcon = when (selectedRole) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Icons.Filled.AutoAwesome
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Icons.Filled.School
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Icons.Filled.Description
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Icons.Filled.EditNote
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentRoleChips.take(5).forEach { chipText ->
                                    SuggestionChip(
                                        onClick = { promptInput = chipText },
                                        icon = {
                                            Icon(
                                                imageVector = rolePromptIcon,
                                                contentDescription = null,
                                                modifier = Modifier.size(15.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = chipText.take(38) + if (chipText.length > 38) "…" else "",
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                        },
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                }
                            }

                            // REQUISITES OF TITLE PAGE (РЕКВИЗИТЫ ТИТУЛЬНОГО ЛИСТА ГОСТ)
                            if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ||
                                selectedRole == AiAcademicAndSecretaryService.AssistantRole.SECRETARY ||
                                customTitlePageText != null
                            ) {
                                OutlinedCard(
                                    onClick = { showTitlePageDialog = true },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.outlinedCardColors(
                                        containerColor = if (customTitlePageText != null)
                                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
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
                                                Icons.Filled.Badge,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = if (customTitlePageText != null) "✓ Реквизиты титульного листа заданы" else "Реквизиты титульного листа (ГОСТ)",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = if (customTitlePageText != null)
                                                        "${requisites.docType}: «${requisites.topic.ifBlank { "Тема работы" }}»"
                                                    else
                                                        "ВУЗ, кафедра, тема, автор, руководитель, город и год...",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Icon(
                                            if (customTitlePageText != null) Icons.Filled.Check else Icons.Filled.Edit,
                                            contentDescription = "Настроить реквизиты",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
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

                            val placeholderText = when (selectedRole) {
                                AiAcademicAndSecretaryService.AssistantRole.GENERAL ->
                                    "Задайте любой вопрос, опишите задачу для кода, тему для анализа, текст для перевода..."
                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                                    "Опишите задачу (математика, физика, химия, алгоритмы), тему конспекта или реферата. Прикрепите фото или скан..."
                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                                    "Опишите вид документа: заявление, служебная записка, акт приёма-передачи, договор. Укажите реквизиты или прикрепите скан..."
                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR ->
                                    "Вставьте текст для проверки, редактирования, сокращения или изменения стиля..."
                            }

                            OutlinedTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                placeholder = {
                                    Text(
                                        text = placeholderText,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { startVoiceInput() },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Mic,
                                            contentDescription = "Голосовой ввод задачи",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 110.dp, max = 220.dp),
                                shape = RoundedCornerShape(14.dp)
                            )

                            // ATTACHMENT & VOICE ACTIONS
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { startVoiceInput() },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Голос", fontSize = 12.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1.1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
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
                                    modifier = Modifier.weight(1.1f),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                                ) {
                                    Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
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
                                    containerColor = when (selectedRole) {
                                        AiAcademicAndSecretaryService.AssistantRole.GENERAL -> MaterialTheme.colorScheme.tertiary
                                        AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> MaterialTheme.colorScheme.primary
                                        AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> MaterialTheme.colorScheme.secondary
                                        AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            ) {
                                val (btnIcon, btnText) = when (selectedRole) {
                                    AiAcademicAndSecretaryService.AssistantRole.GENERAL ->
                                        Icons.Filled.AutoAwesome to "Спросить Общего Помощника"
                                    AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                                        Icons.Filled.School to "Запустить решение задачи / реферат"
                                    AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                                        Icons.Filled.Description to "Сформировать документ по ГОСТ"
                                    AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR ->
                                        Icons.Filled.EditNote to "Улучшить и отредактировать текст"
                                }
                                Icon(
                                    imageVector = btnIcon,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = btnText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        // ==========================================
                        // DIALOGUE SCREEN: CHAT WITH MESSAGES, CONTINUING CONVERSATION & REFINEMENTS
                        // ==========================================
                        val unifiedDoc = assembleUnifiedDocument()
                        val modelCount = messages.count { !it.isUser }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 6.dp)
                        ) {
                            // Unified Document Management Card at the top of dialogue
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                    ),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                Icons.Filled.AutoStories,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = "Единый документ",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                Text(
                                                    text = if (customTitlePageText != null)
                                                        "✓ Титульный лист настроен (${requisites.docType})"
                                                    else
                                                        "Нажмите «Реквизиты» для титульного листа",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilledTonalButton(
                                                onClick = { showTitlePageDialog = true },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(34.dp)
                                            ) {
                                                Icon(Icons.Filled.Badge, null, modifier = Modifier.size(15.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Реквизиты", fontSize = 12.sp)
                                            }

                                            if (repository != null) {
                                                Button(
                                                    onClick = {
                                                        if (unifiedDoc.isNotBlank()) {
                                                            scope.launch {
                                                                val title = requisites.topic.ifBlank {
                                                                    messages.firstOrNull { !it.isUser }?.text?.lines()?.firstOrNull { it.isNotBlank() }?.take(40)?.trim()?.removePrefix("#")?.trim() ?: "Единый_документ"
                                                                }
                                                                val newNote = Note(
                                                                    title = title,
                                                                    content = unifiedDoc,
                                                                    folder = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Учеба и Наука" else "Документы",
                                                                    tags = listOf("единый документ", "гост")
                                                                )
                                                                repository.insertNote(newNote)
                                                                Toast.makeText(context, "Создан единый документ в заметках!", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    },
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Сохранить всё", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

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
                                        },
                                        onDeepenGeneration = {
                                            sendMessage(
                                                "Сделай эту работу значительно более глубокой, солидной, развернутой и профессиональной (полноценный фундаментальный труд по всем стандартам ГОСТ). " +
                                                "Подробно раскрой каждый подраздел (по 5-8 плотных академических абзацев), включи детальный обзор научных школ, теории и авторитетных ученых, " +
                                                "формулы с пошаговым выводом и единицами СИ, большую сравнительную аналитическую таблицу данных, практические примеры, численные расчеты и развернутые выводы по всем задачам."
                                            )
                                        },
                                        unifiedDocumentText = unifiedDoc,
                                        totalModelMessagesCount = modelCount,
                                        sessionImageUris = sessionImageUris
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
                                            val (roleLoadingTitle, roleLoadingSubtitle) = when (selectedRole) {
                                                AiAcademicAndSecretaryService.AssistantRole.GENERAL ->
                                                    "ИИ-Помощник думает над ответом..." to "Анализ задачи, формулирование выводов и решений"
                                                AiAcademicAndSecretaryService.AssistantRole.PROFESSOR ->
                                                    "Профессор анализирует задачу и рассчитывает выкладки..." to "Формирование развернутого ответа со всеми деталями"
                                                AiAcademicAndSecretaryService.AssistantRole.SECRETARY ->
                                                    "Секретарь формулирует и оформляет документ по ГОСТ..." to "Составление реквизитов и официального текста"
                                                AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR ->
                                                    "Редактор вычитывает и совершенствует текст..." to "Стилистическая правка, саммари и структурирование"
                                            }
                                            Column {
                                                Text(
                                                    text = roleLoadingTitle,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = roleLoadingSubtitle,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Error state with retry / resume / edit functionality
                            if (errorMessage != null) {
                                item {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    Icons.Filled.ErrorOutline,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = errorMessage!!,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { errorMessage = null },
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Close,
                                                        contentDescription = "Скрыть",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Action Buttons: Retry / Resume / Edit
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                FilledTonalButton(
                                                    onClick = { retryLastRequest() },
                                                    colors = ButtonDefaults.filledTonalButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.error,
                                                        contentColor = MaterialTheme.colorScheme.onError
                                                    ),
                                                    modifier = Modifier.weight(1.2f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Повторить запрос", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                                }

                                                OutlinedButton(
                                                    onClick = { resumeLastGeneration() },
                                                    modifier = Modifier.weight(1.1f),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Возобновить", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                                }

                                                OutlinedButton(
                                                    onClick = { editLastRequest() },
                                                    modifier = Modifier.weight(1.0f),
                                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                                                ) {
                                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(15.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Изменить", fontSize = 11.5.sp)
                                                }
                                            }
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

                                    // Quick 1-tap Photo Picker button
                                    IconButton(
                                        onClick = {
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        modifier = Modifier.size(38.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AddPhotoAlternate,
                                            contentDescription = "Прикрепить фото или скан",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
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
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { startVoiceInput() },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Mic,
                                                    contentDescription = "Голосовой ввод",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
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
    onContinueGeneration: (() -> Unit)? = null,
    onDeepenGeneration: (() -> Unit)? = null,
    unifiedDocumentText: String? = null,
    totalModelMessagesCount: Int = 1,
    sessionImageUris: List<String> = emptyList()
) {
    var isSavedInApp by remember { mutableStateOf(false) }

    val baseTitle = remember(message.text) {
        message.text.lines().firstOrNull { it.isNotBlank() }?.take(40)?.trim()
            ?.removePrefix("#")?.removePrefix("*")?.trim()
            ?: (if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Научный_материал" else "Официальный_документ")
    }

    val hasMultipleParts = totalModelMessagesCount > 1 && !unifiedDocumentText.isNullOrBlank()
    val textToExport = if (!unifiedDocumentText.isNullOrBlank()) unifiedDocumentText else message.text

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
                val (cardRoleIcon, cardRoleText, cardRoleBg) = when (selectedRole) {
                    AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Triple(
                        Icons.Filled.AutoAwesome,
                        "Ответ ИИ-Помощника",
                        MaterialTheme.colorScheme.tertiaryContainer
                    )
                    AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Triple(
                        Icons.Filled.School,
                        "Ответ Профессора",
                        MaterialTheme.colorScheme.primaryContainer
                    )
                    AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Triple(
                        Icons.Filled.Work,
                        "Документ Секретаря",
                        MaterialTheme.colorScheme.secondaryContainer
                    )
                    AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Triple(
                        Icons.Filled.EditNote,
                        "Редактор заметок",
                        MaterialTheme.colorScheme.primaryContainer
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = cardRoleBg
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = cardRoleIcon,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = cardRoleText,
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
                                        folder = when (selectedRole) {
                                            AiAcademicAndSecretaryService.AssistantRole.GENERAL -> "ИИ-Ответы"
                                            AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> "Учеба и Наука"
                                            AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> "Документы"
                                            AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> "Черновики и Статьи"
                                        },
                                        tags = when (selectedRole) {
                                            AiAcademicAndSecretaryService.AssistantRole.GENERAL -> listOf("ии", "помощник")
                                            AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> listOf("профессор", "наука")
                                            AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> listOf("секретарь", "гост")
                                            AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> listOf("редактор", "текст")
                                        }
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

                    // Quick Download .txt icon
                    IconButton(
                        onClick = { exportToTxt(context, baseTitle, textToExport) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.FileDownload,
                            contentDescription = "Скачать текстовый файл (.txt)",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(17.dp)
                        )
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

            // High-visibility download banner for extended documents & text files
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.DownloadDone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (hasMultipleParts) "Полный единый документ готов (${textToExport.length} симв.):" else "Детальный ответ готов к скачиванию (${textToExport.length} симв.):",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { exportToTxt(context, baseTitle, textToExport) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Icon(Icons.Filled.FileDownload, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📥 Скачать .txt", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        FilledTonalButton(
                            onClick = { exportToDocx(context, baseTitle, textToExport, sessionImageUris) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("📄 Word (.docx)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Quick Deepen / Expand Button for comprehensive academic rigor
            if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR && onDeepenGeneration != null) {
                Spacer(modifier = Modifier.height(10.dp))
                FilledTonalButton(
                    onClick = onDeepenGeneration,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.AutoStories, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "📚 Сделать работу более развернутой и глубокой (ГОСТ)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }
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
                                content = textToExport,
                                imageUrisJson = org.json.JSONArray(sessionImageUris).toString(),
                                folder = when (selectedRole) {
                                    AiAcademicAndSecretaryService.AssistantRole.GENERAL -> "ИИ-Ответы"
                                    AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> "Учеба и Наука"
                                    AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> "Документы"
                                    AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> "Черновики и Статьи"
                                },
                                tags = when (selectedRole) {
                                    AiAcademicAndSecretaryService.AssistantRole.GENERAL -> listOf("ии", "помощник", "единый документ")
                                    AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> listOf("профессор", "наука", "единый документ")
                                    AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> listOf("секретарь", "гост", "единый документ")
                                    AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> listOf("редактор", "текст", "единый документ")
                                }
                            )
                            repository.insertNote(newNote)
                            isSavedInApp = true
                            Toast.makeText(context, if (hasMultipleParts) "Единый документ успешно сохранен в заметки!" else "Успешно сохранено в заметки: «$baseTitle»!", Toast.LENGTH_LONG).show()
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
                        text = if (isSavedInApp) "✓ Сохранено в приложении" else if (hasMultipleParts) "📄 Сохранить единый документ (все $totalModelMessagesCount разделов)" else "Сохранить в заметки приложения",
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                if (hasMultipleParts) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val newNote = Note(
                                    title = "$baseTitle (фрагмент)",
                                    content = message.text,
                                    folder = if (selectedRole == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) "Учеба и Наука" else "Документы",
                                    tags = listOf("фрагмент")
                                )
                                repository.insertNote(newNote)
                                Toast.makeText(context, "Фрагмент сохранен в отдельную заметку", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.ContentCut, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить только этот фрагмент отдельно", fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 2. INSERT INTO OPEN NOTE (if opened from note editor)
            if (onInsertTextIntoNote != null) {
                FilledTonalButton(
                    onClick = {
                        onInsertTextIntoNote(textToExport)
                        Toast.makeText(context, if (hasMultipleParts) "Единый документ вставлен в открытую заметку!" else "Вставлено в текущую открытую заметку!", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Filled.PostAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasMultipleParts) "Вставить единый документ целиком" else "Вставить в открытую заметку")
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 3. EXPORT BUTTONS ROW: TEXT (.TXT), WORD (.DOCX / .DOC), EXCEL, PDF
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Text .txt (Текстовый файл)
                FilledTonalButton(
                    onClick = { exportToTxt(context, baseTitle, textToExport) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Filled.TextSnippet, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasMultipleParts) "Текст (единый .txt)" else "Текст (.txt)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Word .docx
                FilledTonalButton(
                    onClick = { exportToDocx(context, baseTitle, textToExport, sessionImageUris) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasMultipleParts) "Word (единый .docx)" else "Word (.docx)", fontSize = 12.sp)
                }

                // Word .doc (RTF)
                FilledTonalButton(
                    onClick = { exportToRtfDoc(context, baseTitle, textToExport, sessionImageUris) },
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
                    onClick = { exportToPdf(context, baseTitle, textToExport, sessionImageUris) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.PictureAsPdf, null, modifier = Modifier.size(16.dp), tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (hasMultipleParts) "PDF (единый ГОСТ)" else "PDF (ГОСТ)", fontSize = 12.sp)
                }

                // Direct Save .txt to Downloads folder
                FilledTonalButton(
                    onClick = { saveTxtDirectlyToDownloads(context, baseTitle, textToExport) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(".txt в Загрузки", fontSize = 12.sp)
                }

                // Direct Save Word to Downloads folder
                FilledTonalButton(
                    onClick = { saveDocxDirectlyToDownloads(context, baseTitle, textToExport, sessionImageUris) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.Download, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Word в Загрузки", fontSize = 12.sp)
                }
            }
        }
    }
}

private fun exportToDocx(context: Context, title: String, text: String, imageUris: List<String> = emptyList()) {
    try {
        val note = Note(title = title, content = text, imageUrisJson = org.json.JSONArray(imageUris).toString())
        val file = DocxGenerator.generateDocxFile(context, note, includeSignature = false)
        shareFile(context, file, "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "Открыть в Word (.docx)")
    } catch (e: Exception) {
        val saved = saveDirectlyToDownloads(context, title, "docx", text)
        Toast.makeText(context, "Файл Word сохранен в Загрузки: ${saved?.name}", Toast.LENGTH_LONG).show()
    }
}

private fun exportToRtfDoc(context: Context, title: String, text: String, imageUris: List<String> = emptyList()) {
    try {
        val note = Note(title = title, content = text, imageUrisJson = org.json.JSONArray(imageUris).toString())
        val file = DocxGenerator.generateRtfDocFile(context, note)
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

private fun exportToPdf(context: Context, title: String, text: String, imageUris: List<String> = emptyList()) {
    try {
        val tempNote = Note(title = title, content = text, imageUrisJson = org.json.JSONArray(imageUris).toString())
        val file = ShareExportUtil.generatePdfFile(
            context = context,
            note = tempNote,
            config = com.example.util.PdfExportConfig(
                includePageNumbers = true,
                includeCorporateLetterhead = true,
                includeVerificationQr = true,
                includeImages = true
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

private fun saveDocxDirectlyToDownloads(context: Context, title: String, text: String, imageUris: List<String> = emptyList()) {
    try {
        val note = Note(title = title, content = text, imageUrisJson = org.json.JSONArray(imageUris).toString())
        val file = DocxGenerator.generateDocxFile(context, note, includeSignature = false)
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

private fun exportToTxt(context: Context, title: String, text: String) {
    try {
        val cleanTitle = title.replace(Regex("[^a-zA-Zа-яА-ЯёЁ0-9_\\-]"), "_").trim('_').take(35).ifBlank { "document" }
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val txtFile = File(exportDir, "${cleanTitle}.txt")
        txtFile.writeText(text, Charsets.UTF_8)

        // Save a copy to public Downloads folder as well
        val saved = saveDirectlyToDownloads(context, cleanTitle, "txt", text)

        // Open share / view chooser with text/plain
        shareFile(context, txtFile, "text/plain", "Скачать или открыть текстовый файл (.txt)")
        if (saved != null) {
            Toast.makeText(context, "Файл .txt сохранен в Загрузки: ${saved.name}", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        val saved = saveDirectlyToDownloads(context, title, "txt", text)
        Toast.makeText(context, "Файл .txt сохранен в Загрузки: ${saved?.name ?: "document.txt"}", Toast.LENGTH_LONG).show()
    }
}

private fun saveTxtDirectlyToDownloads(context: Context, title: String, text: String) {
    try {
        val saved = saveDirectlyToDownloads(context, title, "txt", text)
        if (saved != null) {
            Toast.makeText(context, "Текстовый файл (.txt) сохранен в Загрузки: ${saved.name}", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, "Файл .txt готов", Toast.LENGTH_SHORT).show()
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка сохранения .txt: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun exportChatHistoryToTxt(
    context: Context,
    messages: List<AcademicChatMessage>,
    role: AiAcademicAndSecretaryService.AssistantRole
) {
    try {
        val sb = StringBuilder()
        sb.append("====================================================\n")
        sb.append("   ИСТОРИЯ ДИАЛОГА С АКАДЕМИЧЕСКИМ ИИ\n")
        sb.append("   Роль: ${role.title}\n")
        sb.append("   Специализация: ${role.subtitle}\n")
        sb.append("   Дата выгрузки: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}\n")
        sb.append("====================================================\n\n")

        messages.forEachIndexed { _, msg ->
            if (msg.isUser) {
                sb.append("----------------------------------------------------\n")
                sb.append("[ПОЛЬЗОВАТЕЛЬ]:\n")
                sb.append(msg.text)
                sb.append("\n\n")
            } else {
                sb.append("----------------------------------------------------\n")
                sb.append("[ИИ — ${role.title}]:\n")
                sb.append(msg.text)
                sb.append("\n\n")
            }
        }

        val fullHistory = sb.toString()
        val roleSuffix = when (role) {
            AiAcademicAndSecretaryService.AssistantRole.GENERAL -> "Помощник"
            AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> "Профессор"
            AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> "Секретарь"
            AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> "Редактор"
        }
        val title = "Чат_${roleSuffix}"
        exportToTxt(context, title, fullHistory)
        Toast.makeText(context, "История диалога экспортирована в текстовый файл (.txt)", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Ошибка экспорта диалога: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
