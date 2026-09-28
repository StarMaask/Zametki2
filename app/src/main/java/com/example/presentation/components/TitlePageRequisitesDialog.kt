package com.example.presentation.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class TitlePageRequisites(
    val ministry: String = "МИНИСТЕРСТВО НАУКИ И ВЫСШЕГО ОБРАЗОВАНИЯ РОССИЙСКОЙ ФЕДЕРАЦИИ",
    val institution: String = "Федеральное государственное бюджетное образовательное учреждение высшего образования",
    val facultyAndDept: String = "Факультет естественных и инженерных наук\nКафедра фундаментальных дисциплин",
    val docType: String = "РЕФЕРАТ",
    val discipline: String = "",
    val topic: String = "",
    val author: String = "студент 3 курса группы ИВТ-301 Иванов И.И.",
    val supervisor: String = "научный руководитель, д.т.н., профессор Петров П.П.",
    val cityAndYear: String = "Москва, 2026"
) {
    fun toFormattedTitlePageText(): String {
        val sb = StringBuilder()
        if (ministry.isNotBlank()) sb.append(ministry.trim()).append("\n")
        if (institution.isNotBlank()) sb.append(institution.trim()).append("\n")
        if (facultyAndDept.isNotBlank()) {
            facultyAndDept.lines().map { it.trim() }.filter { it.isNotBlank() }.forEach {
                sb.append(it).append("\n")
            }
        }
        sb.append("\n")
        sb.append(docType.trim().uppercase()).append("\n")
        if (discipline.isNotBlank()) {
            val cleanDisc = discipline.trim().removePrefix("«").removeSuffix("»").removePrefix("\"").removeSuffix("\"")
            sb.append("по дисциплине: «").append(cleanDisc).append("»\n")
        }
        if (topic.isNotBlank()) {
            val cleanTopic = topic.trim().removePrefix("«").removeSuffix("»").removePrefix("\"").removeSuffix("\"")
            sb.append("на тему: «").append(cleanTopic).append("»\n")
        }
        sb.append("\n")
        if (author.isNotBlank()) {
            val cleanAuthor = author.trim()
            val line = if (!cleanAuthor.startsWith("Выполнил", ignoreCase = true)) "Выполнил: $cleanAuthor" else cleanAuthor
            sb.append(line).append("\n")
        }
        if (supervisor.isNotBlank()) {
            val cleanSup = supervisor.trim()
            val line = if (!cleanSup.startsWith("Проверил", ignoreCase = true)) "Проверил: $cleanSup" else cleanSup
            sb.append(line).append("\n")
        }
        sb.append("\n")
        sb.append(cityAndYear.trim().ifBlank { "Москва, 2026" }).append("\n")
        sb.append("--- РАЗРЫВ СТРАНИЦЫ ---\n")
        return sb.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TitlePageRequisitesDialog(
    initialRequisites: TitlePageRequisites = TitlePageRequisites(),
    onDismissRequest: () -> Unit,
    onApply: (TitlePageRequisites, formattedTitlePage: String) -> Unit
) {
    var ministry by remember { mutableStateOf(initialRequisites.ministry) }
    var institution by remember { mutableStateOf(initialRequisites.institution) }
    var facultyAndDept by remember { mutableStateOf(initialRequisites.facultyAndDept) }
    var docType by remember { mutableStateOf(initialRequisites.docType) }
    var discipline by remember { mutableStateOf(initialRequisites.discipline) }
    var topic by remember { mutableStateOf(initialRequisites.topic) }
    var author by remember { mutableStateOf(initialRequisites.author) }
    var supervisor by remember { mutableStateOf(initialRequisites.supervisor) }
    var cityAndYear by remember { mutableStateOf(initialRequisites.cityAndYear) }

    var docTypeDropdownExpanded by remember { mutableStateOf(false) }

    val docTypeOptions = listOf(
        "РЕФЕРАТ",
        "КУРСОВАЯ РАБОТА",
        "НАУЧНЫЙ ДОКЛАД / СООБЩЕНИЕ",
        "ОТЧЕТ О НАУЧНО-ИССЛЕДОВАТЕЛЬСКОЙ РАБОТЕ (НИР)",
        "ВЫПУСКНАЯ КВАЛИФИКАЦИОННАЯ РАБОТА (ДИПЛОМ / ВКР)",
        "МАГИСТЕРСКАЯ ДИССЕРТАЦИЯ",
        "НАУЧНАЯ СТАТЬЯ ВАК",
        "СЛУЖЕБНАЯ ЗАПИСКА",
        "ЗАЯВЛЕНИЕ",
        "ПРИКАЗ"
    )

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Badge,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Реквизиты титульного листа",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ГОСТ 7.32-2017 и требования ВУЗов",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismissRequest) {
                        Icon(Icons.Filled.Close, contentDescription = "Закрыть")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))

                // Scrollable Form Fields
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Document Type Selector
                    Text("Вид работы / документа:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedCard(
                            onClick = { docTypeDropdownExpanded = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = docType,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                        }

                        DropdownMenu(
                            expanded = docTypeDropdownExpanded,
                            onDismissRequest = { docTypeDropdownExpanded = false }
                        ) {
                            docTypeOptions.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        docType = type
                                        docTypeDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Topic
                    OutlinedTextField(
                        value = topic,
                        onValueChange = { topic = it },
                        label = { Text("Тема работы (заглавными или обычными буквами)") },
                        placeholder = { Text("Например: Физико-географическая характеристика города...") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Discipline
                    OutlinedTextField(
                        value = discipline,
                        onValueChange = { discipline = it },
                        label = { Text("Дисциплина / Предмет") },
                        placeholder = { Text("Например: Геоурбанистика и экология") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // University / Institution
                    OutlinedTextField(
                        value = institution,
                        onValueChange = { institution = it },
                        label = { Text("Учебное заведение / Организация") },
                        placeholder = { Text("ФГБОУ ВО «МГУ им. М.В. Ломоносова»") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Faculty & Department
                    OutlinedTextField(
                        value = facultyAndDept,
                        onValueChange = { facultyAndDept = it },
                        label = { Text("Факультет и Кафедра") },
                        placeholder = { Text("Факультет естественных наук\nКафедра экологии") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        maxLines = 3
                    )

                    // Author
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        label = { Text("Выполнил (ФИО, курс, группа / должность)") },
                        placeholder = { Text("студент 3 курса группы ИВТ-301 Иванов И.И.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // Supervisor
                    OutlinedTextField(
                        value = supervisor,
                        onValueChange = { supervisor = it },
                        label = { Text("Проверил / Научный руководитель (должность, степень, ФИО)") },
                        placeholder = { Text("научный руководитель, д.т.н., профессор Петров П.П.") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )

                    // City and Year
                    OutlinedTextField(
                        value = cityAndYear,
                        onValueChange = { cityAndYear = it },
                        label = { Text("Город и Год") },
                        placeholder = { Text("Москва, 2026") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                            val req = TitlePageRequisites(
                                ministry = ministry,
                                institution = institution,
                                facultyAndDept = facultyAndDept,
                                docType = docType,
                                discipline = discipline,
                                topic = topic,
                                author = author,
                                supervisor = supervisor,
                                cityAndYear = cityAndYear
                            )
                            onApply(req, req.toFormattedTitlePageText())
                        },
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Применить и вставить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
