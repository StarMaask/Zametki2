package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.NoteTemplate
import com.example.domain.model.NoteTemplateManager

@Composable
fun NoteTemplateDialog(
    onDismissRequest: () -> Unit,
    onTemplateSelect: (NoteTemplate) -> Unit
) {
    var expandedTemplate by remember { mutableStateOf<NoteTemplate?>(null) }
    var newItemText by remember { mutableStateOf("") }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editingItemText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Готовые шаблоны заметок",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Нажмите на стрелку, чтобы редактировать и удалять пункты",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
            ) {
                items(NoteTemplate.values()) { template ->
                    val templateData = NoteTemplateManager.getTemplate(template)
                    val isExpanded = expandedTemplate == template
                    val isBlank = template == NoteTemplate.BLANK

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isExpanded) {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            }
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isBlank) {
                                            onTemplateSelect(template)
                                        } else {
                                            expandedTemplate = if (isExpanded) null else template
                                        }
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val icon: ImageVector = when (template) {
                                    NoteTemplate.BLANK -> Icons.Filled.Description
                                    NoteTemplate.SHOPPING_LIST -> Icons.Filled.ShoppingCart
                                    NoteTemplate.DAILY_PLAN -> Icons.Filled.Checklist
                                    NoteTemplate.MEETING_NOTES -> Icons.Filled.Groups
                                    NoteTemplate.PROJECT_IDEA -> Icons.Filled.Lightbulb
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.size(42.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = templateData.title,
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    Text(
                                        text = if (templateData.checklistItems.isNotEmpty()) {
                                            "${templateData.description} (${templateData.checklistItems.size} пунктов)"
                                        } else {
                                            templateData.description
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (!isBlank) {
                                    IconButton(
                                        onClick = {
                                            expandedTemplate = if (isExpanded) null else template
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть и редактировать пункты"
                                        )
                                    }
                                }
                            }

                            // Expanded Checklist Items Manager
                            AnimatedVisibility(visible = isExpanded && !isBlank) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp)
                                ) {
                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Пункты шаблона (${templateData.checklistItems.size}):",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        TextButton(
                                            onClick = { NoteTemplateManager.resetTemplate(template) },
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Сбросить", fontSize = 12.sp)
                                        }
                                    }

                                    if (templateData.checklistItems.isEmpty()) {
                                        Text(
                                            text = "В шаблоне нет пунктов. Добавьте свои ниже!",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    } else {
                                        templateData.checklistItems.forEachIndexed { index, itemText ->
                                            key(template.name + "_item_" + index) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 3.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))

                                                    if (editingItemIndex == index && expandedTemplate == template) {
                                                        OutlinedTextField(
                                                            value = editingItemText,
                                                            onValueChange = { editingItemText = it },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f),
                                                            textStyle = MaterialTheme.typography.bodySmall,
                                                            trailingIcon = {
                                                                IconButton(
                                                                    onClick = {
                                                                        if (editingItemText.isNotBlank()) {
                                                                            NoteTemplateManager.updateItemInTemplate(template, index, editingItemText.trim())
                                                                        }
                                                                        editingItemIndex = null
                                                                    }
                                                                ) {
                                                                    Icon(Icons.Filled.Done, contentDescription = "Применить", modifier = Modifier.size(16.dp))
                                                                }
                                                            }
                                                        )
                                                    } else {
                                                        Text(
                                                            text = itemText,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .clickable {
                                                                    editingItemIndex = index
                                                                    editingItemText = itemText
                                                                }
                                                                .padding(vertical = 4.dp)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                editingItemIndex = index
                                                                editingItemText = itemText
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Edit,
                                                                contentDescription = "Редактировать пункт",
                                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            if (editingItemIndex == index) editingItemIndex = null
                                                            NoteTemplateManager.deleteItemFromTemplate(template, index)
                                                        },
                                                        modifier = Modifier.size(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Filled.DeleteOutline,
                                                            contentDescription = "Удалить пункт",
                                                            tint = MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // Add Item Row
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = newItemText,
                                            onValueChange = { newItemText = it },
                                            placeholder = { Text("Новый пункт...", fontSize = 12.sp) },
                                            singleLine = true,
                                            textStyle = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = {
                                                if (newItemText.isNotBlank()) {
                                                    NoteTemplateManager.addItemToTemplate(template, newItemText.trim())
                                                    newItemText = ""
                                                }
                                            },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Filled.Add, contentDescription = "Добавить пункт", modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Добавить", fontSize = 12.sp)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Apply / Create Note Button
                                    Button(
                                        onClick = {
                                            onTemplateSelect(template)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Создать заметку по этому шаблону")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}
