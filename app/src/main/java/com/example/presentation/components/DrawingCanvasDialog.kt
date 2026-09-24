package com.example.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import java.io.FileOutputStream

data class DrawingStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float,
    val isEraser: Boolean = false
)

@Composable
fun DrawingCanvasDialog(
    onDismiss: () -> Unit,
    onSaveDrawing: (String) -> Unit
) {
    val context = LocalContext.current
    val strokes = remember { mutableStateListOf<DrawingStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var selectedColor by remember { mutableStateOf(Color(0xFF1E293B)) } // Deep Ink
    var selectedWidth by remember { mutableFloatStateOf(6f) }
    var isEraserMode by remember { mutableStateOf(false) }

    val palette = listOf(
        Color(0xFF1E293B) to "Чернила",
        Color(0xFF1D4ED8) to "Синяя ручка",
        Color(0xFFB91C1C) to "Красная ручка",
        Color(0xFF15803D) to "Зелёный",
        Color(0xFF57534E) to "Карандаш",
        Color(0xFFB45309) to "Охра"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Отмена")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                if (strokes.isNotEmpty()) {
                                    strokes.removeAt(strokes.lastIndex)
                                }
                            },
                            enabled = strokes.isNotEmpty()
                        ) {
                            Icon(Icons.Filled.Undo, contentDescription = "Отменить штрих")
                        }

                        IconButton(
                            onClick = {
                                strokes.clear()
                                currentPoints = emptyList()
                            }
                        ) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Очистить всё")
                        }
                    }

                    Button(
                        onClick = {
                            if (strokes.isEmpty() && currentPoints.isEmpty()) {
                                onDismiss()
                                return@Button
                            }

                            // Save with TRANSPARENT background (ARGB_8888 with no background fill)
                            val bitmap = Bitmap.createBitmap(1080, 1440, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            // canvas remains completely transparent!

                            val allStrokes = strokes + if (currentPoints.size >= 2) listOf(DrawingStroke(currentPoints, selectedColor, selectedWidth, isEraserMode)) else emptyList()

                            for (stroke in allStrokes) {
                                if (stroke.points.size < 2) continue
                                val paint = Paint().apply {
                                    isAntiAlias = true
                                    strokeCap = Paint.Cap.ROUND
                                    strokeJoin = Paint.Join.ROUND
                                    strokeWidth = stroke.strokeWidth * 1.5f
                                    if (stroke.isEraser) {
                                        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                                    } else {
                                        color = stroke.color.toArgb()
                                        style = Paint.Style.STROKE
                                    }
                                }

                                val path = Path().apply {
                                    moveTo(stroke.points[0].x * 1.5f, stroke.points[0].y * 1.5f)
                                    for (i in 1 until stroke.points.size) {
                                        lineTo(stroke.points[i].x * 1.5f, stroke.points[i].y * 1.5f)
                                    }
                                }
                                canvas.drawPath(path, paint)
                            }

                            val file = File(context.cacheDir, "drawing_${System.currentTimeMillis()}.png")
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                            }
                            onSaveDrawing(file.absolutePath)
                        },
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Вставить на лист")
                    }
                }

                // Drawing Canvas Area with notebook texture hint
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFFBF8F2)) // Soft paper preview color
                        .pointerInput(selectedColor, selectedWidth, isEraserMode) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    currentPoints = listOf(offset)
                                },
                                onDragEnd = {
                                    if (currentPoints.size >= 2) {
                                        strokes.add(DrawingStroke(currentPoints, selectedColor, selectedWidth, isEraserMode))
                                    }
                                    currentPoints = emptyList()
                                },
                                onDragCancel = {
                                    currentPoints = emptyList()
                                },
                                onDrag = { change, _ ->
                                    currentPoints = currentPoints + change.position
                                }
                            )
                        }
                ) {
                    // Subtle ruled lines on canvas so the user knows they are writing on paper
                    ComposeCanvas(modifier = Modifier.fillMaxSize()) {
                        val lineSpacing = 32.dp.toPx()
                        var y = lineSpacing
                        while (y < size.height) {
                            drawLine(
                                color = Color(0xFF94A3B8).copy(alpha = 0.22f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            y += lineSpacing
                        }

                        // Draw completed strokes
                        for (stroke in strokes) {
                            if (stroke.points.size < 2) continue
                            for (i in 0 until stroke.points.size - 1) {
                                drawLine(
                                    color = if (stroke.isEraser) Color(0xFFFBF8F2) else stroke.color,
                                    start = stroke.points[i],
                                    end = stroke.points[i + 1],
                                    strokeWidth = stroke.strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        // Draw current active stroke
                        if (currentPoints.size >= 2) {
                            for (i in 0 until currentPoints.size - 1) {
                                drawLine(
                                    color = if (isEraserMode) Color(0xFFFBF8F2) else selectedColor,
                                    start = currentPoints[i],
                                    end = currentPoints[i + 1],
                                    strokeWidth = selectedWidth,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    if (strokes.isEmpty() && currentPoints.isEmpty()) {
                        Text(
                            text = "Пишите или рисуйте пальцем/стилусом прямо здесь...\nФон будет прозрачным, рисунок ляжет на лист блокнота.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8).copy(alpha = 0.8f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp)
                        )
                    }
                }

                // Drawing Tool Controls Bar (Colors + Stroke Sizes + Eraser)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Palette colors
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                palette.forEach { (color, name) ->
                                    val isSelected = !isEraserMode && selectedColor == color
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                            .clickable {
                                                selectedColor = color
                                                isEraserMode = false
                                            }
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f),
                                                shape = CircleShape
                                            )
                                    )
                                }
                            }

                            // Eraser toggle
                            FilledIconToggleButton(
                                checked = isEraserMode,
                                onCheckedChange = { isEraserMode = it },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.AutoFixNormal,
                                    contentDescription = "Ластик",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Stroke Width Selector
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Толщина:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            listOf(3f to "Тонкая", 6f to "Средняя", 12f to "Толстая").forEach { (width, label) ->
                                FilterChip(
                                    selected = selectedWidth == width,
                                    onClick = { selectedWidth = width },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.height(28.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
