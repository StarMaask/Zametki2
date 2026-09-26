package com.example.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun MarkdownRenderer(
    markdownText: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    fontFamily: FontFamily = FontFamily.Default
) {
    val lines = markdownText.lines()
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        var lineIndex = 0
        while (lineIndex < lines.size) {
            val line = lines[lineIndex]
            val trimmed = line.trim()

            if (trimmed.startsWith("```")) {
                if (inCodeBlock) {
                    // End code block
                    CodeBlock(code = codeBlockLines.joinToString("\n"))
                    codeBlockLines.clear()
                    inCodeBlock = false
                } else {
                    // Start code block
                    inCodeBlock = true
                }
                lineIndex++
                continue
            }

            if (inCodeBlock) {
                codeBlockLines.add(line)
                lineIndex++
                continue
            }

            // Check if this line is part of a markdown table: | ... |
            if (trimmed.startsWith("|") && trimmed.endsWith("|") && trimmed.length > 2) {
                val tableLines = mutableListOf<String>()
                while (lineIndex < lines.size && lines[lineIndex].trim().startsWith("|") && lines[lineIndex].trim().endsWith("|")) {
                    tableLines.add(lines[lineIndex].trim())
                    lineIndex++
                }
                if (tableLines.isNotEmpty()) {
                    RenderMarkdownTable(tableLines, textColor, fontFamily)
                }
                continue
            }

            // 1. Right Header Block for Official Documents (ГОСТ Р 7.0.97-2016)
            if (lineIndex == 0 && (trimmed.startsWith("Кому:", ignoreCase = true) ||
                        trimmed.startsWith("От кого:", ignoreCase = true) ||
                        trimmed.startsWith("Директору", ignoreCase = true) ||
                        trimmed.startsWith("Руководителю", ignoreCase = true) ||
                        trimmed.startsWith("Генеральному", ignoreCase = true) ||
                        trimmed.startsWith("Ректору", ignoreCase = true) ||
                        trimmed.startsWith("Начальнику", ignoreCase = true))) {
                val headerLines = mutableListOf<String>()
                while (lineIndex < lines.size) {
                    val cur = lines[lineIndex].trim()
                    val upper = cur.uppercase().removePrefix("#").trim()
                    if (upper == "ЗАЯВЛЕНИЕ" || upper == "СЛУЖЕБНАЯ ЗАПИСКА" || upper == "АКТ" ||
                        upper == "ПРОТОКОЛ" || upper == "ОБЪЯСНИТЕЛЬНАЯ ЗАПИСКА" || upper == "ПРЕТЕНЗИЯ" ||
                        (cur.startsWith("# ") && upper.length < 50)) {
                        break
                    }
                    if (cur.isNotBlank()) {
                        headerLines.add(cur)
                    }
                    lineIndex++
                }
                if (headerLines.isNotEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.58f)
                                .align(Alignment.TopEnd)
                        ) {
                            headerLines.forEach { hLine ->
                                Text(
                                    text = parseInlineMarkdown(hLine, textColor),
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                    color = textColor,
                                    fontFamily = fontFamily,
                                    lineHeight = 17.sp
                                )
                            }
                        }
                    }
                }
                continue
            }

            // 2. Centered Official Title
            val officialTitles = setOf("ЗАЯВЛЕНИЕ", "СЛУЖЕБНАЯ ЗАПИСКА", "ДОКЛАДНАЯ ЗАПИСКА", "ОБЪЯСНИТЕЛЬНАЯ ЗАПИСКА", "АКТ", "АКТ ПРИЁМА-ПЕРЕДАЧИ", "АКТ ПРИЕМА-ПЕРЕДАЧИ", "ПРОТОКОЛ", "ПРЕТЕНЗИЯ")
            if (officialTitles.contains(trimmed.uppercase())) {
                Text(
                    text = trimmed.uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = fontFamily,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp)
                )
                lineIndex++
                continue
            }

            // 3. Official Signatures Footer (Date on left, Signature on right)
            if (trimmed.startsWith("Дата:", ignoreCase = true) || trimmed.startsWith("«___»")) {
                var nextSigLine: String? = null
                var sigIndex = lineIndex + 1
                while (sigIndex < lines.size) {
                    val nextL = lines[sigIndex].trim()
                    if (nextL.isNotBlank()) {
                        if (nextL.contains("Подпись", ignoreCase = true) || nextL.contains("____________ /")) {
                            nextSigLine = nextL
                        }
                        break
                    }
                    sigIndex++
                }

                if (nextSigLine != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = parseInlineMarkdown(trimmed, textColor),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = textColor,
                            fontFamily = fontFamily
                        )
                        Text(
                            text = parseInlineMarkdown(nextSigLine, textColor),
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = textColor,
                            fontFamily = fontFamily
                        )
                    }
                    lineIndex = sigIndex + 1
                    continue
                }
            }

            when {
                trimmed.startsWith("### ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("### "), textColor),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("## ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("## "), textColor),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                }
                trimmed.startsWith("# ") -> {
                    Text(
                        text = parseInlineMarkdown(trimmed.removePrefix("# "), textColor),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = MaterialTheme.colorScheme.primary,
                        fontFamily = fontFamily,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                    )
                }
                trimmed.startsWith("> ") -> {
                    // Blockquote
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(26.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(trimmed.removePrefix("> "), textColor.copy(alpha = 0.85f)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                            fontFamily = fontFamily
                        )
                    }
                }
                trimmed.startsWith("- [ ] ") || trimmed.startsWith("- [x] ") || trimmed.startsWith("- [X] ") -> {
                    val isChecked = !trimmed.startsWith("- [ ] ")
                    val taskText = trimmed.substring(6)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isChecked) "☑" else "☐",
                            fontSize = 16.sp,
                            color = if (isChecked) MaterialTheme.colorScheme.primary else textColor.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = parseInlineMarkdown(taskText, if (isChecked) textColor.copy(alpha = 0.5f) else textColor),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                textDecoration = if (isChecked) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            fontFamily = fontFamily
                        )
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("• ") -> {
                    val bulletText = trimmed.substring(2)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = "•",
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp, end = 8.dp)
                        )
                        Text(
                            text = parseInlineMarkdown(bulletText, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = fontFamily,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                trimmed == "---" || trimmed == "***" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .height(1.dp)
                            .background(textColor.copy(alpha = 0.15f))
                    )
                }
                trimmed.isBlank() -> {
                    Spacer(modifier = Modifier.height(6.dp))
                }
                else -> {
                    // Regular paragraph text
                    Text(
                        text = parseInlineMarkdown(line, textColor),
                        style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                        fontFamily = fontFamily,
                        color = textColor
                    )
                }
            }
            lineIndex++
        }
    }
}

@Composable
private fun RenderMarkdownTable(
    tableLines: List<String>,
    textColor: Color,
    fontFamily: FontFamily
) {
    if (tableLines.isEmpty()) return

    fun splitRow(line: String): List<String> {
        val trimmed = line.removePrefix("|").removeSuffix("|")
        return trimmed.split("|").map { it.trim() }
    }

    val firstRow = splitRow(tableLines[0])
    val isSecondRowSeparator = tableLines.size > 1 && tableLines[1].replace(" ", "").removePrefix("|").removeSuffix("|").split("|").all { cell -> cell.all { it == '-' || it == ':' } }

    val headers = firstRow
    val dataRowStart = if (isSecondRowSeparator) 2 else 1
    val rows = tableLines.drop(dataRowStart).map { splitRow(it) }

    MarkdownTable(
        headers = headers,
        rows = rows,
        textColor = textColor,
        fontFamily = fontFamily
    )
}

@Composable
private fun MarkdownTable(
    headers: List<String>,
    rows: List<List<String>>,
    textColor: Color,
    fontFamily: FontFamily
) {
    val hScroll = rememberScrollState()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .horizontalScroll(hScroll)
                .fillMaxWidth()
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                headers.forEach { header ->
                    Text(
                        text = parseInlineMarkdown(header, MaterialTheme.colorScheme.primary),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        fontFamily = fontFamily,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .widthIn(min = 100.dp, max = 240.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)

            // Data Rows
            rows.forEachIndexed { rowIndex, row ->
                val rowBg = if (rowIndex % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    headers.indices.forEach { colIndex ->
                        val cellText = if (colIndex < row.size) row[colIndex] else ""
                        Text(
                            text = parseInlineMarkdown(cellText, textColor),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = fontFamily,
                            color = textColor,
                            modifier = Modifier
                                .widthIn(min = 100.dp, max = 240.dp)
                                .padding(horizontal = 8.dp)
                        )
                    }
                }
                if (rowIndex < rows.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
private fun CodeBlock(code: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

fun parseInlineMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // 1. Color tag [color=#HEX]text[/color]
            if (text.startsWith("[color=#", i)) {
                val endTag = text.indexOf("[/color]", i)
                val closeBracket = text.indexOf(']', i)
                if (endTag != -1 && closeBracket != -1 && closeBracket < endTag) {
                    val hex = text.substring(i + 7, closeBracket)
                    val inner = text.substring(closeBracket + 1, endTag)
                    try {
                        val parsedColor = Color(android.graphics.Color.parseColor(hex))
                        withStyle(SpanStyle(color = parsedColor)) {
                            append(inner)
                        }
                        i = endTag + 8
                        continue
                    } catch (_: Exception) {}
                }
            }

            // 2. Bold (**text**)
            if (i + 1 < text.length && text[i] == '*' && text[i + 1] == '*') {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = defaultColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 3. Strikethrough (~~text~~)
            if (i + 1 < text.length && text[i] == '~' && text[i + 1] == '~') {
                val end = text.indexOf("~~", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough, color = defaultColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 4. Underline (<u>text</u>)
            if (text.startsWith("<u>", i, ignoreCase = true)) {
                val end = text.indexOf("</u>", i + 3, ignoreCase = true)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = defaultColor)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 4
                    continue
                }
            }

            // 5. Underline (__text__)
            if (i + 1 < text.length && text[i] == '_' && text[i + 1] == '_') {
                val end = text.indexOf("__", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline, color = defaultColor)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                    continue
                }
            }

            // 6. Highlight (==text==)
            if (i + 1 < text.length && text[i] == '=' && text[i + 1] == '=') {
                val end = text.indexOf("==", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(background = Color(0xFFFEF08A), color = Color(0xFF1E293B))) {
                        append(" ${text.substring(i + 2, end)} ")
                    }
                    i = end + 2
                    continue
                }
            }

            // 7. Italic (*text*)
            if (text[i] == '*') {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = defaultColor)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                    continue
                }
            }

            // 8. Inline code (`text`)
            if (text[i] == '`') {
                val end = text.indexOf('`', i + 1)
                if (end != -1) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = defaultColor.copy(alpha = 0.1f),
                            fontWeight = FontWeight.Medium
                        )
                    ) {
                        append(" ${text.substring(i + 1, end)} ")
                    }
                    i = end + 1
                    continue
                }
            }

            append(text[i])
            i++
        }
    }
}
