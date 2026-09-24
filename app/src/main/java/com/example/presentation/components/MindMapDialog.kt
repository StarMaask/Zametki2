package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.util.MindMapGenerator
import com.example.util.MindMapNode
import com.example.util.MindMapNodeType
import kotlin.math.roundToInt

@Composable
fun MindMapDialog(
    noteTitle: String,
    noteContent: String,
    onDismissRequest: () -> Unit,
    onNavigateToOffset: ((Int) -> Unit)? = null
) {
    val graph = remember(noteTitle, noteContent) {
        MindMapGenerator.generate(noteTitle, noteContent)
    }

    var scale by remember { mutableFloatStateOf(0.9f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var selectedNode by remember { mutableStateOf<MindMapNode?>(null) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                // Interactive Pan & Zoom Area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.4f, 2.5f)
                                offset += pan
                            }
                        }
                ) {
                    val lineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    val secondaryLineColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)

                    // Canvas drawing curved connections
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerX = size.width / 2f + offset.x
                        val centerY = size.height / 2f + offset.y

                        for ((from, to) in graph.connections) {
                            val startX = centerX + from.x * scale
                            val startY = centerY + from.y * scale
                            val endX = centerX + to.x * scale
                            val endY = centerY + to.y * scale

                            val path = Path().apply {
                                moveTo(startX, startY)
                                val midX = (startX + endX) / 2f
                                val midY = (startY + endY) / 2f
                                quadraticTo(midX, startY, endX, endY)
                            }

                            drawPath(
                                path = path,
                                color = if (from.type == MindMapNodeType.ROOT) lineColor else secondaryLineColor,
                                style = Stroke(
                                    width = if (from.type == MindMapNodeType.ROOT) 3.dp.toPx() * scale else 2.dp.toPx() * scale,
                                    cap = StrokeCap.Round
                                )
                            )
                        }
                    }

                    // Node Components placed by coordinates
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val density = LocalDensity.current
                        val centerX = constraints.maxWidth / 2f + offset.x
                        val centerY = constraints.maxHeight / 2f + offset.y

                        for (node in graph.allNodes) {
                            val screenX = centerX + node.x * scale
                            val screenY = centerY + node.y * scale
                            val isSelected = selectedNode?.id == node.id

                            MindMapNodeView(
                                node = node,
                                scale = scale,
                                isSelected = isSelected,
                                modifier = Modifier
                                    .offset {
                                        IntOffset(
                                            x = (screenX - 70.dp.toPx() * scale).roundToInt(),
                                            y = (screenY - 24.dp.toPx() * scale).roundToInt()
                                        )
                                    }
                                    .clickable {
                                        selectedNode = if (isSelected) null else node
                                    }
                            )
                        }
                    }
                }

                // Top Floating Bar: Title & Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Hub,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "Интеллект-карта лекции",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${graph.allNodes.size} понятий и связей",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalIconButton(
                            onClick = {
                                scale = 0.9f
                                offset = Offset.Zero
                            }
                        ) {
                            Icon(Icons.Filled.FilterCenterFocus, contentDescription = "Центрировать")
                        }
                        FilledTonalIconButton(
                            onClick = onDismissRequest
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                        }
                    }
                }

                // Bottom Node Detail Card (When tapped)
                Column(
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    AnimatedVisibility(
                        visible = selectedNode != null,
                        enter = slideInVertically { it },
                        exit = slideOutVertically { it }
                    ) {
                        selectedNode?.let { node ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surface,
                                shadowElevation = 8.dp,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(getNodeColor(node.type).copy(alpha = 0.2f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = getNodeIcon(node.type),
                                                    contentDescription = null,
                                                    tint = getNodeColor(node.type),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(10.dp))
                                            Column {
                                                Text(
                                                    text = node.text,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = node.subtext,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        IconButton(onClick = { selectedNode = null }) {
                                            Icon(Icons.Filled.Close, contentDescription = "Скрыть")
                                        }
                                    }

                                    if (onNavigateToOffset != null && node.textOffsetInContent > 0) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Button(
                                            onClick = {
                                                onNavigateToOffset(node.textOffsetInContent)
                                                onDismissRequest()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp)
                                        ) {
                                            Icon(Icons.Filled.NorthEast, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("Перейти к разделу в конспекте")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MindMapNodeView(
    node: MindMapNode,
    scale: Float,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val nodeColor = getNodeColor(node.type)
    val containerColor = if (isSelected) nodeColor else MaterialTheme.colorScheme.surface
    val contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface

    Surface(
        modifier = modifier
            .widthIn(min = 100.dp * scale, max = 180.dp * scale)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else nodeColor.copy(alpha = 0.5f),
                shape = RoundedCornerShape(16.dp)
            ),
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        shadowElevation = if (isSelected) 6.dp else 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (10 * scale).dp, vertical = (6 * scale).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = getNodeIcon(node.type),
                contentDescription = null,
                tint = if (isSelected) Color.White else nodeColor,
                modifier = Modifier.size((16 * scale).dp)
            )
            Spacer(modifier = Modifier.width((6 * scale).dp))
            Text(
                text = node.text,
                fontSize = (12 * scale).sp,
                fontWeight = if (node.type == MindMapNodeType.ROOT) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun getNodeColor(type: MindMapNodeType): Color = when (type) {
    MindMapNodeType.ROOT -> Color(0xFF6750A4)
    MindMapNodeType.SECTION -> Color(0xFF0288D1)
    MindMapNodeType.DEFINITION -> Color(0xFF2E7D32)
    MindMapNodeType.QUESTION -> Color(0xFFE65100)
    MindMapNodeType.ACTION_ITEM -> Color(0xFFD32F2F)
    MindMapNodeType.KEY_POINT -> Color(0xFF7B1FA2)
}

private fun getNodeIcon(type: MindMapNodeType) = when (type) {
    MindMapNodeType.ROOT -> Icons.Filled.AutoStories
    MindMapNodeType.SECTION -> Icons.Filled.Topic
    MindMapNodeType.DEFINITION -> Icons.Filled.MenuBook
    MindMapNodeType.QUESTION -> Icons.Filled.HelpOutline
    MindMapNodeType.ACTION_ITEM -> Icons.Filled.TaskAlt
    MindMapNodeType.KEY_POINT -> Icons.Filled.Lightbulb
}
