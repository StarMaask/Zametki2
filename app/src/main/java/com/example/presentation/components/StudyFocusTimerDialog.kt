package com.example.presentation.components

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.domain.model.CheckListItem
import com.example.util.AmbientSound
import com.example.util.StudyAmbientSoundPlayer
import kotlinx.coroutines.delay

enum class SessionPhase(val title: String, val defaultMinutes: Int) {
    FOCUS_STANDARD("Фокус (Помодоро)", 25),
    FOCUS_DEEP("Глубокая учёба", 50),
    SHORT_BREAK("Короткий перерыв", 5),
    LONG_BREAK("Длинный отдых", 15)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudyFocusTimerDialog(
    noteTitle: String = "",
    noteCheckList: List<CheckListItem> = emptyList(),
    onToggleCheckItem: ((String) -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val soundPlayer = remember { StudyAmbientSoundPlayer(context) }

    // SharedPreferences for tracking pomodoros
    val prefs = remember { context.getSharedPreferences("pomodoro_study_stats", Context.MODE_PRIVATE) }
    var completedPomodoros by remember { mutableIntStateOf(prefs.getInt("completed_cycles_today", 0)) }

    var currentPhase by remember { mutableStateOf(SessionPhase.FOCUS_STANDARD) }
    var totalSeconds by remember { mutableIntStateOf(currentPhase.defaultMinutes * 60) }
    var secondsRemaining by remember { mutableIntStateOf(totalSeconds) }
    var isRunning by remember { mutableStateOf(false) }

    var selectedAmbientSound by remember { mutableStateOf(AmbientSound.NONE) }
    var ambientVolume by remember { mutableFloatStateOf(0.5f) }
    var isZenMode by remember { mutableStateOf(false) }

    // Sound player management
    DisposableEffect(Unit) {
        onDispose {
            soundPlayer.stop()
        }
    }

    LaunchedEffect(selectedAmbientSound, ambientVolume) {
        soundPlayer.setVolume(ambientVolume)
        if (selectedAmbientSound != AmbientSound.NONE) {
            soundPlayer.play(selectedAmbientSound)
        } else {
            soundPlayer.stop()
        }
    }

    // Timer countdown loop
    LaunchedEffect(isRunning, secondsRemaining) {
        if (isRunning && secondsRemaining > 0) {
            delay(1000L)
            secondsRemaining--
        } else if (isRunning && secondsRemaining == 0) {
            isRunning = false
            soundPlayer.playCompletionChime()

            if (currentPhase == SessionPhase.FOCUS_STANDARD || currentPhase == SessionPhase.FOCUS_DEEP) {
                completedPomodoros++
                prefs.edit().putInt("completed_cycles_today", completedPomodoros).apply()
                // Switch to break
                currentPhase = SessionPhase.SHORT_BREAK
                totalSeconds = currentPhase.defaultMinutes * 60
                secondsRemaining = totalSeconds
            } else {
                // Switch back to focus
                currentPhase = SessionPhase.FOCUS_STANDARD
                totalSeconds = currentPhase.defaultMinutes * 60
                secondsRemaining = totalSeconds
            }
        }
    }

    fun selectPhase(phase: SessionPhase) {
        currentPhase = phase
        totalSeconds = phase.defaultMinutes * 60
        secondsRemaining = totalSeconds
        isRunning = false
    }

    val progress = if (totalSeconds > 0) {
        (totalSeconds - secondsRemaining).toFloat() / totalSeconds.toFloat()
    } else 0f

    val minutes = secondsRemaining / 60
    val seconds = secondsRemaining % 60
    val timeFormatted = String.format("%02d:%02d", minutes, seconds)

    // Pulsing animation when running
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRunning) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val isBreak = currentPhase == SessionPhase.SHORT_BREAK || currentPhase == SessionPhase.LONG_BREAK
    val phaseColor by animateColorAsState(
        targetValue = if (isBreak) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        label = "phaseColor"
    )

    Dialog(
        onDismissRequest = {
            soundPlayer.stop()
            onDismissRequest()
        },
        properties = DialogProperties(usePlatformDefaultWidth = !isZenMode)
    ) {
        if (isZenMode) {
            // Fullscreen Zen Focus Mode
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    IconButton(
                        onClick = { isZenMode = false },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Filled.FullscreenExit, contentDescription = "Выйти из Дзен-режима")
                    }

                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (noteTitle.isNotBlank()) {
                            Text(
                                text = noteTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = phaseColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = currentPhase.title,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = phaseColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(260.dp)
                                .scale(pulseScale)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = phaseColor,
                                trackColor = phaseColor.copy(alpha = 0.12f),
                                strokeWidth = 12.dp,
                                strokeCap = StrokeCap.Round
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timeFormatted,
                                    fontSize = 56.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isRunning) "Идёт сессия" else "На паузе",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    secondsRemaining = totalSeconds
                                    isRunning = false
                                }
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Сброс")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Сброс")
                            }

                            Button(
                                onClick = { isRunning = !isRunning },
                                colors = ButtonDefaults.buttonColors(containerColor = phaseColor)
                            ) {
                                Icon(if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isRunning) "Пауза" else "Старт")
                            }
                        }
                    }
                }
            }
        } else {
            // Standard Study Focus Dialog
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(phaseColor.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isBreak) Icons.Filled.Coffee else Icons.Filled.HourglassBottom,
                                    contentDescription = null,
                                    tint = phaseColor,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Таймер концентрации",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Помодоро & Учебные интервалы",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { isZenMode = true }) {
                                Icon(Icons.Filled.Fullscreen, contentDescription = "Дзен режим", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = {
                                soundPlayer.stop()
                                onDismissRequest()
                            }) {
                                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                            }
                        }
                    }

                    if (noteTitle.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "📖 $noteTitle",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Phase Selector Chips
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionPhase.values().forEach { phase ->
                            FilterChip(
                                selected = currentPhase == phase,
                                onClick = { selectPhase(phase) },
                                label = { Text(phase.title) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Center Countdown Ring
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(170.dp)
                                .scale(pulseScale)
                        ) {
                            CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize(),
                                color = phaseColor,
                                trackColor = phaseColor.copy(alpha = 0.12f),
                                strokeWidth = 10.dp,
                                strokeCap = StrokeCap.Round
                            )

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = timeFormatted,
                                    fontSize = 38.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isBreak) "Отдых" else "Фокус",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = phaseColor,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Primary Play / Pause / Reset Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                secondsRemaining = totalSeconds
                                isRunning = false
                            }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Сброс")
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Button(
                            onClick = { isRunning = !isRunning },
                            modifier = Modifier.height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = phaseColor)
                        ) {
                            Icon(
                                imageVector = if (isRunning) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRunning) "Пауза" else "Старт",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        IconButton(
                            onClick = {
                                // Skip to next phase
                                if (isBreak) {
                                    selectPhase(SessionPhase.FOCUS_STANDARD)
                                } else {
                                    selectPhase(SessionPhase.SHORT_BREAK)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Пропустить фазу")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Ambient Study Sound Selector
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Headphones,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Фоновый звук для учёбы",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (selectedAmbientSound != AmbientSound.NONE) {
                                    Text(
                                        text = "Играет",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AmbientSound.values().forEach { sound ->
                                    FilterChip(
                                        selected = selectedAmbientSound == sound,
                                        onClick = { selectedAmbientSound = sound },
                                        label = { Text(sound.title, style = MaterialTheme.typography.bodySmall) }
                                    )
                                }
                            }

                            if (selectedAmbientSound != AmbientSound.NONE) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Filled.VolumeDown, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Slider(
                                        value = ambientVolume,
                                        onValueChange = { ambientVolume = it },
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }

                    // Checklist goals inside the note (if any)
                    if (noteCheckList.isNotEmpty() && onToggleCheckItem != null) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "Цели сессии по конспекту:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val displayItems = noteCheckList.take(4)
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            displayItems.forEach { item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleCheckItem(item.id) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (item.isChecked) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (item.isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = item.text,
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                                        color = if (item.isChecked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Daily summary footer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "🍅 Циклов сегодня: $completedPomodoros",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(
                            onClick = {
                                soundPlayer.stop()
                                onDismissRequest()
                            }
                        ) {
                            Text("Закрыть")
                        }
                    }
                }
            }
        }
    }
}
