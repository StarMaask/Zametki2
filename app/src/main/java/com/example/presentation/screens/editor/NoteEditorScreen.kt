package com.example.presentation.screens.editor

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.domain.model.NoteFontFamily
import com.example.domain.model.NoteTemplate
import com.example.domain.model.PageFormat
import com.example.presentation.components.AudioPlaybackCard
import com.example.presentation.components.AudioRecordDialog
import com.example.presentation.components.CustomFontDigitizerDialog
import com.example.presentation.components.DrawingCanvasDialog
import com.example.presentation.components.FullscreenImageViewerDialog
import com.example.presentation.components.MarkdownRenderer
import com.example.presentation.components.NotebookPalette
import com.example.presentation.components.NotebookPaperCanvas
import com.example.presentation.components.NoteInfoDialog
import com.example.presentation.components.NoteTemplateDialog
import com.example.presentation.components.PageFormatSelectorDialog
import com.example.presentation.components.PinSetupDialog
import com.example.presentation.components.PinVerifyDialog
import com.example.presentation.components.ShareNoteBottomSheet
import com.example.presentation.components.TooltipIconButton
import com.example.presentation.components.VoiceSettingsDialog
import com.example.presentation.components.AudioPerceptionSettingsDialog
import com.example.util.SpeechPostProcessor
import com.example.presentation.components.getFontFamily
import com.example.presentation.components.getInkColor
import com.example.presentation.components.getPaperColor
import com.example.presentation.components.getPlaceholderColor
import com.example.data.preferences.UserPreferencesManager
import com.example.ui.theme.NoteColors
import com.example.util.LectureTranscriptionManager
import com.example.util.NoteFontHelper
import com.example.util.NoteSpeechManager
import com.example.util.RichMarkdownVisualTransformation
import com.example.util.ShareExportUtil
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun NoteEditorScreen(
    viewModel: NoteEditorViewModel,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showColorPicker by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showDrawingDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showInsertMenu by remember { mutableStateOf(false) }
    var showFormatMenu by remember { mutableStateOf(false) }
    var showOrganizeMenu by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showPageFormatDialog by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showFontDigitizerDialog by remember { mutableStateOf(false) }
    var showTextColorMenu by remember { mutableStateOf(false) }
    var showFontFamilyMenu by remember { mutableStateOf(false) }
    var showVoiceSettingsDialog by remember { mutableStateOf(false) }
    var showAudioPerceptionDialog by remember { mutableStateOf(false) }
    var previewingImageUri by remember { mutableStateOf<String?>(null) }
    var digitizerInitialImageUri by remember { mutableStateOf<String?>(null) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinUnlockDialog by remember { mutableStateOf(false) }

    val preferencesManager = remember { UserPreferencesManager(context) }
    val speechManager = remember { NoteSpeechManager(context) }
    val lectureManager = remember { LectureTranscriptionManager(context) }

    LaunchedEffect(Unit) {
        preferencesManager.ttsVoiceNameFlow.collect { voiceName ->
            speechManager.setVoice(voiceName)
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.ttsPitchFlow.collect { pitch ->
            speechManager.setPitch(pitch)
        }
    }
    LaunchedEffect(Unit) {
        preferencesManager.ttsRateFlow.collect { rate ->
            speechManager.setRate(rate)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            lectureManager.release()
            speechManager.release()
        }
    }

    var contentTextFieldValue by remember {
        mutableStateOf(TextFieldValue(state.content, TextRange(state.content.length)))
    }

    LaunchedEffect(state.content) {
        if (contentTextFieldValue.text != state.content) {
            contentTextFieldValue = TextFieldValue(state.content, TextRange(state.content.length))
        }
    }

    var newTagInput by remember { mutableStateOf("") }
    var folderInput by remember { mutableStateOf("") }
    var checkItemInput by remember { mutableStateOf("") }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.addImage(it.toString()) }
    }

    fun appendRecognizedText(spoken: String) {
        if (spoken.isBlank()) return
        val currentText = contentTextFieldValue.text
        val selection = contentTextFieldValue.selection
        val insertPos = if (selection.start in 0..currentText.length) selection.start else currentText.length
        val separator = if (insertPos > 0 && !currentText[insertPos - 1].isWhitespace()) " " else ""
        val newText = currentText.substring(0, insertPos) + separator + spoken + " " + currentText.substring(insertPos)
        val newCursor = insertPos + separator.length + spoken.length + 1
        contentTextFieldValue = TextFieldValue(newText, TextRange(newCursor))
        viewModel.onContentChange(newText)
    }

    val ocrImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            coroutineScope.launch {
                Toast.makeText(context, "Распознавание текста с фото...", Toast.LENGTH_SHORT).show()
                val extracted = com.example.util.HandwritingPhotoDigitizer.extractTextFromImage(context, uri)
                appendRecognizedText(extracted)
                Toast.makeText(context, "Текст с фото вставлен в заметку!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            showAudioDialog = true
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val replacements = preferencesManager.getWordReplacementsSync()
            val smartPunct = preferencesManager.isSmartPunctuationSync()
            val bestCandidate = SpeechPostProcessor.selectBestCandidate(matches, replacements)
            if (!bestCandidate.isNullOrBlank()) {
                val processed = SpeechPostProcessor.process(bestCandidate, smartPunct, replacements)
                appendRecognizedText(processed)
                Toast.makeText(context, "Речь оцифрована: $processed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var startSpeechToTextAction: () -> Unit = {}

    val recordAudioPermissionForSpeechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val started = lectureManager.startRecording { chunk ->
                appendRecognizedText(chunk)
            }
            if (!started) {
                startSpeechToTextAction()
            }
        } else {
            Toast.makeText(context, "Требуется доступ к микрофону для распознавания речи", Toast.LENGTH_SHORT).show()
        }
    }

    fun startSpeechToText() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            val selectedLang = preferencesManager.getSpeechLanguageSync()
            val accuracyMode = preferencesManager.getSpeechAccuracySync()
            val langTag = if (selectedLang.isBlank() || selectedLang == "auto") {
                Locale.getDefault().toLanguageTag()
            } else {
                selectedLang
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                if (accuracyMode == "prefer_offline") {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите — голос преобразуется в текст заметки...")
            }
            try {
                speechRecognizerLauncher.launch(intent)
            } catch (_: Exception) {
                Toast.makeText(context, "Распознавание речи недоступно на данном устройстве", Toast.LENGTH_SHORT).show()
            }
        } else {
            recordAudioPermissionForSpeechLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    startSpeechToTextAction = { startSpeechToText() }

    fun toggleLectureRecording() {
        if (lectureManager.isRecording) {
            lectureManager.stopRecording()
        } else {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                val started = lectureManager.startRecording { chunk ->
                    appendRecognizedText(chunk)
                }
                if (!started) {
                    startSpeechToText()
                }
            } else {
                recordAudioPermissionForSpeechLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun applyFormatting(prefix: String, suffix: String = prefix) {
        val selection = contentTextFieldValue.selection
        val text = contentTextFieldValue.text
        if (selection.start != selection.end) {
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            val selected = text.substring(start, end)

            // 1. If selection itself is already wrapped with prefix and suffix -> toggle off (unwrap)
            if (selected.startsWith(prefix) && selected.endsWith(suffix) && selected.length >= prefix.length + suffix.length) {
                val unwrapped = selected.substring(prefix.length, selected.length - suffix.length)
                val newText = text.substring(0, start) + unwrapped + text.substring(end)
                val newSelection = TextRange(start, start + unwrapped.length)
                contentTextFieldValue = TextFieldValue(newText, newSelection)
                viewModel.onContentChange(newText)
                return
            }

            // 2. If text around selection is already wrapped -> toggle off (unwrap outer)
            if (start >= prefix.length && end + suffix.length <= text.length) {
                val before = text.substring(start - prefix.length, start)
                val after = text.substring(end, end + suffix.length)
                if (before == prefix && after == suffix) {
                    val newText = text.substring(0, start - prefix.length) + selected + text.substring(end + suffix.length)
                    val newSelection = TextRange(start - prefix.length, end - prefix.length)
                    contentTextFieldValue = TextFieldValue(newText, newSelection)
                    viewModel.onContentChange(newText)
                    return
                }
            }

            // 3. Normal wrap
            val newText = text.substring(0, start) + prefix + selected + suffix + text.substring(end)
            val newSelection = TextRange(start + prefix.length, end + prefix.length)
            contentTextFieldValue = TextFieldValue(newText, newSelection)
            viewModel.onContentChange(newText)
        } else {
            val insertPos = selection.start
            val placeholder = when (prefix) {
                "**" -> "жирный текст"
                "*" -> "курсив"
                "<u>" -> "подчеркнутый"
                "~~" -> "зачеркнутый"
                "# " -> "Заголовок"
                "## " -> "Подзаголовок"
                "• " -> "пункт списка"
                "> " -> "цитата"
                else -> "текст"
            }
            val inserted = prefix + placeholder + suffix
            val newText = text.substring(0, insertPos) + inserted + text.substring(insertPos)
            val newSelection = TextRange(insertPos + prefix.length, insertPos + prefix.length + placeholder.length)
            contentTextFieldValue = TextFieldValue(newText, newSelection)
            viewModel.onContentChange(newText)
        }
    }

    fun applyColorToSelection(hexColor: String) {
        val selection = contentTextFieldValue.selection
        val text = contentTextFieldValue.text
        val openTag = "[color=$hexColor]"
        val closeTag = "[/color]"
        if (selection.start != selection.end) {
            val start = minOf(selection.start, selection.end)
            val end = maxOf(selection.start, selection.end)
            val selected = text.substring(start, end)
            // Strip any existing color tags in the selection
            val cleaned = selected.replace(Regex("\\[color=#[0-9a-fA-F]{6}\\]"), "").replace("[/color]", "")
            val newText = text.substring(0, start) + openTag + cleaned + closeTag + text.substring(end)
            val newSelection = TextRange(start + openTag.length, start + openTag.length + cleaned.length)
            contentTextFieldValue = TextFieldValue(newText, newSelection)
            viewModel.onContentChange(newText)
        } else {
            viewModel.onTextColorChange(hexColor)
            Toast.makeText(context, "Цвет шрифта заметки установлен", Toast.LENGTH_SHORT).show()
        }
    }

    val paperColor = getPaperColor(state.pageFormat, state.colorHex)
    val inkColor = getInkColor(state.pageFormat, paperColor)
    val placeholderColor = getPlaceholderColor(state.pageFormat, paperColor)
    val pageFontFamily = getFontFamily(state.pageFormat)
    val isDarkPaper = (paperColor.red * 0.299f + paperColor.green * 0.587f + paperColor.blue * 0.114f) < 0.45f

    val activeFontFamily = if (state.noteFont != NoteFontFamily.DEFAULT) {
        NoteFontHelper.getFontFamily(context, state.noteFont)
    } else {
        pageFontFamily
    }

    val activeInkColor = try {
        if (state.textColorHex.isNotBlank() && state.textColorHex != "#1C1B1F" && !isDarkPaper && state.pageFormat != PageFormat.BLUEPRINT) {
            Color(android.graphics.Color.parseColor(state.textColorHex))
        } else if (state.noteFont == NoteFontFamily.CUSTOM_DIGITIZED && !isDarkPaper && state.pageFormat != PageFormat.BLUEPRINT) {
            val customHex = UserPreferencesManager(context).getHandwritingInkColorSync()
            if (!customHex.isNullOrBlank()) {
                Color(android.graphics.Color.parseColor(customHex))
            } else {
                inkColor
            }
        } else {
            inkColor
        }
    } catch (_: Exception) {
        inkColor
    }

    BackHandler {
        viewModel.saveNote(context) {
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (state.noteId == 0L) "Новая заметка" else "Заметка",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = {
                            viewModel.saveNote(context) {
                                onBack()
                            }
                        },
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Сохранить и вернуться",
                        contentDescription = "Назад"
                    )
                },
                actions = {
                    // 1. Save Button
                    TooltipIconButton(
                        onClick = {
                            viewModel.saveNote(context) {
                                Toast.makeText(context, "Заметка сохранена", Toast.LENGTH_SHORT).show()
                                onBack()
                            }
                        },
                        icon = Icons.Filled.Done,
                        tooltip = "Сохранить и выйти",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    // 2. Markdown Preview / Edit Mode Toggle
                    TooltipIconButton(
                        onClick = { viewModel.toggleMarkdownPreview() },
                        icon = if (state.isMarkdownPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                        tooltip = if (state.isMarkdownPreview) "Режим редактирования" else "Предпросмотр Markdown и стилей",
                        tint = if (state.isMarkdownPreview) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )

                    // 3. Share Button
                    TooltipIconButton(
                        onClick = { showShareSheet = true },
                        icon = Icons.Filled.Share,
                        tooltip = "Поделиться заметкой (текст, картинка, PDF)",
                        tint = MaterialTheme.colorScheme.primary
                    )

                    // 4. Overflow Menu
                    Box {
                        TooltipIconButton(
                            onClick = { showTopMenu = true },
                            icon = Icons.Filled.MoreVert,
                            tooltip = "Дополнительные действия"
                        )
                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false }
                        ) {
                            // Озвучивание
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (speechManager.isSpeaking) "Остановить чтение" else "Озвучить заметку вслух")
                                        Text("Воспроизведение текста через синтезатор", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(if (speechManager.isSpeaking) Icons.Filled.VolumeUp else Icons.Filled.VolumeMute, null, tint = if (speechManager.isSpeaking) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showTopMenu = false
                                    if (speechManager.isSpeaking) {
                                        speechManager.stop()
                                    } else {
                                        val fullText = if (state.content.isNotBlank()) state.content else state.title
                                        speechManager.speak(fullText, state.title)
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Настройки голоса")
                                        Text("Выбор голоса, тембр и скорость речи", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.SettingsVoice, null) },
                                onClick = {
                                    showTopMenu = false
                                    showVoiceSettingsDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Качество восприятия звука")
                                        Text("Настройка микрофона и словарь автозамены слов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Tune, null, tint = MaterialTheme.colorScheme.primary) },
                                onClick = {
                                    showTopMenu = false
                                    showAudioPerceptionDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (lectureManager.isRecording) "Остановить «Звук в текст»" else "Звук в текст (Непрерывная запись)")
                                        Text(if (lectureManager.isRecording) "Завершить распознавание речи" else "Плавный перевод речи и лекций в текст без прерываний", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(if (lectureManager.isRecording) Icons.Filled.Stop else Icons.Filled.Mic, null, tint = if (lectureManager.isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface) },
                                onClick = {
                                    showTopMenu = false
                                    toggleLectureRecording()
                                }
                            )
                            HorizontalDivider()

                            // Оформление листа
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Формат листа: ${state.pageFormat.title}")
                                        Text("Книга, линии, клетка, крафт, винтаж", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.AutoStories, null) },
                                onClick = {
                                    showTopMenu = false
                                    showPageFormatDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Оцифровка и свой шрифт")
                                        Text("Оцифровать почерк или загрузить TTF/OTF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Gesture, null) },
                                onClick = {
                                    showTopMenu = false
                                    showFontDigitizerDialog = true
                                }
                            )
                            HorizontalDivider()

                            // Безопасность и организация
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (state.isPinned) "Открепить заметку" else "Закрепить вверху")
                                        Text("Фиксация в начале списка заметок", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(if (state.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin, null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.togglePin()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (state.reminderTime != null) "Изменить напоминание" else "Установить напоминание")
                                        Text("Уведомление в заданный день и час", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(if (state.reminderTime != null) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsNone, null) },
                                onClick = {
                                    showTopMenu = false
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    }
                                    val calendar = Calendar.getInstance()
                                    DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                        TimePickerDialog(context, { _, hourOfDay, minute ->
                                            val reminderCal = Calendar.getInstance().apply {
                                                set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                            }
                                            viewModel.onReminderChange(reminderCal.timeInMillis)
                                        }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show()
                                    }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (state.isLocked) "Снять защиту PIN-кодом" else "Защитить PIN-кодом")
                                        Text(if (state.isLocked) "Заметка будет открываться без ввода PIN" else "Скрывать содержимое до ввода PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(if (state.isLocked) Icons.Filled.LockOpen else Icons.Filled.Lock, null) },
                                onClick = {
                                    showTopMenu = false
                                    if (!state.isLocked) {
                                        if (!preferencesManager.hasCustomPinSetSync()) {
                                            showPinSetupDialog = true
                                        } else {
                                            viewModel.toggleLock()
                                            Toast.makeText(context, "Заметка защищена PIN-кодом", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        if (preferencesManager.hasCustomPinSetSync()) {
                                            showPinUnlockDialog = true
                                        } else {
                                            viewModel.toggleLock()
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Применить шаблон")
                                        Text("Заполнить структуру (план, покупки, встреча)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.AutoAwesome, null) },
                                onClick = {
                                    showTopMenu = false
                                    showTemplateDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Информация о заметке")
                                        Text("Статистика символов, дата изменения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Info, null) },
                                onClick = {
                                    showTopMenu = false
                                    showInfoDialog = true
                                }
                            )
                            HorizontalDivider()

                            // Экспорт
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Экспорт в PDF")
                                        Text("Создать документ с заголовком и датой", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, null) },
                                onClick = {
                                    showTopMenu = false
                                    ShareExportUtil.shareAsPdf(context, state.toDomainNote())
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Экспорт в TXT")
                                        Text("Сохранить как текстовый файл", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Description, null) },
                                onClick = {
                                    showTopMenu = false
                                    ShareExportUtil.shareAsTxtFile(context, state.toDomainNote())
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(if (state.isArchived) "Из архива" else "В архив")
                                        Text(if (state.isArchived) "Вернуть в общий список" else "Скрыть с главного экрана", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.toggleArchive { onBack() }
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text("Удалить в корзину", color = MaterialTheme.colorScheme.error)
                                        Text("Можно восстановить позже", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                    }
                                },
                                leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showTopMenu = false
                                    viewModel.moveToTrash(context) { onBack() }
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding(),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. Верхняя панель: Быстрое форматирование и начертание шрифтов
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Жирный
                        FilledTonalIconButton(
                            onClick = { applyFormatting("**", "**") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.FormatBold, contentDescription = "Жирный текст", modifier = Modifier.size(18.dp))
                        }

                        // Курсив
                        FilledTonalIconButton(
                            onClick = { applyFormatting("*", "*") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.FormatItalic, contentDescription = "Курсив", modifier = Modifier.size(18.dp))
                        }

                        // Подчеркнутый
                        FilledTonalIconButton(
                            onClick = { applyFormatting("<u>", "</u>") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.FormatUnderlined, contentDescription = "Подчёркивание", modifier = Modifier.size(18.dp))
                        }

                        // Зачеркнутый
                        FilledTonalIconButton(
                            onClick = { applyFormatting("~~", "~~") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.FormatStrikethrough, contentDescription = "Зачёркивание", modifier = Modifier.size(18.dp))
                        }

                        // Выбор цвета текста (шрифта)
                        Box {
                            val activeHex = state.textColorHex
                            val currentDotColor = try { Color(android.graphics.Color.parseColor(activeHex)) } catch (_: Exception) { MaterialTheme.colorScheme.primary }
                            AssistChip(
                                onClick = { showTextColorMenu = true },
                                label = { Text("Цвет", fontSize = 12.sp) },
                                leadingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(currentDotColor)
                                    )
                                },
                                modifier = Modifier.height(34.dp)
                            )
                            DropdownMenu(
                                expanded = showTextColorMenu,
                                onDismissRequest = { showTextColorMenu = false }
                            ) {
                                Text(
                                    text = "Цвет шрифта (для выделения или всей заметки):",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                val textColors = listOf(
                                    "#1C1B1F" to "Угольный чёрный",
                                    "#1A56DB" to "Синий чернильный",
                                    "#7E22CE" to "Королевский пурпурный",
                                    "#DC2626" to "Карминный красный",
                                    "#059669" to "Изумрудный зелёный",
                                    "#EA580C" to "Янтарный оранжевый",
                                    "#78350F" to "Тёплая сепия / Шоколад",
                                    "#F8FAFC" to "Меловой белый"
                                )
                                textColors.forEach { (hex, name) ->
                                    val swatch = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.Black }
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        leadingIcon = {
                                            Box(
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clip(CircleShape)
                                                    .background(swatch)
                                            )
                                        },
                                        onClick = {
                                            showTextColorMenu = false
                                            applyColorToSelection(hex)
                                        }
                                    )
                                }
                            }
                        }

                        // Выбор начертания и шрифта (рукописный, печатный, оцифровка)
                        Box {
                            AssistChip(
                                onClick = { showFontFamilyMenu = true },
                                label = {
                                    Text(
                                        text = state.noteFont.title.substringBefore(" "),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (state.noteFont.isHandwriting) Icons.Filled.Gesture else Icons.Filled.FontDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                },
                                modifier = Modifier.height(34.dp)
                            )
                            DropdownMenu(
                                expanded = showFontFamilyMenu,
                                onDismissRequest = { showFontFamilyMenu = false }
                            ) {
                                Text(
                                    text = "Шрифт заметки:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                )
                                NoteFontFamily.values().forEach { font ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(font.title, fontWeight = if (font == state.noteFont) FontWeight.Bold else FontWeight.Normal)
                                                Text(font.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = if (font.isHandwriting) Icons.Filled.Gesture else Icons.Filled.TextFields,
                                                contentDescription = null,
                                                tint = if (font == state.noteFont) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        },
                                        trailingIcon = if (font == state.noteFont) {
                                            { Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) }
                                        } else null,
                                        onClick = {
                                            showFontFamilyMenu = false
                                            viewModel.onFontFormatChange(font)
                                        }
                                    )
                                }
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Оцифровать свой почерк...", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Нарисовать образцы или загрузить TTF/OTF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Brush, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showFontFamilyMenu = false
                                        showFontDigitizerDialog = true
                                    }
                                )
                            }
                        }

                        // Звук в текст (Плавная непрерывная запись)
                        FilledTonalButton(
                            onClick = { toggleLectureRecording() },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = if (lectureManager.isRecording) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (lectureManager.isRecording) Icons.Filled.FiberManualRecord else Icons.Filled.Mic,
                                contentDescription = "Звук в текст",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (lectureManager.isRecording) {
                                    if (lectureManager.isPaused) "Пауза" else "Запись..."
                                } else {
                                    "Звук в текст"
                                },
                                fontSize = 12.sp
                            )
                        }

                        // Качество восприятия звука и словарь оцифровки
                        FilledTonalIconButton(
                            onClick = { showAudioPerceptionDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Качество восприятия звука", modifier = Modifier.size(16.dp))
                        }

                        // Озвучить текст (Text-to-Speech)
                        FilledTonalButton(
                            onClick = {
                                if (speechManager.isSpeaking) {
                                    speechManager.stop()
                                } else {
                                    val fullText = if (state.content.isNotBlank()) state.content else state.title
                                    speechManager.speak(fullText, state.title)
                                }
                            },
                            modifier = Modifier.height(34.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            colors = if (speechManager.isSpeaking) {
                                ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                ButtonDefaults.filledTonalButtonColors()
                            }
                        ) {
                            Icon(
                                imageVector = if (speechManager.isSpeaking) Icons.Filled.VolumeUp else Icons.Filled.RecordVoiceOver,
                                contentDescription = "Озвучить текст",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (speechManager.isSpeaking) "Стоп" else "Озвучить", fontSize = 12.sp)
                        }

                        // Настройки голоса синтезатора
                        FilledTonalIconButton(
                            onClick = { showVoiceSettingsDialog = true },
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(Icons.Filled.SettingsVoice, contentDescription = "Выбор голоса", modifier = Modifier.size(16.dp))
                        }

                        // Заголовок H1
                        FilledTonalIconButton(
                            onClick = { applyFormatting("# ", "") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("H1", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        // Заголовок H2
                        FilledTonalIconButton(
                            onClick = { applyFormatting("## ", "") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Text("H2", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }

                        // Список
                        FilledTonalIconButton(
                            onClick = { applyFormatting("• ", "") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = "Список", modifier = Modifier.size(18.dp))
                        }

                        // Цитата
                        FilledTonalIconButton(
                            onClick = { applyFormatting("> ", "") },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.FormatQuote, contentDescription = "Цитата", modifier = Modifier.size(18.dp))
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // 2. Нижняя строка действий
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. ВСТАВКА (Insert Dropdown Menu)
                        Box {
                            TooltipIconButton(
                                onClick = { showInsertMenu = true },
                                icon = Icons.Filled.AddCircleOutline,
                                tooltip = "Вставить медиа или список",
                                tint = MaterialTheme.colorScheme.primary
                            )
                            DropdownMenu(
                                expanded = showInsertMenu,
                                onDismissRequest = { showInsertMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Чек-лист задач")
                                            Text("Создать список дел с галочками", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.CheckBox, null) },
                                    onClick = {
                                        showInsertMenu = false
                                        viewModel.toggleChecklistMode()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Звук в текст (Диктовка)")
                                            Text("Голосовой ввод речи прямо в заметку", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.RecordVoiceOver, null) },
                                    onClick = {
                                        showInsertMenu = false
                                        startSpeechToText()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Фото / Изображение")
                                            Text("Прикрепить картинку из галереи", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Image, null) },
                                    onClick = {
                                        showInsertMenu = false
                                        imagePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Оцифровать почерк по фото (.jpg)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                            Text("Загрузить фото листа алфавита для создания шрифта", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.AutoFixHigh, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showInsertMenu = false
                                        showFontDigitizerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Распознать текст с фото (OCR)")
                                            Text("Извлечь рукописный или печатный текст в заметку", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.DocumentScanner, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        showInsertMenu = false
                                        ocrImagePickerLauncher.launch("image/*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Рисунок от руки")
                                            Text("Холст для эскизов и схем", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Brush, null) },
                                    onClick = {
                                        showInsertMenu = false
                                        showDrawingDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Голосовая запись")
                                            Text("Записать аудио на диктофон", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Mic, null) },
                                    onClick = {
                                        showInsertMenu = false
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                            showAudioDialog = true
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                )
                            }
                        }

                        // 2. ФОРМАТИРОВАНИЕ (Formatting Dropdown Menu)
                        Box {
                            TooltipIconButton(
                                onClick = { showFormatMenu = true },
                                icon = Icons.Filled.TextFormat,
                                tooltip = "Форматирование текста",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DropdownMenu(
                                expanded = showFormatMenu,
                                onDismissRequest = { showFormatMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Жирный текст (**)")
                                            Text("Выделение важного", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FormatBold, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("**", "**")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Курсивный текст (*)")
                                            Text("Наклонный шрифт", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FormatItalic, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("*", "*")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Подчёркивание (<u>)")
                                            Text("Линия под текстом", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FormatUnderlined, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("<u>", "</u>")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Зачёркивание (~~)")
                                            Text("Зачёркнутый текст", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FormatStrikethrough, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("~~", "~~")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Заголовок (# )")
                                            Text("Крупный заголовок раздела", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Title, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("# ", "")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Маркированный список (• )")
                                            Text("Элемент перечисления", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.FormatListBulleted, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("• ", "")
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Цитата (> )")
                                            Text("Блок цитирования", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FormatQuote, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        applyFormatting("> ", "")
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Сменить шрифт заметки")
                                            Text("Печатный, рукописный, винтажный", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.FontDownload, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        showFontFamilyMenu = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Формат листа: ${state.pageFormat.title}")
                                            Text("Книга, тетрадь в линейку или в клетку", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.AutoStories, null) },
                                    onClick = {
                                        showFormatMenu = false
                                        showPageFormatDialog = true
                                    }
                                )
                            }
                        }

                        // 3. ФОРМАТ ЛИСТА (Book / Ruled / Grid / Kraft / Vintage / etc.)
                        TooltipIconButton(
                            onClick = { showPageFormatDialog = true },
                            icon = when (state.pageFormat) {
                                PageFormat.BOOK -> Icons.Filled.AutoStories
                                PageFormat.RULED -> Icons.Filled.FormatAlignJustify
                                PageFormat.GRID -> Icons.Filled.BorderAll
                                PageFormat.KRAFT -> Icons.Filled.Style
                                PageFormat.VINTAGE -> Icons.Filled.Bookmark
                                PageFormat.MIDNIGHT -> Icons.Filled.DarkMode
                                PageFormat.BLUEPRINT -> Icons.Filled.Edit
                                PageFormat.BLANK -> Icons.Filled.Description
                            },
                            tooltip = "Формат листа: ${state.pageFormat.title}",
                            tint = MaterialTheme.colorScheme.primary
                        )

                        // 4. ПАЛИТРА ЦВЕТОВ (Color Row Toggle)
                        TooltipIconButton(
                            onClick = { showColorPicker = !showColorPicker },
                            icon = Icons.Filled.Palette,
                            tooltip = "Цвет фона заметки",
                            tint = if (showColorPicker) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 5. ОРГАНИЗАЦИЯ (Organize Dropdown Menu: Folder & Tags)
                        Box {
                            TooltipIconButton(
                                onClick = { showOrganizeMenu = true },
                                icon = Icons.Filled.FolderOpen,
                                tooltip = "Папка и теги",
                                tint = if (state.folder != null || state.tags.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            DropdownMenu(
                                expanded = showOrganizeMenu,
                                onDismissRequest = { showOrganizeMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Папка")
                                            Text(if (state.folder != null) "Текущая: ${state.folder}" else "Назначить папку", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Folder, null) },
                                    onClick = {
                                        showOrganizeMenu = false
                                        folderInput = state.folder ?: ""
                                        showFolderDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Добавить тег")
                                            Text("Теги для быстрой фильтрации", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Label, null) },
                                    onClick = {
                                        showOrganizeMenu = false
                                        showTagDialog = true
                                    }
                                )
                            }
                        }

                        // 6. ОТМЕНА (Undo)
                        TooltipIconButton(
                            onClick = { viewModel.undo() },
                            icon = Icons.AutoMirrored.Filled.Undo,
                            tooltip = "Отменить ввод",
                            enabled = state.canUndo,
                            tint = if (state.canUndo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )

                        // 7. ПОВТОР (Redo)
                        TooltipIconButton(
                            onClick = { viewModel.redo() },
                            icon = Icons.AutoMirrored.Filled.Redo,
                            tooltip = "Повторить ввод",
                            enabled = state.canRedo,
                            tint = if (state.canRedo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        val scrollState = rememberScrollState()
        val coroutineScope = rememberCoroutineScope()
        val bringIntoViewRequester = remember { BringIntoViewRequester() }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .imeNestedScroll()
                .verticalScroll(scrollState)
                .padding(16.dp)
        ) {
            // Active Speech (Звук в тексте / Озвучивание) Player Banner
            AnimatedVisibility(visible = speechManager.isSpeaking) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.VolumeUp,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Озвучивание текста (звук)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Чтение текста заметки вслух",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalButton(
                                onClick = { speechManager.cycleSpeechRate() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "${speechManager.speechRate}x",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = { speechManager.stop() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Stop,
                                    contentDescription = "Остановить чтение",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Color Selector Bar if toggled
            if (showColorPicker) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "Выберите оттенок бумаги (13 дизайнерских цветов):",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(NoteColors) { hex ->
                                val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.LightGray }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .clickable { viewModel.onColorChange(hex) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (state.colorHex.equals(hex, ignoreCase = true)) {
                                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp)) {}
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Quick Page Format Selector Tabs (1-tap format switching)
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth().padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(PageFormat.values()) { format ->
                        val isSelected = format == state.pageFormat
                        val chipIcon = when (format) {
                            PageFormat.BOOK -> Icons.Filled.AutoStories
                            PageFormat.RULED -> Icons.Filled.FormatAlignJustify
                            PageFormat.GRID -> Icons.Filled.BorderAll
                            PageFormat.KRAFT -> Icons.Filled.Style
                            PageFormat.VINTAGE -> Icons.Filled.Bookmark
                            PageFormat.MIDNIGHT -> Icons.Filled.DarkMode
                            PageFormat.BLUEPRINT -> Icons.Filled.Edit
                            PageFormat.BLANK -> Icons.Filled.Description
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.onPageFormatChange(format) },
                            label = {
                                Text(
                                    text = format.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = {
                                Icon(chipIcon, null, modifier = Modifier.size(14.dp))
                            }
                        )
                    }
                }
            }

            // Folder, Reminder, Tags Chip Row
            if (!state.folder.isNullOrBlank() || state.reminderTime != null || state.tags.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!state.folder.isNullOrBlank()) {
                        SuggestionChip(
                            onClick = { folderInput = state.folder ?: ""; showFolderDialog = true },
                            label = { Text(state.folder!!) },
                            icon = { Icon(Icons.Filled.Folder, contentDescription = "Папка", modifier = Modifier.size(14.dp)) }
                        )
                    }

                    if (state.reminderTime != null) {
                        val reminderStr = SimpleDateFormat("d MMM, HH:mm", Locale("ru")).format(Date(state.reminderTime!!))
                        InputChip(
                            selected = true,
                            onClick = { viewModel.onReminderChange(null) },
                            label = { Text(reminderStr) },
                            leadingIcon = { Icon(Icons.Filled.NotificationsActive, contentDescription = "Напоминание", modifier = Modifier.size(14.dp)) },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Отключить напоминание", modifier = Modifier.size(12.dp)) }
                        )
                    }

                    state.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = { viewModel.removeTag(tag) },
                            label = { Text("#$tag") },
                            trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Удалить тег", modifier = Modifier.size(12.dp)) }
                        )
                    }
                }
            }

            // Smooth Continuous Speech-to-Text Active Banner
            if (lectureManager.isRecording) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (lectureManager.isPaused) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (lectureManager.isPaused) MaterialTheme.colorScheme.outline
                                            else MaterialTheme.colorScheme.error
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Звук в текст • ${lectureManager.formattedDuration()}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (lectureManager.isPaused) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (lectureManager.isPaused) "Запись на паузе"
                                        else if (lectureManager.isListening) "Слушаю речь..."
                                        else "Ожидание голоса...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (lectureManager.isPaused) MaterialTheme.colorScheme.outline
                                        else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalIconButton(
                                    onClick = { showAudioPerceptionDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = "Качество восприятия звука",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                FilledTonalIconButton(
                                    onClick = { lectureManager.togglePause() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (lectureManager.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                                        contentDescription = if (lectureManager.isPaused) "Продолжить" else "Пауза",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Button(
                                    onClick = { toggleLectureRecording() },
                                    modifier = Modifier.height(36.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Готово", fontSize = 12.sp)
                                }
                            }
                        }
                        if (lectureManager.partialHypothesis.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Filled.GraphicEq,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = lectureManager.partialHypothesis,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Attached Images & Hand Drawings (Full format & Click-to-view fullscreen)
            if (state.imageUris.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    state.imageUris.forEach { uri ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { previewingImageUri = uri },
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                AsyncImage(
                                    model = uri,
                                    contentDescription = "Рисунок или изображение заметки",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 180.dp, max = 360.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                )
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilledTonalIconButton(
                                        onClick = {
                                            digitizerInitialImageUri = uri
                                            showFontDigitizerDialog = true
                                        },
                                        modifier = Modifier.size(34.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.65f),
                                            contentColor = Color(0xFFFFD54F)
                                        )
                                    ) {
                                        Icon(Icons.Filled.AutoFixHigh, contentDescription = "Оцифровать почерк с этого фото", modifier = Modifier.size(18.dp))
                                    }
                                    FilledTonalIconButton(
                                        onClick = { previewingImageUri = uri },
                                        modifier = Modifier.size(34.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.65f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Filled.Fullscreen, contentDescription = "Открыть на весь экран", modifier = Modifier.size(20.dp))
                                    }
                                    FilledTonalIconButton(
                                        onClick = { viewModel.removeImage(uri) },
                                        modifier = Modifier.size(34.dp),
                                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.65f),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Удалить рисунок", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Attached Audio Card
            if (!state.audioUri.isNullOrBlank()) {
                AudioPlaybackCard(
                    audioUri = state.audioUri!!,
                    onDelete = { viewModel.onAudioUriChange(null) }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Notebook Paper Sheet (Book / Ruled / Grid / Blank Canvas)
            NotebookPaperCanvas(
                format = state.pageFormat,
                paperColor = paperColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 480.dp)
            ) {
                val innerPaddingStart = when (state.pageFormat) {
                    PageFormat.BOOK -> 26.dp
                    PageFormat.RULED, PageFormat.GRID -> 56.dp
                    PageFormat.KRAFT -> 20.dp
                    PageFormat.VINTAGE -> 26.dp
                    PageFormat.MIDNIGHT -> 22.dp
                    PageFormat.BLUEPRINT -> 24.dp
                    PageFormat.BLANK -> 16.dp
                }
                val innerPaddingEnd = when (state.pageFormat) {
                    PageFormat.BOOK -> 22.dp
                    PageFormat.VINTAGE -> 26.dp
                    PageFormat.MIDNIGHT -> 22.dp
                    PageFormat.BLUEPRINT -> 24.dp
                    else -> 16.dp
                }
                val innerPaddingTop = when (state.pageFormat) {
                    PageFormat.KRAFT -> 34.dp
                    PageFormat.VINTAGE -> 28.dp
                    PageFormat.BOOK -> 22.dp
                    PageFormat.RULED, PageFormat.GRID -> 16.dp
                    else -> 20.dp
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = innerPaddingStart, end = innerPaddingEnd, top = innerPaddingTop, bottom = 32.dp)
                ) {
                    // Header for styles
                    when (state.pageFormat) {
                        PageFormat.BOOK -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                HorizontalDivider(modifier = Modifier.weight(1f), color = inkColor.copy(alpha = 0.2f))
                                Text(
                                    text = "  СТРАНИЦА КНИГИ  ",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = FontFamily.Serif,
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = inkColor.copy(alpha = 0.6f)
                                    )
                                )
                                HorizontalDivider(modifier = Modifier.weight(1f), color = inkColor.copy(alpha = 0.2f))
                            }
                        }
                        PageFormat.VINTAGE -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "✦ ──────── ❖ ──────── ✦",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        letterSpacing = 2.sp,
                                        fontWeight = FontWeight.Light,
                                        color = inkColor.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        }
                        PageFormat.BLUEPRINT -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SPECIFICATION // DWG-01",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.5.sp,
                                    color = inkColor.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "SCALE 1:1",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    letterSpacing = 1.sp,
                                    color = inkColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                        PageFormat.MIDNIGHT -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "✦ ЧЕРНОВИК НА СЛАНЦЕ ✦",
                                    fontSize = 11.sp,
                                    letterSpacing = 2.sp,
                                    color = inkColor.copy(alpha = 0.6f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        PageFormat.RULED -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dateFormat = remember { SimpleDateFormat("d MMMM yyyy г.", Locale("ru")) }
                                Text(
                                    text = "Тема заметки:",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = inkColor.copy(alpha = 0.5f)
                                )
                                Text(
                                    text = dateFormat.format(Date(state.updatedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = inkColor.copy(alpha = 0.5f)
                                )
                            }
                        }
                        else -> {}
                    }

                    // Title Field
                    TextField(
                        value = state.title,
                        onValueChange = { viewModel.onTitleChange(it) },
                        placeholder = {
                            Text(
                                text = when (state.pageFormat) {
                                    PageFormat.BOOK -> "Название главы / заметки"
                                    PageFormat.VINTAGE -> "Заголовок манускрипта"
                                    PageFormat.BLUEPRINT -> "Чертёж / Проект"
                                    PageFormat.MIDNIGHT -> "Название заметки"
                                    else -> "Заголовок заметки"
                                },
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = activeFontFamily,
                                color = placeholderColor
                            )
                        },
                        textStyle = MaterialTheme.typography.titleLarge.copy(
                            fontSize = if (state.pageFormat == PageFormat.BOOK) 23.sp else 21.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = activeFontFamily,
                            color = activeInkColor
                        ),
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = activeInkColor,
                            unfocusedTextColor = activeInkColor,
                            focusedPlaceholderColor = placeholderColor,
                            unfocusedPlaceholderColor = placeholderColor,
                            cursorColor = if (state.pageFormat == PageFormat.BOOK) NotebookPalette.BookBookmark else MaterialTheme.colorScheme.primary,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.pageFormat == PageFormat.RULED) {
                        HorizontalDivider(
                            color = NotebookPalette.RuledLine,
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else if (state.pageFormat == PageFormat.VINTAGE) {
                        HorizontalDivider(
                            color = NotebookPalette.VintageFiligree.copy(alpha = 0.35f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    // Checklist mode or Regular content field
                    if (state.isChecklistMode) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val completedCount = state.checkList.count { it.isChecked }
                                val totalCount = state.checkList.size
                                Column {
                                    Text(
                                        text = "Список дел ($completedCount/$totalCount)",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = if (isDarkPaper) NotebookPalette.DarkInk else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = if (state.sortCompletedToEnd) "Выполненные переносятся вниз" else "Без автосортировки",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = placeholderColor
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (completedCount > 0) {
                                        TooltipIconButton(
                                            onClick = { viewModel.clearCompletedItems() },
                                            icon = Icons.Filled.DeleteSweep,
                                            tooltip = "Удалить выполненные пункты",
                                            tint = inkColor.copy(alpha = 0.7f)
                                        )
                                    }
                                    TooltipIconButton(
                                        onClick = { viewModel.toggleSortCompletedToEnd() },
                                        icon = Icons.Filled.SwapVert,
                                        tooltip = if (state.sortCompletedToEnd) "Отключить сортировку завершённых вниз" else "Переносить выполненные в конец",
                                        tint = if (state.sortCompletedToEnd) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    TextButton(onClick = { viewModel.toggleChecklistMode() }) {
                                        Text("В текст", color = if (isDarkPaper) NotebookPalette.DarkInk else MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            state.checkList.forEachIndexed { index, item ->
                                key(item.id) {
                                    val itemRequester = remember { BringIntoViewRequester() }

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .bringIntoViewRequester(itemRequester)
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = item.isChecked,
                                            onCheckedChange = { viewModel.toggleChecklistItem(item.id) },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = inkColor.copy(alpha = 0.6f)
                                            ),
                                            modifier = Modifier.minimumInteractiveComponentSize()
                                        )

                                        BasicTextField(
                                            value = item.text,
                                            onValueChange = { newText ->
                                                viewModel.updateChecklistItem(item.id, newText)
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                                .onFocusChanged { focusState ->
                                                    if (focusState.isFocused) {
                                                        coroutineScope.launch {
                                                            itemRequester.bringIntoView()
                                                        }
                                                    }
                                                },
                                            textStyle = if (item.isChecked) {
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    color = inkColor.copy(alpha = 0.45f),
                                                    textDecoration = TextDecoration.LineThrough,
                                                    fontFamily = activeFontFamily
                                                )
                                            } else {
                                                MaterialTheme.typography.bodyLarge.copy(
                                                    color = inkColor,
                                                    fontFamily = activeFontFamily
                                                )
                                            },
                                            cursorBrush = SolidColor(if (state.pageFormat == PageFormat.BOOK) NotebookPalette.BookBookmark else if (isDarkPaper) Color.White else MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(
                                                capitalization = KeyboardCapitalization.Sentences,
                                                imeAction = ImeAction.Next
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onNext = {
                                                    viewModel.addChecklistItem("", insertAfterId = item.id)
                                                }
                                            ),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (item.text.isEmpty()) {
                                                        Text(
                                                            text = "Пункт списка...",
                                                            style = MaterialTheme.typography.bodyLarge.copy(
                                                                color = placeholderColor,
                                                                fontFamily = activeFontFamily
                                                            )
                                                        )
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )

                                        // Delete item button with proper touch target
                                        IconButton(
                                            onClick = { viewModel.removeChecklistItem(item.id) },
                                            modifier = Modifier
                                                .size(44.dp)
                                                .minimumInteractiveComponentSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Close,
                                                contentDescription = "Удалить пункт",
                                                tint = inkColor.copy(alpha = 0.65f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = checkItemInput,
                                    onValueChange = { checkItemInput = it },
                                    placeholder = { Text("Новый пункт списка...", color = placeholderColor) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = inkColor),
                                    keyboardOptions = KeyboardOptions(
                                        capitalization = KeyboardCapitalization.Sentences,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (checkItemInput.isNotBlank()) {
                                                viewModel.addChecklistItem(checkItemInput.trim())
                                                checkItemInput = ""
                                            }
                                        }
                                    ),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = inkColor,
                                        unfocusedTextColor = inkColor,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = inkColor.copy(alpha = 0.35f),
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (checkItemInput.isNotBlank()) {
                                            viewModel.addChecklistItem(checkItemInput.trim())
                                            checkItemInput = ""
                                        }
                                    }
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Добавить пункт")
                                }
                            }
                        }
                    } else if (state.isMarkdownPreview) {
                        // Режим предварительного просмотра Markdown с подсветкой шрифта и цвета
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 360.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "ПРЕДПРОСМОТР ФОРМАТИРОВАНИЯ",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                    TextButton(onClick = { viewModel.toggleMarkdownPreview() }) {
                                        Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Редактировать", fontSize = 12.sp)
                                    }
                                }
                                MarkdownRenderer(
                                    markdownText = if (state.content.isNotBlank()) state.content else "*Заметка пуста. Нажмите «Редактировать», чтобы ввести текст.*",
                                    textColor = activeInkColor,
                                    fontFamily = activeFontFamily,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        BasicTextField(
                            value = contentTextFieldValue,
                            onValueChange = { newTfv ->
                                contentTextFieldValue = newTfv
                                viewModel.onContentChange(newTfv.text)
                            },
                            visualTransformation = remember(activeInkColor) { RichMarkdownVisualTransformation(activeInkColor) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 380.dp)
                                .bringIntoViewRequester(bringIntoViewRequester)
                                .onFocusChanged { focusState ->
                                    if (focusState.isFocused) {
                                        coroutineScope.launch {
                                            bringIntoViewRequester.bringIntoView()
                                        }
                                    }
                                },
                            textStyle = MaterialTheme.typography.bodyLarge.copy(
                                fontFamily = activeFontFamily,
                                fontSize = if (state.noteFont == NoteFontFamily.CUSTOM_DIGITIZED) 19.sp else if (state.pageFormat == PageFormat.BOOK || state.pageFormat == PageFormat.VINTAGE) 17.sp else 16.sp,
                                lineHeight = if (state.noteFont == NoteFontFamily.CUSTOM_DIGITIZED) 32.sp else if (state.pageFormat == PageFormat.RULED) 34.sp else 30.sp,
                                letterSpacing = if (state.noteFont == NoteFontFamily.CUSTOM_DIGITIZED) (UserPreferencesManager(context).getHandwritingSpacingSync() * 0.4f).sp else androidx.compose.ui.unit.TextUnit.Unspecified,
                                color = activeInkColor
                            ),
                            cursorBrush = SolidColor(if (state.pageFormat == PageFormat.BOOK) NotebookPalette.BookBookmark else if (isDarkPaper) Color.White else MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (contentTextFieldValue.text.isEmpty()) {
                                        Text(
                                            text = when (state.pageFormat) {
                                                PageFormat.BOOK -> "Начните писать главу книги или мысли..."
                                                PageFormat.VINTAGE -> "Начертайте древний свиток или заметку..."
                                                PageFormat.MIDNIGHT -> "Пишите белым мелом по грифелю..."
                                                PageFormat.BLUEPRINT -> "Чертежные заметки, расчеты, схемы..."
                                                PageFormat.KRAFT -> "Заметки на крафтовой бумаге..."
                                                else -> "Текст заметки..."
                                            },
                                            fontFamily = activeFontFamily,
                                            color = placeholderColor,
                                            fontSize = if (state.pageFormat == PageFormat.BOOK || state.pageFormat == PageFormat.VINTAGE) 17.sp else 16.sp
                                        )
                                    }
                                    innerTextField()
                                }
                            }
                        )
                    }
                }
            }

            // Headroom spacer so keyboard does not cover the bottom text or paper
            Spacer(modifier = Modifier.height(260.dp))
        }
    }

    // Dialogs
    if (showTagDialog) {
        AlertDialog(
            onDismissRequest = { showTagDialog = false },
            title = { Text("Добавить тег") },
            text = {
                Column {
                    Text(
                        text = "Теги начинаются со знака # и помогают сортировать похожие заметки.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTagInput,
                        onValueChange = { newTagInput = it },
                        placeholder = { Text("Например: работа, рецепты") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.addTag(newTagInput)
                    newTagInput = ""
                    showTagDialog = false
                }) {
                    Text("Добавить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTagDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Папка заметки") },
            text = {
                Column {
                    Text(
                        text = "Введите название новой папки или выберите из существующих:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        placeholder = { Text("Имя папки") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.availableFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Существующие папки:", style = MaterialTheme.typography.labelSmall)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.availableFolders) { f ->
                                AssistChip(
                                    onClick = { folderInput = f },
                                    label = { Text(f) }
                                )
                            }
                        }
                    }
                    if (!state.folder.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = {
                            viewModel.onFolderChange(null)
                            showFolderDialog = false
                        }) {
                            Text("Убрать из папки", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.onFolderChange(if (folderInput.isBlank()) null else folderInput.trim())
                    showFolderDialog = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showInfoDialog) {
        val completedCount = state.checkList.count { it.isChecked }
        NoteInfoDialog(
            wordCount = state.wordCount,
            charCount = state.charCount,
            createdAt = state.createdAt,
            updatedAt = state.updatedAt,
            completedChecklistItems = completedCount,
            totalChecklistItems = state.checkList.size,
            onDismiss = { showInfoDialog = false }
        )
    }

    if (showAudioDialog) {
        AudioRecordDialog(
            onDismiss = { showAudioDialog = false },
            onRecordingFinished = { path ->
                viewModel.onAudioUriChange(path)
                showAudioDialog = false
            }
        )
    }

    if (showDrawingDialog) {
        DrawingCanvasDialog(
            onDismiss = { showDrawingDialog = false },
            onSaveDrawing = { path ->
                viewModel.addImage(path)
                showDrawingDialog = false
            }
        )
    }

    if (showTemplateDialog) {
        NoteTemplateDialog(
            onDismissRequest = { showTemplateDialog = false },
            onTemplateSelect = { template ->
                showTemplateDialog = false
                viewModel.applyTemplate(template)
            }
        )
    }

    if (showPageFormatDialog) {
        PageFormatSelectorDialog(
            currentFormat = state.pageFormat,
            currentColorHex = state.colorHex,
            onDismissRequest = { showPageFormatDialog = false },
            onFormatSelect = { format ->
                showPageFormatDialog = false
                viewModel.onPageFormatChange(format)
            },
            onColorSelect = { hex ->
                viewModel.onColorChange(hex)
            }
        )
    }

    if (showShareSheet) {
        ShareNoteBottomSheet(
            note = state.toDomainNote(),
            onDismissRequest = { showShareSheet = false }
        )
    }

    if (showFontDigitizerDialog) {
        CustomFontDigitizerDialog(
            initialImageUri = digitizerInitialImageUri,
            onDismissRequest = {
                showFontDigitizerDialog = false
                digitizerInitialImageUri = null
            },
            onFontApplied = { font ->
                viewModel.onFontFormatChange(font)
                Toast.makeText(context, "Применён почерк: ${font.title}", Toast.LENGTH_SHORT).show()
            },
            onTextExtracted = { extractedText ->
                val current = state.content
                val updated = if (current.isBlank()) extractedText else "$current\n$extractedText"
                viewModel.onContentChange(updated)
            }
        )
    }

    if (showVoiceSettingsDialog) {
        VoiceSettingsDialog(
            speechManager = speechManager,
            onDismissRequest = { showVoiceSettingsDialog = false },
            onSaveSettings = { voiceId, pitch, rate ->
                speechManager.setVoice(voiceId)
                speechManager.setPitch(pitch)
                speechManager.setRate(rate)
                coroutineScope.launch {
                    preferencesManager.setTtsSettings(voiceId, pitch, rate)
                }
                showVoiceSettingsDialog = false
            }
        )
    }

    if (showAudioPerceptionDialog) {
        AudioPerceptionSettingsDialog(
            preferencesManager = preferencesManager,
            onDismissRequest = { showAudioPerceptionDialog = false }
        )
    }

    if (previewingImageUri != null) {
        val currentPreviewUri = previewingImageUri!!
        FullscreenImageViewerDialog(
            imageUri = currentPreviewUri,
            onDismissRequest = { previewingImageUri = null },
            onDigitizeHandwriting = {
                digitizerInitialImageUri = currentPreviewUri
                showFontDigitizerDialog = true
            },
            onExtractText = {
                coroutineScope.launch {
                    Toast.makeText(context, "Распознавание текста с фото...", Toast.LENGTH_SHORT).show()
                    val text = com.example.util.HandwritingPhotoDigitizer.extractTextFromImage(context, Uri.parse(currentPreviewUri))
                    val current = state.content
                    val updated = if (current.isBlank()) text else "$current\n$text"
                    viewModel.onContentChange(updated)
                    Toast.makeText(context, "Текст перенесен в заметку!", Toast.LENGTH_SHORT).show()
                }
            },
            onDeleteImage = {
                val uriToDelete = previewingImageUri
                previewingImageUri = null
                uriToDelete?.let { viewModel.removeImage(it) }
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onPinConfigured = { newPin: String ->
                preferencesManager.setPinCodeSync(newPin)
                coroutineScope.launch {
                    preferencesManager.setPinCode(newPin)
                    preferencesManager.setPinEnabled(true)
                }
                showPinSetupDialog = false
                viewModel.toggleLock()
                Toast.makeText(context, "PIN-код успешно установлен. Заметка защищена", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showPinSetupDialog = false }
        )
    }

    if (showPinUnlockDialog) {
        val savedPin = preferencesManager.getPinCodeSync()
        PinVerifyDialog(
            correctPin = savedPin,
            onSuccess = {
                showPinUnlockDialog = false
                viewModel.toggleLock()
                Toast.makeText(context, "Защита PIN-кодом снята", Toast.LENGTH_SHORT).show()
            },
            onDismissRequest = { showPinUnlockDialog = false }
        )
    }
}
