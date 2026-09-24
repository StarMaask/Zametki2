package com.example.presentation.screens.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.presentation.components.NoteCard
import com.example.presentation.components.TooltipIconButton
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: NoteRepository,
    onNoteClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Note>>(emptyList()) }
    var allNotes by remember { mutableStateOf<List<Note>>(emptyList()) }
    var filterType by remember { mutableStateOf("ALL") } // ALL, TASKS, AUDIO, IMAGES

    LaunchedEffect(Unit) {
        repository.getActiveNotes().collectLatest { notes ->
            allNotes = notes
        }
    }

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            repository.searchNotes(searchQuery).collectLatest { results ->
                searchResults = results
            }
        } else {
            searchResults = emptyList()
        }
    }

    val displayResults = remember(searchResults, filterType) {
        when (filterType) {
            "TASKS" -> searchResults.filter { it.checkListJson.isNotBlank() }
            "AUDIO" -> searchResults.filter { !it.audioUri.isNullOrBlank() }
            "IMAGES" -> searchResults.filter { it.imageUrisJson.isNotBlank() && it.imageUrisJson != "[]" }
            else -> searchResults
        }
    }

    val popularTags = remember(allNotes) {
        allNotes.flatMap { it.tags }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск конспектов, формул, тегов...") },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Вернуться назад"
                    )
                },
                actions = {
                    if (searchQuery.isNotEmpty()) {
                        TooltipIconButton(
                            onClick = { searchQuery = "" },
                            icon = Icons.Filled.Clear,
                            tooltip = "Очистить строку поиска"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Quick Filter Type Chips when searching
            if (searchQuery.isNotBlank() && searchResults.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        FilterChip(
                            selected = filterType == "ALL",
                            onClick = { filterType = "ALL" },
                            label = { Text("Все (${searchResults.size})") }
                        )
                    }
                    val tasksCount = searchResults.count { it.checkListJson.isNotBlank() }
                    if (tasksCount > 0) {
                        item {
                            FilterChip(
                                selected = filterType == "TASKS",
                                onClick = { filterType = if (filterType == "TASKS") "ALL" else "TASKS" },
                                label = { Text("Задачи ($tasksCount)") },
                                leadingIcon = { Icon(Icons.Filled.CheckBox, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    val audioCount = searchResults.count { !it.audioUri.isNullOrBlank() }
                    if (audioCount > 0) {
                        item {
                            FilterChip(
                                selected = filterType == "AUDIO",
                                onClick = { filterType = if (filterType == "AUDIO") "ALL" else "AUDIO" },
                                label = { Text("Аудио ($audioCount)") },
                                leadingIcon = { Icon(Icons.Filled.Mic, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                    val imageCount = searchResults.count { it.imageUrisJson.isNotBlank() && it.imageUrisJson != "[]" }
                    if (imageCount > 0) {
                        item {
                            FilterChip(
                                selected = filterType == "IMAGES",
                                onClick = { filterType = if (filterType == "IMAGES") "ALL" else "IMAGES" },
                                label = { Text("С фото ($imageCount)") },
                                leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }

            if (searchQuery.isBlank()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Быстрый академический поиск",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Поиск по заголовкам, тексту лекций, тегам и предметам",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (popularTags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(28.dp))
                        Text(
                            text = "Популярные теги и темы:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(popularTags) { tag ->
                                SuggestionChip(
                                    onClick = { searchQuery = tag },
                                    label = { Text("#$tag") },
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }

                    // Fast suggestion chips
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Быстрый поиск по типам:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = { searchQuery = "лекция" },
                            label = { Text("Лекции") },
                            leadingIcon = { Icon(Icons.Filled.School, null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = { searchQuery = "экзамен" },
                            label = { Text("Экзамены") },
                            leadingIcon = { Icon(Icons.Filled.Event, null, modifier = Modifier.size(16.dp)) }
                        )
                        AssistChip(
                            onClick = { searchQuery = "лабораторная" },
                            label = { Text("Лабораторные") },
                            leadingIcon = { Icon(Icons.Filled.Science, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            } else if (displayResults.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Ничего не найдено",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "По запросу «$searchQuery» совпадений не обнаружено",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(displayResults, key = { "${it.id}_${it.updatedAt}_${it.createdAt}" }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onNoteClick(note.id) },
                            onLongClick = {},
                            onPinClick = {}
                        )
                    }
                }
            }
        }
    }
}
