package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.Note
import com.example.util.DocxGenerator
import com.example.util.GeminiOcrService
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiDocumentExpertDialog(
    initialText: String,
    onDismissRequest: () -> Unit,
    onApplyText: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Tab 0: Document Analysis State
    var analysisResult by remember { mutableStateOf<GeminiOcrService.DocumentAnalysisResult?>(null) }

    // Tab 1: ГОСТ Formatting State
    var gostFormattedText by remember { mutableStateOf<String?>(null) }

    // Tab 2: Stylistic Interpretations State
    var interpretations by remember { mutableStateOf<GeminiOcrService.DocumentInterpretations?>(null) }

    val tabTitles = listOf("🧐 Анализ документа", "📐 ГОСТ формат", "🎭 Варианты трактовки")

    // Automatic load on tab selection if not loaded yet
    LaunchedEffect(selectedTab) {
        errorMessage = null
        if (selectedTab == 0 && analysisResult == null && initialText.isNotBlank()) {
            isLoading = true
            coroutineScope.launch {
                val res = GeminiOcrService.analyzeDocument(context, initialText)
                isLoading = false
                if (res.isSuccess) {
                    analysisResult = res.getOrNull()
                } else {
                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка анализа"
                }
            }
        } else if (selectedTab == 1 && gostFormattedText == null && initialText.isNotBlank()) {
            isLoading = true
            coroutineScope.launch {
                val res = GeminiOcrService.formatDocumentByGost(context, initialText)
                isLoading = false
                if (res.isSuccess) {
                    gostFormattedText = res.getOrNull()
                } else {
                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка форматирования"
                }
            }
        } else if (selectedTab == 2 && interpretations == null && initialText.isNotBlank()) {
            isLoading = true
            coroutineScope.launch {
                val res = GeminiOcrService.generateInterpretations(context, initialText)
                isLoading = false
                if (res.isSuccess) {
                    interpretations = res.getOrNull()
                } else {
                    errorMessage = res.exceptionOrNull()?.localizedMessage ?: "Ошибка трактовок"
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "ИИ Экспертиза документов",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Анализ по ГОСТ, исправление и трактовки",
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

                // Navigation Tabs
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    text = title,
                                    fontSize = 12.sp,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (initialText.isBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Заметка пуста. Введите текст документа перед анализом.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = when (selectedTab) {
                                    0 -> "ИИ анализирует структуру документа и реквизиты..."
                                    1 -> "Применение стандартов ГОСТ Р 7.0.97-2016..."
                                    else -> "Генерация профессиональных стилистических трактовок..."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Icon(
                                Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorMessage ?: "Произошла ошибка",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    when (selectedTab) {
                                        0 -> analysisResult = null
                                        1 -> gostFormattedText = null
                                        2 -> interpretations = null
                                    }
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Повторить запрос")
                            }
                        }
                    }
                } else {
                    // Content Area per Tab
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        when (selectedTab) {
                            0 -> DocumentAnalysisView(
                                result = analysisResult,
                                onApply = {
                                    analysisResult?.correctedText?.let { onApplyText(it) }
                                    onDismissRequest()
                                },
                                onReanalyze = {
                                    analysisResult = null
                                }
                            )
                            1 -> GostFormattingView(
                                formattedText = gostFormattedText,
                                onApply = {
                                    gostFormattedText?.let { onApplyText(it) }
                                    onDismissRequest()
                                },
                                onReformat = {
                                    gostFormattedText = null
                                }
                            )
                            2 -> StylisticInterpretationsView(
                                interpretations = interpretations,
                                onApplyOption = { text ->
                                    onApplyText(text)
                                    onDismissRequest()
                                },
                                onRegenerate = {
                                    interpretations = null
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocumentAnalysisView(
    result: GeminiOcrService.DocumentAnalysisResult?,
    onApply: () -> Unit,
    onReanalyze: () -> Unit
) {
    if (result == null) return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Card: Type & Summary
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Assignment, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = result.documentType,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = result.summary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Requisite Checklist Badges
        Text(
            text = "Обязательные реквизиты документа по ГОСТ:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RequisiteBadge("Кому", result.hasRecipient, Modifier.weight(1f))
            RequisiteBadge("От кого", result.hasApplicant, Modifier.weight(1f))
            RequisiteBadge("Заголовок", result.hasTitle, Modifier.weight(1f))
            RequisiteBadge("Дата", result.hasDate, Modifier.weight(1f))
            RequisiteBadge("Подпись", result.hasSignature, Modifier.weight(1f))
        }

        // Detected Errors / Issues
        if (result.detectedErrors.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Выявленные ошибки и замечания (${result.detectedErrors.size}):",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    result.detectedErrors.forEach { err ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("• ", color = MaterialTheme.colorScheme.error)
                            Text(err, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Recommendations
        if (result.formattingRecommendations.isNotEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TipsAndUpdates, null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Рекомендации по улучшению оформления:",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    result.formattingRecommendations.forEach { rec ->
                        Row(modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Text(rec, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        // Corrected preview
        Text(
            text = "Исправленный вариант ИИ:",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = result.correctedText,
                style = MaterialTheme.typography.bodySmall,
                lineHeight = 18.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReanalyze,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Перепроверить")
            }
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1.5f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Применить исправления")
            }
        }
    }
}

@Composable
private fun RequisiteBadge(name: String, exists: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (exists) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = if (exists) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                contentDescription = null,
                tint = if (exists) Color(0xFF15803D) else Color(0xFFB91C1C),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (exists) Color(0xFF15803D) else Color(0xFFB91C1C)
            )
        }
    }
}

@Composable
private fun GostFormattingView(
    formattedText: String?,
    onApply: () -> Unit,
    onReformat: () -> Unit
) {
    if (formattedText == null) return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Стандарт ГОСТ Р 7.0.97-2016 применен",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = "Правый блок адресата, центрированный заголовок, абзацы с красной строкой, блок подписи и даты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            val dummyNote = remember(formattedText) {
                Note(
                    id = 0,
                    title = "",
                    content = formattedText,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            }
            val structure = remember(dummyNote) {
                DocxGenerator.parseStructure(dummyNote)
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Right Header Block (Кому, От кого)
                if (structure.headerLines.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .align(Alignment.TopEnd)
                        ) {
                            structure.headerLines.forEach { hLine ->
                                Text(
                                    text = hLine,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    lineHeight = 17.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 2. Centered Document Title
                val title = structure.documentTitle ?: "ДОКУМЕНТ"
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(14.dp))

                // 3. Body paragraphs with 1.25 cm first line indent
                structure.bodyElements.forEach { element ->
                    when (element) {
                        is DocxGenerator.BodyElement.Paragraph -> {
                            if (element.isHeading) {
                                Text(
                                    text = element.text,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                                )
                            } else {
                                Text(
                                    text = "        " + element.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Justify,
                                    lineHeight = 21.sp,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            }
                        }
                        is DocxGenerator.BodyElement.Table -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(6.dp)
                                ) {
                                    element.headers.forEach { h ->
                                        Text(
                                            text = h,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                element.rows.forEach { row ->
                                    HorizontalDivider()
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(6.dp)
                                    ) {
                                        row.forEach { c ->
                                            Text(
                                                text = c,
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Date & Signature
                if (structure.footerLines.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(20.dp))
                    val dateText = structure.footerLines.firstOrNull { it.startsWith("Дата", ignoreCase = true) || it.startsWith("«___»") }
                        ?: "Дата: «___» __________ 202_ г."
                    val sigText = structure.footerLines.firstOrNull { it.contains("Подпись", ignoreCase = true) || it.contains("____________ /") }
                        ?: "Подпись: ____________ / ____________ /"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sigText,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReformat,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Обновить")
            }
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1.5f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.DoneAll, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Вставить в заметку")
            }
        }
    }
}

@Composable
private fun StylisticInterpretationsView(
    interpretations: GeminiOcrService.DocumentInterpretations?,
    onApplyOption: (String) -> Unit,
    onRegenerate: () -> Unit
) {
    if (interpretations == null) return
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Выберите подходящий стиль оформления текста:",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Option 1: Official GOST
        InterpretationCard(
            title = "🏛️ Официально-деловой (ГОСТ)",
            subtitle = "Юридическая строгость, точные формулировки, для руководства и органов",
            content = interpretations.officialGost,
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            onApply = { onApplyOption(interpretations.officialGost) }
        )

        // Option 2: Diplomatic
        InterpretationCard(
            title = "🤝 Дипломатичный и партнерский",
            subtitle = "Вежливый корпоративный тон, акцент на сотрудничестве и взаимоуважении",
            content = interpretations.diplomatic,
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
            onApply = { onApplyOption(interpretations.diplomatic) }
        )

        // Option 3: Concise
        InterpretationCard(
            title = "⚡ Лаконичный и убедительный",
            subtitle = "Кратко, без воды, только факты и суть для быстрого согласования",
            content = interpretations.concise,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f),
            onApply = { onApplyOption(interpretations.concise) }
        )

        OutlinedButton(
            onClick = onRegenerate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Сгенерировать другие варианты")
        }
    }
}

@Composable
private fun InterpretationCard(
    title: String,
    subtitle: String,
    content: String,
    containerColor: Color,
    onApply: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .padding(10.dp)
            ) {
                Text(
                    text = if (expanded || content.length < 240) content else content.take(240) + "...",
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onApply,
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Icon(Icons.Filled.Check, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Выбрать этот вариант", fontSize = 12.sp)
            }
        }
    }
}
