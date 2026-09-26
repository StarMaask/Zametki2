package com.example.presentation.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.GeminiOcrService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDocumentAssistantDialog(
    initialText: String,
    onApplyText: (String) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Анализ & Ошибки, 1: Форматирование ГОСТ, 2: Трактовки стиля
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Analysis results
    var analysisResult by remember { mutableStateOf<GeminiOcrService.DocumentAnalysisResult?>(null) }

    // Formatted by GOST
    var formattedText by remember { mutableStateOf<String?>(null) }

    // Interpretations
    var interpretations by remember { mutableStateOf<GeminiOcrService.DocumentInterpretations?>(null) }
    var selectedInterpretationIndex by remember { mutableIntStateOf(0) }

    fun runAnalysis() {
        if (initialText.isBlank()) {
            errorMessage = "Заметка пуста. Напишите текст или выберите шаблон для анализа."
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val res = GeminiOcrService.analyzeDocument(context, initialText)
            if (res.isSuccess) {
                analysisResult = res.getOrNull()
            } else {
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка анализа"
            }
            isLoading = false
        }
    }

    fun runGostFormatting() {
        if (initialText.isBlank()) {
            errorMessage = "Заметка пуста для форматирования."
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val res = GeminiOcrService.formatDocumentByGost(context, initialText)
            if (res.isSuccess) {
                formattedText = res.getOrNull()
            } else {
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка форматирования"
            }
            isLoading = false
        }
    }

    fun runInterpretations() {
        if (initialText.isBlank()) {
            errorMessage = "Заметка пуста для создания трактовок."
            return
        }
        isLoading = true
        errorMessage = null
        coroutineScope.launch {
            val res = GeminiOcrService.generateInterpretations(context, initialText)
            if (res.isSuccess) {
                interpretations = res.getOrNull()
            } else {
                errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка создания трактовок"
            }
            isLoading = false
        }
    }

    // Auto-load current tab on open
    LaunchedEffect(selectedTab) {
        when (selectedTab) {
            0 -> if (analysisResult == null) runAnalysis()
            1 -> if (formattedText == null) runGostFormatting()
            2 -> if (interpretations == null) runInterpretations()
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        icon = {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "ИИ Экспертиза документов",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Анализ по ГОСТ, исправление ошибок и трактовки текста",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Экспертиза", fontSize = 12.sp, fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.FactCheck, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("ГОСТ", fontSize = 12.sp, fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.FormatAlignJustify, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Трактовки", fontSize = 12.sp, fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(Icons.Filled.Psychology, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(260.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> "ИИ анализирует реквизиты и ошибки..."
                                    1 -> "ИИ форматирует документ по стандартам ГОСТ..."
                                    else -> "ИИ составляет профессиональные трактовки..."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Внимание", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    when (selectedTab) {
                                        0 -> runAnalysis()
                                        1 -> runGostFormatting()
                                        2 -> runInterpretations()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Повторить запрос")
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 420.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (selectedTab) {
                            // 0: ЭКСПЕРТИЗА
                            0 -> {
                                analysisResult?.let { result ->
                                    // Document Type Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "Тип: ${result.documentType}",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }

                                    Text(
                                        text = result.summary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    // Requisites Checklist
                                    Text("Проверка обязательных реквизитов (ГОСТ):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        RequisiteBadge("Кому", result.hasRecipient)
                                        RequisiteBadge("От кого", result.hasApplicant)
                                        RequisiteBadge("Название", result.hasTitle)
                                        RequisiteBadge("Дата", result.hasDate)
                                        RequisiteBadge("Подпись", result.hasSignature)
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    // Detected Errors
                                    if (result.detectedErrors.isNotEmpty()) {
                                        Text("Обнаруженные замечания:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                                        result.detectedErrors.forEach { err ->
                                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Filled.Close, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(err, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    // Recommendations
                                    if (result.formattingRecommendations.isNotEmpty()) {
                                        Text("Рекомендации делопроизводства:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        result.formattingRecommendations.forEach { rec ->
                                            Row(modifier = Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
                                                Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(rec, style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }

                                    // Preview of Corrected Text
                                    Text("Исправленный и выверенный документ:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                                    ) {
                                        Text(
                                            text = result.correctedText,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onApplyText(result.correctedText)
                                            Toast.makeText(context, "Исправленный документ применён в заметку!", Toast.LENGTH_SHORT).show()
                                            onDismissRequest()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Filled.DoneAll, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Применить исправленный текст в заметку")
                                    }
                                }
                            }

                            // 1: ФОРМАТИРОВАНИЕ ГОСТ
                            1 -> {
                                formattedText?.let { formatted ->
                                    Text(
                                        text = "Документ оформлен по ГОСТ Р 7.0.97-2016:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "• Шапка с реквизитами выровнена справа\n• Заголовок документа отцентрирован\n• Основной текст структурирован с красной строкой\n• Добавлены реквизиты даты и подписи",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(bottom = 10.dp)
                                    )

                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)
                                    ) {
                                        Text(
                                            text = formatted,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }

                                    Button(
                                        onClick = {
                                            onApplyText(formatted)
                                            Toast.makeText(context, "Форматирование по ГОСТ применено!", Toast.LENGTH_SHORT).show()
                                            onDismissRequest()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Filled.FormatAlignJustify, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Вставить оформленный текст по ГОСТ")
                                    }
                                }
                            }

                            // 2: ТРАКТОВКИ СТИЛЯ
                            2 -> {
                                interpretations?.let { inter ->
                                    Text(
                                        text = "Выберите подходящую трактовку текста:",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )

                                    val options = listOf(
                                        Triple("⚖️ Официально-деловая (ГОСТ)", "Юридически выверенный, строгий слог для руководства и госорганов", inter.officialGost),
                                        Triple("🤝 Дипломатичная и конструктивная", "Корпоративный уважительный стиль для партнеров и коллег", inter.diplomatic),
                                        Triple("⚡ Лаконичная и убедительная", "Кратко, чётко, только суть и факты без лишней воды", inter.concise)
                                    )

                                    options.forEachIndexed { index, (title, desc, textContent) ->
                                        val isSelected = selectedInterpretationIndex == index
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 10.dp)
                                                .clickable { selectedInterpretationIndex = index },
                                            shape = RoundedCornerShape(12.dp),
                                            colors = CardDefaults.cardColors(
                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                            ),
                                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    RadioButton(
                                                        selected = isSelected,
                                                        onClick = { selectedInterpretationIndex = index }
                                                    )
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Column {
                                                        Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                        Text(desc, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = MaterialTheme.colorScheme.surface,
                                                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp)
                                                ) {
                                                    Text(
                                                        text = textContent,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        modifier = Modifier.padding(8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val chosenText = when (selectedInterpretationIndex) {
                                                0 -> inter.officialGost
                                                1 -> inter.diplomatic
                                                else -> inter.concise
                                            }
                                            onApplyText(chosenText)
                                            Toast.makeText(context, "Выбранная трактовка применена в заметку!", Toast.LENGTH_SHORT).show()
                                            onDismissRequest()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Применить выбранную трактовку")
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
                Text("Закрыть")
            }
        }
    )
}

@Composable
private fun RequisiteBadge(title: String, isPresent: Boolean) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isPresent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPresent) Icons.Filled.Check else Icons.Filled.Close,
                contentDescription = null,
                tint = if (isPresent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(3.dp))
            Text(
                text = title,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPresent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}
