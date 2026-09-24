package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage

@Composable
fun FullscreenImageViewerDialog(
    imageUri: String,
    onDismissRequest: () -> Unit,
    onDeleteImage: (() -> Unit)? = null,
    onDigitizeHandwriting: (() -> Unit)? = null,
    onExtractText: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
        ) {
            // Interactive Zoomable / Pannable Image
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale > 1f) {
                                offsetX += pan.x
                                offsetY += pan.y
                            } else {
                                offsetX = 0f
                                offsetY = 0f
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUri,
                    contentDescription = "Просмотр рисунка на весь экран",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY
                        )
                )
            }

            // Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(44.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (onExtractText != null) {
                        IconButton(
                            onClick = {
                                onExtractText()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DocumentScanner,
                                contentDescription = "Распознать текст с фото",
                                tint = Color(0xFF81D4FA)
                            )
                        }
                    }
                    if (onDigitizeHandwriting != null) {
                        IconButton(
                            onClick = {
                                onDigitizeHandwriting()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = "Оцифровать почерк с фото",
                                tint = Color(0xFFFFD54F)
                            )
                        }
                    }
                    if (onDeleteImage != null) {
                        IconButton(
                            onClick = {
                                onDeleteImage()
                                onDismissRequest()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Удалить рисунок",
                                tint = Color(0xFFFF5252)
                            )
                        }
                    }
                }
            }
        }
    }
}
