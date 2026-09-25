package com.example.presentation.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.preferences.UserPreferencesManager
import com.example.util.GeminiOcrService
import com.example.util.HandwritingPhotoDigitizer
import kotlinx.coroutines.launch

enum class PageOcrStatus {
    PENDING,
    PROCESSING,
    DONE,
    ERROR
}

data class PageScanItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val uri: Uri,
    var status: PageOcrStatus = PageOcrStatus.PENDING,
    var recognizedText: String = "",
    var errorMessage: String? = null,
    var isExpanded: Boolean = false
)

enum class MergeFormat(val title: String) {
    WITH_HEADERS("С заголовками страниц"),
    PLAIN_PARAGRAPHS("Сплошным текстом"),
    NUMBERED_LIST("Нумерованный список")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPhotoOcrDialog(
    initialImageUris: List<Uri>,
    onDismissRequest: () -> Unit,
    onInsertText: (insertedText: String, insertAtCursor: Boolean) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context) }
    val hasApiKey = remember { GeminiOcrService.hasAvailableApiKey(context) }

    var items by remember {
        mutableStateOf(initialImageUris.map { PageScanItem(uri = it) })
    }

    var selectedMode by remember {
        mutableStateOf(if (hasApiKey) OcrMode.GEMINI_AI else OcrMode.LOCAL_DEVICE)
    }

    var selectedTab by remember { mutableStateOf(0) } // 0: По страницам, 1: Объединение, 2: ИИ-структурирование
    var mergeFormat by remember { mutableStateOf(MergeFormat.WITH_HEADERS) }

    var structuredText by remember { mutableStateOf("") }
    var isStructuring by remember { mutableStateOf(false) }
    var structureMode by remember { mutableStateOf(GeminiOcrService.TextStructureMode.STRUCTURED_NOTES) }
    var structureError by remember { mutableStateOf<String?>(null) }

    // Launcher to add more photos to the batch
    val addPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { newUris ->
        if (newUris.isNotEmpty()) {
            val newItems = newUris.map { PageScanItem(uri = it) }
            items = items + newItems
        }
    }

    // Function to process a single item
    fun processItem(index: Int) {
        val item = items.getOrNull(index) ?: return
        coroutineScope.launch {
            items = items.toMutableList().also { list ->
                list[index] = list[index].copy(status = PageOcrStatus.PROCESSING, errorMessage = null)
            }

            if (selectedMode == OcrMode.GEMINI_AI) {
                val key = prefs.getGeminiApiKeySync()
                val result = GeminiOcrService.recognizeTextWithGemini(context, item.uri, key)
                if (result.isSuccess) {
                    val text = result.getOrNull() ?: ""
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = PageOcrStatus.DONE,
                            recognizedText = text,
                            errorMessage = null
                        )
                    }
                } else {
                    // Fallback to local on-device OCR
                    val localText = try {
                        HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, item.uri)
                    } catch (e: Exception) {
                        ""
                    }
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = if (localText.isNotBlank()) PageOcrStatus.DONE else PageOcrStatus.ERROR,
                            recognizedText = localText,
                            errorMessage = if (localText.isNotBlank()) null else result.exceptionOrNull()?.localizedMessage
                        )
                    }
                }
            } else {
                try {
                    val localText = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, item.uri)
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = PageOcrStatus.DONE,
                            recognizedText = localText,
                            errorMessage = null
                        )
                    }
                } catch (e: Exception) {
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = PageOcrStatus.ERROR,
                            recognizedText = "",
                            errorMessage = e.localizedMessage ?: "Ошибка распознавания"
                        )
                    }
                }
            }
        }
    }

    // Auto-process pending items sequentially
    LaunchedEffect(items, selectedMode) {
        for (i in items.indices) {
            if (items[i].status == PageOcrStatus.PENDING) {
                processItem(i)
            }
        }
    }

    val completedCount = items.count { it.status == PageOcrStatus.DONE }
    val totalCount = items.size
    val isAnyProcessing = items.any { it.status == PageOcrStatus.PROCESSING }

    // Merged text builder based on selected format
    val mergedText by remember(items, mergeFormat) {
        derivedStateOf {
            val validItems = items.filter { it.recognizedText.isNotBlank() }
            when (mergeFormat) {
                MergeFormat.WITH_HEADERS -> {
                    validItems.mapIndexed { idx, it ->
                        "--- Страница ${idx + 1} ---\n${it.recognizedText.trim()}"
                    }.joinToString("\n\n")
                }
                MergeFormat.PLAIN_PARAGRAPHS -> {
                    validItems.joinToString("\n\n") { it.recognizedText.trim() }
                }
                MergeFormat.NUMBERED_LIST -> {
                    validItems.mapIndexed { idx, it ->
                        "${idx + 1}. ${it.recognizedText.trim()}"
                    }.joinToString("\n\n")
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TOP HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CollectionsBookmark,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Пакетное сканирование фото",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Обработано $completedCount из $totalCount страниц",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                // MODE SELECTOR & PROGRESS BAR
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = selectedMode == OcrMode.GEMINI_AI,
                                onClick = {
                                    selectedMode = OcrMode.GEMINI_AI
                                    // Reset and reprocess all
                                    items = items.map { it.copy(status = PageOcrStatus.PENDING) }
                                },
                                label = { Text("✨ ИИ Gemini") },
                                leadingIcon = {
                                    if (selectedMode == OcrMode.GEMINI_AI) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )

                            FilterChip(
                                selected = selectedMode == OcrMode.LOCAL_DEVICE,
                                onClick = {
                                    selectedMode = OcrMode.LOCAL_DEVICE
                                    items = items.map { it.copy(status = PageOcrStatus.PENDING) }
                                },
                                label = { Text("⚡ Офлайн (ML Kit)") },
                                leadingIcon = {
                                    if (selectedMode == OcrMode.LOCAL_DEVICE) {
                                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                }
                            )
                        }

                        OutlinedButton(
                            onClick = { addPhotosLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Фото", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    if (isAnyProcessing) {
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // NAVIGATION TABS
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Страницы ($totalCount)") },
                        icon = { Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Объединить") },
                        icon = { Icon(Icons.Filled.Merge, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("ИИ-структурирование") },
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                // TAB CONTENT
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    when (selectedTab) {
                        // TAB 0: PAGES LIST
                        0 -> {
                            if (items.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text("Нет выбранных фото", style = MaterialTheme.typography.bodyMedium)
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(onClick = { addPhotosLauncher.launch("image/*") }) {
                                            Text("Выбрать фото")
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                                        ElevatedCard(
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.weight(1f)
                                                    ) {
                                                        AsyncImage(
                                                            model = item.uri,
                                                            contentDescription = "Страница ${index + 1}",
                                                            modifier = Modifier
                                                                .size(44.dp)
                                                                .clip(RoundedCornerShape(8.dp)),
                                                            contentScale = ContentScale.Crop
                                                        )
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                        Column {
                                                            Text(
                                                                text = "Страница ${index + 1}",
                                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                            )
                                                            when (item.status) {
                                                                PageOcrStatus.PROCESSING -> {
                                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp)
                                                                        Spacer(modifier = Modifier.width(6.dp))
                                                                        Text("Распознавание...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                                    }
                                                                }
                                                                PageOcrStatus.DONE -> {
                                                                    Text(
                                                                        text = "${item.recognizedText.length} симв. (готово)",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.primary
                                                                    )
                                                                }
                                                                PageOcrStatus.ERROR -> {
                                                                    Text(
                                                                        text = item.errorMessage ?: "Ошибка",
                                                                        style = MaterialTheme.typography.labelSmall,
                                                                        color = MaterialTheme.colorScheme.error
                                                                    )
                                                                }
                                                                PageOcrStatus.PENDING -> {
                                                                    Text("В очереди", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                                }
                                                            }
                                                        }
                                                    }

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = { processItem(index) },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Filled.Refresh, contentDescription = "Повторить", modifier = Modifier.size(18.dp))
                                                        }

                                                        if (index > 0) {
                                                            IconButton(
                                                                onClick = {
                                                                    val mutable = items.toMutableList()
                                                                    val tmp = mutable[index]
                                                                    mutable[index] = mutable[index - 1]
                                                                    mutable[index - 1] = tmp
                                                                    items = mutable
                                                                },
                                                                modifier = Modifier.size(32.dp)
                                                            ) {
                                                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Вверх", modifier = Modifier.size(18.dp))
                                                            }
                                                        }

                                                        IconButton(
                                                            onClick = {
                                                                items = items.toMutableList().also { it.removeAt(index) }
                                                            },
                                                            modifier = Modifier.size(32.dp)
                                                        ) {
                                                            Icon(Icons.Filled.DeleteOutline, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                                        }
                                                    }
                                                }

                                                if (item.recognizedText.isNotBlank()) {
                                                    Spacer(modifier = Modifier.height(8.dp))
                                                    OutlinedTextField(
                                                        value = item.recognizedText,
                                                        onValueChange = { newText ->
                                                            items = items.toMutableList().also { list ->
                                                                list[index] = list[index].copy(recognizedText = newText)
                                                            }
                                                        },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .heightIn(max = 140.dp),
                                                        textStyle = MaterialTheme.typography.bodySmall,
                                                        placeholder = { Text("Текст страницы...") }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // TAB 1: MERGED TEXT PREVIEW
                        1 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Формат объединения:",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        MergeFormat.values().forEach { fmt ->
                                            FilterChip(
                                                selected = mergeFormat == fmt,
                                                onClick = { mergeFormat = fmt },
                                                label = { Text(fmt.title, fontSize = 11.sp) }
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = mergedText,
                                    onValueChange = {},
                                    readOnly = false,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    placeholder = { Text("Объединенный текст всех распознанных страниц...") }
                                )
                            }
                        }

                        // TAB 2: AI STRUCTURING & SYNTHESIS
                        2 -> {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Text(
                                    text = "Выберите режим интеллектуальной обработки:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    GeminiOcrService.TextStructureMode.values().forEach { mode ->
                                        FilterChip(
                                            selected = structureMode == mode,
                                            onClick = { structureMode = mode },
                                            label = { Text(mode.title, fontSize = 11.sp) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Button(
                                    onClick = {
                                        val texts = items.map { it.recognizedText }.filter { it.isNotBlank() }
                                        if (texts.isEmpty()) {
                                            Toast.makeText(context, "Нет распознанного текста для структурирования", Toast.LENGTH_SHORT).show()
                                            return@Button
                                        }
                                        isStructuring = true
                                        structureError = null
                                        coroutineScope.launch {
                                            val result = GeminiOcrService.structureBatchTexts(
                                                context = context,
                                                pageTexts = texts,
                                                mode = structureMode
                                            )
                                            isStructuring = false
                                            if (result.isSuccess) {
                                                structuredText = result.getOrNull() ?: ""
                                                structureError = null
                                            } else {
                                                structureError = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                                            }
                                        }
                                    },
                                    enabled = !isStructuring && completedCount > 0,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isStructuring) {
                                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("ИИ Gemini структурирует материалы...")
                                    } else {
                                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Структурировать страницы (${structureMode.title})")
                                    }
                                }

                                if (structureError != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = structureError!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                OutlinedTextField(
                                    value = structuredText.ifEmpty { mergedText },
                                    onValueChange = { structuredText = it },
                                    label = { Text(if (structuredText.isNotBlank()) "Результат ИИ-структурирования" else "Предпросмотр исходного текста") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    placeholder = { Text("Здесь появится структурированный конспект...") }
                                )
                            }
                        }
                    }
                }

                // BOTTOM ACTION BAR
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                val textToCopy = when (selectedTab) {
                                    2 -> structuredText.ifEmpty { mergedText }
                                    else -> mergedText
                                }
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Recognized Notes", textToCopy))
                                Toast.makeText(context, "Текст скопирован в буфер", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Копия")
                        }

                        Button(
                            onClick = {
                                val textToInsert = when (selectedTab) {
                                    2 -> structuredText.ifEmpty { mergedText }
                                    else -> mergedText
                                }
                                onInsertText(textToInsert, true)
                                onDismissRequest()
                            },
                            enabled = completedCount > 0,
                            modifier = Modifier.weight(1.4f)
                        ) {
                            Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Вставить в текст")
                        }
                    }
                }
            }
        }
    }
}
