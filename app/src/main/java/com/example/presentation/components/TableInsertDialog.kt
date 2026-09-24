package com.example.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class TablePreset(
    val title: String,
    val description: String,
    val headers: List<String>,
    val rows: List<List<String>>
)

val defaultTablePresets = listOf(
    TablePreset(
        title = "Сравнение A и B",
        description = "Критерии сравнения двух понятий или теорий",
        headers = listOf("Критерий", "Объект A", "Объект B"),
        rows = listOf(
            listOf("Определение", "Значение A", "Значение B"),
            listOf("Преимущества", "Плюсы A", "Плюсы B"),
            listOf("Недостатки", "Минусы A", "Минусы B")
        )
    ),
    TablePreset(
        title = "Формулы и величины",
        description = "Физические и математические формулы",
        headers = listOf("Величина", "Обозначение", "Формула", "Ед. изм."),
        rows = listOf(
            listOf("Скорость", "v", "v = s / t", "м/с"),
            listOf("Ускорение", "a", "a = Δv / Δt", "м/с²"),
            listOf("Сила", "F", "F = m · a", "Н")
        )
    ),
    TablePreset(
        title = "Глоссарий терминов",
        description = "Термины, перевод и определение",
        headers = listOf("Термин", "Контекст", "Определение"),
        rows = listOf(
            listOf("Полиморфизм", "ООП", "Способность функции обрабатывать данные разных типов"),
            listOf("Инкапсуляция", "ООП", "Сокрытие внутреннего состояния объекта")
        )
    ),
    TablePreset(
        title = "План / Расписание",
        description = "Время, тема занятия и аудитория",
        headers = listOf("Время", "Дисциплина", "Аудитория", "Преподаватель"),
        rows = listOf(
            listOf("09:00 - 10:30", "Высшая математика", "Ауд. 302", "Проф. Иванов"),
            listOf("10:45 - 12:15", "Физика (лекция)", "Ауд. 104", "Доц. Петров")
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableInsertDialog(
    onDismissRequest: () -> Unit,
    onInsertTable: (String) -> Unit
) {
    var selectedPresetIndex by remember { mutableIntStateOf(-1) }
    var columnCount by remember { mutableIntStateOf(3) }
    var rowCount by remember { mutableIntStateOf(3) }

    // Dynamic grid values
    val headers = remember { mutableStateListOf("Столбец 1", "Столбец 2", "Столбец 3") }
    val cellMatrix = remember {
        mutableStateListOf(
            mutableStateListOf("А1", "Б1", "В1"),
            mutableStateListOf("А2", "Б2", "В2")
        )
    }

    fun applyPreset(preset: TablePreset) {
        columnCount = preset.headers.size
        rowCount = preset.rows.size + 1
        headers.clear()
        headers.addAll(preset.headers)
        cellMatrix.clear()
        for (r in preset.rows) {
            cellMatrix.add(r.toMutableStateList())
        }
    }

    fun updateGridDimensions(newCols: Int, newRows: Int) {
        val safeCols = newCols.coerceIn(2, 6)
        val safeRows = newRows.coerceIn(2, 8)
        columnCount = safeCols
        rowCount = safeRows

        // Adjust headers
        while (headers.size < safeCols) {
            headers.add("Столбец ${headers.size + 1}")
        }
        while (headers.size > safeCols) {
            headers.removeAt(headers.size - 1)
        }

        // Adjust rows count (safeRows - 1 because header is 1)
        val dataRowCount = safeRows - 1
        while (cellMatrix.size < dataRowCount) {
            val newRow = (1..safeCols).map { "" }.toMutableStateList()
            cellMatrix.add(newRow)
        }
        while (cellMatrix.size > dataRowCount) {
            cellMatrix.removeAt(cellMatrix.size - 1)
        }

        // Adjust cols in each row
        for (row in cellMatrix) {
            while (row.size < safeCols) {
                row.add("")
            }
            while (row.size > safeCols) {
                row.removeAt(row.size - 1)
            }
        }
    }

    fun generateMarkdownTable(): String {
        val colWidths = IntArray(headers.size)
        for (i in headers.indices) {
            colWidths[i] = maxOf(3, headers[i].length)
        }
        for (r in cellMatrix) {
            for (i in r.indices) {
                if (i < colWidths.size) {
                    colWidths[i] = maxOf(colWidths[i], r[i].length)
                }
            }
        }

        val sb = StringBuilder()
        // Header
        sb.append("|")
        for (i in headers.indices) {
            val text = headers[i].ifBlank { " " }
            val padded = text.padEnd(colWidths[i])
            sb.append(" $padded |")
        }
        sb.append("\n")

        // Separator
        sb.append("|")
        for (i in headers.indices) {
            val dashes = "-".repeat(colWidths[i])
            sb.append(" $dashes |")
        }
        sb.append("\n")

        // Rows
        for (r in cellMatrix) {
            sb.append("|")
            for (i in headers.indices) {
                val text = if (i < r.size) r[i].ifBlank { " " } else " "
                val padded = text.padEnd(colWidths[i])
                sb.append(" $padded |")
            }
            sb.append("\n")
        }

        return sb.toString()
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
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
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.TableChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Вставка таблицы",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Форматированная Markdown-таблица",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Presets horizontal list
                Text(
                    text = "Готовые шаблоны:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(defaultTablePresets.indices.toList()) { idx ->
                        val preset = defaultTablePresets[idx]
                        val isSelected = selectedPresetIndex == idx
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPresetIndex = idx
                                applyPreset(preset)
                            },
                            label = { Text(preset.title, fontSize = 12.sp) },
                            leadingIcon = if (isSelected) {
                                { Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Dimension controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Столбцы: $columnCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { updateGridDimensions(columnCount - 1, rowCount) },
                            enabled = columnCount > 2,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledTonalIconButton(
                            onClick = { updateGridDimensions(columnCount + 1, rowCount) },
                            enabled = columnCount < 6,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Строки: $rowCount", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalIconButton(
                            onClick = { updateGridDimensions(columnCount, rowCount - 1) },
                            enabled = rowCount > 2,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("-", fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        FilledTonalIconButton(
                            onClick = { updateGridDimensions(columnCount, rowCount + 1) },
                            enabled = rowCount < 8,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Text("+", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable Table Editor Grid
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                ) {
                    val hScroll = rememberScrollState()
                    val vScroll = rememberScrollState()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(vScroll)
                            .horizontalScroll(hScroll)
                            .padding(10.dp)
                    ) {
                        // Header Inputs
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (c in 0 until columnCount) {
                                OutlinedTextField(
                                    value = if (c < headers.size) headers[c] else "",
                                    onValueChange = { newVal ->
                                        if (c < headers.size) headers[c] = newVal
                                    },
                                    placeholder = { Text("Заголовок ${c + 1}", fontSize = 11.sp) },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.width(130.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                    )
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), thickness = 2.dp)
                        Spacer(modifier = Modifier.height(6.dp))

                        // Rows Inputs
                        for (r in 0 until cellMatrix.size) {
                            val rowData = cellMatrix[r]
                            Row(
                                modifier = Modifier.padding(vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0 until columnCount) {
                                    OutlinedTextField(
                                        value = if (c < rowData.size) rowData[c] else "",
                                        onValueChange = { newVal ->
                                            if (c < rowData.size) rowData[c] = newVal
                                        },
                                        placeholder = { Text("Ячейка", fontSize = 11.sp) },
                                        singleLine = true,
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.width(130.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val markdown = generateMarkdownTable()
                            onInsertTable(markdown)
                            onDismissRequest()
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вставить таблицу")
                    }
                }
            }
        }
    }
}
