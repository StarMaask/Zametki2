package com.example.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.AiAcademicAndSecretaryService
import com.example.util.AiChatSessionManager
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatHistoryBottomSheet(
    currentSessionId: String,
    onDismissRequest: () -> Unit,
    onSelectSession: (AiChatSessionManager.SavedAiSession) -> Unit,
    onStartNewSession: () -> Unit
) {
    val context = LocalContext.current
    var sessions by remember {
        mutableStateOf(AiChatSessionManager.getAllSessions(context))
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedRoleFilter by remember { mutableStateOf<AiAcademicAndSecretaryService.AssistantRole?>(null) }
    var sessionToRename by remember { mutableStateOf<AiChatSessionManager.SavedAiSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var showClearAllConfirmDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember {
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
    }

    val filteredSessions = remember(sessions, searchQuery, selectedRoleFilter) {
        sessions.filter { session ->
            val matchesRole = selectedRoleFilter == null || session.role == selectedRoleFilter
            val matchesQuery = searchQuery.isBlank() ||
                session.title.contains(searchQuery, ignoreCase = true) ||
                session.messages.any { it.text.contains(searchQuery, ignoreCase = true) }
            matchesRole && matchesQuery
        }
    }

    if (sessionToRename != null) {
        AlertDialog(
            onDismissRequest = { sessionToRename = null },
            title = { Text("Переименовать диалог") },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Название диалога") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val session = sessionToRename
                        if (session != null && renameInput.isNotBlank()) {
                            AiChatSessionManager.renameSession(context, session.id, renameInput)
                            sessions = AiChatSessionManager.getAllSessions(context)
                        }
                        sessionToRename = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { sessionToRename = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showClearAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirmDialog = false },
            title = { Text("Очистить всю историю?") },
            text = { Text("Все сохраненные диалоги с ИИ будут удалены без возможности восстановления.") },
            confirmButton = {
                Button(
                    onClick = {
                        AiChatSessionManager.clearAllSessions(context)
                        sessions = emptyList()
                        showClearAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Удалить всё")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "История диалогов с ИИ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${sessions.size} диалогов сохранено",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sessions.isNotEmpty()) {
                        IconButton(onClick = { showClearAllConfirmDialog = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить историю", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action: Start new dialog
            FilledTonalButton(
                onClick = {
                    onStartNewSession()
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 10.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Начать новый диалог с чистого листа", fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search input
            if (sessions.isNotEmpty()) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Поиск по истории бесед...", fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Очистить", modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Role Filter Chips
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == null,
                            onClick = { selectedRoleFilter = null },
                            label = { Text("Все (${sessions.size})", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.GENERAL,
                            onClick = {
                                selectedRoleFilter = if (selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.GENERAL) null else AiAcademicAndSecretaryService.AssistantRole.GENERAL
                            },
                            label = { Text("🌐 Общие", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR,
                            onClick = {
                                selectedRoleFilter = if (selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.PROFESSOR) null else AiAcademicAndSecretaryService.AssistantRole.PROFESSOR
                            },
                            label = { Text("🎓 Профессор", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.SECRETARY,
                            onClick = {
                                selectedRoleFilter = if (selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.SECRETARY) null else AiAcademicAndSecretaryService.AssistantRole.SECRETARY
                            },
                            label = { Text("💼 Секретарь", fontSize = 12.sp) }
                        )
                    }
                    item {
                        FilterChip(
                            selected = selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR,
                            onClick = {
                                selectedRoleFilter = if (selectedRoleFilter == AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR) null else AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR
                            },
                            label = { Text("✍️ Редактор", fontSize = 12.sp) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }

            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.ChatBubbleOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "История диалогов пуста",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Все ваши диалоги с Общим помощником, Профессором и Секретарем будут автоматически сохраняться здесь",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                    }
                }
            } else if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Ничего не найдено по запросу «$searchQuery»",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredSessions, key = { it.id }) { session ->
                        val isCurrent = session.id == currentSessionId
                        val roleInfo = when (session.role) {
                            AiAcademicAndSecretaryService.AssistantRole.GENERAL -> Triple(
                                Icons.Filled.AutoAwesome,
                                "Общий AI",
                                MaterialTheme.colorScheme.tertiary
                            )
                            AiAcademicAndSecretaryService.AssistantRole.PROFESSOR -> Triple(
                                Icons.Filled.School,
                                "Профессор",
                                MaterialTheme.colorScheme.primary
                            )
                            AiAcademicAndSecretaryService.AssistantRole.SECRETARY -> Triple(
                                Icons.Filled.Work,
                                "Секретарь",
                                MaterialTheme.colorScheme.secondary
                            )
                            AiAcademicAndSecretaryService.AssistantRole.CREATIVE_EDITOR -> Triple(
                                Icons.Filled.EditNote,
                                "Редактор",
                                MaterialTheme.colorScheme.primary
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    onSelectSession(session)
                                    onDismissRequest()
                                },
                            color = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = if (isCurrent) {
                                androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            } else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Role icon
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = roleInfo.third.copy(alpha = 0.15f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = roleInfo.first,
                                            contentDescription = null,
                                            tint = roleInfo.third,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(10.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = session.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = roleInfo.second,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = roleInfo.third,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = " • ${session.messages.size} сообщ.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = " • ${dateFormatter.format(Date(session.updatedAt))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            renameInput = session.title
                                            sessionToRename = session
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Переименовать",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            AiChatSessionManager.deleteSession(context, session.id)
                                            sessions = AiChatSessionManager.getAllSessions(context)
                                        },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.DeleteOutline,
                                            contentDescription = "Удалить диалог",
                                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
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
