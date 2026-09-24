package com.example.presentation.components

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.util.NoteSpeechManager
import com.example.util.TtsVoiceOption
import kotlin.math.roundToInt

@Composable
fun VoiceSettingsDialog(
    speechManager: NoteSpeechManager,
    onDismissRequest: () -> Unit,
    onSaveSettings: (voiceId: String?, pitch: Float, rate: Float) -> Unit
) {
    var selectedVoiceId by remember { mutableStateOf(speechManager.selectedVoiceId) }
    var pitch by remember { mutableFloatStateOf(speechManager.speechPitch) }
    var rate by remember { mutableFloatStateOf(speechManager.speechRate) }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Filled.RecordVoiceOver,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Настройки голоса озвучки",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Выберите голос и настройте тон для максимально естественного звучания:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Voice selection
                Text(
                    text = "Доступные голоса",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        // Default voice option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVoiceId = null }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedVoiceId == null,
                                onClick = { selectedVoiceId = null }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Системный по умолчанию",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = "Стандартный голос вашего устройства",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Engine voices list
                        speechManager.availableVoices.forEach { voice ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedVoiceId = voice.id }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedVoiceId == voice.id,
                                    onClick = { selectedVoiceId = voice.id }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = voice.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                        )
                                        if (voice.isHighQuality) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.primaryContainer,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "HQ",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${voice.genderLabel} • ${if (voice.isNetwork) "Онлайн-синтез (Google)" else "Локальный"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Pitch slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Высота тона (тембр)",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = when {
                                pitch < 0.9f -> "Глубокий (${(pitch * 100).roundToInt()}%)"
                                pitch > 1.1f -> "Звонкий (${(pitch * 100).roundToInt()}%)"
                                else -> "Естественный (100%)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = pitch,
                        onValueChange = { pitch = it },
                        valueRange = 0.7f..1.3f,
                        steps = 11
                    )
                }

                // Rate slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Скорость чтения",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                        Text(
                            text = "${(rate * 10).roundToInt() / 10.0}x",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = rate,
                        onValueChange = { rate = it },
                        valueRange = 0.75f..1.75f,
                        steps = 7
                    )
                }

                // Test voice button
                OutlinedButton(
                    onClick = {
                        speechManager.previewVoice(selectedVoiceId, pitch, rate)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (speechManager.isSpeaking) Icons.Filled.Stop else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (speechManager.isSpeaking) "Остановить образец" else "Прослушать образец голоса")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    speechManager.setVoice(selectedVoiceId)
                    speechManager.setPitch(pitch)
                    speechManager.setRate(rate)
                    onSaveSettings(selectedVoiceId, pitch, rate)
                    onDismissRequest()
                }
            ) {
                Text("Применить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Отмена")
            }
        }
    )
}
