package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.presentation.screens.notes_list.SortOrder
import com.example.ui.theme.NoteColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterBottomSheet(
    selectedColor: String?,
    onColorSelected: (String?) -> Unit,
    selectedTag: String?,
    onTagSelected: (String?) -> Unit,
    availableTags: List<String>,
    selectedFolder: String?,
    onFolderSelected: (String?) -> Unit,
    availableFolders: List<String>,
    sortOrder: SortOrder,
    onSortOrderSelected: (SortOrder) -> Unit,
    onClearAllFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сортировка и фильтры",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                TextButton(onClick = onClearAllFilters) {
                    Text("Сбросить всё")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Порядок сортировки", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SortOrder.values()) { order ->
                    FilterChip(
                        selected = sortOrder == order,
                        onClick = { onSortOrderSelected(order) },
                        label = { Text(order.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Цвет заметки", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedColor == null,
                        onClick = { onColorSelected(null) },
                        label = { Text("Все") }
                    )
                }
                items(NoteColors) { hex ->
                    val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.LightGray }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color)
                            .clickable { onColorSelected(if (selectedColor == hex) null else hex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedColor == hex) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            ) {}
                        }
                    }
                }
            }

            if (availableFolders.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Папка", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedFolder == null,
                            onClick = { onFolderSelected(null) },
                            label = { Text("Все папки") }
                        )
                    }
                    items(availableFolders) { folder ->
                        FilterChip(
                            selected = selectedFolder == folder,
                            onClick = { onFolderSelected(if (selectedFolder == folder) null else folder) },
                            label = { Text(folder) }
                        )
                    }
                }
            }

            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = "Теги", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedTag == null,
                            onClick = { onTagSelected(null) },
                            label = { Text("Все теги") }
                        )
                    }
                    items(availableTags) { tag ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { onTagSelected(if (selectedTag == tag) null else tag) },
                            label = { Text("#$tag") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Применить")
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
