package com.example.presentation.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.NoteFontFamily
import com.example.util.GlyphCategory
import com.example.util.HandwritingGlyphItem
import com.example.util.HandwritingGlyphManager
import com.example.util.NoteFontHelper
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomFontDigitizerDialog(
    initialImageUri: String? = null,
    onDismissRequest: () -> Unit,
    onFontApplied: (NoteFontFamily) -> Unit,
    onTextExtracted: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val prefs = remember { UserPreferencesManager(context) }

    // Tabs: 0: Алфавит по буквам, 1: Связный текст и переходы, 2: Тетрадь и проверка, 3: Импорт TTF
    var selectedTab by remember { mutableIntStateOf(0) }

    // Glyph List State
    var glyphItems by remember { mutableStateOf(HandwritingGlyphManager.loadAllGlyphItems(context)) }
    var selectedCategoryFilter by remember { mutableStateOf<GlyphCategory?>(null) }
    var selectedLetterToCapture by remember { mutableStateOf<HandwritingGlyphItem?>(null) }

    // Full-sheet bulk processing
    var isProcessingSheet by remember { mutableStateOf(false) }

    // Sentence analysis state
    var sentencePhotoUri by remember { mutableStateOf<Uri?>(null) }
    var isAnalyzingSentence by remember { mutableStateOf(false) }
    var penSlant by remember { mutableFloatStateOf(prefs.getHandwritingSlantSync()) }
    var penThickness by remember { mutableFloatStateOf(prefs.getHandwritingThicknessSync()) }
    var letterSpacing by remember { mutableFloatStateOf(prefs.getHandwritingSpacingSync()) }
    var selectedInkHex by remember { mutableStateOf(prefs.getHandwritingInkColorSync() ?: "#1E3A8A") }
    var connectionStyle by remember { mutableStateOf("Слитный скорописный") }

    // Live Testing Preview text
    var testInputText by remember {
        mutableStateOf("Привет! Это мой настоящий оцифрованный почерк. Все буквы, цифры 12345 и знаки (!?) сохранены.")
    }

    val (digitizedCount, totalCount) = remember(glyphItems) {
        val count = glyphItems.count { it.isDigitized }
        count to glyphItems.size
    }

    // Sheet photo picker launcher (auto-segmentation)
    val sheetPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isProcessingSheet = true
            coroutineScope.launch {
                val result = HandwritingGlyphManager.segmentAndSaveAlphabetSheet(context, uri)
                isProcessingSheet = false
                result.onSuccess { count ->
                    glyphItems = HandwritingGlyphManager.loadAllGlyphItems(context)
                    onFontApplied(NoteFontFamily.CUSTOM_DIGITIZED)
                    Toast.makeText(context, "Оцифровано $count букв с листа!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Ошибка обработки листа: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Sentence photo picker launcher
    val sentencePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            sentencePhotoUri = uri
            isAnalyzingSentence = true
            coroutineScope.launch {
                val res = HandwritingGlyphManager.analyzeSentenceWriting(context, uri)
                isAnalyzingSentence = false
                res.onSuccess { metrics ->
                    penSlant = metrics.slantAngle
                    penThickness = metrics.strokeThickness
                    letterSpacing = metrics.letterSpacing
                    selectedInkHex = metrics.inkColorHex
                    connectionStyle = metrics.connectionStyle
                    onFontApplied(NoteFontFamily.CUSTOM_DIGITIZED)
                    Toast.makeText(context, "Параметры переходов и наклона извлечены!", Toast.LENGTH_SHORT).show()
                }.onFailure { err ->
                    Toast.makeText(context, "Ошибка анализа: ${err.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Direct TTF import
    val fontFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val destDir = File(context.filesDir, "custom_fonts").apply { mkdirs() }
                val destFile = File(destDir, "active_font.ttf")
                context.contentResolver.openInputStream(uri)?.use { inStream ->
                    FileOutputStream(destFile).use { outStream ->
                        inStream.copyTo(outStream)
                    }
                }
                prefs.setCustomFontPathSync(destFile.absolutePath)
                prefs.setDefaultNoteFontSync(NoteFontFamily.CUSTOM_DIGITIZED.id)
                onFontApplied(NoteFontFamily.CUSTOM_DIGITIZED)
                Toast.makeText(context, "Файл шрифта успешно импортирован!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Ошибка импорта шрифта: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // Single letter modal dialog if selected
    selectedLetterToCapture?.let { letterItem ->
        SingleLetterCaptureDialog(
            item = letterItem,
            onDismissRequest = { selectedLetterToCapture = null },
            onGlyphSaved = {
                selectedLetterToCapture = null
                glyphItems = HandwritingGlyphManager.loadAllGlyphItems(context)
                onFontApplied(NoteFontFamily.CUSTOM_DIGITIZED)
            }
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Студия оцифровки почерка",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Оцифровано $digitizedCount из $totalCount символов (${if (totalCount > 0) (digitizedCount * 100 / totalCount) else 0}%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Bar
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) digitizedCount.toFloat() / totalCount else 0f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Primary Tabs
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Алфавит по буквам", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.GridOn, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Связный текст", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.Gesture, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Проверка в тетради", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.EditNote, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                        text = { Text("Импорт TTF", fontSize = 12.sp) },
                        icon = { Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // TAB CONTENT
                Box(modifier = Modifier.weight(1f)) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 0: ALPHABET CHARACTER GRID
                            Column(modifier = Modifier.fillMaxSize()) {
                                // Action Bar: Bulk photo button + Filters
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { sheetPickerLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.DocumentScanner, null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Фото всего листа алфавита", fontSize = 11.sp)
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            HandwritingGlyphManager.resetAllGlyphs(context)
                                            glyphItems = HandwritingGlyphManager.loadAllGlyphItems(context)
                                            Toast.makeText(context, "Символы сброшены", Toast.LENGTH_SHORT).show()
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Icon(Icons.Filled.Refresh, null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Сброс", fontSize = 11.sp)
                                    }
                                }

                                if (isProcessingSheet) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("Сегментация и оцифровка букв с листа бумаги...", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Filter Chips: Все, Строчные, Заглавные, Цифры, Знаки
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    FilterChip(
                                        selected = selectedCategoryFilter == null,
                                        onClick = { selectedCategoryFilter = null },
                                        label = { Text("Все (${glyphItems.size})", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == GlyphCategory.LOWERCASE,
                                        onClick = { selectedCategoryFilter = GlyphCategory.LOWERCASE },
                                        label = { Text("а-я (33)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == GlyphCategory.UPPERCASE,
                                        onClick = { selectedCategoryFilter = GlyphCategory.UPPERCASE },
                                        label = { Text("А-Я (33)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == GlyphCategory.DIGIT,
                                        onClick = { selectedCategoryFilter = GlyphCategory.DIGIT },
                                        label = { Text("0-9 (10)", fontSize = 11.sp) }
                                    )
                                    FilterChip(
                                        selected = selectedCategoryFilter == GlyphCategory.PUNCTUATION,
                                        onClick = { selectedCategoryFilter = GlyphCategory.PUNCTUATION },
                                        label = { Text("Знаки (10)", fontSize = 11.sp) }
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                val filteredItems = glyphItems.filter {
                                    selectedCategoryFilter == null || it.category == selectedCategoryFilter
                                }

                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 64.dp),
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(filteredItems, key = { it.char.code }) { item ->
                                        CharacterGridCell(
                                            item = item,
                                            onClick = { selectedLetterToCapture = item }
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // TAB 1: CONNECTED TEXT & TRANSITIONS
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Оцифровка переходов, наклона и динамики пера по фото связного предложения:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(10.dp))

                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Button(
                                            onClick = { sentencePickerLauncher.launch("image/*") },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Загрузить фото рукописного предложения")
                                        }

                                        if (isAnalyzingSentence) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                            Text("Извлечение наклона, ритма и цвета чернил...", fontSize = 11.sp)
                                        }

                                        if (sentencePhotoUri != null) {
                                            Spacer(modifier = Modifier.height(10.dp))
                                            AsyncImage(
                                                model = sentencePhotoUri,
                                                contentDescription = "Образец связного текста",
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(120.dp)
                                                    .clip(RoundedCornerShape(8.dp)),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Индивидуальные параметры почерка:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Slant Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Наклон почерка: ${penSlant.toInt()}°", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
                                    Slider(
                                        value = penSlant,
                                        onValueChange = {
                                            penSlant = it
                                            prefs.saveHandwritingSettingsSync(slant = it, thickness = penThickness, spacing = letterSpacing, samplePath = null, inkColorHex = selectedInkHex)
                                            HandwritingGlyphManager.syncActiveFont(context)
                                        },
                                        valueRange = -5f..25f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Thickness Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Толщина пера: ${penThickness.toInt()} px", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
                                    Slider(
                                        value = penThickness,
                                        onValueChange = {
                                            penThickness = it
                                            prefs.saveHandwritingSettingsSync(slant = penSlant, thickness = it, spacing = letterSpacing, samplePath = null, inkColorHex = selectedInkHex)
                                        },
                                        valueRange = 2f..8f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                // Letter Spacing Slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Интервал букв:", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(140.dp))
                                    Slider(
                                        value = letterSpacing,
                                        onValueChange = {
                                            letterSpacing = it
                                            prefs.saveHandwritingSettingsSync(slant = penSlant, thickness = penThickness, spacing = it, samplePath = null, inkColorHex = selectedInkHex)
                                        },
                                        valueRange = 0.8f..2.0f,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Ink Color Selection
                                Text("Цвет чернил ручки:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    val colors = listOf(
                                        "#1E3A8A" to "Синяя шариковая",
                                        "#212121" to "Черная гелевая",
                                        "#4A148C" to "Фиолетовая",
                                        "#1B5E20" to "Зеленая паста"
                                    )
                                    colors.forEach { (hex, name) ->
                                        Surface(
                                            color = Color(android.graphics.Color.parseColor(hex)),
                                            shape = CircleShape,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .border(
                                                    width = if (selectedInkHex == hex) 3.dp else 1.dp,
                                                    color = if (selectedInkHex == hex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    selectedInkHex = hex
                                                    prefs.saveHandwritingSettingsSync(slant = penSlant, thickness = penThickness, spacing = letterSpacing, samplePath = null, inkColorHex = hex)
                                                    Toast.makeText(context, name, Toast.LENGTH_SHORT).show()
                                                }
                                        ) {}
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "Тип соединений букв:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    listOf("Слитный скорописный", "Полуслитный", "Раздельный").forEach { style ->
                                        FilterChip(
                                            selected = connectionStyle == style,
                                            onClick = { connectionStyle = style },
                                            label = { Text(style, fontSize = 11.sp) }
                                        )
                                    }
                                }
                            }
                        }
                        2 -> {
                            // TAB 2: INTERACTIVE NOTEBOOK TESTING
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(
                                    text = "Проверьте ввод любого текста вашим реальным почерком:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                OutlinedTextField(
                                    value = testInputText,
                                    onValueChange = { testInputText = it },
                                    label = { Text("Введите проверочный русский текст:") },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = "Отображение на тетрадном листе:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.height(6.dp))

                                val customFont = NoteFontHelper.getFontFamily(context, NoteFontFamily.CUSTOM_DIGITIZED)
                                val inkColor = try {
                                    Color(android.graphics.Color.parseColor(selectedInkHex))
                                } catch (e: Exception) {
                                    Color(0xFF1E3A8A)
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFFAF9F6),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                    tonalElevation = 2.dp
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Text(
                                            text = testInputText,
                                            fontFamily = customFont,
                                            fontSize = 20.sp,
                                            lineHeight = 28.sp,
                                            letterSpacing = (letterSpacing * 0.5f).sp,
                                            color = inkColor
                                        )
                                    }
                                }
                            }
                        }
                        3 -> {
                            // TAB 3: TTF / OTF FILE IMPORT
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(Icons.Filled.FolderOpen, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Импорт готового файла шрифта (.ttf / .otf)",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Если у вас есть сгенерированный шрифт вашего почерка (например, из Calligraphr), вы можете загрузить его напрямую.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { fontFilePickerLauncher.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.UploadFile, null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Выбрать файл шрифта (.ttf / .otf)")
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Bottom Primary Action Button
                Button(
                    onClick = {
                        prefs.setDefaultNoteFontSync(NoteFontFamily.CUSTOM_DIGITIZED.id)
                        onFontApplied(NoteFontFamily.CUSTOM_DIGITIZED)
                        Toast.makeText(context, "Оцифрованный почерк активирован для заметок!", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Check, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Применить мой почерк ко всем заметкам")
                }
            }
        }
    }
}

@Composable
private fun CharacterGridCell(
    item: HandwritingGlyphItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (item.isDigitized) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = androidx.compose.foundation.BorderStroke(
            width = if (item.isDigitized) 1.5.dp else 1.dp,
            color = if (item.isDigitized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.isDigitized && item.imagePath != null && File(item.imagePath).exists()) {
                AsyncImage(
                    model = File(item.imagePath),
                    contentDescription = item.title,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
                // Small check badge in corner
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            } else {
                Text(
                    text = item.char.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}
