package com.example.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NoteInfoDialog(
    wordCount: Int,
    charCount: Int,
    createdAt: Long,
    updatedAt: Long,
    completedChecklistItems: Int,
    totalChecklistItems: Int,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru"))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Информация о заметке", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoRow(label = "Количество слов:", value = wordCount.toString())
                InfoRow(label = "Количество символов:", value = charCount.toString())
                if (totalChecklistItems > 0) {
                    InfoRow(
                        label = "Пунктов чек-листа:",
                        value = "$completedChecklistItems из $totalChecklistItems выполнено"
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                InfoRow(label = "Создана:", value = dateFormat.format(Date(createdAt)))
                InfoRow(label = "Изменена:", value = dateFormat.format(Date(updatedAt)))
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}
