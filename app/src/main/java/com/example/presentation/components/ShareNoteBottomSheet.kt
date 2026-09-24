package com.example.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
                badge = "Популярно",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsNotebookSheet(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.PictureAsPdf,
                title = "Документ PDF (.pdf)",
                subtitle = "Формат A4 для печати, архива и официальной отправки",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsPdf(context, note)
                }
            )

            ShareOptionItem(
                icon = Icons.Filled.Description,
                title = "Текстовый файл (.txt)",
                subtitle = "Файл документа для компьютера и текстовых редакторов",
                onClick = {
                    onDismissRequest()
                    ShareExportUtil.shareAsTxtFile(context, note)
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
