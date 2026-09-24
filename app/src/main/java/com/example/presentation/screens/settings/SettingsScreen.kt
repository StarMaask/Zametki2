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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.preferences.FontSizeScale
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.Note
import com.example.domain.repository.NoteRepository
import com.example.presentation.components.AudioPerceptionSettingsDialog
import com.example.presentation.components.TooltipIconButton
import com.example.ui.theme.AppThemePreset
import com.example.util.BiometricAuthUtil
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
