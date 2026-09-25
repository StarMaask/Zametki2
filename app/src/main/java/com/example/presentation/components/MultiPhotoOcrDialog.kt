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
import androidx.compose.ui.text.style.TextOverflow
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
    WITH_HEADERS("С заголовками"),
    PLAIN_PARAGRAPHS("Сплошной"),
    NUMBERED_LIST("Нумерованный")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiPhotoOcrDialog(
    initialImageUris: List<Uri>,
    onDismissRequest: () -> Unit,
    onInsertText: (insertedText: String, insertAtCursor: Boolean, saveImmediately: Boolean) -> Unit
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

    var selectedTab by remember { mutableStateOf(0) } // 0: Страницы, 1: Объединение, 2: ИИ-конспект
    var mergeFormat by remember { mutableStateOf(MergeFormat.WITH_HEADERS) }

    var structuredText by remember { mutableStateOf("") }
    var isStructuring by remember { mutableStateOf(false) }
    var structureMode by remember { mutableStateOf(GeminiOcrService.TextStructureMode.STRUCTURED_NOTES) }
    var structureError by remember { mutableStateOf<String?>(null) }
    var showApiKeyDialog by remember { mutableStateOf(false) }

    // Launcher to add more photos
    val addPhotosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { newUris ->
        if (newUris.isNotEmpty()) {
            val newItems = newUris.map { PageScanItem(uri = it) }
            items = items + newItems
        }
    }

    // Function to run OCR for a specific item
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
                    val text = result.getOrNull()?.trim() ?: ""
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = PageOcrStatus.DONE,
                            recognizedText = text,
                            errorMessage = null
                        )
                    }
                } else {
                    val geminiError = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                    // Fallback to local on-device OCR
                    try {
                        val localText = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, item.uri).trim()
                        if (localText.isNotBlank()) {
                            items = items.toMutableList().also { list ->
                                list[index] = list[index].copy(
                                    status = PageOcrStatus.DONE,
                                    recognizedText = localText,
                                    errorMessage = null
                                )
                            }
                        } else {
                            items = items.toMutableList().also { list ->
                                list[index] = list[index].copy(
                                    status = PageOcrStatus.ERROR,
                                    recognizedText = "",
                                    errorMessage = "Текст на фото не распознан"
                                )
                            }
                        }
                    } catch (e: Exception) {
                        items = items.toMutableList().also { list ->
                            list[index] = list[index].copy(
                                status = PageOcrStatus.ERROR,
                                recognizedText = "",
                                errorMessage = geminiError
                            )
                        }
                    }
                }
            } else {
                try {
                    val localText = HandwritingPhotoDigitizer.extractTextFromImageOnDevice(context, item.uri).trim()
                    if (localText.isNotBlank()) {
                        items = items.toMutableList().also { list ->
                            list[index] = list[index].copy(
                                status = PageOcrStatus.DONE,
                                recognizedText = localText,
                                errorMessage = null
                            )
                        }
                    } else {
                        items = items.toMutableList().also { list ->
                            list[index] = list[index].copy(
                                status = PageOcrStatus.ERROR,
                                recognizedText = "",
                                errorMessage = "Текст не обнаружен. Проверьте четкость снимка."
                            )
                        }
                    }
                } catch (e: Exception) {
                    items = items.toMutableList().also { list ->
                        list[index] = list[index].copy(
                            status = PageOcrStatus.ERROR,
                            recognizedText = "",
                            errorMessage = e.localizedMessage ?: "Ошибка оптического распознавания"
                        )
                    }
                }
            }
        }
    }

    // Auto-process pending items
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

    var customMergedText by remember { mutableStateOf<String?>(null) }

    // When items or merge format change, reset customMergedText if not edited
    val activeMergedText = customMergedText ?: mergedText

    fun getActiveText(): String {
        return when (selectedTab) {
            2 -> structuredText.ifEmpty { activeMergedText }
            else -> activeMergedText
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .imePadding()
        ) {
            Card(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. TOP HEADER (COMPACT WITH DIRECT SAVE BUTTON)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.CollectionsBookmark,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Пакетный скан фото",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1
                                )
                                Text(
                                    text = "Готово $completedCount из $totalCount",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            // Direct prominent Save button in top bar (matches Note Editor style)
                            Button(
                                onClick = {
                                    onInsertText(getActiveText(), true, true)
                                    onDismissRequest()
                                },
                                enabled = completedCount > 0,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = { showApiKeyDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text("Ключ", fontSize = 11.sp)
                            }

                            IconButton(
                                onClick = onDismissRequest,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть", modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    // 2. MODE SELECTOR & CONTROLS ROW
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = selectedMode == OcrMode.GEMINI_AI,
                                onClick = {
                                    selectedMode = OcrMode.GEMINI_AI
                                    items = items.map { it.copy(status = PageOcrStatus.PENDING) }
                                },
                                label = { Text("✨ ИИ Gemini", fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )

                            FilterChip(
                                selected = selectedMode == OcrMode.LOCAL_DEVICE,
                                onClick = {
                                    selectedMode = OcrMode.LOCAL_DEVICE
                                    items = items.map { it.copy(status = PageOcrStatus.PENDING) }
                                },
                                label = { Text("⚡ Офлайн (ML Kit)", fontSize = 11.sp) },
                                modifier = Modifier.height(32.dp)
                            )

                            OutlinedButton(
                                onClick = { showApiKeyDialog = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Ключ", fontSize = 11.sp)
                            }
                        }

                        FilledTonalButton(
                            onClick = { addPhotosLauncher.launch("image/*") },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("+ Фото", fontSize = 11.sp)
                        }
                    }

                    if (isAnyProcessing) {
                        LinearProgressIndicator(
                            progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                        )
                    }

                    // 3. TABS
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text("Страницы ($totalCount)", fontSize = 12.sp, maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text("Объединить", fontSize = 12.sp, maxLines = 1) }
                        )
                        Tab(
                            selected = selectedTab == 2,
                            onClick = { selectedTab = 2 },
                            text = { Text("ИИ-конспект", fontSize = 12.sp, maxLines = 1) }
                        )
                    }

                    // 4. MAIN SCROLLABLE CONTENT (WEIGHT = 1F)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        when (selectedTab) {
                            // TAB 0: PAGES LIST
                            0 -> {
                                if (items.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(44.dp), tint = MaterialTheme.colorScheme.outline)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text("Нет выбранных фото", style = MaterialTheme.typography.bodyMedium)
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { addPhotosLauncher.launch("image/*") }) {
                                                Text("Выбрать фото")
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                                                    .size(42.dp)
                                                                    .clip(RoundedCornerShape(8.dp)),
                                                                contentScale = ContentScale.Crop
                                                            )
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Column(modifier = Modifier.weight(1f)) {
                                                                Text(
                                                                    text = "Страница ${index + 1}",
                                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                                                )
                                                                when (item.status) {
                                                                    PageOcrStatus.PROCESSING -> {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            CircularProgressIndicator(modifier = Modifier.size(10.dp), strokeWidth = 2.dp)
                                                                            Spacer(modifier = Modifier.width(4.dp))
                                                                            Text("Распознавание...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                                                        }
                                                                    }
                                                                    PageOcrStatus.DONE -> {
                                                                        Text(
                                                                            text = "Готово • ${item.recognizedText.length} симв.",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.primary
                                                                        )
                                                                    }
                                                                    PageOcrStatus.ERROR -> {
                                                                        Text(
                                                                            text = item.errorMessage ?: "Ошибка распознавания",
                                                                            style = MaterialTheme.typography.labelSmall,
                                                                            color = MaterialTheme.colorScheme.error,
                                                                            maxLines = 1,
                                                                            overflow = TextOverflow.Ellipsis
                                                                        )
                                                                    }
                                                                    PageOcrStatus.PENDING -> {
                                                                        Text("В очереди", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            if (item.recognizedText.isNotBlank()) {
                                                                IconButton(
                                                                    onClick = {
                                                                        items = items.toMutableList().also { list ->
                                                                            list[index] = list[index].copy(isExpanded = !list[index].isExpanded)
                                                                        }
                                                                    },
                                                                    modifier = Modifier.size(32.dp)
                                                                ) {
                                                                    Icon(
                                                                        if (item.isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                                        contentDescription = "Текст",
                                                                        modifier = Modifier.size(18.dp)
                                                                    )
                                                                }
                                                            }

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

                                                    if (item.status == PageOcrStatus.ERROR && item.errorMessage != null) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                                            modifier = Modifier.fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = item.errorMessage!!,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                                modifier = Modifier.padding(6.dp)
                                                            )
                                                        }
                                                    }

                                                    AnimatedVisibility(visible = item.isExpanded || (item.recognizedText.isNotBlank() && items.size <= 2)) {
                                                        Column {
                                                            Spacer(modifier = Modifier.height(6.dp))
                                                            OutlinedTextField(
                                                                value = item.recognizedText,
                                                                onValueChange = { newText ->
                                                                    items = items.toMutableList().also { list ->
                                                                        list[index] = list[index].copy(recognizedText = newText)
                                                                    }
                                                                },
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .heightIn(max = 120.dp),
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
                            }

                            // TAB 1: MERGED TEXT
                            1 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = "Формат:",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                        )

                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            MergeFormat.values().forEach { fmt ->
                                                FilterChip(
                                                    selected = mergeFormat == fmt,
                                                    onClick = { mergeFormat = fmt },
                                                    label = { Text(fmt.title, fontSize = 10.sp) },
                                                    modifier = Modifier.height(30.dp)
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = activeMergedText,
                                        onValueChange = { customMergedText = it },
                                        readOnly = false,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        placeholder = { Text("Объединенный текст всех распознанных страниц...") }
                                    )
                                }
                            }

                            // TAB 2: AI STRUCTURING
                            2 -> {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        GeminiOcrService.TextStructureMode.values().forEach { mode ->
                                            FilterChip(
                                                selected = structureMode == mode,
                                                onClick = { structureMode = mode },
                                                label = { Text(mode.title, fontSize = 10.sp, maxLines = 1) },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

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
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(38.dp)
                                    ) {
                                        if (isStructuring) {
                                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("ИИ структурирует конспект...", fontSize = 12.sp)
                                        } else {
                                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Запустить ИИ-структурирование", fontSize = 12.sp)
                                        }
                                    }

                                    if (structureError != null) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = structureError!!,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error,
                                            maxLines = 2
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = structuredText.ifEmpty { activeMergedText },
                                        onValueChange = { structuredText = it },
                                        label = { Text(if (structuredText.isNotBlank()) "Готовый ИИ-конспект" else "Исходный объединенный текст", fontSize = 11.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
                                        placeholder = { Text("Здесь появится структурированный конспект...") }
                                    )
                                }
                            }
                        }
                    }

                    // 5. BOTTOM ACTION BAR (ALWAYS DOCKED & VISIBLE)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        tonalElevation = 6.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val textToCopy = getActiveText()
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Recognized Notes", textToCopy))
                                    Toast.makeText(context, "Текст скопирован в буфер", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(0.9f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Копия", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val textToInsert = getActiveText()
                                    onInsertText(textToInsert, true, false)
                                    onDismissRequest()
                                },
                                enabled = completedCount > 0,
                                modifier = Modifier.weight(1.0f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Filled.PlaylistAdd, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text("Вставить", fontSize = 11.sp)
                            }

                            Button(
                                onClick = {
                                    val textToInsert = getActiveText()
                                    onInsertText(textToInsert, true, true)
                                    onDismissRequest()
                                },
                                enabled = completedCount > 0,
                                modifier = Modifier.weight(1.5f),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Сохранить", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showApiKeyDialog) {
        GeminiApiKeyDialog(
            onDismissRequest = { showApiKeyDialog = false },
            onKeySaved = { _ ->
                showApiKeyDialog = false
                selectedMode = OcrMode.GEMINI_AI
                items = items.map { it.copy(status = PageOcrStatus.PENDING) }
            }
        )
    }
}
