package com.example.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun HelpDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Подсказки и справка", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Краткое руководство по всем функциям заметок:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HelpItem(
                    icon = Icons.Filled.AddCircleOutline,
                    title = "Вставка мультимедиа и списков",
                    description = "Нажмите значок «Вставка» в редакторе, чтобы прикрепить чек-лист дел, фото из галереи, рукописный рисунок или аудиозапись."
                )

                HelpItem(
                    icon = Icons.Filled.FormatPaint,
                    title = "Форматирование текста",
                    description = "Выпадающее меню форматирования позволяет в один клик вставить жирный шрифт, курсив, заголовок, маркированный список или цитату."
                )

                HelpItem(
                    icon = Icons.Filled.Notifications,
                    title = "Напоминания (AlarmManager)",
                    description = "Установите точное время напоминания через значок колокольчика. Приложение пришлет уведомление точно в указанный срок."
                )

                HelpItem(
                    icon = Icons.Filled.Folder,
                    title = "Папки и теги",
                    description = "Организуйте заметки по папкам (Проекты, Работа, Личное) и помечайте их тегами (#важное, #покупки) для быстрой фильтрации."
                )

                HelpItem(
                    icon = Icons.Filled.Checklist,
                    title = "Режим выбора нескольких заметок",
                    description = "Удерживайте любую карточку заметки долгим нажатием, чтобы перейти в режим группового выделения, изменения цвета, перемещения в папку или удаления."
                )

                HelpItem(
                    icon = Icons.Filled.Lock,
                    title = "PIN-код и безопасность",
                    description = "В Настройках можно включить 4-значный PIN-код, чтобы защитить личные заметки от посторонних глаз."
                )

                HelpItem(
                    icon = Icons.Filled.Backup,
                    title = "Резервное копирование и экспорт",
                    description = "В Настройках доступен полный экспорт и восстановление базы заметок в JSON-формате."
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}

@Composable
private fun HelpItem(
    icon: ImageVector,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
