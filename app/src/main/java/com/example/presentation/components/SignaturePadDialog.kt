package com.example.presentation.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.preferences.UserPreferencesManager
import com.example.util.SignatureManager
import kotlinx.coroutines.launch

private data class SignatureStroke(
    val points: List<Offset>,
    val color: Color,
    val strokeWidth: Float
)

@Composable
fun SignaturePadDialog(
    preferencesManager: UserPreferencesManager,
    onDismissRequest: () -> Unit,
    onSignatureSaved: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val availableColors = listOf(
        Color(0xFF002FA7) to "Синие чернила (ГОСТ)",
        Color(0xFF0D1B2A) to "Тёмно-синий",
        Color(0xFF111111) to "Чёрный",
        Color(0xFF283593) to "Фиолетово-синий"
    )

    var currentColor by remember { mutableStateOf(availableColors[0].first) }
    var currentStrokeWidth by remember { mutableFloatStateOf(4.5f) }

    val strokes = remember { mutableStateListOf<SignatureStroke>() }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val hasExistingSignature = remember { SignatureManager.hasSignature(context) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("signature_pad_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Draw,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Рукописная подпись",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Для вставки в документы Word (.docx) и PDF",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Canvas Container (Simulates high-quality paper sheet with baseline guide)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFAF9F6))
                        .border(1.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                        .onSizeChanged { canvasSize = it }
                ) {
                    // Signature drawing area
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(currentColor, currentStrokeWidth) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        currentPoints = listOf(offset)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        currentPoints = currentPoints + change.position
                                    },
                                    onDragEnd = {
                                        if (currentPoints.size > 1) {
                                            strokes.add(
                                                SignatureStroke(
                                                    points = currentPoints,
                                                    color = currentColor,
                                                    strokeWidth = currentStrokeWidth
                                                )
                                            )
                                        }
                                        currentPoints = emptyList()
                                    },
                                    onDragCancel = {
                                        currentPoints = emptyList()
                                    }
                                )
                            }
                    ) {
                        // Baseline guide
                        val baselineY = size.height * 0.75f
                        drawLine(
                            color = Color(0xFFD0D0D0),
                            start = Offset(24f, baselineY),
                            end = Offset(size.width - 24f, baselineY),
                            strokeWidth = 1.2f
                        )

                        // Draw finished strokes
                        strokes.forEach { stroke ->
                            if (stroke.points.size > 1) {
                                val path = Path().apply {
                                    moveTo(stroke.points[0].x, stroke.points[0].y)
                                    for (i in 1 until stroke.points.size) {
                                        val prev = stroke.points[i - 1]
                                        val curr = stroke.points[i]
                                        quadraticTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                                    }
                                }
                                drawPath(
                                    path = path,
                                    color = stroke.color,
                                    style = Stroke(
                                        width = stroke.strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }

                        // Draw active stroke
                        if (currentPoints.size > 1) {
                            val path = Path().apply {
                                moveTo(currentPoints[0].x, currentPoints[0].y)
                                for (i in 1 until currentPoints.size) {
                                    val prev = currentPoints[i - 1]
                                    val curr = currentPoints[i]
                                    quadraticTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                                }
                            }
                            drawPath(
                                path = path,
                                color = currentColor,
                                style = Stroke(
                                    width = currentStrokeWidth,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }

                    // Watermark / Prompt if empty
                    if (strokes.isEmpty() && currentPoints.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Gesture,
                                contentDescription = null,
                                tint = Color(0xFFBBBBBB),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Распишитесь пальцем или стилусом на этой линии",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF999999)
                            )
                        }
                    }

                    // Floating quick clear button
                    if (strokes.isNotEmpty() || currentPoints.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                strokes.clear()
                                currentPoints = emptyList()
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        ) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = "Очистить холст",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Controls: Colors & Stroke Width
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Color chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableColors.forEach { (color, _) ->
                            val isSelected = currentColor == color
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 2.5.dp else 0.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { currentColor = color },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Stroke width selector
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        listOf(2.5f to "Тонко", 4.5f to "Средне", 7.0f to "Жирно").forEach { (w, label) ->
                            FilterChip(
                                selected = currentStrokeWidth == w,
                                onClick = { currentStrokeWidth = w },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.height(30.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Existing signature hint
                if (hasExistingSignature) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Verified,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "В приложении уже сохранена подпись",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            TextButton(
                                onClick = { showDeleteConfirm = true },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Delete Confirmation Dialog
                if (showDeleteConfirm) {
                    AlertDialog(
                        onDismissRequest = { showDeleteConfirm = false },
                        title = { Text("Удалить подпись?") },
                        text = { Text("Сохранённая цифровая подпись будет безвозвратно удалена.") },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    SignatureManager.deleteSignature(context)
                                    showDeleteConfirm = false
                                    onDismissRequest()
                                }
                            ) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { showDeleteConfirm = false }) {
                                Text("Отмена")
                            }
                        }
                    )
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Отмена")
                    }

                    Button(
                        onClick = {
                            if (strokes.isNotEmpty() && canvasSize.width > 0 && canvasSize.height > 0) {
                                val bitmap = createTransparentSignatureBitmap(strokes, canvasSize)
                                SignatureManager.saveSignature(context, bitmap)
                                onSignatureSaved()
                                onDismissRequest()
                            }
                        },
                        enabled = strokes.isNotEmpty(),
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

/**
 * Renders the vector strokes onto a transparent ARGB_8888 bitmap.
 * Auto-crops to content bounding box for perfect document placement.
 */
private fun createTransparentSignatureBitmap(
    strokes: List<SignatureStroke>,
    canvasSize: IntSize
): Bitmap {
    val fullBitmap = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(fullBitmap)

    strokes.forEach { stroke ->
        if (stroke.points.size > 1) {
            val paint = AndroidPaint().apply {
                color = stroke.color.toArgb()
                strokeWidth = stroke.strokeWidth * 1.5f
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                isAntiAlias = true
            }

            val path = AndroidPath().apply {
                moveTo(stroke.points[0].x, stroke.points[0].y)
                for (i in 1 until stroke.points.size) {
                    val prev = stroke.points[i - 1]
                    val curr = stroke.points[i]
                    quadTo(prev.x, prev.y, (prev.x + curr.x) / 2f, (prev.y + curr.y) / 2f)
                }
            }
            canvas.drawPath(path, paint)
        }
    }

    // Compute bounding box to crop empty margins
    var minX = canvasSize.width.toFloat()
    var maxX = 0f
    var minY = canvasSize.height.toFloat()
    var maxY = 0f

    strokes.flatMap { it.points }.forEach { pt ->
        if (pt.x < minX) minX = pt.x
        if (pt.x > maxX) maxX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.y > maxY) maxY = pt.y
    }

    val padding = 16f
    val cropLeft = (minX - padding).coerceAtLeast(0f).toInt()
    val cropTop = (minY - padding).coerceAtLeast(0f).toInt()
    val cropWidth = ((maxX - minX) + padding * 2).coerceAtMost((canvasSize.width - cropLeft).toFloat()).toInt().coerceAtLeast(10)
    val cropHeight = ((maxY - minY) + padding * 2).coerceAtMost((canvasSize.height - cropTop).toFloat()).toInt().coerceAtLeast(10)

    return try {
        Bitmap.createBitmap(fullBitmap, cropLeft, cropTop, cropWidth, cropHeight)
    } catch (_: Exception) {
        fullBitmap
    }
}
