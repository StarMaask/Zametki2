package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.CorporateLetterhead
import kotlinx.coroutines.launch

@Composable
fun CorporateLetterheadDialog(
    preferencesManager: UserPreferencesManager,
    onDismissRequest: () -> Unit,
    onSaved: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    val savedLetterhead = remember { preferencesManager.getCorporateLetterheadSync() }

    var isEnabled by remember { mutableStateOf(savedLetterhead.isEnabled) }
    var organizationName by remember { mutableStateOf(savedLetterhead.organizationName) }
    var department by remember { mutableStateOf(savedLetterhead.department) }
    var innKppOgrn by remember { mutableStateOf(savedLetterhead.innKppOgrn) }
    var address by remember { mutableStateOf(savedLetterhead.address) }
    var contacts by remember { mutableStateOf(savedLetterhead.contacts) }
    var primaryColorHex by remember { mutableStateOf(savedLetterhead.primaryColorHex) }
    var showAccentLine by remember { mutableStateOf(savedLetterhead.showAccentLine) }
    var showVerificationQr by remember { mutableStateOf(savedLetterhead.showVerificationQr) }

    val presetColors = listOf(
        "#0D47A1" to "Глубокий синий (Классика)",
        "#1B5E20" to "Изумрудно-зеленый",
        "#B71C1C" to "Бордовый / Гербовый",
        "#263238" to "Графитовый / Стальной",
        "#4A148C" to "Королевский пурпур"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.90f)
                .padding(vertical = 12.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                            Icon(Icons.Filled.Business, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Фирменный бланк", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Колонтитул организации для PDF и документов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Enable Toggle
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Использовать фирменный бланк", fontWeight = FontWeight.Bold)
                                Text("Автоматически печатать шапку компании в PDF", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = isEnabled, onCheckedChange = { isEnabled = it })
                        }
                    }

                    // Live Preview Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0E0E0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            val activeColor = try {
                                Color(android.graphics.Color.parseColor(primaryColorHex))
                            } catch (_: Exception) {
                                Color(0xFF0D47A1)
                            }
                            Text(
                                text = organizationName.ifBlank { "НАИМЕНОВАНИЕ ОРГАНИЗАЦИИ" }.uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = activeColor,
                                fontSize = 14.sp
                            )
                            if (department.isNotBlank()) {
                                Text(text = department, fontSize = 11.sp, color = Color(0xFF424242))
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$innKppOgrn | $address | $contacts",
                                fontSize = 9.sp,
                                color = Color(0xFF757575),
                                lineHeight = 12.sp
                            )
                            if (showAccentLine) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(3.dp)
                                        .background(activeColor)
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = organizationName,
                        onValueChange = { organizationName = it },
                        label = { Text("Название организации / ВУЗа") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Подразделение / Департамент / Факультет") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = innKppOgrn,
                        onValueChange = { innKppOgrn = it },
                        label = { Text("Реквизиты (ИНН, КПП, ОГРН)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = address,
                        onValueChange = { address = it },
                        label = { Text("Юридический / почтовый адрес") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contacts,
                        onValueChange = { contacts = it },
                        label = { Text("Контакты (Телефон, Email, Web-сайт)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Accent Colors
                    Text("Цвет фирменного стиля:", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presetColors.forEach { (hex, _) ->
                            val color = Color(android.graphics.Color.parseColor(hex))
                            val isSelected = primaryColorHex.equals(hex, ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (isSelected) 3.dp else 1.dp,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        shape = CircleShape
                                    )
                                    .clickable { primaryColorHex = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Акцентная цветная разделительная полоса", fontSize = 13.sp)
                        Switch(checked = showAccentLine, onCheckedChange = { showAccentLine = it })
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismissRequest,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Отмена")
                    }
                    Button(
                        onClick = {
                            val newLetterhead = CorporateLetterhead(
                                isEnabled = isEnabled,
                                organizationName = organizationName.trim(),
                                department = department.trim(),
                                innKppOgrn = innKppOgrn.trim(),
                                address = address.trim(),
                                contacts = contacts.trim(),
                                primaryColorHex = primaryColorHex,
                                showAccentLine = showAccentLine,
                                showVerificationQr = showVerificationQr
                            )
                            scope.launch {
                                preferencesManager.saveCorporateLetterhead(newLetterhead)
                                onSaved()
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}
