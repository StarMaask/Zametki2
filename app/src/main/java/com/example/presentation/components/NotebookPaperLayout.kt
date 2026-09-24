package com.example.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.PageFormat
import com.example.ui.theme.NoteColors

// Authentic Paper & Ink Palette
object NotebookPalette {
    // 1. Книга (Book / Ivory Parchment)
    val BookPaper = Color(0xFFFAF7EE)
    val BookSpineDark = Color(0xFF4A3528)
    val BookSpineMid = Color(0xFF5D4037)
    val BookSpineLight = Color(0xFF795548)
    val BookBookmark = Color(0xFFC62828)
    val BookInk = Color(0xFF2C2420)
    val BookPlaceholder = Color(0xFF786F68)

    // 2. В линейку (Ruled School Notebook)
    val RuledPaper = Color(0xFFFCFBF7)
    val RuledLine = Color(0xFFD2DFEC)
    val RuledMargin = Color(0xFFEF5350)
    val RuledInk = Color(0xFF1B365D)
    val RuledPlaceholder = Color(0xFF6B7280)

    // 3. В клеточку (Grid Math Notebook)
    val GridPaper = Color(0xFFFBFBFB)
    val GridLine = Color(0xFFD9E3EC)
    val GridMargin = Color(0xFFEF5350)
    val GridInk = Color(0xFF1E293B)
    val GridPlaceholder = Color(0xFF64748B)

    // 4. Крафтовая бумага (Kraft Paper)
    val KraftPaper = Color(0xFFD8C4A5)
    val KraftFiberDark = Color(0x283E2723)
    val KraftFiberLight = Color(0x35FFF8E1)
    val KraftClipBody = Color(0xFF5D4037)
    val KraftClipBrass = Color(0xFFC5A059)
    val KraftInk = Color(0xFF261C14)
    val KraftPlaceholder = Color(0xFF6D5C4F)

    // 5. Старинный пергамент (Vintage Manuscript)
    val VintagePaper = Color(0xFFF3E5C8)
    val VintageVignetteDark = Color(0xFFB88E52)
    val VintageBurn = Color(0x353E2723)
    val VintageFiligree = Color(0xFF795229)
    val VintageInk = Color(0xFF2E1C0F)
    val VintagePlaceholder = Color(0xFF73573D)

    // 6. Грифельная доска (Midnight Chalkboard)
    val MidnightPaper = Color(0xFF1E232B)
    val MidnightFrame = Color(0xFF4E342E)
    val MidnightChalk = Color(0xFFF8FAFC)
    val MidnightGrid = Color(0x15FFFFFF)
    val MidnightPlaceholder = Color(0xFF94A3B8)

    // 7. Инженерный чертёж (Blueprint)
    val BlueprintPaper = Color(0xFF152D4A)
    val BlueprintLineMinor = Color(0x3360A5FA)
    val BlueprintLineMajor = Color(0x6693C5FD)
    val BlueprintInk = Color(0xFFF0FDF4)
    val BlueprintPlaceholder = Color(0xFF93C5FD)

    // 8. Чистый лист (Blank)
    val BlankInk = Color(0xFF18181B)
    val BlankPlaceholder = Color(0xFF71717A)

    val DarkInk = Color(0xFFF8FAFC)
    val DarkPlaceholder = Color(0xFF94A3B8)
}

fun getPaperColor(format: PageFormat, customColorHex: String): Color {
    // If the user picked a specific custom color (other than default white/empty),
    // we tint the page with that exact color for any format!
    if (customColorHex.isNotBlank() && !customColorHex.equals("#FFFFFF", ignoreCase = true)) {
        try {
            return Color(android.graphics.Color.parseColor(customColorHex))
        } catch (_: Exception) {}
    }

    // Default authentic page colors for each format
    return when (format) {
        PageFormat.BOOK -> NotebookPalette.BookPaper
        PageFormat.RULED -> NotebookPalette.RuledPaper
        PageFormat.GRID -> NotebookPalette.GridPaper
        PageFormat.KRAFT -> NotebookPalette.KraftPaper
        PageFormat.VINTAGE -> NotebookPalette.VintagePaper
        PageFormat.MIDNIGHT -> NotebookPalette.MidnightPaper
        PageFormat.BLUEPRINT -> NotebookPalette.BlueprintPaper
        PageFormat.BLANK -> Color(0xFFFAF9F6)
    }
}

fun getInkColor(format: PageFormat, paperColor: Color): Color {
    val r = paperColor.red
    val g = paperColor.green
    val b = paperColor.blue
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b

    return if (luminance < 0.45f) {
        when (format) {
            PageFormat.BLUEPRINT -> NotebookPalette.BlueprintInk
            PageFormat.MIDNIGHT -> NotebookPalette.MidnightChalk
            else -> NotebookPalette.DarkInk
        }
    } else {
        when (format) {
            PageFormat.BOOK -> NotebookPalette.BookInk
            PageFormat.RULED -> NotebookPalette.RuledInk
            PageFormat.GRID -> NotebookPalette.GridInk
            PageFormat.KRAFT -> NotebookPalette.KraftInk
            PageFormat.VINTAGE -> NotebookPalette.VintageInk
            PageFormat.MIDNIGHT -> NotebookPalette.BookInk
            PageFormat.BLUEPRINT -> NotebookPalette.RuledInk
            PageFormat.BLANK -> NotebookPalette.BlankInk
        }
    }
}

fun getPlaceholderColor(format: PageFormat, paperColor: Color): Color {
    val r = paperColor.red
    val g = paperColor.green
    val b = paperColor.blue
    val luminance = 0.299f * r + 0.587f * g + 0.114f * b

    return if (luminance < 0.45f) {
        when (format) {
            PageFormat.BLUEPRINT -> NotebookPalette.BlueprintPlaceholder
            PageFormat.MIDNIGHT -> NotebookPalette.MidnightPlaceholder
            else -> NotebookPalette.DarkPlaceholder
        }
    } else {
        when (format) {
            PageFormat.BOOK -> NotebookPalette.BookPlaceholder
            PageFormat.RULED -> NotebookPalette.RuledPlaceholder
            PageFormat.GRID -> NotebookPalette.GridPlaceholder
            PageFormat.KRAFT -> NotebookPalette.KraftPlaceholder
            PageFormat.VINTAGE -> NotebookPalette.VintagePlaceholder
            PageFormat.MIDNIGHT -> NotebookPalette.DarkPlaceholder
            PageFormat.BLUEPRINT -> NotebookPalette.BlueprintPlaceholder
            PageFormat.BLANK -> NotebookPalette.BlankPlaceholder
        }
    }
}

fun getFontFamily(format: PageFormat): FontFamily {
    return when (format) {
        PageFormat.BOOK, PageFormat.VINTAGE -> FontFamily.Serif
        PageFormat.BLUEPRINT -> FontFamily.Monospace
        else -> FontFamily.Default
    }
}

@Composable
fun BookBookmarkRibbon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 16.dp, height = 38.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(0f, 0f)
            lineTo(w, 0f)
            lineTo(w, h)
            lineTo(w / 2f, h - 8.dp.toPx())
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = path,
            color = NotebookPalette.BookBookmark
        )
    }
}

@Composable
fun KraftBinderClip(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(width = 54.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height

        // Steel body
        drawRoundRect(
            color = NotebookPalette.KraftClipBody,
            topLeft = Offset(w * 0.15f, 0f),
            size = Size(w * 0.7f, h * 0.75f),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
        )

        // Brass wire handle
        val wirePath = Path().apply {
            moveTo(w * 0.28f, h * 0.75f)
            lineTo(w * 0.28f, h * 0.95f)
            lineTo(w * 0.72f, h * 0.95f)
            lineTo(w * 0.72f, h * 0.75f)
        }
        drawPath(
            path = wirePath,
            color = NotebookPalette.KraftClipBrass,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun SpiralRings(
    modifier: Modifier = Modifier,
    count: Int = 14
) {
    Column(
        modifier = modifier.fillMaxHeight(),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        repeat(count) {
            Box(
                modifier = Modifier
                    .size(width = 14.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color(0xFF78909C),
                                Color(0xFFCFD8DC),
                                Color(0xFF546E7A)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun NotebookPaperCanvas(
    format: PageFormat,
    paperColor: Color,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(6.dp, shape = RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(paperColor)
            .drawBehind {
                val canvasWidth = size.width
                val canvasHeight = size.height

                when (format) {
                    PageFormat.BOOK -> {
                        // 1. Left Book Spine Gradient
                        val spineWidth = 14.dp.toPx()
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    NotebookPalette.BookSpineDark,
                                    NotebookPalette.BookSpineMid,
                                    NotebookPalette.BookSpineLight
                                ),
                                startX = 0f,
                                endX = spineWidth
                            ),
                            topLeft = Offset.Zero,
                            size = Size(spineWidth, canvasHeight)
                        )

                        // 2. Spine curvature shadow onto the book page
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0x35000000),
                                    Color(0x15000000),
                                    Color.Transparent
                                ),
                                startX = spineWidth,
                                endX = spineWidth + 24.dp.toPx()
                            ),
                            topLeft = Offset(spineWidth, 0f),
                            size = Size(24.dp.toPx(), canvasHeight)
                        )

                        // 3. Subtle page perimeter deckle border
                        drawRect(
                            color = Color(0x14000000),
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }

                    PageFormat.RULED -> {
                        // Left Red Margin Line
                        val marginX = 48.dp.toPx()
                        drawLine(
                            color = NotebookPalette.RuledMargin,
                            start = Offset(marginX, 0f),
                            end = Offset(marginX, canvasHeight),
                            strokeWidth = 1.5.dp.toPx()
                        )

                        // Horizontal ruled lines:
                        // Starting at top margin with clean lineSpacing
                        val topMargin = 72.dp.toPx()
                        val lineSpacing = 34.dp.toPx()
                        var y = topMargin
                        while (y < canvasHeight) {
                            drawLine(
                                color = NotebookPalette.RuledLine,
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 1.dp.toPx()
                            )
                            y += lineSpacing
                        }
                    }

                    PageFormat.GRID -> {
                        val gridSize = 20.dp.toPx()

                        // 1. Soft grid lines
                        var y = gridSize
                        while (y < canvasHeight) {
                            drawLine(
                                color = NotebookPalette.GridLine.copy(alpha = 0.65f),
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 0.75.dp.toPx()
                            )
                            y += gridSize
                        }

                        var x = gridSize
                        while (x < canvasWidth) {
                            drawLine(
                                color = NotebookPalette.GridLine.copy(alpha = 0.65f),
                                start = Offset(x, 0f),
                                end = Offset(x, canvasHeight),
                                strokeWidth = 0.75.dp.toPx()
                            )
                            x += gridSize
                        }

                        // 2. Left Red Margin Line
                        val marginX = 48.dp.toPx()
                        drawLine(
                            color = NotebookPalette.GridMargin,
                            start = Offset(marginX, 0f),
                            end = Offset(marginX, canvasHeight),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    PageFormat.KRAFT -> {
                        // 1. Organic paper fibers / speckles
                        val fiberColorDark = NotebookPalette.KraftFiberDark
                        val fiberColorLight = NotebookPalette.KraftFiberLight
                        val random = java.util.Random(1042)
                        for (i in 0 until 90) {
                            val fx = random.nextFloat() * canvasWidth
                            val fy = random.nextFloat() * canvasHeight
                            val fLength = 4f + random.nextFloat() * 12f
                            val isDark = random.nextBoolean()
                            drawLine(
                                color = if (isDark) fiberColorDark else fiberColorLight,
                                start = Offset(fx, fy),
                                end = Offset(fx + fLength, fy + (random.nextFloat() - 0.5f) * 6f),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        // 2. Subtle warm kraft edge vignette
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color(0x223E2723)),
                                center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                                radius = Math.max(canvasWidth, canvasHeight) * 0.75f
                            ),
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight)
                        )

                        // 3. Vintage paper border
                        drawRect(
                            color = Color(0x253E2723),
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    PageFormat.VINTAGE -> {
                        // 1. Aged burnt parchment vignette along borders
                        drawRect(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.Transparent, Color(0x183E2723), NotebookPalette.VintageBurn),
                                center = Offset(canvasWidth / 2f, canvasHeight / 2f),
                                radius = Math.max(canvasWidth, canvasHeight) * 0.7f
                            ),
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight)
                        )

                        // 2. Double vintage antique frame border
                        val frameInset = 10.dp.toPx()
                        drawRect(
                            color = NotebookPalette.VintageFiligree.copy(alpha = 0.45f),
                            topLeft = Offset(frameInset, frameInset),
                            size = Size(canvasWidth - frameInset * 2, canvasHeight - frameInset * 2),
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                        val innerInset = 14.dp.toPx()
                        drawRect(
                            color = NotebookPalette.VintageFiligree.copy(alpha = 0.25f),
                            topLeft = Offset(innerInset, innerInset),
                            size = Size(canvasWidth - innerInset * 2, canvasHeight - innerInset * 2),
                            style = Stroke(width = 0.8.dp.toPx())
                        )

                        // 3. Four corner antique flourishes (decorative corners)
                        val cornerSize = 22.dp.toPx()
                        val filigreeColor = NotebookPalette.VintageFiligree.copy(alpha = 0.6f)
                        // Top-Left
                        drawArc(
                            color = filigreeColor,
                            startAngle = 180f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(frameInset, frameInset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Top-Right
                        drawArc(
                            color = filigreeColor,
                            startAngle = 270f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(canvasWidth - frameInset - cornerSize, frameInset),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Bottom-Left
                        drawArc(
                            color = filigreeColor,
                            startAngle = 90f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(frameInset, canvasHeight - frameInset - cornerSize),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Bottom-Right
                        drawArc(
                            color = filigreeColor,
                            startAngle = 0f,
                            sweepAngle = 90f,
                            useCenter = false,
                            topLeft = Offset(canvasWidth - frameInset - cornerSize, canvasHeight - frameInset - cornerSize),
                            size = Size(cornerSize, cornerSize),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    PageFormat.MIDNIGHT -> {
                        // 1. Chalkboard dark frame border
                        val frameBorder = 5.dp.toPx()
                        drawRect(
                            color = NotebookPalette.MidnightFrame,
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight),
                            style = Stroke(width = frameBorder)
                        )

                        // 2. Subtle faint chalk grid
                        val gridSpacing = 28.dp.toPx()
                        var y = gridSpacing
                        while (y < canvasHeight) {
                            drawLine(
                                color = NotebookPalette.MidnightGrid,
                                start = Offset(frameBorder, y),
                                end = Offset(canvasWidth - frameBorder, y),
                                strokeWidth = 0.6.dp.toPx()
                            )
                            y += gridSpacing
                        }
                    }

                    PageFormat.BLUEPRINT -> {
                        val minorGrid = 16.dp.toPx()
                        val majorGrid = 80.dp.toPx()

                        // 1. Minor cyan grid
                        var y = minorGrid
                        while (y < canvasHeight) {
                            drawLine(
                                color = NotebookPalette.BlueprintLineMinor,
                                start = Offset(0f, y),
                                end = Offset(canvasWidth, y),
                                strokeWidth = 0.6.dp.toPx()
                            )
                            y += minorGrid
                        }
                        var x = minorGrid
                        while (x < canvasWidth) {
                            drawLine(
                                color = NotebookPalette.BlueprintLineMinor,
                                start = Offset(x, 0f),
                                end = Offset(x, canvasHeight),
                                strokeWidth = 0.6.dp.toPx()
                            )
                            x += minorGrid
                        }

                        // 2. Major cyan grid
                        var my = majorGrid
                        while (my < canvasHeight) {
                            drawLine(
                                color = NotebookPalette.BlueprintLineMajor,
                                start = Offset(0f, my),
                                end = Offset(canvasWidth, my),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            my += majorGrid
                        }
                        var mx = majorGrid
                        while (mx < canvasWidth) {
                            drawLine(
                                color = NotebookPalette.BlueprintLineMajor,
                                start = Offset(mx, 0f),
                                end = Offset(mx, canvasHeight),
                                strokeWidth = 1.2.dp.toPx()
                            )
                            mx += majorGrid
                        }

                        // 3. Technical outer border
                        drawRect(
                            color = NotebookPalette.BlueprintLineMajor,
                            topLeft = Offset(8.dp.toPx(), 8.dp.toPx()),
                            size = Size(canvasWidth - 16.dp.toPx(), canvasHeight - 16.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    PageFormat.BLANK -> {
                        // Subtle clean aesthetic border
                        drawRect(
                            color = Color(0x18000000),
                            topLeft = Offset.Zero,
                            size = Size(canvasWidth, canvasHeight),
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }
            }
    ) {
        // Decorative top bookmark for Book format
        if (format == PageFormat.BOOK) {
            BookBookmarkRibbon(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 22.dp)
            )
        }

        // Metal bulldog clip for Kraft format
        if (format == PageFormat.KRAFT) {
            KraftBinderClip(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 0.dp)
            )
        }

        // Spiral rings for ruled and grid formats
        if (format == PageFormat.RULED || format == PageFormat.GRID) {
            SpiralRings(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 6.dp)
            )
        }

        content()
    }
}

@Composable
fun PageFormatSelectorDialog(
    currentFormat: PageFormat,
    currentColorHex: String,
    onDismissRequest: () -> Unit,
    onFormatSelect: (PageFormat) -> Unit,
    onColorSelect: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = Icons.Filled.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Формат и цвет страницы",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Выберите дизайнерский стиль листа:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    PageFormat.values().forEach { format ->
                        val isSelected = format == currentFormat
                        val icon: ImageVector = when (format) {
                            PageFormat.BOOK -> Icons.Filled.AutoStories
                            PageFormat.RULED -> Icons.Filled.FormatAlignJustify
                            PageFormat.GRID -> Icons.Filled.BorderAll
                            PageFormat.KRAFT -> Icons.Filled.Style
                            PageFormat.VINTAGE -> Icons.Filled.Bookmark
                            PageFormat.MIDNIGHT -> Icons.Filled.DarkMode
                            PageFormat.BLUEPRINT -> Icons.Filled.Edit
                            PageFormat.BLANK -> Icons.Filled.Description
                        }

                        Card(
                            onClick = { onFormatSelect(format) },
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                }
                            ),
                            border = if (isSelected) {
                                androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                            } else null,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = format.title,
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = format.subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Выбрано",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "Оттенок бумаги страницы:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NoteColors.take(7).forEach { hex ->
                        val color = try { Color(android.graphics.Color.parseColor(hex)) } catch (_: Exception) { Color.LightGray }
                        val isColorSelected = currentColorHex.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(color)
                                .shadow(if (isColorSelected) 3.dp else 0.dp, CircleShape)
                                .clickable { onColorSelect(hex) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isColorSelected) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(10.dp)) {}
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Готово")
            }
        }
    )
}
