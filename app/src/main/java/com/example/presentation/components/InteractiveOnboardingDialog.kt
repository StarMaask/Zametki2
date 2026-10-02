package com.example.presentation.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class OnboardingStep(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val iconTint: Color,
    val headerGradient: List<Color>,
    val badges: List<String>,
    val highlights: List<Pair<String, String>>,
    val actionButtonText: String? = null,
    val onActionClick: (() -> Unit)? = null
)

@Composable
fun InteractiveOnboardingDialog(
    onDismissRequest: () -> Unit,
    onComplete: () -> Unit,
    onOpenAiAssistant: (() -> Unit)? = null,
    onCreateDemoNote: (() -> Unit)? = null
) {
    var currentStepIndex by remember { mutableStateOf(0) }

    val steps = listOf(
        OnboardingStep(
            title = "Умные конспекты и тетрадь",
            subtitle = "Твой идеальный тетрадный лист для школы, ВУЗа и личных записей",
            icon = Icons.Filled.EditNote,
            iconTint = Color(0xFF1D4ED8),
            headerGradient = listOf(Color(0xFFDBEAFE), Color(0xFFEFF6FF)),
            badges = listOf("Клетка и линейка", "Формулы LaTeX", "Чек-листы"),
            highlights = listOf(
                "📐 Научные формулы" to "Пиши формулы любой сложности нажатием кнопки ∑ (дроби, корни, степени, векторы)",
                "📝 Тетрадные стили" to "Выбирай формат листа: классическая школьная клетка, линейка или чистый лист",
                "✅ Интерактивные списки" to "Создавай списки домашних заданий и отмечай галочками выполненные пункты"
            ),
            actionButtonText = if (onCreateDemoNote != null) "👋 Создать заметку-пример" else null,
            onActionClick = onCreateDemoNote
        ),
        OnboardingStep(
            title = "Личный ИИ-репетитор",
            subtitle = "Помощь в решении задач, сочинениях, коде и объяснении тем",
            icon = Icons.Filled.AutoAwesome,
            iconTint = Color(0xFF7C3AED),
            headerGradient = listOf(Color(0xFFEDE9FE), Color(0xFFFAF5FF)),
            badges = listOf("Решение по шагам", "Фото задач", "Любые вопросы"),
            highlights = listOf(
                "📷 Скан и фото" to "Сфотографируй задачу из учебника — ИИ распишет пошаговый ход решения",
                "📚 Сочинения и доклады" to "Поможет составить план, подобрать аргументы, цитаты и написать текст",
                "💡 Простое объяснение" to "Объяснит непонятное правило или физический закон понятными словами на примерах"
            ),
            actionButtonText = if (onOpenAiAssistant != null) "✨ Попробовать чат с ИИ" else null,
            onActionClick = onOpenAiAssistant
        ),
        OnboardingStep(
            title = "Голосовой ввод и лекции",
            subtitle = "Диктовка речи в текст с автоматической расстановкой знаков препинания",
            icon = Icons.Filled.Mic,
            iconTint = Color(0xFF047857),
            headerGradient = listOf(Color(0xFFD1FAE5), Color(0xFFECFDF5)),
            badges = listOf("Авто-пунктуация", "Запись лекций", "Озвучка"),
            highlights = listOf(
                "🎙️ Быстрая диктовка" to "Наговаривай мысли в микрофон — точки, запятые и абзацы расставятся сами",
                "🔊 Аудиозапись урока" to "Записывай непрерывный звук лекции с параллельным конспектированием",
                "🎧 Озвучка текста" to "Слушай свои конспекты в наушниках по пути на учёбу"
            )
        ),
        OnboardingStep(
            title = "Карточки и Интеллект-карты",
            subtitle = "Эффективная подготовка к контрольным, зачётам и экзаменам",
            icon = Icons.Filled.Psychology,
            iconTint = Color(0xFFB45309),
            headerGradient = listOf(Color(0xFFFEF3C7), Color(0xFFFFFBEB)),
            badges = listOf("Шпаргалки", "Интервальное повторение", "Mind Maps"),
            highlights = listOf(
                "🧠 Флеш-карточки" to "Превращай любой конспект в карточки вопросов и ответов для тренировки памяти",
                "🗺️ Интеллект-карты" to "Автоматическая визуальная схема связей темы для наглядного понимания",
                "⏱️ Таймер Помодоро" to "25 минут сфокусированной учёбы без отвлечений на телефон"
            )
        ),
        OnboardingStep(
            title = "ГОСТ и экспорт документов",
            subtitle = "Печать и передача материалов в Word, PDF и Excel в один клик",
            icon = Icons.Filled.PictureAsPdf,
            iconTint = Color(0xFFBE123C),
            headerGradient = listOf(Color(0xFFFFE4E6), Color(0xFFFFF1F2)),
            badges = listOf("Word (.docx)", "PDF формат", "Титульный лист"),
            highlights = listOf(
                "📄 Форматирование ГОСТ" to "Стандартные поля, 1.25 см абзацный отступ, правильные шрифты и таблицы",
                "🎓 Титульный лист" to "Авто-генерация титульного листа реферата, доклада или курсовой",
                "📤 Скачивание и печать" to "Сохраняй готовые файлы прямо на телефон или отправляй преподавателю"
            )
        )
    )

    val currentStep = steps[currentStepIndex]
    val isLastStep = currentStepIndex == steps.size - 1

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Top Bar: Step Indicator & Skip Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Step dots
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        steps.indices.forEach { index ->
                            val isSelected = index == currentStepIndex
                            Box(
                                modifier = Modifier
                                    .height(6.dp)
                                    .width(if (isSelected) 22.dp else 6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                            )
                        }
                    }

                    // Skip button
                    TextButton(
                        onClick = {
                            onComplete()
                            onDismissRequest()
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isLastStep) "Закрыть" else "Пропустить",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Header Visual Banner with Icon
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Brush.verticalGradient(currentStep.headerGradient))
                                .padding(vertical = 20.dp, horizontal = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = currentStep.icon,
                                        contentDescription = null,
                                        tint = currentStep.iconTint,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = currentStep.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = currentStep.subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Badges row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        currentStep.badges.forEach { badge ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = badge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Highlights
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        currentStep.highlights.forEach { (heading, desc) ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .size(18.dp)
                                            .padding(top = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = heading,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Optional Interactive Action Button
                    if (currentStep.actionButtonText != null && currentStep.onActionClick != null) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                onComplete()
                                onDismissRequest()
                                currentStep.onActionClick.invoke()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = currentStep.actionButtonText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom Navigation Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentStepIndex > 0) {
                        TextButton(
                            onClick = { currentStepIndex-- },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Назад")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Button(
                        onClick = {
                            if (isLastStep) {
                                onComplete()
                                onDismissRequest()
                            } else {
                                currentStepIndex++
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = if (isLastStep) "🚀 Начать пользоваться!" else "Далее",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        if (!isLastStep) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
