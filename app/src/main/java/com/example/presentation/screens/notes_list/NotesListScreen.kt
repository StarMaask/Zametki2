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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
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
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.Note
import com.example.domain.model.NoteTemplate
import com.example.presentation.components.FilterBottomSheet
import com.example.presentation.components.HelpDialog
import com.example.presentation.components.NoteCard
import com.example.presentation.components.NoteTemplateDialog
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
    var noteToShare by remember { mutableStateOf<Note?>(null) }

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
                        Box {
                            TooltipIconButton(
                                onClick = { menuExpanded = true },
                                icon = Icons.Filled.MoreVert,
                                tooltip = "Главное меню"
                            )
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Архив")
                                            Text("Архивированные заметки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Archive, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onArchiveClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Корзина")
                                            Text("Удаленные заметки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Delete, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onTrashClick()
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Академическая статистика")
                                            Text("Прогресс учебы, дедлайны, объем", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Analytics, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        showStudyStatisticsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Таймер учёбы (Помодоро)")
                                            Text("Интервалы концентрации и фоновые звуки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.HourglassBottom, null, tint = MaterialTheme.colorScheme.primary) },
                                    onClick = {
                                        menuExpanded = false
                                        showFocusTimerDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Справка и подсказки")
                                            Text("Как пользоваться функциями", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.HelpOutline, null) },
                                    onClick = {
                                        menuExpanded = false
                                        showHelpDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text("Настройки")
                                            Text("PIN-код, темы, бэкап", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    leadingIcon = { Icon(Icons.Filled.Settings, null) },
                                    onClick = {
                                        menuExpanded = false
                                        onSettingsClick()
                                    }
                                )
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
                            text = if (hasActiveFilter) "Нет заметок с такими фильтрами" else "У вас пока нет заметок",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (hasActiveFilter) "Попробуйте изменить параметры поиска или сбросить фильтры" else "Нажмите кнопку «+», чтобы создать заметку или чек-лист",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        if (hasActiveFilter) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(onClick = { viewModel.clearAllFilters() }) {
                                Text("Сбросить фильтры")
                            }
                        }
                    }
                } else {
                    if (state.isGridLayout) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
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
}
