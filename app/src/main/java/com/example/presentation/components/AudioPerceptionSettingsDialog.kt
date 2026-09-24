package com.example.presentation.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.preferences.UserPreferencesManager
import com.example.util.SpeechPostProcessor
import kotlinx.coroutines.launch
import java.util.Locale

data class LanguageOption(val tag: String, val title: String, val subtitle: String)

val SUPPORTED_SPEECH_LANGUAGES = listOf(
    LanguageOption("auto", "Авто (язык системы)", "Автоматический выбор устройства"),
    LanguageOption("ru-RU", "Русский (ru-RU)", "Русский язык и региональные диалекты"),
    LanguageOption("en-US", "English (en-US)", "United States English"),
    LanguageOption("en-GB", "English (en-GB)", "British English"),
    LanguageOption("kk-KZ", "Қазақ тілі (kk-KZ)", "Казахский язык"),
    LanguageOption("be-BY", "Беларуская (be-BY)", "Белорусский язык"),
    LanguageOption("uk-UA", "Українська (uk-UA)", "Украинский язык"),
    LanguageOption("de-DE", "Deutsch (de-DE)", "Немецкий язык"),
    LanguageOption("fr-FR", "Français (fr-FR)", "Французский язык"),
    LanguageOption("es-ES", "Español (es-ES)", "Испанский язык"),
    LanguageOption("zh-CN", "中文 (zh-CN)", "Китайский (упрощенный)")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPerceptionSettingsDialog(
    preferencesManager: UserPreferencesManager,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Perception & Mic, 1: Vocabulary, 2: Test

    val currentLang by preferencesManager.speechLanguageFlow.collectAsState(initial = preferencesManager.getSpeechLanguageSync())
    val currentAccuracy by preferencesManager.speechAccuracyFlow.collectAsState(initial = preferencesManager.getSpeechAccuracySync())
    val currentAudioSource by preferencesManager.audioSourceProfileFlow.collectAsState(initial = preferencesManager.getAudioSourceProfileSync())
    val currentSensitivity by preferencesManager.micSensitivityFlow.collectAsState(initial = preferencesManager.getMicSensitivitySync())
    val isSmartPunctuation by preferencesManager.smartPunctuationFlow.collectAsState(initial = preferencesManager.isSmartPunctuationSync())
    val isNoiseSuppression by preferencesManager.noiseSuppressionFlow.collectAsState(initial = preferencesManager.isNoiseSuppressionSync())
    val wordReplacements by preferencesManager.wordReplacementsFlow.collectAsState(initial = preferencesManager.getWordReplacementsSync())

    // Vocabulary input states
    var wrongWordInput by remember { mutableStateOf("") }
    var correctWordInput by remember { mutableStateOf("") }

    // Test states
    var isTestingMic by remember { mutableStateOf(false) }
    var testRecognizedRaw by remember { mutableStateOf("") }
    var testRecognizedProcessed by remember { mutableStateOf("") }
    var testRmsLevel by remember { mutableFloatStateOf(0f) }
    var testRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    fun stopTest() {
        try {
            testRecognizer?.cancel()
            testRecognizer?.destroy()
        } catch (_: Exception) {}
        testRecognizer = null
        isTestingMic = false
        testRmsLevel = 0f
    }

    DisposableEffect(Unit) {
        onDispose {
            stopTest()
        }
    }

    fun startTestMic() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            testRecognizedRaw = "Для проверки звука разрешите доступ к микрофону."
            return
        }

        stopTest()
        isTestingMic = true
        testRecognizedRaw = "Слушаю... Произнесите фразу или профессиональный термин"
        testRecognizedProcessed = ""

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(context)
            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    testRecognizedRaw = "Говорите в микрофон..."
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {
                    testRmsLevel = rmsdB
                }

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    testRecognizedRaw = "Обработка распознанного звука..."
                }

                override fun onError(error: Int) {
                    isTestingMic = false
                    testRmsLevel = 0f
                    testRecognizedRaw = "Звук не распознан (код ошибки $error). Попробуйте сказать громче."
                }

                override fun onResults(results: Bundle?) {
                    isTestingMic = false
                    testRmsLevel = 0f
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val raw = matches?.firstOrNull() ?: ""
                    testRecognizedRaw = raw
                    testRecognizedProcessed = SpeechPostProcessor.process(
                        text = raw,
                        enableSmartPunctuation = preferencesManager.isSmartPunctuationSync(),
                        replacements = preferencesManager.getWordReplacementsSync()
                    )
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partialMatches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val raw = partialMatches?.firstOrNull() ?: ""
                    if (raw.isNotBlank()) {
                        testRecognizedRaw = raw
                        testRecognizedProcessed = SpeechPostProcessor.process(
                            text = raw,
                            enableSmartPunctuation = preferencesManager.isSmartPunctuationSync(),
                            replacements = preferencesManager.getWordReplacementsSync()
                        )
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                val langTag = if (currentLang.isBlank() || currentLang == "auto") {
                    Locale.getDefault().toLanguageTag()
                } else {
                    currentLang
                }
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, langTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langTag)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                if (currentAccuracy == "prefer_offline") {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            testRecognizer = recognizer
            recognizer.startListening(intent)
        } catch (e: Exception) {
            isTestingMic = false
            testRecognizedRaw = "Ошибка инициализации микрофона: ${e.message}"
        }
    }

    AlertDialog(
        onDismissRequest = {
            stopTest()
            onDismissRequest()
        },
        icon = {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Качество восприятия звука",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Настройка микрофона и исправление неверно оцифрованных слов",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                // Tab Row
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Восприятие", fontSize = 12.sp, maxLines = 1) },
                        icon = { Icon(Icons.Filled.Tune, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Словарь", fontSize = 12.sp, maxLines = 1)
                                if (wordReplacements.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text("${wordReplacements.size}", fontSize = 10.sp)
                                    }
                                }
                            }
                        },
                        icon = { Icon(Icons.Filled.Spellcheck, null, modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Тест звука", fontSize = 12.sp, maxLines = 1) },
                        icon = { Icon(Icons.Filled.Mic, null, modifier = Modifier.size(16.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // TAB CONTENT
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (selectedTab) {
                        0 -> {
                            // TAB 0: PERCEPTION & MIC PROFILE
                            // Language Selection
                            Text(
                                text = "Язык распознавания речи",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            var showLangDropdown by remember { mutableStateOf(false) }
                            val activeLang = SUPPORTED_SPEECH_LANGUAGES.find { it.tag == currentLang }
                                ?: SUPPORTED_SPEECH_LANGUAGES.first()

                            OutlinedCard(
                                onClick = { showLangDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(activeLang.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                        Text(activeLang.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                                }
                            }

                            if (showLangDropdown) {
                                AlertDialog(
                                    onDismissRequest = { showLangDropdown = false },
                                    title = { Text("Выберите язык диктовки") },
                                    text = {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .verticalScroll(rememberScrollState())
                                        ) {
                                            SUPPORTED_SPEECH_LANGUAGES.forEach { lang ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            scope.launch { preferencesManager.setSpeechLanguage(lang.tag) }
                                                            showLangDropdown = false
                                                        }
                                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    RadioButton(
                                                        selected = currentLang == lang.tag,
                                                        onClick = {
                                                            scope.launch { preferencesManager.setSpeechLanguage(lang.tag) }
                                                            showLangDropdown = false
                                                        }
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column {
                                                        Text(lang.title, fontWeight = FontWeight.Medium)
                                                        Text(lang.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        TextButton(onClick = { showLangDropdown = false }) { Text("Закрыть") }
                                    }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Accuracy & Model Mode
                            Text(
                                text = "Точность оцифровки",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { scope.launch { preferencesManager.setSpeechAccuracy("online_high_accuracy") } }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = currentAccuracy == "online_high_accuracy",
                                            onClick = { scope.launch { preferencesManager.setSpeechAccuracy("online_high_accuracy") } }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Высокая точность (Google Речь / Облако)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("Наилучшее распознавание редких слов, акцентов и словарного запаса", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }

                                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { scope.launch { preferencesManager.setSpeechAccuracy("prefer_offline") } }
                                            .padding(vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = currentAccuracy == "prefer_offline",
                                            onClick = { scope.launch { preferencesManager.setSpeechAccuracy("prefer_offline") } }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text("Офлайн-режим (На устройстве)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                            Text("Работает без интернета, локальный встроенный словарь", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Microphone Profile
                            Text(
                                text = "Профиль микрофона и аудиоисточник",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    val sources = listOf(
                                        Triple("VOICE_RECOGNITION", "Речевой микрофон (Рекомендуется)", "Аппаратный фокус на голосе, срез низкочастотного гула"),
                                        Triple("CAMCORDER", "Направленный микрофон (Лекции)", "Оптимален для записи речи спикера на дистанции"),
                                        Triple("MIC", "Стандартный всенаправленный", "Базовый звук устройства без специальной речевой фильтрации")
                                    )

                                    sources.forEachIndexed { index, (key, title, desc) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { scope.launch { preferencesManager.setAudioSourceProfile(key) } }
                                                .padding(vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = currentAudioSource == key,
                                                onClick = { scope.launch { preferencesManager.setAudioSourceProfile(key) } }
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Column {
                                                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                        if (index < sources.size - 1) {
                                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                        }
                                    }
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // Sensitivity & Noise Suppression Toggles
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Аппаратное шумоподавление", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Фильтрует шум кулеров, кондиционеров и гул помещения", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isNoiseSuppression,
                                    onCheckedChange = { scope.launch { preferencesManager.setNoiseSuppression(it) } }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Голосовая пунктуация", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    Text("Слова «точка», «запятая», «абзац» преобразуются в знаки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = isSmartPunctuation,
                                    onCheckedChange = { scope.launch { preferencesManager.setSmartPunctuation(it) } }
                                )
                            }
                        }

                        1 -> {
                            // TAB 1: CUSTOM VOCABULARY & MISHEARD WORDS
                            Text(
                                text = "Словарь исправления неверно оцифрованных слов",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Если система часто ошибается в распознавании конкретных терминов, профессиональных названий, имён или аббревиатур — задайте правило автозамены:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Add Word Form
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Добавить правило исправления",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = wrongWordInput,
                                            onValueChange = { wrongWordInput = it },
                                            label = { Text("Как слышит микрофон") },
                                            placeholder = { Text("например: реакет") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = correctWordInput,
                                            onValueChange = { correctWordInput = it },
                                            label = { Text("Заменять на") },
                                            placeholder = { Text("например: React") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        enabled = wrongWordInput.isNotBlank() && correctWordInput.isNotBlank(),
                                        onClick = {
                                            if (wrongWordInput.isNotBlank() && correctWordInput.isNotBlank()) {
                                                scope.launch {
                                                    preferencesManager.addWordReplacement(wrongWordInput, correctWordInput)
                                                    wrongWordInput = ""
                                                    correctWordInput = ""
                                                }
                                            }
                                        },
                                        modifier = Modifier.align(Alignment.End)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Добавить в словарь")
                                    }
                                }
                            }

                            // Presets
                            Text(
                                text = "Быстрые шаблоны частых терминов:",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.addWordReplacement("реакет", "React")
                                            preferencesManager.addWordReplacement("питон", "Python")
                                            preferencesManager.addWordReplacement("жава", "Java")
                                            preferencesManager.addWordReplacement("котлин", "Kotlin")
                                        }
                                    },
                                    label = { Text("Языки IT", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.Code, null, modifier = Modifier.size(14.dp)) }
                                )
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.addWordReplacement("ватсап", "WhatsApp")
                                            preferencesManager.addWordReplacement("телеграм", "Telegram")
                                            preferencesManager.addWordReplacement("ютуб", "YouTube")
                                        }
                                    },
                                    label = { Text("Сервисы", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.Chat, null, modifier = Modifier.size(14.dp)) }
                                )
                                AssistChip(
                                    onClick = {
                                        scope.launch {
                                            preferencesManager.addWordReplacement("итд", "и т. д.")
                                            preferencesManager.addWordReplacement("итп", "и т. п.")
                                        }
                                    },
                                    label = { Text("Сокращения", fontSize = 11.sp) },
                                    leadingIcon = { Icon(Icons.Filled.ShortText, null, modifier = Modifier.size(14.dp)) }
                                )
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            // List of Active Replacements
                            Text(
                                text = "Активные правила автозамены (${wordReplacements.size})",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )

                            if (wordReplacements.isEmpty()) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Пользовательских правил пока нет. Добавьте выше слова, которые микрофон путает при вашей диктовке.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(14.dp)
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    wordReplacements.forEach { (wrong, correct) ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = wrong,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = MaterialTheme.colorScheme.error,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = correct,
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                        color = MaterialTheme.colorScheme.primary,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { scope.launch { preferencesManager.removeWordReplacement(wrong) } },
                                                    modifier = Modifier.size(28.dp)
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Удалить", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.outline)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        2 -> {
                            // TAB 2: LIVE TEST & MIC METER
                            Text(
                                text = "Проверка восприятия звука микрофоном",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Нажмите кнопку, скажите тестовую фразу и убедитесь, как звук оцифровывается в реальном времени:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Mic Level Visualizer
                            val animatedRms by animateFloatAsState(targetValue = testRmsLevel.coerceIn(0f, 15f) / 15f, label = "rms")
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isTestingMic) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isTestingMic) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f + (animatedRms * 0.4f))
                                                else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                    ) {
                                        Icon(
                                            imageVector = if (isTestingMic) Icons.Filled.Mic else Icons.Filled.MicNone,
                                            contentDescription = null,
                                            tint = if (isTestingMic) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // Dynamic Level Bar
                                    LinearProgressIndicator(
                                        progress = { if (isTestingMic) animatedRms else 0f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                    )

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Button(
                                        onClick = {
                                            if (isTestingMic) {
                                                stopTest()
                                            } else {
                                                startTestMic()
                                            }
                                        },
                                        colors = if (isTestingMic) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                        else ButtonDefaults.buttonColors()
                                    ) {
                                        Icon(
                                            imageVector = if (isTestingMic) Icons.Filled.Stop else Icons.Filled.RecordVoiceOver,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isTestingMic) "Остановить тест" else "Сказать тестовую фразу")
                                    }
                                }
                            }

                            // Recognized Result Output
                            if (testRecognizedRaw.isNotBlank()) {
                                Text(
                                    text = "Результат оцифровки:",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                                )
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        if (testRecognizedProcessed.isNotBlank()) {
                                            Text(
                                                text = "Окончательный текст с автокоррекцией:",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = testRecognizedProcessed,
                                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(6.dp))
                                        }
                                        Text(
                                            text = "Исходный сигнал:",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = testRecognizedRaw,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                stopTest()
                onDismissRequest()
            }) {
                Text("Готово")
            }
        }
    )
}
