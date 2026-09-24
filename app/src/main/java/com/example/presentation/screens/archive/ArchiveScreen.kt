package com.example.presentation.screens.archive

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
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
fun ArchiveScreen(
    repository: NoteRepository,
    onNoteClick: (Long) -> Unit,
    onBack: () -> Unit
) {
    val notes by repository.getArchivedNotes().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Архив") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Вернуться назад"
                    )
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
                        imageVector = Icons.Filled.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Архив пуст",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Заметки, скрытые из основного списка, будут храниться здесь",
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
                                onClick = { onNoteClick(note.id) },
                                onLongClick = {},
                                onPinClick = {}
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        TooltipIconButton(
                            onClick = {
                                scope.launch {
                                    repository.updateNote(note.copy(isArchived = false, updatedAt = System.currentTimeMillis()))
                                }
                            },
                            icon = Icons.Filled.Unarchive,
                            tooltip = "Восстановить из архива"
                        )
                    }
                }
            }
        }
    }
}
