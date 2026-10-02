package com.example.presentation.screens.notes_list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.Note
import com.example.domain.model.NoteTemplate
import com.example.presentation.components.FilterBottomSheet
import com.example.presentation.components.HelpDialog
import com.example.presentation.components.NoteCard
import com.example.presentation.components.NoteTemplateDialog
import com.example.presentation.components.AiAcademicSecretaryDialog
import com.example.presentation.components.AiChatHistoryBottomSheet
import com.example.presentation.components.InteractiveOnboardingDialog
import com.example.domain.model.PageFormat
import com.example.presentation.components.FlashcardStudyDialog
import com.example.presentation.components.MindMapDialog
import com.example.presentation.components.StudyFocusTimerDialog
import com.example.presentation.components.StudyStatisticsDialog
import com.example.presentation.components.PageFormatSelectorDialog
import com.example.presentation.components.PinSetupDialog
import com.example.presentation.components.PinVerifyDialog
import com.example.presentation.components.ShareNoteBottomSheet
import com.example.presentation.components.TooltipIconButton
import com.example.ui.theme.NoteColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    viewModel: NotesListViewModel,
    onNoteClick: (Long) -> Unit,
    onNewNoteWithTemplate: ((NoteTemplate?) -> Unit)? = null,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onArchiveClick: () -> Unit,
    onTrashClick: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var showColorDialog by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var showHelpDialog by remember { mutableStateOf(false) }
    var showTemplateDialog by remember { mutableStateOf(false) }
    var showFlashcardStudyForSelection by remember { mutableStateOf(false) }
    var showMindMapForSelection by remember { mutableStateOf(false) }
    var showStudyStatisticsDialog by remember { mutableStateOf(false) }
    var showFocusTimerDialog by remember { mutableStateOf(false) }
    var showAiAcademicSecretaryDialog by remember { mutableStateOf(false) }
    var showAiChatHistoryDialog by remember { mutableStateOf(false) }
    var showInteractiveOnboardingDialog by remember { mutableStateOf(false) }
    var noteToShare by remember { mutableStateOf<Note?>(null) }
    val deletedNotes by viewModel.repository.getDeletedNotes().collectAsState(initial = emptyList())

    var targetLockedNoteId by remember { mutableStateOf<Long?>(null) }
    var showPinVerifyDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }

    val handleNoteCardClick: (Note) -> Unit = { note ->
        if (state.isSelectionMode) {
            viewModel.toggleNoteSelection(note.id)
        } else if (note.isLocked) {
            targetLockedNoteId = note.id
            if (preferencesManager.hasCustomPinSetSync()) {
                showPinVerifyDialog = true
            } else {
                showPinSetupDialog = true
            }
        } else {
            onNoteClick(note.id)
        }
    }

    val createWelcomeDemoNote: () -> Unit = {
        coroutineScope.launch {
            val demoNote = Note(
                title = "👋 Добро пожаловать! Как устроен умный конспект",
                content = """
# 🎒 Привет! Это твой умный помощник в учебе и делах

Здесь ты можешь писать конспекты, решать сложные задачи с ИИ, записывать голос учителя и форматировать доклады по ГОСТ.

---

### 🚀 Главные возможности приложения:

1. **✨ ИИ-Помощник (кнопка в правом нижнем углу и вверху):**
   - Нажми на иконку волшебной палочки, чтобы спросить что угодно.
   - Сфотографируй задачу из учебника — ИИ распишет подробное решение по шагам!
   - Попроси написать школьное сочинение, доклад или тезисы к уроку.

2. **📐 Математические формулы и наука:**
   - Формулы поддерживают красивый научный вид:
     ${'$'}${'$'}E = mc^2${'$'}${'$'}
     ${'$'}${'$'}c = \sqrt{a^2 + b^2}${'$'}${'$'}
     ${'$'}${'$'}x_{1,2} = \frac{-b \pm \sqrt{D}}{2a}${'$'}${'$'}
   - Нажми кнопку **∑** на нижней панели клавиатуры для быстрой вставки любых символов!

3. **📝 Интерактивный чек-лист дел:**
   - [x] Открыть приложение «Академические Заметки»
   - [ ] Задать свой первый вопрос ИИ-Помощнику
   - [ ] Сфотографировать конспект тетради или доску
   - [ ] Попробовать таймер концентрации «Помодоро»

4. **🎙️ Голосовой ввод и запись лекций:**
   - Нажми на иконку микрофона вверху, чтобы надиктовать мысли — знаки препинания расставятся автоматически!

5. **📤 Скачивание и печать:**
   - В верхнем меню доступен экспорт в **Word (.docx)**, **PDF** и **Excel (.xls)** с аккуратными полями и титульным листом.

---
💡 *Совет: в верхнем меню открой «Интеллект-карта» или «Карточки для запоминания», чтобы превратить эту заметку в интерактивный тренажёр перед экзаменом!*
                """.trimIndent(),
                folder = "Школа и Учеба",
                tags = listOf("старт", "подсказки", "шпаргалка"),
                colorHex = "#FFFFFF",
                textColorHex = "#1C1B1F"
            )
            viewModel.repository.insertNote(demoNote)
            Toast.makeText(context, "Обучающая заметка создана! Нажмите на неё, чтобы посмотреть", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        if (!preferencesManager.isOnboardingCompletedSync()) {
            showInteractiveOnboardingDialog = true
        }
        if (preferencesManager.isFirstLaunchSync()) {
            preferencesManager.setFirstLaunchDoneSync()
            if (state.notes.isEmpty()) {
                createWelcomeDemoNote()
            }
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("${state.selectedNoteIds.size} выбрано") },
                    navigationIcon = {
                        TooltipIconButton(
                            onClick = { viewModel.clearSelection() },
                            icon = Icons.Filled.Close,
                            tooltip = "Снять выделение"
                        )
                    },
                    actions = {
                        if (state.selectedNoteIds.isNotEmpty()) {
                            TooltipIconButton(
                                onClick = {
                                    showFlashcardStudyForSelection = true
                                },
                                icon = Icons.Filled.School,
                                tooltip = "Учить выбранные конспекты (Карточки)"
                            )
                            TooltipIconButton(
                                onClick = {
                                    showMindMapForSelection = true
                                },
                                icon = Icons.Filled.Hub,
                                tooltip = "Интеллект-карта выбранных конспектов"
                            )
                            TooltipIconButton(
                                onClick = {
                                    showFocusTimerDialog = true
                                },
                                icon = Icons.Filled.HourglassBottom,
                                tooltip = "Таймер концентрации по выбранным"
                            )
                            TooltipIconButton(
                                onClick = {
                                    val selectedList = state.notes.filter { state.selectedNoteIds.contains(it.id) }
                                    if (selectedList.size == 1) {
                                        noteToShare = selectedList.first()
                                    } else if (selectedList.size > 1) {
                                        val combined = Note(
                                            id = 0L,
                                            title = "Подборка заметок (${selectedList.size})",
                                            content = selectedList.joinToString("\n\n---\n\n") { n ->
                                                val header = if (n.title.isNotBlank()) "## ${n.title}\n" else ""
                                                val meta = if (!n.folder.isNullOrBlank()) "*Папка: ${n.folder}*\n\n" else ""
                                                "$header$meta${n.content}"
                                            },
                                            updatedAt = System.currentTimeMillis()
                                        )
                                        noteToShare = combined
                                    }
                                },
                                icon = Icons.Filled.Share,
                                tooltip = "Поделиться выбранными"
                            )
                        }
                        val anyUnpinned = state.notes.filter { state.selectedNoteIds.contains(it.id) }.any { !it.isPinned }
                        TooltipIconButton(
                            onClick = { viewModel.pinSelectedNotes(anyUnpinned) },
                            icon = if (anyUnpinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            tooltip = if (anyUnpinned) "Закрепить выбранные" else "Открепить выбранные"
                        )
                        val anyUnlocked = state.notes.filter { state.selectedNoteIds.contains(it.id) }.any { !it.isLocked }
                        TooltipIconButton(
                            onClick = { viewModel.lockSelectedNotes(anyUnlocked) },
                            icon = if (anyUnlocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                            tooltip = if (anyUnlocked) "Защитить выбранные PIN-кодом" else "Снять защиту PIN-кодом"
                        )
                        TooltipIconButton(
                            onClick = { viewModel.selectAllNotes() },
                            icon = Icons.Filled.SelectAll,
                            tooltip = "Выбрать все заметки"
                        )
                        TooltipIconButton(
                            onClick = { showColorDialog = true },
                            icon = Icons.Filled.Palette,
                            tooltip = "Изменить цвет выбранных"
                        )
                        TooltipIconButton(
                            onClick = { showFormatDialog = true },
                            icon = Icons.Filled.AutoStories,
                            tooltip = "Стиль тетради/листа для выбранных"
                        )
                        TooltipIconButton(
                            onClick = { showFolderDialog = true },
                            icon = Icons.Filled.Folder,
                            tooltip = "Переместить в папку"
                        )
                        TooltipIconButton(
                            onClick = { viewModel.archiveSelectedNotes() },
                            icon = Icons.Filled.Archive,
                            tooltip = "Переместить в архив"
                        )
                        TooltipIconButton(
                            onClick = { viewModel.deleteSelectedNotes() },
                            icon = Icons.Filled.Delete,
                            tooltip = "Удалить в корзину"
                        )
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = state.selectedFolder ?: "Все заметки",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleLarge
                            )
                            if (state.selectedTag != null || state.selectedColor != null || state.selectedFormat != null) {
                                Text(
                                    text = buildString {
                                        if (state.selectedFormat != null) append("формат: ${state.selectedFormat} ")
                                        if (state.selectedTag != null) append("#${state.selectedTag} ")
                                        if (state.selectedColor != null) append("цветовой фильтр")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    actions = {
                        TooltipIconButton(
                            onClick = { showAiAcademicSecretaryDialog = true },
                            icon = Icons.Filled.AutoAwesome,
                            tooltip = "ИИ-Помощник (все вопросы)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        TooltipIconButton(
                            onClick = onSearchClick,
                            icon = Icons.Filled.Search,
                            tooltip = "Поиск по заметкам и тегам"
                        )
                        TooltipIconButton(
                            onClick = { showStudyStatisticsDialog = true },
                            icon = Icons.Filled.Analytics,
                            tooltip = "Академическая статистика"
                        )
                        TooltipIconButton(
                            onClick = { showFocusTimerDialog = true },
                            icon = Icons.Filled.HourglassBottom,
                            tooltip = "Таймер концентрации (Помодоро)",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        TooltipIconButton(
                            onClick = { showFilterSheet = true },
                            icon = Icons.AutoMirrored.Filled.Sort,
                            tooltip = "Сортировка и фильтры"
                        )
                        TooltipIconButton(
                            onClick = { viewModel.toggleLayout() },
                            icon = if (state.isGridLayout) Icons.Filled.ViewAgenda else Icons.Filled.GridView,
                            tooltip = if (state.isGridLayout) "Вид: в один столбец" else "Вид: в две колонки"
                        )

                        var menuExpanded by remember { mutableStateOf(false) }
                        var activeListSubMenu by remember { mutableStateOf<String?>(null) }
                        Box {
                            TooltipIconButton(
                                onClick = {
                                    activeListSubMenu = null
                                    menuExpanded = true
                                },
                                icon = Icons.Filled.MoreVert,
                                tooltip = "Главное меню"
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = {
                                    menuExpanded = false
                                    activeListSubMenu = null
                                }
                            ) {
                                if (activeListSubMenu == null) {
                                    // Main Categories
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("✨ ИИ-Помощник (все вопросы и ответы)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                Text("Общий ассистент, Профессор, Секретарь, ГОСТ, код", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showAiAcademicSecretaryDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("💬 История диалогов с ИИ", fontWeight = FontWeight.SemiBold)
                                                Text("Сохраненные сессии бесед, поиск и экспорт", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Forum, null, tint = MaterialTheme.colorScheme.tertiary) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showAiChatHistoryDialog = true
                                        }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("📁 Управление заметками", fontWeight = FontWeight.SemiBold)
                                                Text("Архив, корзина, хранилище", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.FolderSpecial, null, tint = MaterialTheme.colorScheme.primary) },
                                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.size(16.dp)) },
                                        onClick = { activeListSubMenu = "storage" }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("📑 Шаблоны документов ГОСТ", fontWeight = FontWeight.SemiBold)
                                                Text("Заявления, акты, служебные записки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.secondary) },
                                        onClick = {
                                            menuExpanded = false
                                            showTemplateDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("📊 Продуктивность и учёба", fontWeight = FontWeight.SemiBold)
                                                Text("Статистика, дедлайны, таймер Помодоро", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Analytics, null, tint = MaterialTheme.colorScheme.tertiary) },
                                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.size(16.dp)) },
                                        onClick = { activeListSubMenu = "productivity" }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("⚙️ Настройки и справка", fontWeight = FontWeight.SemiBold)
                                                Text("Gemini API, безопасность, инструкция", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                        trailingIcon = { Icon(Icons.AutoMirrored.Filled.NavigateNext, null, modifier = Modifier.size(16.dp)) },
                                        onClick = { activeListSubMenu = "settings" }
                                    )
                                } else if (activeListSubMenu == "storage") {
                                    // Submenu: Storage & Organization
                                    DropdownMenuItem(
                                        text = { Text("← Назад в главное меню", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { activeListSubMenu = null }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Архив заметок")
                                                Text("Скрытые из основного списка", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            onArchiveClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Корзина", color = MaterialTheme.colorScheme.error)
                                                Text("Удаленные заметки (восстановление)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            onTrashClick()
                                        }
                                    )
                                } else if (activeListSubMenu == "productivity") {
                                    // Submenu: Productivity & Study
                                    DropdownMenuItem(
                                        text = { Text("← Назад в главное меню", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { activeListSubMenu = null }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Академическая статистика")
                                                Text("Прогресс конспектов, объем, дедлайны", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Analytics, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showStudyStatisticsDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Таймер концентрации (Помодоро)")
                                                Text("Фокусировка, фоновые звуки и дзен", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.HourglassBottom, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showFocusTimerDialog = true
                                        }
                                    )
                                } else if (activeListSubMenu == "settings") {
                                    // Submenu: Settings & Help
                                    DropdownMenuItem(
                                        text = { Text("← Назад в главное меню", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary) },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = { activeListSubMenu = null }
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Параметры и настройки")
                                                Text("Gemini API, PIN-код, тема, бэкап", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            onSettingsClick()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("Справка и подсказки")
                                                Text("Руководство по возможностям", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.HelpOutline, null) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showHelpDialog = true
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text("🎓 Обучение и гид по функциям")
                                                Text("Интерактивный тур для новичков и школьников", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Filled.School, null, tint = MaterialTheme.colorScheme.primary) },
                                        onClick = {
                                            menuExpanded = false
                                            activeListSubMenu = null
                                            showInteractiveOnboardingDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showAiAcademicSecretaryDialog = true },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    text = { Text("ИИ-Помощник", fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )

                SmallFloatingActionButton(
                    onClick = { showTemplateDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(imageVector = Icons.Filled.AutoAwesome, contentDescription = "Создать по шаблону")
                }

                FloatingActionButton(
                    onClick = {
                        if (onNewNoteWithTemplate != null) {
                            onNewNoteWithTemplate(null)
                        } else {
                            onNoteClick(0L)
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Создать новую заметку")
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Active Filter Chips Bar (if any filter is active)
            val hasActiveFilter = state.selectedColor != null || state.selectedTag != null || state.selectedFolder != null || state.selectedFormat != null
            if (hasActiveFilter) {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        item {
                            SuggestionChip(
                                onClick = { viewModel.clearAllFilters() },
                                label = { Text("Сбросить всё") },
                                icon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(14.dp)) }
                            )
                        }
                        if (state.selectedFormat != null) {
                            item {
                                val fmtTitle = try {
                                    PageFormat.valueOf(state.selectedFormat!!).title
                                } catch (_: Exception) { state.selectedFormat!! }
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.setFormatFilter(null) },
                                    label = { Text("Формат: $fmtTitle") },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }
                        if (state.selectedFolder != null) {
                            item {
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.setFolderFilter(null) },
                                    label = { Text("Папка: ${state.selectedFolder}") },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }
                        if (state.selectedTag != null) {
                            item {
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.setTagFilter(null) },
                                    label = { Text("#${state.selectedTag}") },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }
                        if (state.selectedColor != null) {
                            item {
                                InputChip(
                                    selected = true,
                                    onClick = { viewModel.setColorFilter(null) },
                                    label = { Text("Цветной фильтр") },
                                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(12.dp)) }
                                )
                            }
                        }
                    }
                }
            }

            // Horizontal Course / Folder Tabs Bar
            if (state.availableFolders.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedFolder == null,
                            onClick = { viewModel.setFolderFilter(null) },
                            label = { Text("Все заметки (${state.notes.size})") },
                            leadingIcon = if (state.selectedFolder == null) {
                                { Icon(Icons.Filled.AllInclusive, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }

                    items(state.availableFolders) { folderName ->
                        val count = state.notes.count { it.folder == folderName }
                        val isSelected = state.selectedFolder == folderName
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    viewModel.setFolderFilter(null)
                                } else {
                                    viewModel.setFolderFilter(folderName)
                                }
                            },
                            label = { Text("$folderName ($count)") },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isSelected) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                val notesToDisplay = state.filteredNotes

                if (notesToDisplay.isEmpty()) {
                    if (hasActiveFilter) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(72.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Нет заметок с такими фильтрами",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Попробуйте изменить параметры поиска или сбросить фильтры",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(onClick = { viewModel.clearAllFilters() }) {
                                Text("Сбросить фильтры")
                            }
                        }
                    } else {
                        // Interactive Hero Onboarding for new users & students
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Welcome Hero Card
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(20.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.AutoAwesome,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Добро пожаловать в Умные Заметки!",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Твой карманный помощник для учебы, уроков, решения задач и документов. Выбери действие для быстрого старта:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Быстрый старт:",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 4.dp, bottom = 8.dp)
                            )

                            // Quick Action 1: Ask AI or Solve Task
                            QuickStartHeroCard(
                                title = "✨ Спросить ИИ или решить задачу",
                                subtitle = "Пошаговые решения по математике, физике, сочинения, код и любые вопросы",
                                icon = Icons.Filled.AutoAwesome,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                onClick = { showAiAcademicSecretaryDialog = true }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Action 2: New Note
                            QuickStartHeroCard(
                                title = "📝 Новый конспект или домашка",
                                subtitle = "Чистый тетрадный лист в клетку или линейку с поддержкой формул и списков",
                                icon = Icons.Filled.EditNote,
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                onClick = {
                                    if (onNewNoteWithTemplate != null) {
                                        onNewNoteWithTemplate(null)
                                    } else {
                                        onNoteClick(0L)
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Action 3: Ready Templates
                            QuickStartHeroCard(
                                title = "📑 Готовые шаблоны (доклады, рефераты)",
                                subtitle = "Оформление по ГОСТ с титульным листом и оглавлением в один клик",
                                icon = Icons.Filled.Description,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                onClick = { showTemplateDialog = true }
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Quick Action 4: Demo Note
                            QuickStartHeroCard(
                                title = "👋 Создать обучающую демо-заметку",
                                subtitle = "Посмотреть наглядный пример с формулами, чек-листом и подсказками",
                                icon = Icons.Filled.School,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = { createWelcomeDemoNote() }
                            )
                        }
                    }
                } else {
                    if (state.isGridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notesToDisplay, key = { "${it.id}_${it.updatedAt}_${it.createdAt}" }) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = { handleNoteCardClick(note) },
                                    onLongClick = { viewModel.toggleNoteSelection(note.id) },
                                    onPinClick = { viewModel.togglePin(note) },
                                    onShareClick = { noteToShare = note },
                                    isSelected = state.selectedNoteIds.contains(note.id),
                                    isSelectionMode = state.isSelectionMode
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(notesToDisplay, key = { "${it.id}_${it.updatedAt}_${it.createdAt}" }) { note ->
                                NoteCard(
                                    note = note,
                                    onClick = { handleNoteCardClick(note) },
                                    onLongClick = { viewModel.toggleNoteSelection(note.id) },
                                    onPinClick = { viewModel.togglePin(note) },
                                    onShareClick = { noteToShare = note },
                                    isSelected = state.selectedNoteIds.contains(note.id),
                                    isSelectionMode = state.isSelectionMode
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        FilterBottomSheet(
            selectedColor = state.selectedColor,
            onColorSelected = { viewModel.setColorFilter(it) },
            selectedTag = state.selectedTag,
            onTagSelected = { viewModel.setTagFilter(it) },
            availableTags = state.availableTags,
            selectedFolder = state.selectedFolder,
            onFolderSelected = { viewModel.setFolderFilter(it) },
            availableFolders = state.availableFolders,
            selectedFormat = state.selectedFormat,
            onFormatSelected = { viewModel.setFormatFilter(it) },
            sortOrder = state.sortOrder,
            onSortOrderSelected = { viewModel.setSortOrder(it) },
            onClearAllFilters = {
                viewModel.clearAllFilters()
                showFilterSheet = false
            },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showHelpDialog) {
        HelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showFolderDialog) {
        AlertDialog(
            onDismissRequest = { showFolderDialog = false },
            title = { Text("Переместить в папку") },
            text = {
                Column {
                    Text(
                        text = "Выберите папку для выбранных заметок (${state.selectedNoteIds.size}):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("Имя папки") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (state.availableFolders.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(state.availableFolders) { f ->
                                AssistChip(
                                    onClick = { newFolderName = f },
                                    label = { Text(f) }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            viewModel.moveSelectedToFolder(null)
                            showFolderDialog = false
                        }
                    ) {
                        Text("Убрать из папки")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        viewModel.moveSelectedToFolder(newFolderName.trim())
                    }
                    showFolderDialog = false
                    newFolderName = ""
                }) {
                    Text("Переместить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFolderDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showColorDialog) {
        AlertDialog(
            onDismissRequest = { showColorDialog = false },
            title = { Text("Выберите цвет для заметок") },
            text = {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(NoteColors) { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.LightGray }
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable {
                                    viewModel.changeColorForSelected(hex)
                                    showColorDialog = false
                                }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showColorDialog = false }) { Text("Закрыть") }
            }
        )
    }

    if (showFormatDialog) {
        PageFormatSelectorDialog(
            currentFormat = PageFormat.BOOK,
            currentColorHex = "#FFFFFF",
            onDismissRequest = { showFormatDialog = false },
            onFormatSelect = { format ->
                viewModel.changePageFormatForSelected(format.name)
                showFormatDialog = false
            },
            onColorSelect = { hex ->
                viewModel.changeColorForSelected(hex)
                showFormatDialog = false
            }
        )
    }

    if (showStudyStatisticsDialog) {
        StudyStatisticsDialog(
            notes = state.notes,
            onDismissRequest = { showStudyStatisticsDialog = false }
        )
    }

    if (showFocusTimerDialog) {
        val selectedNotes = state.notes.filter { state.selectedNoteIds.contains(it.id) }
        val title = if (selectedNotes.isNotEmpty()) {
            selectedNotes.joinToString(", ") { it.title.ifBlank { "Без названия" } }
        } else ""
        StudyFocusTimerDialog(
            noteTitle = title,
            onDismissRequest = { showFocusTimerDialog = false }
        )
    }

    if (showTemplateDialog) {
        NoteTemplateDialog(
            onDismissRequest = { showTemplateDialog = false },
            onTemplateSelect = { template ->
                showTemplateDialog = false
                if (onNewNoteWithTemplate != null) {
                    onNewNoteWithTemplate(template)
                } else {
                    onNoteClick(0L)
                }
            }
        )
    }

    if (noteToShare != null) {
        ShareNoteBottomSheet(
            note = noteToShare!!,
            onDismissRequest = { noteToShare = null }
        )
    }

    if (showPinVerifyDialog) {
        PinVerifyDialog(
            correctPin = preferencesManager.getPinCodeSync(),
            onSuccess = {
                showPinVerifyDialog = false
                targetLockedNoteId?.let { onNoteClick(it) }
                targetLockedNoteId = null
            },
            onDismissRequest = {
                showPinVerifyDialog = false
                targetLockedNoteId = null
            }
        )
    }

    if (showPinSetupDialog) {
        PinSetupDialog(
            onPinConfigured = { pin ->
                coroutineScope.launch {
                    preferencesManager.setPinCode(pin)
                    preferencesManager.setPinEnabled(true)
                    showPinSetupDialog = false
                    targetLockedNoteId?.let { onNoteClick(it) }
                    targetLockedNoteId = null
                }
            },
            onDismissRequest = {
                showPinSetupDialog = false
                targetLockedNoteId = null
            }
        )
    }

    if (showFlashcardStudyForSelection) {
        val selectedNotes = state.notes.filter { state.selectedNoteIds.contains(it.id) }
        val combinedTitle = if (selectedNotes.size == 1) selectedNotes.first().title else "Выбранные конспекты (${selectedNotes.size})"
        val combinedContent = selectedNotes.joinToString("\n\n---\n\n") { "${it.title}\n${it.content}" }
        FlashcardStudyDialog(
            noteTitle = combinedTitle,
            noteContent = combinedContent,
            onDismissRequest = { showFlashcardStudyForSelection = false }
        )
    }

    if (showMindMapForSelection) {
        val selectedNotes = state.notes.filter { state.selectedNoteIds.contains(it.id) }
        val combinedTitle = if (selectedNotes.size == 1) selectedNotes.first().title else "Интеллект-карта (${selectedNotes.size} консп.)"
        val combinedContent = selectedNotes.joinToString("\n\n---\n\n") { "${it.title}\n${it.content}" }
        MindMapDialog(
            noteTitle = combinedTitle,
            noteContent = combinedContent,
            onDismissRequest = { showMindMapForSelection = false },
            onNavigateToOffset = null
        )
    }

    if (showAiAcademicSecretaryDialog) {
        AiAcademicSecretaryDialog(
            initialNote = null,
            repository = viewModel.repository,
            onDismissRequest = { showAiAcademicSecretaryDialog = false },
            onInsertTextIntoNote = null
        )
    }

    if (showAiChatHistoryDialog) {
        AiChatHistoryBottomSheet(
            currentSessionId = "",
            onDismissRequest = { showAiChatHistoryDialog = false },
            onSelectSession = { session ->
                com.example.util.AiChatSessionManager.setActiveSessionId(context, session.id)
                showAiChatHistoryDialog = false
                showAiAcademicSecretaryDialog = true
            },
            onStartNewSession = {
                showAiChatHistoryDialog = false
                com.example.util.AiChatSessionManager.clearActiveSession(context)
                showAiAcademicSecretaryDialog = true
            }
        )
    }

    if (showInteractiveOnboardingDialog) {
        InteractiveOnboardingDialog(
            onDismissRequest = {
                showInteractiveOnboardingDialog = false
            },
            onComplete = {
                preferencesManager.setOnboardingCompletedSync(true)
                showInteractiveOnboardingDialog = false
            },
            onOpenAiAssistant = {
                preferencesManager.setOnboardingCompletedSync(true)
                showInteractiveOnboardingDialog = false
                showAiAcademicSecretaryDialog = true
            },
            onCreateDemoNote = {
                preferencesManager.setOnboardingCompletedSync(true)
                showInteractiveOnboardingDialog = false
                createWelcomeDemoNote()
            }
        )
    }
}

@Composable
private fun QuickStartHeroCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor.copy(alpha = 0.55f),
        border = androidx.compose.foundation.BorderStroke(1.dp, containerColor.copy(alpha = 0.8f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(containerColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.NavigateNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
