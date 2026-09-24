package com.example.presentation.components

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.preferences.UserPreferencesManager
import kotlinx.coroutines.delay
import java.io.File

@Composable
fun AudioRecordDialog(
    onDismiss: () -> Unit,
    onRecordingFinished: (String) -> Unit
) {
    val context = LocalContext.current
    val preferencesManager = remember { UserPreferencesManager(context) }
    var isRecording by remember { mutableStateOf(false) }
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
                Button(
                    onClick = {
                        try {
                            mediaRecorder?.stop()
                            mediaRecorder?.release()
                            mediaRecorder = null
                            isRecording = false
                            outputFile?.let { onRecordingFinished(it.absolutePath) }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Стоп и сохранить")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (isRecording) {
                    try { mediaRecorder?.stop() } catch (_: Exception) {}
                    mediaRecorder?.release()
                }
                onDismiss()
            }) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun AudioPlaybackCard(
    audioUri: String,
    onDelete: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioUri) {
        onDispose {
            player?.release()
            player = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    if (isPlaying) {
                        player?.pause()
                        isPlaying = false
                    } else {
                        try {
                            if (player == null) {
                                player = MediaPlayer().apply {
                                    setDataSource(audioUri)
                                    prepare()
                                    setOnCompletionListener {
                                        isPlaying = false
                                    }
                                }
                            }
                            player?.start()
                            isPlaying = true
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Остановить" else "Воспроизвести"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Аудиозаметка")
            }

            TextButton(onClick = {
                player?.stop()
                player?.release()
                player = null
                onDelete()
            }) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
