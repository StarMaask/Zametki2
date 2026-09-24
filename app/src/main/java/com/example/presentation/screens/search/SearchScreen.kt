package com.example.presentation.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) {
            repository.searchNotes(searchQuery).collectLatest { results ->
                searchResults = results
            }
        } else {
            searchResults = emptyList()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Поиск по тексту, заголовку, тегам...") },
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
        if (searchQuery.isBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Быстрый поиск",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Введите слова из заголовка или текста заметки для мгновенной фильтрации",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else if (searchResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Ничего не найдено",
                        style = MaterialTheme.typography.titleMedium,
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
                    .padding(paddingValues)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(searchResults, key = { "${it.id}_${it.updatedAt}_${it.createdAt}" }) { note ->
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
