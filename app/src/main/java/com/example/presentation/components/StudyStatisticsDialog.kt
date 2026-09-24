package com.example.presentation.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.Note
import com.example.util.LectureSummaryExtractor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudyStatisticsDialog(
    notes: List<Note>,
    onDismissRequest: () -> Unit
) {
    val stats = remember(notes) { calculateStudyStats(notes) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Analytics,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Академическая статистика",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Анализ прогресса учебы и конспектов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // 1. Top Summary Metric Cards (2x2 Grid)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Всего заметок",
                            value = "${stats.totalNotes}",
                            subtitle = "Включая ${stats.pinnedNotes} закреп.",
                            icon = Icons.Filled.MenuBook,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Объем слов",
                            value = "${stats.totalWords}",
                            subtitle = "~${stats.estimatedReadingMinutes} мин чтения",
                            icon = Icons.Filled.Article,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricCard(
                            title = "Определений",
                            value = "${stats.extractedDefinitions}",
                            subtitle = "Терминов в базе",
                            icon = Icons.Filled.School,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Вопросов для самопроверки",
                            value = "${stats.reviewQuestions}",
                            subtitle = "Сформировано карточек",
                            icon = Icons.Filled.Quiz,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 2. Checklist & Homework Progress
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.TaskAlt,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Выполнение задач и дедлайнов",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${stats.checklistCompletionPercent}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val animatedProgress by animateFloatAsState(
                                targetValue = if (stats.totalChecklistItems > 0) stats.completedChecklistItems.toFloat() / stats.totalChecklistItems else 0f,
                                label = "ChecklistProgress"
                            )

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "Выполнено ${stats.completedChecklistItems} из ${stats.totalChecklistItems} заданий (еще ${stats.actionItemsFound} дедлайнов найдено в текстах)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // 3. Media & Content breakdown
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Материалы и форматы",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                MediaStatItem(icon = Icons.Filled.Mic, count = stats.notesWithAudio, label = "Аудиозаписи")
                                MediaStatItem(icon = Icons.Filled.Image, count = stats.notesWithImages, label = "Иллюстрации")
                                MediaStatItem(icon = Icons.Filled.Folder, count = stats.foldersCount, label = "Папки/Курсы")
                                MediaStatItem(icon = Icons.Filled.LocalOffer, count = stats.tagsCount, label = "Теги")
                            }
                        }
                    }

                    // 4. Most active study topics / Folders
                    if (stats.topFolders.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Распределение по предметам / папкам",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                stats.topFolders.forEach { (folderName, count) ->
                                    val percent = if (stats.totalNotes > 0) (count.toFloat() / stats.totalNotes) * 100 else 0f
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(folderName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text("$count зам. (${percent.toInt()}%)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        LinearProgressIndicator(
                                            progress = { percent / 100f },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(6.dp)
                                                .clip(CircleShape),
                                            color = MaterialTheme.colorScheme.secondary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Отлично")
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaStatItem(
    icon: ImageVector,
    count: Int,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(text = "$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

data class StudyStats(
    val totalNotes: Int,
    val pinnedNotes: Int,
    val totalWords: Int,
    val estimatedReadingMinutes: Int,
    val extractedDefinitions: Int,
    val reviewQuestions: Int,
    val actionItemsFound: Int,
    val totalChecklistItems: Int,
    val completedChecklistItems: Int,
    val checklistCompletionPercent: Int,
    val notesWithAudio: Int,
    val notesWithImages: Int,
    val foldersCount: Int,
    val tagsCount: Int,
    val topFolders: List<Pair<String, Int>>
)

private fun calculateStudyStats(notes: List<Note>): StudyStats {
    var words = 0
    var definitions = 0
    var questions = 0
    var actionItems = 0
    var totalChecklist = 0
    var completedChecklist = 0
    var withAudio = 0
    var withImages = 0
    val allTags = mutableSetOf<String>()
    val folderCounts = mutableMapOf<String, Int>()

    notes.forEach { note ->
        // Words
        val noteWords = note.content.split(Regex("\\s+")).filter { it.isNotBlank() }.size
        words += noteWords

        // Definitions & questions via extractor
        val analysis = LectureSummaryExtractor.analyze(note.title, note.content)
        definitions += analysis.definitions.size
        questions += analysis.reviewQuestions.size
        actionItems += analysis.actionItems.size

        // Checklists
        if (note.checkListJson.isNotBlank()) {
            val isCheckedRegex = Regex("\"isChecked\":\\s*true")
            val isUncheckedRegex = Regex("\"isChecked\":\\s*false")
            val checked = isCheckedRegex.findAll(note.checkListJson).count()
            val unchecked = isUncheckedRegex.findAll(note.checkListJson).count()
            val total = checked + unchecked
            totalChecklist += total
            completedChecklist += checked
        }

        // Media
        if (!note.audioUri.isNullOrBlank()) withAudio++
        if (note.imageUrisJson.isNotBlank() && note.imageUrisJson != "[]") withImages++

        // Tags
        allTags.addAll(note.tags)

        // Folders
        val f = note.folder?.trim()?.ifBlank { null } ?: "Без папки"
        folderCounts[f] = (folderCounts[f] ?: 0) + 1
    }

    val readingMinutes = (words / 150).coerceAtLeast(1)
    val completionPercent = if (totalChecklist > 0) ((completedChecklist.toFloat() / totalChecklist) * 100).toInt() else 100

    val topFolders = folderCounts.entries
        .sortedByDescending { it.value }
        .take(5)
        .map { it.key to it.value }

    return StudyStats(
        totalNotes = notes.size,
        pinnedNotes = notes.count { it.isPinned },
        totalWords = words,
        estimatedReadingMinutes = readingMinutes,
        extractedDefinitions = definitions,
        reviewQuestions = questions,
        actionItemsFound = actionItems,
        totalChecklistItems = totalChecklist,
        completedChecklistItems = completedChecklist,
        checklistCompletionPercent = completionPercent,
        notesWithAudio = withAudio,
        notesWithImages = withImages,
        foldersCount = folderCounts.keys.filter { it != "Без папки" }.size,
        tagsCount = allTags.size,
        topFolders = topFolders
    )
}
