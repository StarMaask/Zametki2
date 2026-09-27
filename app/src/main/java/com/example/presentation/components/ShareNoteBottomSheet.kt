package com.example.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Note
import com.example.util.ShareExportUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareNoteBottomSheet(
    note: Note,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPdfDialog by remember { mutableStateOf(false) }
    var showSignatureDialog by remember { mutableStateOf(false) }

    val preferencesManager = remember { com.example.data.preferences.UserPreferencesManager(context) }
    var hasSignature by remember { mutableStateOf(com.example.util.SignatureManager.hasSignature(context)) }
    var isSignatureEnabled by remember { mutableStateOf(hasSignature) }

    if (showSignatureDialog) {
        SignaturePadDialog(
            preferencesManager = preferencesManager,
            onDismissRequest = {
                showSignatureDialog = false
                hasSignature = com.example.util.SignatureManager.hasSignature(context)
                if (hasSignature) isSignatureEnabled = true
            },
            onSignatureSaved = {
                hasSignature = true
                isSignatureEnabled = true
            }
        )
    }

    if (showPdfDialog) {
        PdfExportDialog(
            note = note,
            onDismissRequest = {
                showPdfDialog = false
                onDismissRequest()
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Share,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Поделиться заметкой",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = note.title.ifBlank { "Без названия" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Signature Quick Status Card
            Surface(
                color = if (hasSignature && isSignatureEnabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable {
                        if (hasSignature) {
                            isSignatureEnabled = !isSignatureEnabled
                        } else {
                            showSignatureDialog = true
                        }
                    },
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = if (hasSignature && isSignatureEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Draw,
                            contentDescription = null,
                            tint = if (hasSignature && isSignatureEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Рукописная подпись",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                if (hasSignature) {
                                    Surface(
                                        color = if (isSignatureEnabled) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = if (isSignatureEnabled) "ВКЛ" else "ВЫКЛ",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (hasSignature) {
                                    if (isSignatureEnabled) "Автоматически вставляется в Word и PDF" else "Отключена для текущего экспорта"
                                } else "Нажмите, чтобы нарисовать подпись пальцем/стилусом",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (hasSignature) {
                        Switch(
                            checked = isSignatureEnabled,
                            onCheckedChange = { isSignatureEnabled = it },
                            modifier = Modifier.scale(0.85f)
                        )
                    } else {
                        FilledTonalButton(
                            onClick = { showSignatureDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Создать", fontSize = 11.sp)
                        }
                    }
                }
            }

            ShareOptionItem(
                icon = Icons.Filled.PictureAsPdf,
                title = "Документ PDF (.pdf)",
                subtitle = "ГОСТ Р 7.0.97-2016, шапка справа, центрированный заголовок, красная строка, печать A4",
                badge = "PDF",
                onClick = {
                    showPdfDialog = true
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.Description,
                title = "Документ Word (.docx)",
                subtitle = if (hasSignature && isSignatureEnabled)
                    "Современный стандарт MS Word / Google Docs (ГОСТ + электронная подпись)"
                else
                    "Современный стандарт MS Word / Google Docs (ГОСТ, шапка справа, 1.25 см, 1.5 инт.)",
                badge = if (hasSignature && isSignatureEnabled) "DOCX ✍️" else "DOCX",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsDocx(context, note, isSignatureEnabled)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.Article,
                title = "Документ Word (.doc / RTF)",
                subtitle = "Классический формат .doc (без ошибок повреждения, открывается в любых редакторах)",
                badge = "DOC",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsDoc(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.TableChart,
                title = "Таблица Excel (.xls / .xlsx)",
                subtitle = "Таблицы, задачи и метаданные по колонкам с сеткой",
                badge = "Excel",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsExcel(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.Send,
                title = "Текст заметки",
                subtitle = "Отправить текст в Telegram, WhatsApp, SMS, почту",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsText(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.PhotoLibrary,
                title = "Листок блокнота (Картинка / Фото)",
                subtitle = "Красивое фото страницы с оформлением, линиями и шрифтом",
                badge = "Изображение",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsNotebookSheet(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.Code,
                title = "Документ Markdown (.md)",
                subtitle = "Для Obsidian, Notion, GitHub, Typora и баз знаний",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsMarkdownFile(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.ContentCopy,
                title = "Скопировать в буфер обмена",
                subtitle = "Скопировать полный текст заметки в буфер устройства",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.copyToClipboard(context, note)
                }
            )
        }
    }
}

@Composable
private fun ShareOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (badge != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        ) {
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
