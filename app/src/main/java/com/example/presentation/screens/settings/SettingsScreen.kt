package com.example.presentation.screens.settings

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.preferences.FontSizeScale
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.presentation.components.AudioPerceptionSettingsDialog
import com.example.presentation.components.TooltipIconButton
import com.example.ui.theme.AppThemePreset
import com.example.util.BiometricAuthUtil
import com.example.util.GeminiOcrService
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesManager: UserPreferencesManager,
    repository: NoteRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val clipboardManager = LocalClipboardManager.current

    val currentTheme by preferencesManager.themeFlow.collectAsState(initial = AppThemePreset.PURITY)
    val currentFontSize by preferencesManager.fontSizeFlow.collectAsState(initial = FontSizeScale.NORMAL)
    val isPinEnabled by preferencesManager.isPinEnabledFlow.collectAsState(initial = false)
    val isBiometricEnabled by preferencesManager.isBiometricEnabledFlow.collectAsState(initial = false)
    val isBiometricAvailable = remember { BiometricAuthUtil.isBiometricAvailable(context) }

    var showPinDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var showImportDialog by remember { mutableStateOf(false) }
    var importJsonInput by remember { mutableStateOf("") }
    var importError by remember { mutableStateOf<String?>(null) }
    var showAudioPerceptionDialog by remember { mutableStateOf(false) }

    val geminiApiKeyFromStore by preferencesManager.geminiApiKeyFlow.collectAsState(initial = "")
    var geminiKeyInput by remember { mutableStateOf("") }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isTestingKey by remember { mutableStateOf(false) }
    var testResultStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val ocrPreferAi by preferencesManager.ocrPreferAiFlow.collectAsState(initial = true)

    LaunchedEffect(geminiApiKeyFromStore) {
        if (geminiKeyInput.isEmpty() && geminiApiKeyFromStore.isNotEmpty()) {
            geminiKeyInput = geminiApiKeyFromStore
        }
    }

    val jsonConfig = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Настройки") },
                navigationIcon = {
                    TooltipIconButton(
                        onClick = onBack,
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        tooltip = "Вернуться назад"
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // THEME SECTION
            Text(
                text = "Тема оформления",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Выберите комфортную цветовую схему для интерфейса:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            val displayThemes = listOf(
                AppThemePreset.PURITY,
                AppThemePreset.DEPTH,
                AppThemePreset.WARMTH,
                AppThemePreset.MATERIAL_YOU
            )
            displayThemes.forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { preferencesManager.setTheme(preset) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentTheme == preset,
                        onClick = { scope.launch { preferencesManager.setTheme(preset) } }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(preset.title, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            // FONT SIZE SECTION
            Text(
                text = "Размер шрифта",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Масштабирование текста внутри заметок:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            FontSizeScale.values().forEach { scale ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { preferencesManager.setFontSize(scale) } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = currentFontSize == scale,
                        onClick = { scope.launch { preferencesManager.setFontSize(scale) } }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(scale.title, style = MaterialTheme.typography.bodyLarge)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            // SECURITY SECTION
            Text(
                text = "Безопасность",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Защита конфиденциальных записей от посторонних:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Защита PIN-кодом", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = if (isPinEnabled) "PIN-код активен" else "Блокировка отключена",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isPinEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            showPinDialog = true
                        } else {
                            scope.launch {
                                preferencesManager.setPinEnabled(false)
                                snackbarHostState.showSnackbar("Защита PIN-кодом отключена")
                            }
                        }
                    }
                )
            }

            if (isPinEnabled) {
                OutlinedButton(
                    onClick = { showPinDialog = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Icon(Icons.Filled.Password, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сменить PIN-код")
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Вход по биометрии", style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            text = if (!isBiometricAvailable) "Биометрия не настроена на устройстве"
                            else if (isBiometricEnabled) "Отпечаток пальца / Face Unlock активен"
                            else "Быстрая разблокировка отпечатком",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled && isBiometricAvailable,
                        enabled = isBiometricAvailable,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                preferencesManager.setBiometricEnabled(enabled)
                                snackbarHostState.showSnackbar(
                                    if (enabled) "Вход по биометрии включен" else "Вход по биометрии выключен"
                                )
                            }
                        }
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            // AUDIO PERCEPTION & STT SETTINGS SECTION
            Text(
                text = "Восприятие звука и диктовка",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Настройка микрофона, точности распознавания речи и исправление неверно оцифрованных слов:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedCard(
                onClick = { showAudioPerceptionDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.GraphicEq,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Качество восприятия звука",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Шумоподавление, чувствительность, словарь автозамены",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            // AI & GEMINI API SECTION (DATASTORE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Gemini API & ИИ (DataStore)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Хранилище ключа: локальный DataStore приложения",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (geminiApiKeyFromStore.isNotBlank()) {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("В DataStore", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                } else if (BuildConfig.GEMINI_API_KEY.isNotBlank() && BuildConfig.GEMINI_API_KEY != "null" && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY") {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("BuildConfig", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    )
                } else {
                    SuggestionChip(
                        onClick = {},
                        label = { Text("Не настроен", style = MaterialTheme.typography.labelSmall) },
                        icon = {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Key,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Обновление GEMINI_API_KEY",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Используется для точного распознавания текста (OCR) и ИИ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = geminiKeyInput,
                        onValueChange = {
                            geminiKeyInput = it
                            testResultStatus = null
                        },
                        label = { Text("GEMINI_API_KEY") },
                        placeholder = { Text("AIzaSy...") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("gemini_api_key_input"),
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.VpnKey,
                                contentDescription = null,
                                tint = if (geminiKeyInput.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (geminiKeyInput.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            geminiKeyInput = ""
                                            testResultStatus = null
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Clear,
                                            contentDescription = "Очистить поле ввода",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val clipText = clipboardManager.getText()?.text
                                        if (!clipText.isNullOrBlank()) {
                                            geminiKeyInput = clipText.trim()
                                            testResultStatus = null
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Ключ вставлен из буфера обмена")
                                            }
                                        } else {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Буфер обмена пуст")
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ContentPaste,
                                        contentDescription = "Вставить из буфера",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { isKeyVisible = !isKeyVisible },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        if (isKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = if (isKeyVisible) "Скрыть ключ" else "Показать ключ",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        },
                        supportingText = {
                            val isUnsaved = geminiKeyInput.trim() != geminiApiKeyFromStore
                            if (isUnsaved) {
                                Text(
                                    "Есть несохраненные изменения — нажмите «Сохранить в DataStore»",
                                    color = MaterialTheme.colorScheme.tertiary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else if (geminiApiKeyFromStore.isNotBlank()) {
                                Text(
                                    "Ключ сохранен в DataStore и активен",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelSmall
                                )
                            } else {
                                Text(
                                    "Ключ будет записан в Preferences DataStore",
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val trimmed = geminiKeyInput.trim()
                                    preferencesManager.setGeminiApiKey(trimmed)
                                    testResultStatus = null
                                    snackbarHostState.showSnackbar("GEMINI_API_KEY успешно сохранен в DataStore")
                                }
                            },
                            modifier = Modifier
                                .weight(1.3f)
                                .testTag("save_gemini_key_button")
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Сохранить в DataStore")
                        }

                        OutlinedButton(
                            onClick = {
                                val keyToTest = geminiKeyInput.trim().ifEmpty {
                                    preferencesManager.getGeminiApiKeySync()
                                }.ifEmpty {
                                    BuildConfig.GEMINI_API_KEY.trim()
                                }
                                if (keyToTest.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Сначала введите или сохраните ключ")
                                    }
                                    return@OutlinedButton
                                }
                                isTestingKey = true
                                testResultStatus = null
                                scope.launch {
                                    val result = GeminiOcrService.testApiKey(keyToTest)
                                    isTestingKey = false
                                    if (result.isSuccess) {
                                        testResultStatus = Pair(true, result.getOrNull() ?: "Ключ действителен")
                                    } else {
                                        val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка проверки"
                                        testResultStatus = Pair(false, err)
                                    }
                                }
                            },
                            enabled = !isTestingKey,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isTestingKey) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Проверить")
                            }
                        }
                    }

                    testResultStatus?.let { (success, message) ->
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (success) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                    contentDescription = null,
                                    tint = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (success) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    if (geminiApiKeyFromStore.isNotBlank() || geminiKeyInput.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            onClick = {
                                scope.launch {
                                    preferencesManager.clearGeminiApiKey()
                                    geminiKeyInput = ""
                                    testResultStatus = null
                                    snackbarHostState.showSnackbar("GEMINI_API_KEY удален из DataStore")
                                }
                            },
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            modifier = Modifier.align(Alignment.End)
                        ) {
                            Icon(Icons.Filled.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Удалить ключ из DataStore", style = MaterialTheme.typography.labelMedium)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "Где получить ключ: перейдите на ai.google.dev (Google AI Studio) и создайте бесплатный токен доступа API. Ключ сохранится в DataStore и решит проблему исчерпания квоты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Предпочитать ИИ при сканировании", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Автоматически использовать Gemini для идеального русского языка без искажений",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = ocrPreferAi,
                    onCheckedChange = { prefer ->
                        scope.launch {
                            preferencesManager.setOcrPreferAi(prefer)
                        }
                    }
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))

            // BACKUP & RESTORE SECTION
            Text(
                text = "Резервное копирование и перенос",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "Сохраняйте копии всех заметок и восстанавливайте их в любой момент:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val notes = repository.getAllNotesForBackup()
                            val json = jsonConfig.encodeToString(notes)
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, "notes_backup.json")
                                putExtra(Intent.EXTRA_TEXT, json)
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Экспорт резервной копии"))
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Экспорт")
                }

                OutlinedButton(
                    onClick = {
                        importError = null
                        importJsonInput = ""
                        showImportDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Импорт")
                }
            }
        }
    }

    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Установить PIN-код")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Введите 4 цифры для разблокировки приложения:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) pinInput = it },
                        placeholder = { Text("4 цифры") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = pinInput.length == 4,
                    onClick = {
                        if (pinInput.length == 4) {
                            scope.launch {
                                preferencesManager.setPinCode(pinInput)
                                preferencesManager.setPinEnabled(true)
                                showPinDialog = false
                                pinInput = ""
                                snackbarHostState.showSnackbar("PIN-код успешно сохранен")
                            }
                        }
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Импорт заметок (JSON)")
                }
            },
            text = {
                Column {
                    Text(
                        text = "Вставьте ранее экспортированный текст резервной копии (JSON):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importJsonInput,
                        onValueChange = {
                            importJsonInput = it
                            importError = null
                        },
                        placeholder = { Text("[{\"title\":\"Заметка\", ...}]") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        isError = importError != null
                    )
                    if (importError != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = importError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = importJsonInput.isNotBlank(),
                    onClick = {
                        try {
                            val notes = jsonConfig.decodeFromString<List<Note>>(importJsonInput.trim())
                            if (notes.isEmpty()) {
                                importError = "Список заметок в JSON пуст"
                            } else {
                                scope.launch {
                                    repository.restoreNotes(notes)
                                    showImportDialog = false
                                    snackbarHostState.showSnackbar("Успешно импортировано заметок: ${notes.size}")
                                }
                            }
                        } catch (e: Exception) {
                            importError = "Ошибка парсинга JSON: ${e.localizedMessage ?: "неверный формат"}"
                        }
                    }
                ) {
                    Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Отмена") }
            }
        )
    }

    if (showAudioPerceptionDialog) {
        AudioPerceptionSettingsDialog(
            preferencesManager = preferencesManager,
            onDismissRequest = { showAudioPerceptionDialog = false }
        )
    }
}
