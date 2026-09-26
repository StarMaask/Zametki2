package com.example.presentation.components

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import com.example.data.preferences.UserPreferencesManager
import com.example.util.GeminiOcrService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun AudioRecordDialog(
    onDismiss: () -> Unit,
    onRecordingFinished: (String) -> Unit,
    onTextTranscribed: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val preferencesManager = remember { UserPreferencesManager(context) }
    var isRecording by remember { mutableStateOf(false) }
    var isTranscribing by remember { mutableStateOf(false) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var recordDurationSeconds by remember { mutableIntStateOf(0) }
    var currentAmplitude by remember { mutableFloatStateOf(0f) }
    var showPerceptionSettings by remember { mutableStateOf(false) }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordDurationSeconds = 0
            while (isRecording) {
                delay(1000)
                recordDurationSeconds++
                try {
                    val maxAmp = mediaRecorder?.maxAmplitude ?: 0
                    currentAmplitude = (maxAmp / 32767f).coerceIn(0f, 1f)
                } catch (_: Exception) {}
            }
        }
    }

    if (showPerceptionSettings) {
        AudioPerceptionSettingsDialog(
            preferencesManager = preferencesManager,
            onDismissRequest = { showPerceptionSettings = false }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (isRecording) {
                try { mediaRecorder?.stop() } catch (_: Exception) {}
                mediaRecorder?.release()
            }
            onDismiss()
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Запись голоса")
                IconButton(
                    onClick = { showPerceptionSettings = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Настройка микрофона",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val animatedAmp by animateFloatAsState(targetValue = currentAmplitude, label = "amp")

                Icon(
                    imageVector = Icons.Filled.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (isRecording) {
                    val mins = recordDurationSeconds / 60
                    val secs = recordDurationSeconds % 60
                    Text(
                        text = String.format("%02d:%02d", mins, secs),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { animatedAmp },
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Четкая запись с шумоподавлением",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Нажмите Старт для записи аудиозаметки",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        confirmButton = {
            if (!isRecording) {
                Button(onClick = {
                    try {
                        val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.m4a")
                        outputFile = file

                        val audioSourceProfile = preferencesManager.getAudioSourceProfileSync()
                        val audioSource = when (audioSourceProfile) {
                            "CAMCORDER" -> MediaRecorder.AudioSource.CAMCORDER
                            "MIC" -> MediaRecorder.AudioSource.MIC
                            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
                        }

                        val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            MediaRecorder(context)
                        } else {
                            @Suppress("DEPRECATION")
                            MediaRecorder()
                        }.apply {
                            setAudioSource(audioSource)
                            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                            setAudioEncodingBitRate(128000)
                            setAudioSamplingRate(44100)
                            setOutputFile(file.absolutePath)
                            prepare()
                            start()
                        }
                        mediaRecorder = recorder
                        isRecording = true
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }) {
                    Text("Старт")
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = {
                            try {
                                mediaRecorder?.stop()
                                mediaRecorder?.release()
                                mediaRecorder = null
                                isRecording = false
                                outputFile?.let { onRecordingFinished(it.absolutePath) }
                                onDismiss()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                onDismiss()
                            }
                        },
                        enabled = !isTranscribing
                    ) {
                        Text("Сохранить звук")
                    }

                    if (onTextTranscribed != null) {
                        Button(
                            onClick = {
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                    mediaRecorder = null
                                    isRecording = false
                                    val savedFile = outputFile
                                    if (savedFile != null && savedFile.exists()) {
                                        onRecordingFinished(savedFile.absolutePath)
                                        isTranscribing = true
                                        Toast.makeText(context, "ИИ расшифровывает непрерывную запись...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            val result = GeminiOcrService.transcribeAudioWithGemini(context, savedFile)
                                            isTranscribing = false
                                            if (result.isSuccess) {
                                                val text = result.getOrNull() ?: ""
                                                onTextTranscribed(text)
                                                Toast.makeText(context, "Речь расшифрована в заметку!", Toast.LENGTH_SHORT).show()
                                            } else {
                                                val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка ИИ"
                                                Toast.makeText(context, "Аудио сохранено, ошибка ИИ: $err", Toast.LENGTH_LONG).show()
                                            }
                                            onDismiss()
                                        }
                                    } else {
                                        onDismiss()
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    onDismiss()
                                }
                            },
                            enabled = !isTranscribing,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            if (isTranscribing) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("ИИ пишет...")
                            } else {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("В текст (ИИ)")
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (isRecording) {
                        try { mediaRecorder?.stop() } catch (_: Exception) {}
                        mediaRecorder?.release()
                    }
                    onDismiss()
                },
                enabled = !isTranscribing
            ) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AudioPlaybackCard(
    audioUri: String,
    noteContent: String = "",
    onDelete: () -> Unit,
    onInsertTimestamp: ((String) -> Unit)? = null,
    onTranscribeRequested: ((String) -> Unit)? = null
) {
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var totalDurationMs by remember { mutableLongStateOf(0L) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    // Parse lecture timestamps from the note's text, e.g. [01:23] or [12:05]
    val timestamps = remember(noteContent) {
        val regex = Regex("\\[(\\d{1,2}):(\\d{2})\\]")
        val matches = regex.findAll(noteContent)
        val list = mutableListOf<Pair<String, Long>>()
        val seen = mutableSetOf<String>()
        for (m in matches) {
            val fullTag = m.value // e.g. [01:23]
            val mins = m.groupValues[1].toLongOrNull() ?: 0L
            val secs = m.groupValues[2].toLongOrNull() ?: 0L
            val totalMs = (mins * 60 + secs) * 1000L
            val displayTag = String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
            if (seen.add(displayTag)) {
                list.add(displayTag to totalMs)
            }
        }
        list.sortedBy { it.second }
    }

    // Initialize or release MediaPlayer
    DisposableEffect(audioUri) {
        try {
            val mp = MediaPlayer().apply {
                setDataSource(audioUri)
                prepare()
                setOnCompletionListener {
                    isPlaying = false
                    currentPositionMs = duration.toLong()
                }
            }
            totalDurationMs = mp.duration.toLong()
            player = mp
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            player?.release()
            player = null
        }
    }

    // Progress tracker loop
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                currentPositionMs = player?.currentPosition?.toLong() ?: 0L
            } catch (_: Exception) {}
            delay(250)
        }
    }

    fun seekTo(ms: Long) {
        val target = ms.coerceIn(0L, totalDurationMs.coerceAtLeast(0L))
        try {
            player?.seekTo(target.toInt())
            currentPositionMs = target
        } catch (_: Exception) {}
    }

    fun togglePlayback() {
        val mp = player ?: return
        try {
            if (isPlaying) {
                mp.pause()
                isPlaying = false
            } else {
                if (currentPositionMs >= totalDurationMs && totalDurationMs > 0) {
                    seekTo(0)
                }
                mp.start()
                isPlaying = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun cycleSpeed() {
        val nextSpeed = when (playbackSpeed) {
            1.0f -> 1.25f
            1.25f -> 1.5f
            1.5f -> 2.0f
            2.0f -> 0.75f
            else -> 1.0f
        }
        playbackSpeed = nextSpeed
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                player?.playbackParams = PlaybackParams().apply { speed = nextSpeed }
            } catch (_: Exception) {}
        }
    }

    fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val mins = totalSec / 60
        val secs = totalSec % 60
        return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Icon, Title, Speed Selector, Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (timestamps.isNotEmpty()) "Аудиодорожка лекции (Синхронизировано)" else "Аудиозапись заметки",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${formatMs(currentPositionMs)} / ${formatMs(totalDurationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onInsertTimestamp != null) {
                        AssistChip(
                            onClick = { onInsertTimestamp("[${formatMs(currentPositionMs)}]") },
                            label = { Text("📌 [${formatMs(currentPositionMs)}]", fontSize = 11.sp) },
                            modifier = Modifier.height(30.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    AssistChip(
                        onClick = { cycleSpeed() },
                        label = { Text("${playbackSpeed}x", fontWeight = FontWeight.Bold, fontSize = 11.sp) },
                        modifier = Modifier.height(30.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = {
                            player?.stop()
                            player?.release()
                            player = null
                            onDelete()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DeleteOutline,
                            contentDescription = "Удалить аудио",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Timeline Scrubbing Slider
            Slider(
                value = currentPositionMs.toFloat().coerceIn(0f, totalDurationMs.toFloat().coerceAtLeast(1f)),
                onValueChange = { seekTo(it.toLong()) },
                valueRange = 0f..totalDurationMs.toFloat().coerceAtLeast(1f),
                modifier = Modifier.fillMaxWidth()
            )

            // Playback Controls Row: Skip -10s, Play/Pause, Skip +10s
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { seekTo(currentPositionMs - 10000L) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Назад на 10 сек",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                FilledIconButton(
                    onClick = { togglePlayback() },
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Пауза" else "Воспроизвести",
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                IconButton(
                    onClick = { seekTo(currentPositionMs + 10000L) },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Вперед на 10 сек",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Interactive Lecture Timestamps Row (Click-to-jump into speech moment)
            if (timestamps.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Таймкоды лекции (нажмите для перехода):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(timestamps) { (tag, timeMs) ->
                        val isCurrent = currentPositionMs >= timeMs && currentPositionMs < timeMs + 15000L
                        FilterChip(
                            selected = isCurrent,
                            onClick = {
                                seekTo(timeMs)
                                if (!isPlaying) {
                                    togglePlayback()
                                }
                            },
                            label = { Text(tag, fontSize = 12.sp, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCurrent) Icons.Filled.PlayArrow else Icons.Filled.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            if (onTranscribeRequested != null) {
                Spacer(modifier = Modifier.height(10.dp))
                var isTranscribingAudio by remember { mutableStateOf(false) }
                val cardContext = LocalContext.current
                val coroutineScope = rememberCoroutineScope()

                Button(
                    onClick = {
                        if (isTranscribingAudio) return@Button
                        isTranscribingAudio = true
                        coroutineScope.launch {
                            val file = java.io.File(audioUri)
                            if (file.exists() && file.length() > 0L) {
                                val result = com.example.util.GeminiOcrService.transcribeAudioWithGemini(cardContext, file)
                                if (result.isSuccess) {
                                    val text = result.getOrNull().orEmpty()
                                    if (text.isNotBlank()) {
                                        onTranscribeRequested.invoke(text)
                                        Toast.makeText(cardContext, "Аудиозапись успешно оцифрована в текст!", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    val err = result.exceptionOrNull()?.localizedMessage ?: "Ошибка распознавания"
                                    Toast.makeText(cardContext, err, Toast.LENGTH_LONG).show()
                                }
                            } else {
                                Toast.makeText(cardContext, "Файл аудиозаписи не найден", Toast.LENGTH_SHORT).show()
                            }
                            isTranscribingAudio = false
                        }
                    },
                    enabled = !isTranscribingAudio,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                ) {
                    if (isTranscribingAudio) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ИИ расшифровывает запись в текст...", fontSize = 13.sp)
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Расшифровать запись в текст (ИИ)", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}
