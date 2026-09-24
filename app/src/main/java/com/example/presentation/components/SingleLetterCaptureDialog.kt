package com.example.presentation.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.util.HandwritingGlyphItem
import com.example.util.HandwritingGlyphManager
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleLetterCaptureDialog(
    item: HandwritingGlyphItem,
    onDismissRequest: () -> Unit,
    onGlyphSaved: (HandwritingGlyphItem) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) } // 0: Photo, 1: Draw

    // State for Photo Tab
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cleanedGlyphBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isProcessingPhoto by remember { mutableStateOf(false) }

    // State for Draw Tab
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var strokeWidth by remember { mutableFloatStateOf(6f) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoUri = uri
            isProcessingPhoto = true
            coroutineScope.launch {
                try {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        val bmp = BitmapFactory.decodeStream(stream)
                        if (bmp != null) {
                            loadedBitmap = bmp
                            cleanedGlyphBitmap = HandwritingGlyphManager.extractInkOnlyBitmap(bmp)
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка открытия фото: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    isProcessingPhoto = false
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = item.char.toString(),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Оцифровка: '${item.char}'",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.title,
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

                // Tabs: 0 - Photo, 1 - Draw
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Фото буквы с бумаги", fontSize = 13.sp) },
                        icon = { Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Нарисовать", fontSize = 13.sp) },
                        icon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tab Content
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        // TAB 0: PHOTO
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Сфотографируйте отдельно букву '${item.char}' на белом или тетрадном листе:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (cleanedGlyphBitmap != null) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize().padding(12.dp)
                                    ) {
                                        androidx.compose.foundation.Image(
                                            bitmap = cleanedGlyphBitmap!!.asImageBitmap(),
                                            contentDescription = "Оцифрованный глиф",
                                            modifier = Modifier.size(120.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "✓ Чернила выделены, фон очищен",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else if (item.imagePath != null && File(item.imagePath).exists()) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.fillMaxSize().padding(12.dp)
                                    ) {
                                        AsyncImage(
                                            model = File(item.imagePath),
                                            contentDescription = "Ранее сохраненная буква",
                                            modifier = Modifier.size(120.dp),
                                            contentScale = ContentScale.Fit
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "Ранее сохраненный образец буквы",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                        modifier = Modifier.padding(16.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.AddPhotoAlternate,
                                            contentDescription = null,
                                            modifier = Modifier.size(40.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Выберите фото с написанной буквой '${item.char}'",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isProcessingPhoto) {
                                    Surface(
                                        modifier = Modifier.fillMaxSize(),
                                        color = Color.Black.copy(alpha = 0.5f)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(color = Color.White)
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            FilledTonalButton(
                                onClick = { photoPickerLauncher.launch("image/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Выбрать фото буквы '${item.char}'")
                            }
                        }
                    } else {
                        // TAB 1: DRAW ON SCREEN
                        Column(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                text = "Напишите пальцем или стилусом букву '${item.char}' по строке:",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFFCFDFE))
                                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentStroke = listOf(offset)
                                            },
                                            onDrag = { change, _ ->
                                                currentStroke = currentStroke + change.position
                                            },
                                            onDragEnd = {
                                                if (currentStroke.isNotEmpty()) {
                                                    strokes.add(currentStroke)
                                                    currentStroke = emptyList()
                                                }
                                            }
                                        )
                                    }
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val w = size.width
                                    val h = size.height

                                    // Lined paper guides: top, midline, baseline, bottom
                                    val topGuide = h * 0.22f
                                    val midGuide = h * 0.45f
                                    val baseGuide = h * 0.72f
                                    val descGuide = h * 0.88f

                                    // Slanted guides
                                    val slantAngle = 10f
                                    val slantPx = Math.tan(Math.toRadians(slantAngle.toDouble())).toFloat() * (baseGuide - topGuide)
                                    for (x in 60..w.toInt() step 120) {
                                        drawLine(
                                            color = Color(0xFFE2E8F0),
                                            start = Offset(x.toFloat() + slantPx, topGuide),
                                            end = Offset(x.toFloat(), baseGuide),
                                            strokeWidth = 1f
                                        )
                                    }

                                    // Horizontal guideline lines
                                    drawLine(Color(0xFFCBD5E1), Offset(0f, topGuide), Offset(w, topGuide), strokeWidth = 1f)
                                    drawLine(Color(0xFF94A3B8), Offset(0f, midGuide), Offset(w, midGuide), strokeWidth = 1f)
                                    drawLine(Color(0xFF2563EB).copy(alpha = 0.8f), Offset(0f, baseGuide), Offset(w, baseGuide), strokeWidth = 2f)
                                    drawLine(Color(0xFFE2E8F0), Offset(0f, descGuide), Offset(w, descGuide), strokeWidth = 1f)

                                    // Ghost guide character for reference
                                    // Completed strokes
                                    val inkColor = Color(0xFF1E3A8A)
                                    for (stroke in strokes) {
                                        if (stroke.size >= 2) {
                                            for (i in 0 until stroke.size - 1) {
                                                drawLine(
                                                    color = inkColor,
                                                    start = stroke[i],
                                                    end = stroke[i + 1],
                                                    strokeWidth = strokeWidth,
                                                    cap = StrokeCap.Round
                                                )
                                            }
                                        }
                                    }

                                    // Current stroke
                                    if (currentStroke.size >= 2) {
                                        for (i in 0 until currentStroke.size - 1) {
                                            drawLine(
                                                color = inkColor,
                                                start = currentStroke[i],
                                                end = currentStroke[i + 1],
                                                strokeWidth = strokeWidth,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                                        },
                                        enabled = strokes.isNotEmpty()
                                    ) {
                                        Icon(Icons.Filled.Undo, contentDescription = "Отмена")
                                    }
                                    IconButton(
                                        onClick = { strokes.clear() },
                                        enabled = strokes.isNotEmpty()
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Очистить")
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Bottom Action Button
                Button(
                    onClick = {
                        if (selectedTab == 0) {
                            if (cleanedGlyphBitmap != null) {
                                val savedItem = HandwritingGlyphManager.processAndSaveSingleGlyph(context, item.char, cleanedGlyphBitmap!!)
                                onGlyphSaved(savedItem)
                                Toast.makeText(context, "Буква '${item.char}' сохранена!", Toast.LENGTH_SHORT).show()
                                onDismissRequest()
                            } else {
                                Toast.makeText(context, "Сначала выберите фото буквы", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (strokes.isNotEmpty()) {
                                // Render strokes to bitmap
                                val bmp = renderStrokesToBitmap(strokes, strokeWidth)
                                val savedItem = HandwritingGlyphManager.processAndSaveSingleGlyph(context, item.char, bmp)
                                onGlyphSaved(savedItem)
                                Toast.makeText(context, "Буква '${item.char}' оцифрована!", Toast.LENGTH_SHORT).show()
                                onDismissRequest()
                            } else {
                                Toast.makeText(context, "Нарисуйте букву на бланке", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сохранить букву '${item.char}' в мой почерк")
                }
            }
        }
    }
}

/**
 * Converts user screen drawing strokes into a cropped ink bitmap.
 */
private fun renderStrokesToBitmap(strokes: List<List<Offset>>, strokeWidth: Float): Bitmap {
    var minX = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var minY = Float.MAX_VALUE
    var maxY = Float.MIN_VALUE

    strokes.flatten().forEach { pt ->
        if (pt.x < minX) minX = pt.x
        if (pt.x > maxX) maxX = pt.x
        if (pt.y < minY) minY = pt.y
        if (pt.y > maxY) maxY = pt.y
    }

    val pad = 20f
    minX = max(0f, minX - pad)
    minY = max(0f, minY - pad)
    maxX += pad
    maxY += pad

    val w = max(50, (maxX - minX).toInt())
    val h = max(50, (maxY - minY).toInt())

    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.rgb(30, 58, 138)
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.strokeWidth = strokeWidth
    }

    for (stroke in strokes) {
        if (stroke.size >= 2) {
            for (i in 0 until stroke.size - 1) {
                canvas.drawLine(
                    stroke[i].x - minX,
                    stroke[i].y - minY,
                    stroke[i + 1].x - minX,
                    stroke[i + 1].y - minY,
                    paint
                )
            }
        }
    }

    return bitmap
}
