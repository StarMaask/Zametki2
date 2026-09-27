package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.preferences.UserPreferencesManager
import com.example.domain.model.UserProfileRequisites
import kotlinx.coroutines.launch

@Composable
fun UserProfileRequisitesDialog(
    preferencesManager: UserPreferencesManager,
    onDismissRequest: () -> Unit,
    onSaved: (UserProfileRequisites) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val currentProfile by preferencesManager.userProfileFlow.collectAsState(initial = UserProfileRequisites())

    var fullName by remember { mutableStateOf(currentProfile.fullName) }
    var fullNameGenitive by remember { mutableStateOf(currentProfile.fullNameGenitive) }
    var shortName by remember { mutableStateOf(currentProfile.shortName) }
    var position by remember { mutableStateOf(currentProfile.position) }
    var positionGenitive by remember { mutableStateOf(currentProfile.positionGenitive) }
    var organization by remember { mutableStateOf(currentProfile.organization) }
    var department by remember { mutableStateOf(currentProfile.department) }
    var phone by remember { mutableStateOf(currentProfile.phone) }
    var email by remember { mutableStateOf(currentProfile.email) }
    var address by remember { mutableStateOf(currentProfile.address) }
    var passport by remember { mutableStateOf(currentProfile.passport) }

    var showMoreFields by remember { mutableStateOf(false) }

    // Live preview of requisites
    val previewRequisites = remember(
        fullName, fullNameGenitive, shortName, position, positionGenitive,
        organization, department, phone, email, address, passport
    ) {
        UserProfileRequisites(
            fullName = fullName.trim(),
            fullNameGenitive = fullNameGenitive.trim(),
            shortName = shortName.trim(),
            position = position.trim(),
            positionGenitive = positionGenitive.trim(),
            organization = organization.trim(),
            department = department.trim(),
            phone = phone.trim(),
            email = email.trim(),
            address = address.trim(),
            passport = passport.trim()
        )
    }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .padding(vertical = 12.dp)
                .testTag("user_profile_requisites_dialog"),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                            Icon(
                                imageVector = Icons.Filled.Badge,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Мои реквизиты",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Автозаполнение блока «От кого:» по ГОСТ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Form
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    // Preview Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Visibility,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Предпросмотр в документе:",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFFFAF9F6),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFE0E0E0))
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    val headerLines = previewRequisites.buildHeaderFromLines()
                                    headerLines.forEach { line ->
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                            color = Color(0xFF222222)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = previewRequisites.buildSignatureLine(),
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp),
                                        color = Color(0xFF444444)
                                    )
                                }
                            }
                        }
                    }

                    // Main Fields
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            if (fullNameGenitive.isBlank() || fullNameGenitive == UserProfileRequisites.autoGenitiveName(fullName)) {
                                fullNameGenitive = UserProfileRequisites.autoGenitiveName(it)
                            }
                            if (shortName.isBlank() || shortName == UserProfileRequisites.autoShortName(fullName)) {
                                shortName = UserProfileRequisites.autoShortName(it)
                            }
                        },
                        label = { Text("ФИО полностью (Именительный падеж)") },
                        placeholder = { Text("Иванов Иван Иванович") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = fullNameGenitive,
                        onValueChange = { fullNameGenitive = it },
                        label = { Text("ФИО в родительном падеже (От кого:)") },
                        placeholder = { Text("Иванова Ивана Ивановича") },
                        supportingText = { Text("Используется в шапке официальных заявлений") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = shortName,
                        onValueChange = { shortName = it },
                        label = { Text("Фамилия и инициалы (для подписи)") },
                        placeholder = { Text("Иванов И.И.") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = position,
                        onValueChange = {
                            position = it
                            if (positionGenitive.isBlank()) {
                                positionGenitive = it.lowercase()
                            }
                        },
                        label = { Text("Должность / статус") },
                        placeholder = { Text("Ведущий специалист / Студент") },
                        leadingIcon = { Icon(Icons.Filled.Work, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = positionGenitive,
                        onValueChange = { positionGenitive = it },
                        label = { Text("Должность в род. падеже (От кого: кого?)") },
                        placeholder = { Text("ведущего специалиста / студента гр. 401") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = organization,
                        onValueChange = { organization = it },
                        label = { Text("Организация / Компания / ВУЗ") },
                        placeholder = { Text("ООО «Технологии»") },
                        leadingIcon = { Icon(Icons.Filled.Business, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Подразделение / Отдел / Факультет") },
                        placeholder = { Text("Отдел разработки / Факультет ВМК") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Expandable additional fields
                    TextButton(
                        onClick = { showMoreFields = !showMoreFields },
                        modifier = Modifier.align(Alignment.Start)
                    ) {
                        Icon(
                            if (showMoreFields) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (showMoreFields) "Скрыть контакты и паспорт" else "Дополнительно: контакты, адрес, паспорт")
                    }

                    if (showMoreFields) {
                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = phone,
                            onValueChange = { phone = it },
                            label = { Text("Контактный телефон") },
                            placeholder = { Text("+7 (999) 123-45-67") },
                            leadingIcon = { Icon(Icons.Filled.Phone, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Электронная почта") },
                            placeholder = { Text("ivanov@example.com") },
                            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = address,
                            onValueChange = { address = it },
                            label = { Text("Адрес проживания / регистрации") },
                            placeholder = { Text("г. Москва, ул. Ленина, д. 10, кв. 5") },
                            leadingIcon = { Icon(Icons.Filled.Home, contentDescription = null) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = passport,
                            onValueChange = { passport = it },
                            label = { Text("Паспортные данные (для юр. заявлений)") },
                            placeholder = { Text("серия 4510 № 123456 выдан...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Footer Actions
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
                            val profile = previewRequisites
                            scope.launch {
                                preferencesManager.saveUserProfile(profile)
                                onSaved(profile)
                                onDismissRequest()
                            }
                        },
                        modifier = Modifier.weight(1.5f),
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
