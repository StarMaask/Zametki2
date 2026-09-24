package com.example.presentation.screens.trash

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.presentation.components.NoteCard
import com.example.presentation.components.TooltipIconButton
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val notes by repository.getDeletedNotes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Корзина") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Вернуться назад"
                    )
                },
                actions = {
                    if (notes.isNotEmpty()) {
                        TooltipIconButton(
                            onClick = { showClearDialog = true },
                            icon = Icons.Filled.DeleteForever,
                            tooltip = "Очистить всю корзину",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        if (notes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Корзина пуста",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Удаленные заметки можно восстановить отсюда",
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
                items(notes, key = { "${it.id}_${it.updatedAt}_${it.createdAt}" }) { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            NoteCard(
                                note = note,
                                onClick = {},
                                onLongClick = {},
                                onPinClick = {}
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TooltipIconButton(
                            onClick = {
                                scope.launch {
                                    repository.updateNote(note.copy(isDeleted = false, updatedAt = System.currentTimeMillis()))
                                }
                            },
                            icon = Icons.Filled.Restore,
                            tooltip = "Восстановить заметку"
                        )
                        TooltipIconButton(
                            onClick = {
                                scope.launch {
                                    repository.deleteNotePermanently(note.id)
                                }
                            },
                            icon = Icons.Filled.DeleteForever,
                            tooltip = "Удалить навсегда",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Очистить корзину?") },
            text = { Text("Все заметки (${notes.size}) будут удалены без возможности восстановления.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            repository.clearTrash()
                            showClearDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Отмена") }
            }
        )
    }
}
