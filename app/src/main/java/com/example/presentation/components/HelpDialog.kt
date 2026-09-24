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
                    icon = Icons.Filled.School,
                    title = "Интервальные карточки и самопроверка",
                    description = "Автоматическое извлечение терминов, определений и формул для эффективного заучивания перед экзаменами и коллоквиумами."
                )

                HelpItem(
                    icon = Icons.Filled.Hub,
                    title = "Интеллект-карты понятий",
                    description = "Интерактивный граф связей тем, понятий и определений для структурирования сложных лекций."
                )

                HelpItem(
                    icon = Icons.Filled.Psychology,
                    title = "Умный конспект & Анализ лекции",
                    description = "Выжимка ключевых тезисов, глоссарий, обнаруженные задачи и вопросы для самопроверки."
                )

                HelpItem(
                    icon = Icons.Filled.TableChart,
                    title = "Таблицы и математические формулы",
                    description = "Быстрая вставка настраиваемых таблиц и панель математических символов (∑, ∫, √, π, α, β и др.)."
                )

                HelpItem(
                    icon = Icons.Filled.Mic,
                    title = "Лекция: Звук в текст (Непрерывная запись)",
                    description = "Потоковое распознавание живой речи преподавателя с умным разделением на предложения и фильтрацией слов-паразитов."
                )

                HelpItem(
                    icon = Icons.Filled.DocumentScanner,
                    title = "Распознавание текста с фото (OCR)",
                    description = "Мгновенное сканирование рукописных записей с доски или страниц учебника прямо в текст заметки."
                )

                HelpItem(
                    icon = Icons.Filled.AutoStories,
                    title = "Оформление тетради и листа",
                    description = "8 форматов страниц: книжная верстка, школьная тетрадь в линейку или клетку, крафт, старинный пергамент, доска мелом и чертёж."
                )

                HelpItem(
                    icon = Icons.Filled.Code,
                    title = "Экспорт в Markdown (.md) и PDF",
                    description = "Экспортируйте конспекты для Obsidian, Notion, Typora или печатайте структурированные многостраничные PDF-документы."
                )

                HelpItem(
                    icon = Icons.Filled.Notifications,
                    title = "Напоминания (AlarmManager)",
                    description = "Установите точное время напоминания о сдаче домашнего задания или дедлайне контрольной."
                )

                HelpItem(
                    icon = Icons.Filled.Folder,
                    title = "Предметы, курсы и теги",
                    description = "Организуйте конспекты по учебным дисциплинам и используйте удобную горизонтальную панель вкладок для быстрой фильтрации."
                )

                HelpItem(
                    icon = Icons.Filled.Checklist,
                    title = "Режим выбора нескольких заметок",
                    description = "Удерживайте любую карточку заметки долгим нажатием для совместного изучения карточек, построения общей интеллект-карты или массового экспорта."
                )

                HelpItem(
                    icon = Icons.Filled.Lock,
                    title = "PIN-код и защита заметок",
                    description = "Защита отдельных приватных конспектов или всей базы паролем с биометрией."
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
