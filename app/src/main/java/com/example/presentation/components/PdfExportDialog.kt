package com.example.presentation.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.Note
import com.example.util.PdfExportConfig
import com.example.util.PdfTheme
import com.example.util.ShareExportUtil

@Composable
fun PdfExportDialog(
    note: Note,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    var selectedTheme by remember { mutableStateOf(PdfTheme.ACADEMIC_A4) }
    var includeMetadata by remember { mutableStateOf(true) }
    val hasImages = remember(note.imageUrisJson) {
        ShareExportUtil.parseImageUris(note.imageUrisJson).isNotEmpty()
    }
    var includeImages by remember { mutableStateOf(hasImages) }
    var includePageNumbers by remember { mutableStateOf(true) }
    var includeChecklist by remember { mutableStateOf(note.checkListJson.isNotBlank()) }

    val config = remember(selectedTheme, includeMetadata, includeImages, includePageNumbers, includeChecklist) {
        PdfExportConfig(
            theme = selectedTheme,
            includeMetadata = includeMetadata,
            includeImages = includeImages,
            includePageNumbers = includePageNumbers,
            includeChecklist = includeChecklist
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
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
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Экспорт и печать PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Стандартный формат A4",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Note Summary Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Description,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = note.title.ifBlank { "Без названия" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val words = remember(note.content) {
                                    note.content.split("\\s+".toRegex()).count { it.isNotBlank() }
                                }
                                Text(
                                    text = "Слов: $words  •  Символов: ${note.content.length}${if (hasImages) "  •  Изображений: есть" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Section: Page Design / Theme
                    Text(
                        text = "Стиль оформления страниц A4",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(PdfTheme.entries) { theme ->
                            val isSelected = selectedTheme == theme
                            val borderColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                label = "border"
                            )
                            val containerColor by animateColorAsState(
                                targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                                label = "container"
                            )

                            Surface(
                                modifier = Modifier
                                    .width(160.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(14.dp))
                                    .clickable { selectedTheme = theme },
                                color = containerColor,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    // Mini sheet visualization
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(55.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(theme.pageColor))
                                            .border(1.dp, Color(0x33000000), RoundedCornerShape(6.dp))
                                            .padding(6.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier.fillMaxSize(),
                                            verticalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            // Mini lines
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.6f)
                                                    .height(3.dp)
                                                    .background(Color(theme.inkColor).copy(alpha = 0.8f), CircleShape)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.9f)
                                                    .height(2.dp)
                                                    .background(
                                                        theme.ruledColor?.let { Color(it) } ?: Color(theme.inkColor).copy(alpha = 0.3f),
                                                        CircleShape
                                                    )
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(0.75f)
                                                    .height(2.dp)
                                                    .background(
                                                        theme.ruledColor?.let { Color(it) } ?: Color(theme.inkColor).copy(alpha = 0.3f),
                                                        CircleShape
                                                    )
                                            )
                                        }

                                        // Left red notebook margin preview
                                        theme.marginColor?.let { mColor ->
                                            Box(
                                                modifier = Modifier
                                                    .width(1.5.dp)
                                                    .fillMaxHeight()
                                                    .padding(start = 10.dp)
                                                    .background(Color(mColor))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Text(
                                        text = theme.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = theme.subtitle,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Section: Content Elements Toggles
                    Text(
                        text = "Параметры документа",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Метаданные документа", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Дата создания, папка, теги и аудио-статус в заголовке", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includeMetadata, onCheckedChange = { includeMetadata = it })
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Нумерация страниц", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text("Колонтитул «Стр. X из N» и дата внизу каждой страницы", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = includePageNumbers, onCheckedChange = { includePageNumbers = it })
                    }

                    if (hasImages) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Включить прикреплённые фото и зарисовки", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Фотографии и рукописные схемы с сохранением пропорций", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = includeImages, onCheckedChange = { includeImages = it })
                        }
                    }

                    if (note.checkListJson.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Включить списки задач и чек-листы", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Отрисовка чекбоксов с отметками выполнения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = includeChecklist, onCheckedChange = { includeChecklist = it })
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Print, Share PDF, Save to Downloads
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = {
                            onDismissRequest()
                            ShareExportUtil.printNote(context, note, config)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Печать", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            onDismissRequest()
                            ShareExportUtil.shareAsPdf(context, note, config)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(vertical = 12.dp)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Поделиться", fontWeight = FontWeight.Bold)
                    }

                    OutlinedIconButton(
                        onClick = {
                            ShareExportUtil.savePdfToDownloads(context, note, config)
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "В Загрузки")
                    }
                }
            }
        }
    }
}
